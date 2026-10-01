package com.partimo.domain.model.weather

import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay

/** Previsione di un giorno. */
data class DailyForecast(
    val date: LocalDate,
    val condition: WeatherCondition,
    val maxCelsius: Double,
    val minCelsius: Double,
    val precipitationMm: Double = 0.0,
    /** Probabilità massima di pioggia nel giorno, in percentuale. */
    val precipitationProbability: Int? = null,
)

/** Dati misurati in un giorno passato, per calcolare il clima tipico di un periodo. */
data class DailyObservation(
    val date: LocalDate,
    val maxCelsius: Double,
    val minCelsius: Double,
    val precipitationMm: Double,
)

/** Clima tipico di un periodo dell'anno (es. 7–17 dicembre), calcolato sugli anni passati. */
data class ClimateNormals(
    val from: MonthDay,
    val to: MonthDay,
    val averageMaxCelsius: Double,
    val averageMinCelsius: Double,
    /** Quota dei giorni con almeno 1 mm di pioggia o neve, tra 0 e 1. */
    val wetDaysShare: Double,
    val years: Int,
)

/** Meteo per le date del viaggio: previsioni se è vicino, altrimenti il clima tipico del periodo. */
sealed interface TripWeather {
    data class Forecast(val days: List<DailyForecast>) : TripWeather

    data class Climate(val normals: ClimateNormals) : TripWeather
}

/**
 * Alba, tramonto e "ora d'oro" (sole basso, tra l'orizzonte e 6° di altezza: la luce migliore per le
 * foto) di un giorno, nell'ora locale. I valori mancano quando il sole non sorge o non tramonta
 * (notte o giorno polare).
 */
data class SunTimes(
    val date: LocalDate,
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
    /** Fine dell'ora d'oro del mattino (sole a 6° di altezza). */
    val morningGoldenHourEnd: LocalTime?,
    /** Inizio dell'ora d'oro della sera (sole a 6° di altezza). */
    val eveningGoldenHourStart: LocalTime?,
    val polarDay: Boolean = false,
    val polarNight: Boolean = false,
)
