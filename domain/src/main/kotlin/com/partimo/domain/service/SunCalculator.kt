package com.partimo.domain.service

import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.weather.SunTimes
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/**
 * Alba, tramonto e ora d'oro calcolati sul telefono, senza rete, con l'algoritmo dell'Almanac for
 * Computers (US Naval Observatory, 1990): precisione di un paio di minuti alle latitudini abitate.
 */
object SunCalculator {

    /** Centro del sole all'orizzonte, con rifrazione atmosferica e raggio del disco (90° 50'). */
    private const val SUNRISE_ZENITH = 90.833

    /** Sole a 6° di altezza: limite dell'ora d'oro. */
    private const val GOLDEN_HOUR_ZENITH = 84.0

    fun sunTimes(date: LocalDate, location: GeoPoint, zone: ZoneId): SunTimes {
        val sunrise = event(date, location, zone, SUNRISE_ZENITH, rising = true)
        val sunset = event(date, location, zone, SUNRISE_ZENITH, rising = false)
        return SunTimes(
            date = date,
            sunrise = (sunrise as? SunEvent.At)?.time,
            sunset = (sunset as? SunEvent.At)?.time,
            morningGoldenHourEnd = (event(date, location, zone, GOLDEN_HOUR_ZENITH, rising = true) as? SunEvent.At)?.time,
            eveningGoldenHourStart = (event(date, location, zone, GOLDEN_HOUR_ZENITH, rising = false) as? SunEvent.At)?.time,
            polarDay = sunrise == SunEvent.AlwaysUp,
            polarNight = sunrise == SunEvent.AlwaysDown,
        )
    }

    private sealed interface SunEvent {
        data class At(val time: LocalTime) : SunEvent

        data object AlwaysUp : SunEvent

        data object AlwaysDown : SunEvent
    }

    private fun event(date: LocalDate, location: GeoPoint, zone: ZoneId, zenith: Double, rising: Boolean): SunEvent {
        val dayOfYear = date.dayOfYear
        val lngHour = location.longitude / 15
        val t = dayOfYear + ((if (rising) 6.0 else 18.0) - lngHour) / 24

        // Anomalia media e longitudine vera del sole.
        val meanAnomaly = 0.9856 * t - 3.289
        val trueLongitude = normalizeDegrees(meanAnomaly + 1.916 * sinDeg(meanAnomaly) + 0.020 * sinDeg(2 * meanAnomaly) + 282.634)

        // Ascensione retta, nello stesso quadrante della longitudine vera, in ore.
        var rightAscension = normalizeDegrees(Math.toDegrees(atan(0.91764 * tanDeg(trueLongitude))))
        rightAscension += floor(trueLongitude / 90) * 90 - floor(rightAscension / 90) * 90
        rightAscension /= 15

        // Declinazione e angolo orario all'altezza richiesta.
        val sinDeclination = 0.39782 * sinDeg(trueLongitude)
        val cosDeclination = cos(asin(sinDeclination))
        val cosHourAngle = (cosDeg(zenith) - sinDeclination * sinDeg(location.latitude)) / (cosDeclination * cosDeg(location.latitude))
        if (cosHourAngle > 1) return SunEvent.AlwaysDown
        if (cosHourAngle < -1) return SunEvent.AlwaysUp
        val hourAngle = (if (rising) 360 - Math.toDegrees(acos(cosHourAngle)) else Math.toDegrees(acos(cosHourAngle))) / 15

        val localMeanTime = hourAngle + rightAscension - 0.06571 * t - 6.622
        val utcHours = normalizeHours(localMeanTime - lngHour)
        val utcSeconds = (utcHours * 3_600).roundToLong()
        val utc = date.atStartOfDay(ZoneId.of("UTC")).plusSeconds(utcSeconds)
        return SunEvent.At(toLocal(utc, zone, date))
    }

    /** Ora locale dell'evento, riportata al giorno richiesto anche se in UTC cade nel giorno prima o dopo. */
    private fun toLocal(utc: ZonedDateTime, zone: ZoneId, date: LocalDate): LocalTime {
        val local = utc.withZoneSameInstant(zone)
        val shift = local.toLocalDate().toEpochDay() - date.toEpochDay()
        return if (shift == 0L) local.toLocalTime() else utc.minusDays(shift).withZoneSameInstant(zone).toLocalTime()
    }

    private fun normalizeDegrees(value: Double): Double = ((value % 360) + 360) % 360

    private fun normalizeHours(value: Double): Double = ((value % 24) + 24) % 24

    private fun sinDeg(degrees: Double) = sin(Math.toRadians(degrees))

    private fun cosDeg(degrees: Double) = cos(Math.toRadians(degrees))

    private fun tanDeg(degrees: Double) = tan(Math.toRadians(degrees))
}
