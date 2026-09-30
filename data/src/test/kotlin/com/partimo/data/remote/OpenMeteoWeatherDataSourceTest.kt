package com.partimo.data.remote

import com.partimo.data.remote.weather.OpenMeteoWeatherDataSource
import com.partimo.data.remote.weather.wmoCodeToCondition
import com.partimo.data.testing.inMemoryCache
import com.partimo.data.testing.jsonHeaders
import com.partimo.data.testing.mockHttpClient
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.model.weather.WeatherCondition
import com.partimo.domain.testing.TestData
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OpenMeteoWeatherDataSourceTest {

    // Risposta reale di Open-Meteo (Milano, 30/09/2026) usata come fixture.
    private val responseJson = """
        {"latitude":45.46,"longitude":9.199999,"generationtime_ms":0.22,"utc_offset_seconds":7200,
         "timezone":"Europe/Rome","timezone_abbreviation":"GMT+2","elevation":147.0,
         "current_units":{"time":"iso8601","interval":"seconds","temperature_2m":"°C"},
         "current":{"time":"2026-09-30T19:00","interval":900,"temperature_2m":22.5,
           "apparent_temperature":23.7,"weather_code":2,"wind_speed_10m":4.2,"precipitation":0.00,"is_day":1}}
    """.trimIndent()

    @Test
    fun `interpreta il meteo attuale e riusa la cache`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = mockHttpClient { request ->
            requests += request
            respond(responseJson, HttpStatusCode.OK, jsonHeaders)
        }
        val source = OpenMeteoWeatherDataSource(client, inMemoryCache(), baseUrl = "https://weather.test/v1/")

        val first = source.currentWeather(TestData.VIENNA_CENTER)
        val second = source.currentWeather(TestData.VIENNA_CENTER)

        assertEquals(WeatherCondition.PARTLY_CLOUDY, first.data.condition)
        assertEquals(22.5, first.data.temperatureCelsius)
        assertEquals(23.7, first.data.apparentTemperatureCelsius)
        assertEquals(DataOrigin.REMOTE, first.origin)
        assertEquals(DataOrigin.CACHE, second.origin)
        assertEquals(1, requests.size)

        val request = requests.single()
        assertEquals("/v1/forecast", request.url.encodedPath)
        assertEquals("48.21", request.url.parameters["latitude"])
        assertEquals("16.37", request.url.parameters["longitude"])
        assertFalse(request.url.parameters["current"].isNullOrBlank())
    }

    @Test
    fun `i codici WMO vengono tradotti nelle condizioni di dominio`() {
        assertEquals(WeatherCondition.CLEAR, wmoCodeToCondition(0))
        assertEquals(WeatherCondition.OVERCAST, wmoCodeToCondition(3))
        assertEquals(WeatherCondition.FOG, wmoCodeToCondition(45))
        assertEquals(WeatherCondition.DRIZZLE, wmoCodeToCondition(53))
        assertEquals(WeatherCondition.RAIN, wmoCodeToCondition(63))
        assertEquals(WeatherCondition.RAIN, wmoCodeToCondition(81))
        assertEquals(WeatherCondition.SNOW, wmoCodeToCondition(73))
        assertEquals(WeatherCondition.THUNDERSTORM, wmoCodeToCondition(95))
        assertEquals(WeatherCondition.UNKNOWN, wmoCodeToCondition(999))
    }
}
