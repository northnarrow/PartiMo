package com.partimo.data.remote.osm

import com.partimo.data.network.firstSuccessful
import com.partimo.data.network.isTransient
import com.partimo.domain.model.GeoPoint
import io.ktor.client.HttpClient
import io.ktor.client.plugins.retry
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.parameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

/**
 * Client minimale della Overpass API di OpenStreetMap: gratuita e senza chiave. I dati sono
 * © OpenStreetMap contributors (licenza ODbL) e vanno citati nell'app.
 *
 * Le istanze pubbliche chiedono uno User-Agent che identifichi l'app e un uso leggero (le risposte
 * restano in cache per giorni) e accettano pochissime richieste contemporanee dallo stesso indirizzo: le
 * query dell'app passano una alla volta. Se un'istanza non risponde entro [hedgeAfterMillis] parte la stessa
 * query sulla successiva e vince la prima risposta completa; se un'istanza è sovraccarica (429, 5xx, timeout,
 * query interrotta) si passa subito alla successiva.
 * https://wiki.openstreetmap.org/wiki/Overpass_API#Public_Overpass_API_instances
 */
internal class OverpassApi(
    private val client: HttpClient,
    private val userAgent: String,
    private val endpoints: List<String> = DEFAULT_ENDPOINTS,
    private val hedgeAfterMillis: Long = HEDGE_AFTER_MILLIS,
) {
    private val mutex = Mutex()

    init {
        require(endpoints.isNotEmpty()) { "Serve almeno un'istanza Overpass" }
    }

    suspend fun query(overpassQl: String): String = mutex.withLock {
        firstSuccessful(
            attempts = endpoints.map { endpoint -> suspend { post(endpoint, overpassQl) } },
            hedgeAfterMillis = hedgeAfterMillis,
            // Una richiesta sbagliata fallirebbe ovunque: si cambia istanza solo per i problemi dell'istanza.
            shouldTryNext = { it.isTransient() || it is IncompleteOverpassResponse },
        )
    }

    private suspend fun post(endpoint: String, overpassQl: String): String {
        val body = client.submitForm(url = endpoint, formParameters = parameters { append("data", overpassQl) }) {
            header(HttpHeaders.UserAgent, userAgent)
            // Invece di riprovare sulla stessa istanza (con attese crescenti) si passa alla successiva.
            retry { noRetry() }
            timeout { requestTimeoutMillis = ATTEMPT_TIMEOUT_MILLIS }
        }.bodyAsText()
        // Un'istanza sovraccarica può interrompere la query e rispondere comunque 200 con un elenco incompleto.
        if (INTERRUPTED.containsMatchIn(body)) throw IncompleteOverpassResponse(endpoint)
        return body
    }

    companion object {
        /** In ordine di affidabilità misurata: la principale, poi quella di VK (mail.ru), poi private.coffee. */
        val DEFAULT_ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
        )

        /** Di solito un'istanza risponde in 2–4 secondi: oltre questa attesa parte anche la successiva. */
        const val HEDGE_AFTER_MILLIS = 6_000L
        private const val ATTEMPT_TIMEOUT_MILLIS = 20_000L
        private val INTERRUPTED = Regex("\"remark\"\\s*:\\s*\"[^\"]*runtime error", RegexOption.IGNORE_CASE)
    }
}

/** L'istanza ha interrotto la query (tempo o memoria esauriti) e la risposta è incompleta: si prova altrove. */
internal class IncompleteOverpassResponse(endpoint: String) : IllegalArgumentException("Risposta Overpass incompleta da $endpoint")

/** Query Overpass QL usate dall'app: elementi con nome attorno a un punto, con il centro per le aree. */
internal object OverpassQueries {

    /** Il server si ferma prima del timeout del client: così una query lenta lascia spazio alle altre istanze. */
    private const val TIMEOUT_SECONDS = 18

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
