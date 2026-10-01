package com.partimo.domain.service

import com.partimo.domain.model.GeoPoint
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SunCalculatorTest {

    private val vienna = GeoPoint(48.2, 16.38)
    private val viennaZone = ZoneId.of("Europe/Vienna")

    private fun assertClose(expected: String, actual: LocalTime?, toleranceMinutes: Long = 3) {
        assertNotNull(actual)
        val difference = abs(Duration.between(LocalTime.parse(expected), actual).toMinutes())
        assertTrue(difference <= toleranceMinutes, "Atteso circa $expected, calcolato $actual")
    }

    @Test
    fun `alba e tramonto a Vienna come nelle previsioni reali di Open-Meteo`() {
        // Valori della risposta reale di Open-Meteo (data/src/test/resources/openmeteo/forecast_daily_vienna.json).
        val expected = mapOf(
            LocalDate.of(2026, 10, 1) to ("06:53" to "18:33"),
            LocalDate.of(2026, 10, 6) to ("07:00" to "18:23"),
            LocalDate.of(2026, 10, 11) to ("07:07" to "18:13"),
            LocalDate.of(2026, 10, 16) to ("07:15" to "18:04"),
        )
        expected.forEach { (date, times) ->
            val sun = SunCalculator.sunTimes(date, vienna, viennaZone)
            assertClose(times.first, sun.sunrise)
            assertClose(times.second, sun.sunset)
        }
    }

    @Test
    fun `l'ora d'oro della sera comincia prima del tramonto, quella del mattino finisce dopo l'alba`() {
        val sun = SunCalculator.sunTimes(LocalDate.of(2026, 12, 10), vienna, viennaZone)

        // A Vienna il 10 dicembre il sole tramonta verso le 15:59.
        assertClose("15:59", sun.sunset)
        assertTrue(sun.eveningGoldenHourStart!! < sun.sunset!!)
        assertTrue(sun.morningGoldenHourEnd!! > sun.sunrise!!)
        assertTrue(Duration.between(sun.eveningGoldenHourStart, sun.sunset).toMinutes() in 40..70)
    }

    @Test
    fun `nell'emisfero sud e oltre il circolo polare`() {
        val sydney = SunCalculator.sunTimes(LocalDate.of(2026, 12, 21), GeoPoint(-33.87, 151.21), ZoneId.of("Australia/Sydney"))
        // Estate australe: alba verso le 5:41, tramonto verso le 20:05 (ora legale).
        assertClose("05:41", sydney.sunrise, toleranceMinutes = 4)
        assertClose("20:05", sydney.sunset, toleranceMinutes = 4)

        val tromsoWinter = SunCalculator.sunTimes(LocalDate.of(2026, 12, 21), GeoPoint(69.65, 18.96), ZoneId.of("Europe/Oslo"))
        assertTrue(tromsoWinter.polarNight)
        assertNull(tromsoWinter.sunrise)

        val tromsoSummer = SunCalculator.sunTimes(LocalDate.of(2026, 6, 21), GeoPoint(69.65, 18.96), ZoneId.of("Europe/Oslo"))
        assertTrue(tromsoSummer.polarDay)
        assertNull(tromsoSummer.sunset)
    }
}
