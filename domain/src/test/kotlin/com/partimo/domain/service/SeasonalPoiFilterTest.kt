package com.partimo.domain.service

import com.partimo.domain.model.Hemisphere
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.poi.PoiTag
import com.partimo.domain.model.poi.RecommendationReason
import com.partimo.domain.model.weather.WeatherCondition
import com.partimo.domain.testing.TestData
import com.partimo.domain.testing.TestData.poi
import com.partimo.domain.testing.TestData.weather
import java.time.LocalDate
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeasonalPoiFilterTest {

    private val filter = SeasonalPoiFilter()
    private val today = TestData.TODAY
    private val tomorrow = today.plusDays(1)
    private val december = LocalDate.of(2026, Month.DECEMBER, 12)
    private val july = LocalDate.of(2027, Month.JULY, 10)

    private val park = poi("park", category = PoiCategory.PARK, rating = 4.8)
    private val museum = poi("museum", category = PoiCategory.MUSEUM, rating = 4.2)

    @Test
    fun `il mercatino di Natale è consigliato a dicembre ed escluso a luglio`() {
        val market = poi("xmas", "Mercatino di Natale al Rathausplatz", category = PoiCategory.SEASONAL_EVENT)

        val inDecember = filter.recommend(listOf(market), december, today, Hemisphere.NORTHERN, null).single()

        assertTrue(RecommendationReason.IN_SEASON in inDecember.reasons)
        assertTrue(PoiTag.SEASONAL_HIGHLIGHT in inDecember.poi.tags)
        assertTrue(filter.recommend(listOf(market), july, today, Hemisphere.NORTHERN, null).isEmpty())
    }

    @Test
    fun `le spiagge seguono l'estate dell'emisfero`() {
        val beach = poi("bondi", "Bondi Beach", category = PoiCategory.BEACH, location = TestData.SYDNEY_CENTER)

        assertEquals(1, filter.recommend(listOf(beach), december, today, Hemisphere.SOUTHERN, null).size)
        assertTrue(filter.recommend(listOf(beach), july, today, Hemisphere.SOUTHERN, null).isEmpty())
        assertTrue(filter.recommend(listOf(beach), december, today, Hemisphere.NORTHERN, null).isEmpty())
    }

    @Test
    fun `i mesi espliciti prevalgono sulle deduzioni`() {
        val summerFestival = poi("festival", "Festival sul Danubio", activeMonths = setOf(Month.JUNE))

        assertTrue(filter.recommend(listOf(summerFestival), december, today, Hemisphere.NORTHERN, null).isEmpty())
    }

    @Test
    fun `con allerta meteo e viaggio imminente i luoghi all'aperto sono esclusi`() {
        val result = filter.recommend(listOf(park, museum), tomorrow, today, Hemisphere.NORTHERN, weather(WeatherCondition.THUNDERSTORM))

        assertEquals(listOf("museum"), result.map { it.poi.id })
        assertTrue(RecommendationReason.INDOOR_ALTERNATIVE in result.single().reasons)
    }

    @Test
    fun `il meteo attuale è ignorato per viaggi lontani`() {
        val result = filter.recommend(listOf(park, museum), december, today, Hemisphere.NORTHERN, weather(WeatherCondition.THUNDERSTORM))

        assertEquals(setOf("park", "museum"), result.map { it.poi.id }.toSet())
        assertFalse(filter.isWeatherRelevant(december, today))
    }

    @Test
    fun `con la pioggia i luoghi al chiuso salgono in classifica`() {
        val rainy = filter.recommend(
            listOf(park, museum), tomorrow, today, Hemisphere.NORTHERN,
            weather(WeatherCondition.RAIN, precipitationMm = 1.5),
        )
        val sunny = filter.recommend(listOf(park, museum), tomorrow, today, Hemisphere.NORTHERN, weather(WeatherCondition.CLEAR))

        assertEquals("museum", rainy.first().poi.id)
        assertTrue(RecommendationReason.WEATHER_RISK in rainy.first { it.poi.id == "park" }.reasons)
        assertEquals("park", sunny.first().poi.id)
        assertTrue(RecommendationReason.GREAT_WEATHER_OUTDOOR in sunny.first().reasons)
    }

    @Test
    fun `senza valutazioni i luoghi più noti vengono per primi`() {
        val famous = poi("colosseo", "Colosseo", category = PoiCategory.MONUMENT, rating = null, reviewCount = null, popularity = 1.0)
        val minor = poi("minor", "Chiesa di quartiere", category = PoiCategory.MONUMENT, rating = null, reviewCount = null, popularity = 0.2)
        val unknown = poi("unknown", "Luogo senza dati", category = PoiCategory.MONUMENT, rating = null, reviewCount = null)

        val result = filter.recommend(listOf(minor, unknown, famous), december, today, Hemisphere.NORTHERN, null)

        assertEquals(listOf("colosseo", "unknown", "minor"), result.map { it.poi.id })
    }

    @Test
    fun `la neve rende suggestivi mercatini ed eventi invernali`() {
        val market = poi("market", "Christkindlmarkt", category = PoiCategory.SEASONAL_EVENT)
        val snowyDay = LocalDate.of(2026, Month.DECEMBER, 1)

        val result = filter.recommend(listOf(market), snowyDay, snowyDay, Hemisphere.NORTHERN, weather(WeatherCondition.SNOW, temperature = -2.0))

        assertTrue(RecommendationReason.SNOW_ATMOSPHERE in result.single().reasons)
    }
}

class SeasonalCalendarTest {

    @Test
    fun `a dicembre propone mercatini di Natale e piste di pattinaggio`() {
        val themes = SeasonalCalendar.themesFor(Month.DECEMBER, Hemisphere.NORTHERN).map { it.searchQuery }

        assertEquals(listOf("mercatini di Natale", "piste di pattinaggio sul ghiaccio"), themes)
    }

    @Test
    fun `a luglio nell'emisfero nord propone le spiagge`() {
        val theme = SeasonalCalendar.themesFor(Month.JULY, Hemisphere.NORTHERN).single()

        assertEquals(PoiCategory.BEACH, theme.category)
    }

    @Test
    fun `gli intervalli di mesi attraversano il cambio d'anno`() {
        assertEquals(
            setOf(Month.DECEMBER, Month.JANUARY, Month.FEBRUARY, Month.MARCH),
            SeasonalCalendar.monthRange(Month.DECEMBER, Month.MARCH),
        )
    }
}
