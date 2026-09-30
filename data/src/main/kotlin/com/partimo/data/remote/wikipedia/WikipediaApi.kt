package com.partimo.data.remote.wikipedia

import com.partimo.domain.model.GeoPoint
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import java.util.Locale

/**
 * Client minimale della MediaWiki Action API di Wikipedia: gratuita e senza chiave.
 * Restituisce il body grezzo per consentirne la cache.
 *
 * Wikimedia chiede un User-Agent che identifichi l'app con un contatto: senza, le richieste
 * rientrano nel limite più basso (10 al minuto). Vedi https://www.mediawiki.org/wiki/Wikimedia_APIs/Rate_limits
 */
internal class WikipediaApi(
    private val client: HttpClient,
    private val userAgent: String,
    private val baseUrlFor: (language: String) -> String = { language -> "https://$language.wikipedia.org/w/api.php" },
) {

    /**
     * Voci con coordinate entro [radiusMeters] da [center], dalla più nota alla meno nota
     * (profilo di ranking basato su visite e link in entrata), con descrizione, foto e coordinate.
     */
    suspend fun nearbyPages(language: String, center: GeoPoint, radiusMeters: Int, limit: Int): String =
        query(language) {
            parameter("generator", "search")
            parameter("gsrsearch", nearCoordinates(center, radiusMeters))
            parameter("gsrqiprofile", POPULARITY_PROFILE)
            parameter("gsrlimit", limit)
            parameter("gsrnamespace", ARTICLE_NAMESPACE)
            parameter("prop", "coordinates|description|pageimages|pageprops")
            parameter("colimit", "max")
            parameter("piprop", "thumbnail|name")
            parameter("pithumbsize", CARD_IMAGE_WIDTH_PX)
            parameter("pilimit", "max")
            parameter("ppprop", "wikibase_item")
        }

    /** Voci che citano [text] entro [radiusMeters] da [center]: serve a trovare la voce di un luogo per nome. */
    suspend fun searchNearby(language: String, text: String, center: GeoPoint, radiusMeters: Int, limit: Int): String =
        query(language) {
            parameter("generator", "search")
            parameter("gsrsearch", "$text ${nearCoordinates(center, radiusMeters)}")
            parameter("gsrlimit", limit)
            parameter("gsrnamespace", ARTICLE_NAMESPACE)
            parameter("prop", "coordinates|pageprops")
            parameter("ppprop", "wikibase_item|disambiguation")
        }

    /** Testo completo della voce (con i titoli delle sezioni), foto principale, descrizione e URL. */
    suspend fun article(language: String, title: String): String =
        query(language) {
            parameter("titles", title)
            parameter("redirects", 1)
            parameter("prop", "extracts|pageimages|description|info|pageprops")
            parameter("explaintext", 1)
            parameter("exsectionformat", "wiki")
            parameter("piprop", "thumbnail|name")
            parameter("pithumbsize", DETAIL_IMAGE_WIDTH_PX)
            parameter("inprop", "url")
            parameter("ppprop", "disambiguation")
        }

    /** Autore e licenza di un file (le foto delle voci arrivano quasi sempre da Wikimedia Commons). */
    suspend fun imageInfo(language: String, fileName: String): String =
        query(language) {
            parameter("titles", "File:$fileName")
            parameter("prop", "imageinfo")
            parameter("iiprop", "extmetadata|url")
            parameter("iiextmetadatafilter", "Artist|LicenseShortName")
            parameter("iiextmetadatalanguage", language)
        }

    private suspend fun query(language: String, block: HttpRequestBuilder.() -> Unit): String =
        client.get(baseUrlFor(sanitizeLanguage(language))) {
            header(HttpHeaders.UserAgent, userAgent)
            parameter("action", "query")
            parameter("format", "json")
            parameter("formatversion", 2)
            block()
        }.bodyAsText()

    companion object {
        /** Larghezza delle foto nelle card: una delle misure standard servite dalla cache di Wikimedia. */
        const val CARD_IMAGE_WIDTH_PX = 960
        const val DETAIL_IMAGE_WIDTH_PX = 1280

        /** Ordina i risultati soprattutto per visite e link in entrata: i luoghi più noti vengono per primi. */
        const val POPULARITY_PROFILE = "popular_inclinks_pv"
        private const val ARTICLE_NAMESPACE = 0
        private val LANGUAGE_CODE = Regex("[a-z]{2,3}(-[a-z]+)?")

        /** Filtro geografico di CirrusSearch, es. "nearcoord:5000m,41.89330,12.48290". */
        fun nearCoordinates(center: GeoPoint, radiusMeters: Int): String =
            String.format(Locale.ROOT, "nearcoord:%dm,%.5f,%.5f", radiusMeters, center.latitude, center.longitude)

        /** Il codice lingua finisce nel nome host: si accettano solo codici plausibili. */
        fun sanitizeLanguage(language: String): String =
            language.lowercase(Locale.ROOT).takeIf { LANGUAGE_CODE.matches(it) } ?: "en"
    }
}
