package com.partimo.data.remote.weather

import com.partimo.data.cache.CacheKey
import com.partimo.data.cache.CachePolicy
import com.partimo.data.cache.ResponseCache
import com.partimo.data.network.Fetched
import com.partimo.data.network.NetworkJson
import com.partimo.data.source.TripWeatherDataSource
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.weather.DailyForecast
import com.partimo.domain.model.weather.DailyObservation
import com.partimo.domain.model.weather.WeatherCondition
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Meteo per le date del viaggio da Open-Meteo, gratuito e senza chiave: previsioni giornaliere (fino a
 * 16 giorni) e dati storici giornalieri (archivio ERA5) per il clima tipico del periodo.
 */
internal class OpenMeteoTripWeatherDataSource(
    private val client: HttpClient,
    private val cache: ResponseCache,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val forecastBaseUrl: String = OpenMeteoWeatherDataSource.DEFAULT_BASE_URL,
    private val archiveBaseUrl: String = DEFAULT_ARCHIVE_BASE_URL,
) : TripWeatherDataSource {

    override suspend fun dailyForecast(location: GeoPoint, from: LocalDate, to: LocalDate): Fetched<List<DailyForecast>> {
        val latitude = location.latitude.rounded()
        val longitude = location.longitude.rounded()
        return cache.getOrFetch(
            key = CacheKey.of("weather-daily", latitude, longitude, from, to),
            ttl = CachePolicy.DAILY_FORECAST,
            fetch = {
                client.get("${forecastBaseUrl}forecast") {
                    parameter("latitude", latitude)
                    parameter("longitude", longitude)
                    parameter("daily", FORECAST_FIELDS)
                    parameter("timezone", "auto")
                    parameter("start_date", from)
                    parameter("end_date", to)
                }.bodyAsText()
            },
            parse = { body -> NetworkJson.decodeFromString(OpenMeteoDailyResponse.serializer(), body).daily.toForecasts() },
        )
    }

    override suspend fun dailyHistory(location: GeoPoint, years: Int): Fetched<List<DailyObservation>> {
        require(years in 1..MAX_HISTORY_YEARS) { "Anni di storico fuori intervallo: $years" }
        val latitude = location.latitude.rounded()
        val longitude = location.longitude.rounded()
        // Ultimi anni completi: i dati dell'anno in corso non coprono ancora tutti i mesi.
        val lastYear = LocalDate.now(clock).year - 1
        val start = LocalDate.of(lastYear - years + 1, 1, 1)
        val end = LocalDate.of(lastYear, 12, 31)
        return cache.getOrFetch(
            key = CacheKey.of("weather-history", latitude, longitude, start, end),
            ttl = CachePolicy.CLIMATE,
            fetch = {
                client.get("${archiveBaseUrl}archive") {
                    parameter("latitude", latitude)
                    parameter("longitude", longitude)
                    parameter("start_date", start)
                    parameter("end_date", end)
                    parameter("daily", HISTORY_FIELDS)
                    parameter("timezone", "auto")
                }.bodyAsText()
            },
            parse = { body -> NetworkJson.decodeFromString(OpenMeteoDailyResponse.serializer(), body).daily.toObservations() },
        )
    }

    private fun Double.rounded(): Double = BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP).toDouble()

    companion object {
        const val DEFAULT_ARCHIVE_BASE_URL = "https://archive-api.open-meteo.com/v1/"
        private const val FORECAST_FIELDS =
            "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max"
        private const val HISTORY_FIELDS = "temperature_2m_max,temperature_2m_min,precipitation_sum"
        private const val MAX_HISTORY_YEARS = 30
    }
}

@Serializable
internal data class OpenMeteoDailyResponse(val daily: OpenMeteoDaily)

/** Serie giornaliere parallele: l'elemento i di ogni lista si riferisce al giorno time[i]. */
@Serializable
internal data class OpenMeteoDaily(
    val time: List<String>,
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("temperature_2m_max") val maxTemperature: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val minTemperature: List<Double?> = emptyList(),
    @SerialName("precipitation_sum") val precipitation: List<Double?> = emptyList(),
    @SerialName("precipitation_probability_max") val precipitationProbability: List<Double?> = emptyList(),
)

/** Giorni con dati mancanti (null) scartati uno a uno, senza invalidare la serie. */
internal fun OpenMeteoDaily.toForecasts(): List<DailyForecast> = time.indices.mapNotNull { i ->
    val max = maxTemperature.getOrNull(i) ?: return@mapNotNull null
    val min = minTemperature.getOrNull(i) ?: return@mapNotNull null
    DailyForecast(
        date = LocalDate.parse(time[i]),
        condition = weatherCode.getOrNull(i)?.let(::wmoCodeToCondition) ?: WeatherCondition.UNKNOWN,
        maxCelsius = max,
        minCelsius = min,
        precipitationMm = precipitation.getOrNull(i) ?: 0.0,
        precipitationProbability = precipitationProbability.getOrNull(i)?.roundToInt(),
    )
}

internal fun OpenMeteoDaily.toObservations(): List<DailyObservation> = time.indices.mapNotNull { i ->
    DailyObservation(
        date = LocalDate.parse(time[i]),
        maxCelsius = maxTemperature.getOrNull(i) ?: return@mapNotNull null,
        minCelsius = minTemperature.getOrNull(i) ?: return@mapNotNull null,
        precipitationMm = precipitation.getOrNull(i) ?: return@mapNotNull null,
    )
}
