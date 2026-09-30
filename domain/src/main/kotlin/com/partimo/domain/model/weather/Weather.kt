package com.partimo.domain.model.weather

import java.time.LocalDateTime

enum class WeatherCondition {
    CLEAR, PARTLY_CLOUDY, OVERCAST, FOG, DRIZZLE, RAIN, SNOW, THUNDERSTORM, UNKNOWN
}

/** Impatto del meteo sulle attività all'aperto. */
enum class WeatherSeverity { GOOD, MODERATE, SEVERE }

/** Condizioni meteo attuali in un luogo. */
data class WeatherSnapshot(
    val temperatureCelsius: Double,
    val condition: WeatherCondition,
    val apparentTemperatureCelsius: Double? = null,
    /** Precipitazione nell'ultimo intervallo di osservazione, in millimetri. */
    val precipitationMm: Double = 0.0,
    val windSpeedKmh: Double = 0.0,
    val isDay: Boolean = true,
    val observedAt: LocalDateTime? = null,
) {
    val severity: WeatherSeverity
        get() = when {
            condition == WeatherCondition.THUNDERSTORM ||
                precipitationMm >= HEAVY_PRECIPITATION_MM ||
                windSpeedKmh >= STRONG_WIND_KMH -> WeatherSeverity.SEVERE

            condition in WET_CONDITIONS || precipitationMm > LIGHT_PRECIPITATION_MM -> WeatherSeverity.MODERATE
            else -> WeatherSeverity.GOOD
        }

    private companion object {
        const val HEAVY_PRECIPITATION_MM = 7.0
        const val LIGHT_PRECIPITATION_MM = 0.2
        const val STRONG_WIND_KMH = 50.0
        val WET_CONDITIONS = setOf(
            WeatherCondition.FOG,
            WeatherCondition.DRIZZLE,
            WeatherCondition.RAIN,
            WeatherCondition.SNOW,
        )
    }
}
