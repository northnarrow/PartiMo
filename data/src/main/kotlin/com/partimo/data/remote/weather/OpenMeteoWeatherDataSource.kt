package com.partimo.data.remote.weather

import com.partimo.data.cache.CacheKey
import com.partimo.data.cache.CachePolicy
import com.partimo.data.cache.ResponseCache
import com.partimo.data.network.Fetched
import com.partimo.data.network.NetworkJson
import com.partimo.data.source.WeatherDataSource
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.weather.WeatherCondition
import com.partimo.domain.model.weather.WeatherSnapshot
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

/** Meteo attuale da Open-Meteo: servizio gratuito che non richiede chiavi API. */
class OpenMeteoWeatherDataSource(
    private val client: HttpClient,
    private val cache: ResponseCache,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : WeatherDataSource {

    override suspend fun currentWeather(location: GeoPoint): Fetched<WeatherSnapshot> {
        // Coordinate arrotondate (~1 km): richieste vicine condividono la stessa entry di cache.
        val latitude = location.latitude.rounded()
        val longitude = location.longitude.rounded()
        return cache.getOrFetch(
            key = CacheKey.of("weather", latitude, longitude),
            ttl = CachePolicy.WEATHER,
            fetch = {
                client.get("${baseUrl}forecast") {
                    parameter("latitude", latitude)
                    parameter("longitude", longitude)
                    parameter("current", CURRENT_FIELDS)
                    parameter("timezone", "auto")
                }.bodyAsText()
            },
            parse = { body -> NetworkJson.decodeFromString(OpenMeteoResponse.serializer(), body).current.toDomain() },
        )
    }

    private fun Double.rounded(): Double = BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP).toDouble()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.open-meteo.com/v1/"
        private const val CURRENT_FIELDS =
            "temperature_2m,apparent_temperature,weather_code,wind_speed_10m,precipitation,is_day"
    }
}

@Serializable
internal data class OpenMeteoResponse(val current: OpenMeteoCurrent)

@Serializable
internal data class OpenMeteoCurrent(
    val time: String? = null,
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("wind_speed_10m") val windSpeed: Double = 0.0,
    val precipitation: Double = 0.0,
    @SerialName("is_day") val isDay: Int = 1,
)

internal fun OpenMeteoCurrent.toDomain(): WeatherSnapshot = WeatherSnapshot(
    temperatureCelsius = temperature,
    condition = wmoCodeToCondition(weatherCode),
    apparentTemperatureCelsius = apparentTemperature,
    precipitationMm = precipitation,
    windSpeedKmh = windSpeed,
    isDay = isDay == 1,
    observedAt = time?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
)

/** Codici meteo WMO usati da Open-Meteo → condizioni di dominio. */
internal fun wmoCodeToCondition(code: Int): WeatherCondition = when (code) {
    0 -> WeatherCondition.CLEAR
    1, 2 -> WeatherCondition.PARTLY_CLOUDY
    3 -> WeatherCondition.OVERCAST
    45, 48 -> WeatherCondition.FOG
    in 51..57 -> WeatherCondition.DRIZZLE
    in 61..67, in 80..82 -> WeatherCondition.RAIN
    in 71..77, 85, 86 -> WeatherCondition.SNOW
    95, 96, 99 -> WeatherCondition.THUNDERSTORM
    else -> WeatherCondition.UNKNOWN
}
