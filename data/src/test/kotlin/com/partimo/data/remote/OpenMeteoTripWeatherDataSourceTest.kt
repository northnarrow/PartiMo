package com.partimo.data.remote

import com.partimo.data.remote.weather.OpenMeteoTripWeatherDataSource
import com.partimo.data.testing.inMemoryCache
import com.partimo.data.testing.jsonHeaders
import com.partimo.data.testing.mockHttpClient
import com.partimo.domain.model.weather.WeatherCondition
import com.partimo.domain.testing.TestData
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OpenMeteoTripWeatherDataSourceTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun fixture(name: String): String = requireNotNull(javaClass.getResource("/openmeteo/$name")) { "Fixture mancante: $name" }.readText()

    private fun source(body: String) = OpenMeteoTripWeatherDataSource(
        client = mockHttpClient { request ->
            requests += request
            respond(body, HttpStatusCode.OK, jsonHeaders)
        },
        cache = inMemoryCache(),
        clock = TestData.FIXED_CLOCK,
        forecastBaseUrl = "https://weather.test/v1/",
        archiveBaseUrl = "https://archive.test/v1/",
    )

    @Test
    fun `le previsioni reali di Vienna arrivano giorno per giorno`() = runTest {
        val from = LocalDate.of(2026, 10, 1)
        val days = source(fixture("forecast_daily_vienna.json")).dailyForecast(TestData.VIENNA_CENTER, from, from.plusDays(15)).data

        assertEquals(16, days.size)
        val first = days.first()
        assertEquals(from, first.date)
        assertEquals(WeatherCondition.OVERCAST, first.condition)
        assertEquals(23.9, first.maxCelsius)
        assertEquals(14.3, first.minCelsius)
        assertEquals(0, first.precipitationProbability)
        assertEquals(WeatherCondition.RAIN, days[7].condition)
        assertNull(days.last().precipitationProbability, "Probabilità di pioggia non ancora disponibile")

        val request = requests.single()
        assertEquals("/v1/forecast", request.url.encodedPath)
        assertEquals("2026-10-01", request.url.parameters["start_date"])
        assertEquals("2026-10-16", request.url.parameters["end_date"])
    }

    @Test
    fun `lo storico reale copre dieci anni completi, fino all'anno scorso`() = runTest {
        val history = source(fixture("archive_vienna_2016_2025.json")).dailyHistory(TestData.VIENNA_CENTER, years = 10).data

        assertEquals(3_653, history.size)
        assertEquals(LocalDate.of(2016, 1, 1), history.first().date)
        assertEquals(LocalDate.of(2025, 12, 31), history.last().date)
        val request = requests.single()
        assertEquals("archive.test", request.url.host)
        assertEquals("2016-01-01", request.url.parameters["start_date"])
        assertEquals("2025-12-31", request.url.parameters["end_date"])
    }
}
