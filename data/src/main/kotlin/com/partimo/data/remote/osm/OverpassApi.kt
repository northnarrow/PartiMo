package com.partimo.data.remote.osm

import com.partimo.data.network.isTransient
import com.partimo.domain.model.GeoPoint
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.parameters
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/**
 * Client minimale della Overpass API di OpenStreetMap: gratuita e senza chiave. I dati sono
 * © OpenStreetMap contributors (licenza ODbL) e vanno citati nell'app.
 *
 * Le istanze pubbliche chiedono uno User-Agent che identifichi l'app e un uso leggero (le risposte
 * restano in cache per ore). Se un'istanza è sovraccarica (429, 5xx, timeout) si prova la successiva.
 * https://wiki.openstreetmap.org/wiki/Overpass_API#Public_Overpass_API_instances
 */
internal class OverpassApi(
    private val client: HttpClient,
    private val userAgent: String,
    private val endpoints: List<String> = DEFAULT_ENDPOINTS,
) {

    init {
        require(endpoints.isNotEmpty()) { "Serve almeno un'istanza Overpass" }
    }

    suspend fun query(overpassQl: String): String {
        var lastError: Exception? = null
        for (endpoint in endpoints) {
            try {
                return client.submitForm(url = endpoint, formParameters = parameters { append("data", overpassQl) }) {
                    header(HttpHeaders.UserAgent, userAgent)
                }.bodyAsText()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Una richiesta sbagliata fallirebbe ovunque: si riprova solo per i problemi dell'istanza.
                if (!e.isTransient()) throw e
                lastError = e
            }
        }
        throw checkNotNull(lastError)
    }

    companion object {
        val DEFAULT_ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
        )
    }
}

/** Query Overpass QL usate dall'app: elementi con nome attorno a un punto, con il centro per le aree. */
internal object OverpassQueries {

    private const val TIMEOUT_SECONDS = 25

    /** Ristoranti, fast food e aree ristoro. */
    fun restaurants(center: GeoPoint, radiusMeters: Int, limit: Int): String =
        namedAround("amenity", "restaurant|fast_food|food_court", center, radiusMeters, limit)

    /** Hotel, ostelli, B&B/pensioni, appartamenti per vacanze e motel. */
    fun lodgings(center: GeoPoint, radiusMeters: Int, limit: Int): String =
        namedAround("tourism", "hotel|hostel|guest_house|apartment|motel", center, radiusMeters, limit)

    private fun namedAround(key: String, values: String, center: GeoPoint, radiusMeters: Int, limit: Int): String =
        String.format(
            Locale.ROOT,
            "[out:json][timeout:%d];nwr[\"%s\"~\"^(%s)$\"][\"name\"](around:%d,%.5f,%.5f);out center tags %d;",
            TIMEOUT_SECONDS,
            key,
            values,
            radiusMeters,
            center.latitude,
            center.longitude,
            limit,
        )
}
