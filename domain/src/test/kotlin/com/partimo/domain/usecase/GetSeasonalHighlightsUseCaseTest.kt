package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.poi.PoiTag
import com.partimo.domain.model.poi.Season
import com.partimo.domain.model.weather.WeatherCondition
import com.partimo.domain.testing.FakePoiRepository
import com.partimo.domain.testing.FakeWeatherRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.testing.TestData.poi
import com.partimo.domain.testing.failureError
import com.partimo.domain.testing.successData
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetSeasonalHighlightsUseCaseTest {

    private val poiRepository = FakePoiRepository(
        result = DataResult.Success(
            listOf(
                poi("xmas", "Wiener Christkindlmarkt", category = PoiCategory.SEASONAL_EVENT, rating = 4.6),
                poi("beach", "Donauinsel Strand", category = PoiCategory.BEACH),
                poi("museum", "Kunsthistorisches Museum", category = PoiCategory.MUSEUM, rating = 4.8),
                poi("view", "Kahlenberg", category = PoiCategory.VIEWPOINT, rating = 4.7),
            ),
        ),
    )
    private val weatherRepository = FakeWeatherRepository()
    private val useCase = GetSeasonalHighlightsUseCase(poiRepository, weatherRepository, clock = TestData.FIXED_CLOCK)
    private val december = LocalDate.of(2026, Month.DECEMBER, 12)

    @Test
    fun `a dicembre richiede i temi stagionali e mette in evidenza i mercatini`() = runTest {
        val highlights = useCase(TestData.VIENNA_CENTER, december).successData()

        val query = poiRepository.queries.single()
        assertEquals(Month.DECEMBER, query.travelMonth)
        assertTrue(query.seasonalThemes.any { "Natale" in it.searchQuery })

        val ids = highlights.recommendations.map { it.poi.id }
        assertEquals("xmas", ids.first())
        assertFalse("beach" in ids, "Le spiagge sono fuori stagione a dicembre")
        assertEquals(Season.WINTER, highlights.season)
        assertFalse(highlights.weatherConsidered, "Il meteo attuale non conta per un viaggio tra mesi")
    }

    @Test
    fun `senza meteo restituisce comunque i suggerimenti`() = runTest {
        weatherRepository.result = DataResult.Failure(DataError.NoConnection)

        val highlights = useCase(TestData.VIENNA_CENTER, december).successData()

        assertNull(highlights.currentWeather)
        assertTrue(highlights.recommendations.isNotEmpty())
    }

    @Test
    fun `se i POI non sono disponibili restituisce l'errore`() = runTest {
        poiRepository.result = DataResult.Failure(DataError.Timeout)

        assertEquals(DataError.Timeout, useCase(TestData.VIENNA_CENTER, december).failureError())
    }

    @Test
    fun `filtra solo gli spot fotografici su richiesta`() = runTest {
        val highlights = useCase(TestData.VIENNA_CENTER, december, requiredTags = setOf(PoiTag.PANORAMIC)).successData()

        assertEquals(listOf("view"), highlights.recommendations.map { it.poi.id })
    }

    @Test
    fun `per un viaggio imminente il meteo attuale filtra i luoghi all'aperto`() = runTest {
        weatherRepository.result = DataResult.Success(TestData.weather(WeatherCondition.THUNDERSTORM))

        val highlights = useCase(TestData.VIENNA_CENTER, TestData.TODAY.plusDays(1)).successData()

        assertTrue(highlights.weatherConsidered)
        assertEquals(listOf("museum"), highlights.recommendations.map { it.poi.id })
    }
}
