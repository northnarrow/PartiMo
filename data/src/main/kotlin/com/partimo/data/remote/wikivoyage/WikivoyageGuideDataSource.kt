package com.partimo.data.remote.wikivoyage

import com.partimo.data.cache.CacheKey
import com.partimo.data.cache.CachePolicy
import com.partimo.data.cache.ResponseCache
import com.partimo.data.network.Fetched
import com.partimo.data.network.combinedOrigin
import com.partimo.data.remote.wikipedia.WikiPage
import com.partimo.data.remote.wikipedia.WikipediaApi
import com.partimo.data.remote.wikipedia.parsePages
import com.partimo.data.source.TravelGuideDataSource
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.model.Destination
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.guide.GuideSection
import com.partimo.domain.model.guide.TravelGuide
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders

/**
 * Client minimale di Wikivoyage (stessa MediaWiki Action API di Wikipedia): gratuito, senza chiave,
 * testi CC BY-SA. Come per Wikipedia serve uno User-Agent identificabile.
 */
internal class WikivoyageApi(
    private val client: HttpClient,
    private val userAgent: String,
    private val baseUrlFor: (language: String) -> String = { language -> "https://$language.wikivoyage.org/w/api.php" },
) {

    /** Testo completo della guida [title] (con i titoli nel formato "== Come arrivare =="), coordinate e indirizzo. */
    suspend fun guide(language: String, title: String): String =
        client.get(baseUrlFor(WikipediaApi.sanitizeLanguage(language))) {
            header(HttpHeaders.UserAgent, userAgent)
            parameter("action", "query")
            parameter("format", "json")
            parameter("formatversion", 2)
            parameter("titles", title)
            parameter("redirects", 1)
            parameter("prop", "extracts|coordinates|info|pageprops")
            parameter("explaintext", 1)
            parameter("exsectionformat", "wiki")
            parameter("inprop", "url")
            parameter("ppprop", "disambiguation")
        }.bodyAsText()
}

/**
 * Guida della città da Wikivoyage: prima nella lingua dell'app, altrimenti in inglese (la versione
 * più completa). La pagina deve riguardare davvero la meta: niente disambiguazioni e, se la pagina ha
 * coordinate, entro [MAX_DISTANCE_METERS] dal centro.
 */
internal class WikivoyageGuideDataSource(
    private val api: WikivoyageApi,
    private val cache: ResponseCache,
    private val languages: List<String>,
) : TravelGuideDataSource {

    override suspend fun guide(destination: Destination, forceRefresh: Boolean): Fetched<TravelGuide?> {
        val origins = mutableListOf<DataOrigin>()
        for (language in languages) {
            val page = cache.getOrFetch(
                key = CacheKey.of("wikivoyage", language, destination.name),
                ttl = CachePolicy.GUIDES,
                forceRefresh = forceRefresh,
                fetch = { api.guide(language, destination.name) },
                parse = { body -> parsePages(body).firstOrNull() },
            )
            origins += page.origin
            val usable = page.data?.takeIf { it.describes(destination) }
            if (usable != null) return Fetched(usable.toGuide(language), combinedOrigin(origins))
        }
        return Fetched(null, combinedOrigin(origins))
    }

    private fun WikiPage.describes(destination: Destination): Boolean {
        if (missing || pageprops?.disambiguation != null || extract.isNullOrBlank()) return false
        val coordinate = coordinates.firstOrNull() ?: return true
        val distance = runCatching { destination.center.distanceTo(GeoPoint(coordinate.lat, coordinate.lon)) }.getOrNull() ?: return false
        return distance <= MAX_DISTANCE_METERS
    }

    private fun WikiPage.toGuide(language: String): TravelGuide {
        val sections = WikivoyageGuideParser.parse(extract.orEmpty())
        return TravelGuide(
            title = title,
            language = language,
            url = fullurl ?: "https://$language.wikivoyage.org/wiki/" + title.replace(' ', '_'),
            introduction = sections.introduction,
            sections = sections.sections,
        )
    }

    private companion object {
        /** Il centro di una città e le coordinate della sua guida possono distare qualche chilometro. */
        const val MAX_DISTANCE_METERS = 50_000.0
    }
}

/** Testo di una guida diviso in introduzione e capitoli (con le loro sottosezioni). */
internal data class ParsedGuide(val introduction: List<String>, val sections: List<GuideSection>)

/**
 * Interpreta il testo semplice di Wikivoyage (TextExtracts con `exsectionformat=wiki`): i capitoli sono
 * le righe "== Titolo ==", le sottosezioni "=== Titolo ===" (i livelli più profondi confluiscono nella
 * sottosezione che li contiene), ogni altra riga è un paragrafo.
 */
internal object WikivoyageGuideParser {

    private val HEADING = Regex("""^(={2,6})\s*(.+?)\s*\1$""")

    fun parse(extract: String): ParsedGuide {
        val introduction = mutableListOf<String>()
        val sections = mutableListOf<SectionBuilder>()
        extract.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val heading = HEADING.matchEntire(line)
            val level = heading?.groupValues?.get(1)?.length
            when {
                level == 2 -> sections += SectionBuilder(heading.groupValues[2])
                level == 3 && sections.isNotEmpty() -> sections.last().subsections += SectionBuilder(heading.groupValues[2])
                heading != null && sections.isNotEmpty() -> sections.last().current().paragraphs += heading.groupValues[2] + ":"
                heading != null -> Unit
                sections.isEmpty() -> introduction += line
                else -> sections.last().current().paragraphs += line
            }
        }
        return ParsedGuide(introduction, sections.map { it.build() }.filterNot { it.isEmpty })
    }

    private class SectionBuilder(val title: String) {
        val paragraphs = mutableListOf<String>()
        val subsections = mutableListOf<SectionBuilder>()

        /** Dove va il testo: l'ultima sottosezione aperta, altrimenti il capitolo. */
        fun current(): SectionBuilder = subsections.lastOrNull() ?: this

        fun build(): GuideSection = GuideSection(title, paragraphs.toList(), subsections.map { it.build() }.filterNot { it.isEmpty })
    }
}
