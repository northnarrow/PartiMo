package com.partimo.data.remote.osm

import com.partimo.domain.model.GeoPoint
import io.ktor.client.HttpClient
import io.ktor.client.plugins.retry
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.cos
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Ricerca di Nominatim, il motore di ricerca di OpenStreetMap: per gli elenchi di strutture risponde in meno di un
 * secondo anche quando le istanze Overpass sono sovraccariche. La policy d'uso chiede uno User-Agent che
 * identifichi l'app, risultati tenuti in cache e al massimo una richiesta al secondo: le richieste dell'app passano
 * una alla volta, distanziate di [minIntervalMillis].
 * https://operations.osmfoundation.org/policies/nominatim/
 */
internal class NominatimApi(
    private val client: HttpClient,
    private val userAgent: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val minIntervalMillis: Long = MIN_INTERVAL_MILLIS,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val mutex = Mutex()
    private var lastRequest: TimeMark? = null

    /**
     * Luoghi del tipo [phrase] (es. "hotel", una "special phrase" di Nominatim) dentro il quadrato di lato
     * 2 × [radiusMeters] attorno a [center], con i tag aggiuntivi (stelle, sito), l'indirizzo e i nomi in ogni lingua.
     */
    suspend fun search(phrase: String, center: GeoPoint, radiusMeters: Int, languageCode: String, limit: Int): String = mutex.withLock {
        lastRequest?.let { previous ->
            val wait = minIntervalMillis.milliseconds - previous.elapsedNow()
            if (wait.isPositive()) delay(wait)
        }
        // L'intervallo si conta dall'inizio di ogni richiesta, come chiede la policy (una al secondo).
        lastRequest = timeSource.markNow()
        client.get(baseUrl) {
            parameter("q", phrase)
            parameter("format", "jsonv2")
            parameter("viewbox", viewbox(center, radiusMeters))
            parameter("bounded", 1)
            parameter("limit", limit)
            parameter("extratags", 1)
            parameter("addressdetails", 1)
            parameter("namedetails", 1)
            parameter("accept-language", languageCode)
            header(HttpHeaders.UserAgent, userAgent)
            // Se Nominatim non risponde subito si passa a Overpass: niente nuovi tentativi qui.
            retry { noRetry() }
            timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS }
        }.bodyAsText()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://nominatim.openstreetmap.org/search"
        const val MIN_INTERVAL_MILLIS = 1_100L
        private const val REQUEST_TIMEOUT_MILLIS = 8_000L
        private const val METERS_PER_DEGREE = 111_320.0

        /** Quadrato attorno a [center] nel formato di Nominatim: "ovest,nord,est,sud". */
        fun viewbox(center: GeoPoint, radiusMeters: Int): String {
            val latitudeDelta = radiusMeters / METERS_PER_DEGREE
            val longitudeDelta = radiusMeters / (METERS_PER_DEGREE * cos(Math.toRadians(center.latitude))).coerceAtLeast(1.0)
            return String.format(
                Locale.ROOT,
                "%.5f,%.5f,%.5f,%.5f",
                center.longitude - longitudeDelta,
                center.latitude + latitudeDelta,
                center.longitude + longitudeDelta,
                center.latitude - latitudeDelta,
            )
        }
    }
}

// DTO della risposta di Nominatim (format=jsonv2): https://nominatim.org/release-docs/latest/api/Output/

@Serializable
internal data class NominatimPlace(
    @SerialName("osm_type") val osmType: String? = null,
    @SerialName("osm_id") val osmId: Long? = null,
    val lat: String? = null,
    val lon: String? = null,
    val category: String? = null,
    val type: String? = null,
    val name: String? = null,
    val address: Map<String, String> = emptyMap(),
    val extratags: Map<String, String>? = null,
    val namedetails: Map<String, String>? = null,
) {
    /**
     * Lo stesso luogo come elemento di Overpass, con i tag che l'app legge: nomi, tipo, stelle, sito, accessibilità
     * e via con il numero civico. Così le due fonti condividono interpretazione e formato in cache.
     */
    fun toOsmElement(): OsmElement? {
        val elementType = osmType?.takeIf { it in ELEMENT_TYPES } ?: return null
        val id = osmId ?: return null
        val latitude = lat?.toDoubleOrNull() ?: return null
        val longitude = lon?.toDoubleOrNull() ?: return null
        val tags = buildMap {
            extratags?.let(::putAll)
            namedetails?.let(::putAll)
            if ("name" !in this) name?.let { put("name", it) }
            if (category != null && type != null) put(category, type)
            address["road"]?.let { put("addr:street", it) }
            address["house_number"]?.let { put("addr:housenumber", it) }
        }
        return OsmElement(type = elementType, id = id, lat = latitude, lon = longitude, tags = tags)
    }
}

private val ELEMENT_TYPES = setOf("node", "way", "relation")
