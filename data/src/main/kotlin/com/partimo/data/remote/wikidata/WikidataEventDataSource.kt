package com.partimo.data.remote.wikidata

import com.partimo.data.cache.CacheKey
import com.partimo.data.cache.CachePolicy
import com.partimo.data.cache.ResponseCache
import com.partimo.data.network.Fetched
import com.partimo.data.network.NetworkJson
import com.partimo.data.network.mapNotNullSafely
import com.partimo.data.remote.wikipedia.WikipediaApi
import com.partimo.data.source.EventDataSource
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.event.EventKind
import com.partimo.domain.model.event.EventQuery
import com.partimo.domain.model.event.EventTiming
import com.partimo.domain.model.event.TripEvent
import com.partimo.domain.model.poi.WikipediaPage
import com.partimo.domain.service.SeasonalCalendar
import kotlinx.serialization.Serializable
import java.time.Month
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Eventi che si ripetono ogni anno da Wikidata, gratuita e senza chiave: mercatini di Natale,
 * festival e ricorrenze con il loro mese o giorno, con foto e voce di Wikipedia quando ci sono.
 *
 * Si scartano gli eventi chiusi (con data di fine), le singole edizioni (anno nel nome), le fiere
 * commerciali e gli incontri che si ripetono quasi ogni mese.
 */
class WikidataEventDataSource internal constructor(
    private val api: WikidataApi,
    private val cache: ResponseCache,
    languageCode: String,
) : EventDataSource {

    private val language = WikipediaApi.sanitizeLanguage(languageCode)

    override suspend fun recurringEvents(query: EventQuery, forceRefresh: Boolean): Fetched<List<TripEvent>> {
        val sparql = WikidataQueries.recurringEvents(query.location, query.radiusMeters, language)
        return cache.getOrFetch(
            key = CacheKey.of("wikidata-events", sparql),
            ttl = CachePolicy.EVENTS,
            forceRefresh = forceRefresh,
            fetch = { api.sparql(sparql) },
            parse = { body ->
                NetworkJson.decodeFromString(SparqlResponse.serializer(), body).results.bindings
                    .mapNotNullSafely { row -> row.toEvent(query) }
                    .distinctBy { it.id }
            },
        )
    }

    private fun Map<String, SparqlValue>.toEvent(query: EventQuery): TripEvent? {
        val qid = text("item")?.substringAfterLast('/')?.takeIf { QID.matches(it) } ?: return null
        // Senza etichetta in nessuna lingua il servizio restituisce l'identificativo: non è un nome.
        val name = text("itemLabel")?.takeIf { it != qid } ?: return null
        val description = text("itemDescription")
        if (text("ended") != null || EDITION_YEAR.containsMatchIn(name)) return null
        if (TRADE_FAIR.containsMatchIn(listOfNotNull(name, description).joinToString(" "))) return null

        val days = text("days").orEmpty().split('|').mapNotNull(::parseDay).toSet()
        if (days.size > MAX_YEARLY_DAYS) return null
        val months = text("months").orEmpty().split(' ').mapNotNull { MONTHS[it] }.toSet()
        val christmasMarket = text("christmasMarket") == "1"
        val timing = when {
            days.isNotEmpty() -> EventTiming.YearlyDays(days)
            christmasMarket -> SeasonalCalendar.CHRISTMAS_MARKET_SEASON
            months.isNotEmpty() -> EventTiming.InMonths(months)
            else -> return null
        }

        val nearby = { point: GeoPoint? -> point?.takeIf { it.distanceTo(query.location) <= query.radiusMeters } }
        val venueLocation = nearby(point("venueLocation"))
        val location = nearby(point("ownLocation")) ?: venueLocation ?: nearby(point("areaLocation")) ?: return null
        return TripEvent(
            id = "wikidata:$qid",
            name = name,
            kind = if (christmasMarket) EventKind.CHRISTMAS_MARKET else EventKind.RECURRING_EVENT,
            timing = timing,
            approximateTiming = timing == SeasonalCalendar.CHRISTMAS_MARKET_SEASON,
            description = description?.takeUnless { it.equals(name, ignoreCase = true) },
            location = location,
            venueName = text("venue")?.takeIf { venueLocation != null && !it.equals(name, ignoreCase = true) },
            photoUrl = text("picture")?.let(::commonsThumbnail),
            wikipediaPage = text("localTitle")?.let { WikipediaPage(language, it) } ?: text("enTitle")?.let { WikipediaPage("en", it) },
        )
    }

    private fun Map<String, SparqlValue>.text(name: String): String? = get(name)?.value?.trim()?.takeIf { it.isNotEmpty() }

    /** Coordinate WKT di Wikidata: "Point(longitudine latitudine)". */
    private fun Map<String, SparqlValue>.point(name: String): GeoPoint? {
        val (longitude, latitude) = WKT_POINT.matchEntire(text(name) ?: return null)?.destructured ?: return null
        return runCatching { GeoPoint(latitude.toDouble(), longitude.toDouble()) }.getOrNull()
    }

    internal companion object {
        /** Oltre questo numero di giorni l'appuntamento è quasi mensile (es. un ritrovo), non un evento. */
        const val MAX_YEARLY_DAYS = 4

        private val QID = Regex("Q\\d+")
        private val WKT_POINT = Regex("Point\\((-?[\\d.]+) (-?[\\d.]+)\\)")
        private val EDITION_YEAR = Regex("\\b(19|20)\\d{2}\\b")
        private val TRADE_FAIR = Regex("trade fair|trade show|fiera commerciale|fiera campionaria|fachmesse|salon professionnel", RegexOption.IGNORE_CASE)
        private val DAY_FORMAT = DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH)

        /** Elementi Wikidata dei mesi, valori della proprietà P2922. */
        private val MONTHS: Map<String, Month> = mapOf(
            "Q108" to Month.JANUARY, "Q109" to Month.FEBRUARY, "Q110" to Month.MARCH, "Q118" to Month.APRIL,
            "Q119" to Month.MAY, "Q120" to Month.JUNE, "Q121" to Month.JULY, "Q122" to Month.AUGUST,
            "Q123" to Month.SEPTEMBER, "Q124" to Month.OCTOBER, "Q125" to Month.NOVEMBER, "Q126" to Month.DECEMBER,
        )

        /** Giorno dell'anno dall'etichetta inglese dell'elemento ("January 1"); `null` per valori come "September". */
        fun parseDay(label: String): MonthDay? = try {
            MonthDay.parse(label.trim(), DAY_FORMAT)
        } catch (e: DateTimeParseException) {
            null
        }

        /** Miniatura di una foto di Wikimedia Commons, dall'indirizzo "Special:FilePath" restituito da Wikidata. */
        fun commonsThumbnail(filePath: String): String =
            filePath.replaceFirst("http://", "https://") + "?width=$PHOTO_WIDTH_PX"

        private const val PHOTO_WIDTH_PX = 960
    }
}

// Risposta JSON standard di SPARQL: https://www.w3.org/TR/sparql11-results-json/

@Serializable
internal data class SparqlResponse(val results: SparqlResults = SparqlResults())

@Serializable
internal data class SparqlResults(val bindings: List<Map<String, SparqlValue>> = emptyList())

@Serializable
internal data class SparqlValue(val type: String = "", val value: String = "")
