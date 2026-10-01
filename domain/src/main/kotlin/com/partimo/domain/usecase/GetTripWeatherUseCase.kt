package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.weather.ClimateNormals
import com.partimo.domain.model.weather.DailyObservation
import com.partimo.domain.model.weather.TripWeather
import com.partimo.domain.repository.TripWeatherRepository
import java.time.Clock
import java.time.LocalDate
import java.time.MonthDay

/**
 * Meteo per le date del viaggio. Se la partenza è entro l'orizzonte delle previsioni si usano le
 * previsioni dei giorni del viaggio; altrimenti il clima tipico del periodo, calcolato sui dati degli
 * ultimi anni con qualche giorno di margine prima e dopo (per una media più stabile).
 */
class GetTripWeatherUseCase(
    private val repository: TripWeatherRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val historyYears: Int = DEFAULT_HISTORY_YEARS,
) {

    suspend operator fun invoke(location: GeoPoint, from: LocalDate, to: LocalDate): DataResult<TripWeather> {
        require(!to.isBefore(from)) { "Il viaggio finisce prima di cominciare: $from–$to" }
        val today = LocalDate.now(clock)
        val horizon = today.plusDays(FORECAST_DAYS - 1L)
        return if (!from.isAfter(horizon) && !to.isBefore(today)) {
            forecast(location, maxOf(from, today), minOf(to, horizon))
        } else {
            climate(location, from, to)
        }
    }

    private suspend fun forecast(location: GeoPoint, from: LocalDate, to: LocalDate): DataResult<TripWeather> =
        when (val result = repository.dailyForecast(location, from, to)) {
            is DataResult.Failure -> result
            is DataResult.Success -> {
                val days = result.data.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.sortedBy { it.date }
                if (days.isEmpty()) DataResult.Failure(DataError.InvalidResponse) else DataResult.Success(TripWeather.Forecast(days), result.origin)
            }
        }

    private suspend fun climate(location: GeoPoint, from: LocalDate, to: LocalDate): DataResult<TripWeather> =
        when (val result = repository.dailyHistory(location, historyYears)) {
            is DataResult.Failure -> result
            is DataResult.Success -> normals(result.data, from.minusDays(MARGIN_DAYS), to.plusDays(MARGIN_DAYS))
                ?.let { DataResult.Success(TripWeather.Climate(it), result.origin) }
                ?: DataResult.Failure(DataError.InvalidResponse)
        }

    private fun normals(history: List<DailyObservation>, from: LocalDate, to: LocalDate): ClimateNormals? {
        val start = MonthDay.from(from)
        val end = MonthDay.from(to)
        val window = history.filter { observation -> MonthDay.from(observation.date).isWithin(start, end) }
        if (window.size < MIN_OBSERVATIONS) return null
        return ClimateNormals(
            from = start,
            to = end,
            averageMaxCelsius = window.map { it.maxCelsius }.average(),
            averageMinCelsius = window.map { it.minCelsius }.average(),
            wetDaysShare = window.count { it.precipitationMm >= WET_DAY_MM }.toDouble() / window.size,
            years = window.map { it.date.year }.distinct().size,
        )
    }

    /** Anche a cavallo di Capodanno (es. 28 dicembre–3 gennaio). */
    private fun MonthDay.isWithin(start: MonthDay, end: MonthDay): Boolean =
        if (start <= end) this in start..end else this >= start || this <= end

    private companion object {
        /** Open-Meteo prevede fino a 16 giorni, oggi compreso. */
        const val FORECAST_DAYS = 16
        const val DEFAULT_HISTORY_YEARS = 10
        const val MARGIN_DAYS = 3L
        const val MIN_OBSERVATIONS = 5

        /** Soglia meteorologica del "giorno piovoso". */
        const val WET_DAY_MM = 1.0
    }
}
