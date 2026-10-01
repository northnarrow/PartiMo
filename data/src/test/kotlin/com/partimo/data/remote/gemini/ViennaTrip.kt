package com.partimo.data.remote.gemini

import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.event.EventKind
import com.partimo.domain.model.event.TripEvent
import com.partimo.domain.model.plan.TripKnowledge
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.service.SeasonalCalendar
import com.partimo.domain.testing.TestData
import java.time.LocalDate
import java.time.Month

/**
 * Viaggio di prova usato per registrare le risposte reali di Gemini in `resources/gemini`: cambiandone
 * i dati cambiano i codici dei luoghi e le risposte vanno registrate di nuovo.
 */
internal object ViennaTrip {

    val from: LocalDate = LocalDate.of(2026, Month.DECEMBER, 10)
    val to: LocalDate = LocalDate.of(2026, Month.DECEMBER, 14)

    val places = listOf(
        place("duomo", "Duomo di Santo Stefano", PoiCategory.RELIGIOUS_SITE, "cattedrale di Vienna", 48.2085, 16.3731),
        place("hofburg", "Hofburg", PoiCategory.MONUMENT, "palazzo imperiale degli Asburgo", 48.2066, 16.3654),
        place("schoenbrunn", "Castello di Schönbrunn", PoiCategory.MONUMENT, "residenza estiva imperiale", 48.1845, 16.3122),
        place("belvedere", "Belvedere", PoiCategory.MUSEUM, "complesso di palazzi barocchi e museo", 48.1915, 16.3809),
        place("khm", "Kunsthistorisches Museum", PoiCategory.MUSEUM, "museo di storia dell'arte", 48.2038, 16.3616),
        place("albertina", "Albertina", PoiCategory.MUSEUM, "museo di arti grafiche", 48.2046, 16.3681),
        place("prater", "Prater", PoiCategory.PARK, "grande parco pubblico con il luna park", 48.2161, 16.3958),
        place("naschmarkt", "Naschmarkt", PoiCategory.MARKET, "mercato all'aperto", 48.1985, 16.3626),
        place("mq", "MuseumsQuartier", PoiCategory.MUSEUM, "complesso di musei", 48.2034, 16.3584),
        place("oper", "Wiener Staatsoper", PoiCategory.MONUMENT, "teatro dell'opera di Vienna", 48.2030, 16.3691),
        place("hundertwasser", "Hundertwasserhaus", PoiCategory.MONUMENT, "condominio progettato da Hundertwasser", 48.2073, 16.3943),
        place("karlskirche", "Karlskirche", PoiCategory.RELIGIOUS_SITE, "chiesa barocca", 48.1982, 16.3718),
    )

    val events = listOf(
        market("Q1", "Weihnachtsmarkt am Spittelberg", 48.2032, 16.3537),
        market("Q2", "Wiener Christkindlmarkt", 48.2108, 16.3576),
        market("Q3", "Weihnachtsmarkt Schloss Schönbrunn", 48.1846, 16.3121),
    )

    fun knowledge(): TripKnowledge = TripKnowledge(TestData.destination(), from, to, places = places, events = events)

    private fun place(id: String, name: String, category: PoiCategory, description: String, latitude: Double, longitude: Double) =
        TestData.poi("wikipedia:it:$id", name, category = category, description = description, location = GeoPoint(latitude, longitude))

    private fun market(qid: String, name: String, latitude: Double, longitude: Double) = TripEvent(
        id = "wikidata:$qid",
        name = name,
        kind = EventKind.CHRISTMAS_MARKET,
        timing = SeasonalCalendar.CHRISTMAS_MARKET_SEASON,
        approximateTiming = true,
        location = GeoPoint(latitude, longitude),
    )
}
