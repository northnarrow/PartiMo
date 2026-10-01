package com.partimo.domain.service

import com.partimo.domain.model.hours.OpenState
import com.partimo.domain.model.hours.OpeningHours
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpeningHoursParserTest {

    /** Settimana del 7–13 dicembre 2026: lunedì 7, domenica 13. */
    private fun day(dayOfWeek: DayOfWeek): LocalDate = LocalDate.of(2026, Month.DECEMBER, 7).plusDays(dayOfWeek.ordinal.toLong())

    private fun parse(text: String): OpeningHours = assertNotNull(OpeningHoursParser.parse(text), "Non interpretato: $text")

    private fun OpeningHours.hoursOn(dayOfWeek: DayOfWeek): String =
        spansOn(day(dayOfWeek)).joinToString(",") { "${it.start}-${it.end}" + if (it.openEnd) "+" else "" }.ifEmpty { "chiuso" }

    @Test
    fun `orari reali dei ristoranti di Vienna`() {
        // Valori presi dalla risposta reale di OpenStreetMap usata nei test del data layer.
        val weekdaysAndWeekend = parse("Mo-Fr 10:00-23:00; Sa, Su 09:00-23:00")
        assertEquals("10:00-23:00", weekdaysAndWeekend.hoursOn(DayOfWeek.FRIDAY))
        assertEquals("09:00-23:00", weekdaysAndWeekend.hoursOn(DayOfWeek.SUNDAY))

        val lunchAndDinner = parse("Mo-Fr 11:30-14:30, 18:00-22:00; Sa 18:00-22:00; Su, PH 12:00-14:00, 18:00-22:00")
        assertEquals("11:30-14:30,18:00-22:00", lunchAndDinner.hoursOn(DayOfWeek.TUESDAY))
        assertEquals("18:00-22:00", lunchAndDinner.hoursOn(DayOfWeek.SATURDAY))
        assertEquals("12:00-14:00,18:00-22:00", lunchAndDinner.hoursOn(DayOfWeek.SUNDAY))

        val closedDays = parse("Tu-Th 10:30-18:00; Fr 08:30-18:00; Mo off; Sa 08:30-14:00")
        assertEquals("chiuso", closedDays.hoursOn(DayOfWeek.MONDAY))
        assertEquals("chiuso", closedDays.hoursOn(DayOfWeek.SUNDAY))

        val holidaysOnly = parse("PH off; Tu-Sa 16:00-23:00")
        assertEquals("16:00-23:00", holidaysOnly.hoursOn(DayOfWeek.TUESDAY))
        assertEquals("chiuso", holidaysOnly.hoursOn(DayOfWeek.MONDAY))

        assertEquals("11:00-00:00", parse("Mo-Su,PH 11:00-24:00").hoursOn(DayOfWeek.WEDNESDAY))
        assertEquals("chiuso", parse("Mo-Fr 15:00-23:00; Sa, Su, PH off").hoursOn(DayOfWeek.SATURDAY))
        assertEquals("11:00-22:30", parse("11:00-22:30").hoursOn(DayOfWeek.SUNDAY))
    }

    @Test
    fun `le regole dopo una virgola si aggiungono, quelle dopo un punto e virgola sostituiscono`() {
        val additional = parse("Mo-Su 11:30-15:00, Mo-Sa 17:30-22:30")
        assertEquals("11:30-15:00,17:30-22:30", additional.hoursOn(DayOfWeek.MONDAY))
        assertEquals("11:30-15:00", additional.hoursOn(DayOfWeek.SUNDAY))

        val nightOwls = parse("Su-Th,PH 07:00-02:00, Fr,Sa 07:00-04:00")
        assertEquals("07:00-02:00", nightOwls.hoursOn(DayOfWeek.THURSDAY))
        assertEquals("07:00-04:00", nightOwls.hoursOn(DayOfWeek.SATURDAY))

        assertEquals("chiuso", parse("Su-Fr 11:00-20:30, Sa off").hoursOn(DayOfWeek.SATURDAY))
        assertEquals("09:00-12:00", parse("Mo-Fr 09:00-18:00; We 09:00-12:00").hoursOn(DayOfWeek.WEDNESDAY))
    }

    @Test
    fun `aperto ora, con la chiusura dopo mezzanotte`() {
        val bar = parse("Mo-Th 10:00-02:00; Fr-Sa 10:00-03:00; Su 10:00-00:00")
        val fridayNight = day(DayOfWeek.FRIDAY).atTime(23, 30)

        assertEquals(OpenState.Open(day(DayOfWeek.SATURDAY).atTime(3, 0)), bar.stateAt(fridayNight))
        // Sabato all'1:30 è ancora aperto per la fascia del venerdì.
        assertEquals(OpenState.Open(day(DayOfWeek.SATURDAY).atTime(3, 0)), bar.stateAt(day(DayOfWeek.SATURDAY).atTime(1, 30)))
        assertEquals(OpenState.Closed(day(DayOfWeek.SATURDAY).atTime(10, 0)), bar.stateAt(day(DayOfWeek.SATURDAY).atTime(4, 0)))
    }

    @Test
    fun `chiuso oggi, apre il primo giorno utile`() {
        val museum = parse("Tu-Su 10:00-18:00; Mo off")

        assertEquals(OpenState.Closed(day(DayOfWeek.TUESDAY).atTime(10, 0)), museum.stateAt(day(DayOfWeek.MONDAY).atTime(12, 0)))
        assertEquals(OpenState.Open(day(DayOfWeek.TUESDAY).atTime(18, 0)), museum.stateAt(day(DayOfWeek.TUESDAY).atTime(17, 59)))
        assertIs<OpenState.Closed>(museum.stateAt(day(DayOfWeek.TUESDAY).atTime(18, 0)))
    }

    @Test
    fun `sempre aperto, con le eccezioni di chiusura`() {
        val always = parse("24/7; Dec 25 off")

        assertEquals(OpenState.Open(null), always.stateAt(LocalDateTime.of(2026, 12, 9, 3, 0)))
        assertEquals("chiuso", always.spansOn(LocalDate.of(2026, 12, 25)).let { if (it.isEmpty()) "chiuso" else "aperto" })
        assertEquals(OpenState.Open(LocalDateTime.of(2026, 12, 25, 0, 0)), always.stateAt(LocalDateTime.of(2026, 12, 24, 22, 0)))
    }

    @Test
    fun `orari diversi secondo i mesi`() {
        val castle = parse("Nov-Mar Mo-Su 09:00-16:30; Apr-Oct: Mo-Su 08:30-18:30")

        assertEquals(listOf(LocalTime.of(9, 0)), castle.spansOn(LocalDate.of(2026, 12, 10)).map { it.start })
        assertEquals(listOf(LocalTime.of(8, 30)), castle.spansOn(LocalDate.of(2026, 7, 10)).map { it.start })
    }

    @Test
    fun `apertura senza orario di chiusura`() {
        val club = parse("Fr-Sa 22:00+")

        assertEquals(OpenState.Open(null), club.stateAt(day(DayOfWeek.FRIDAY).atTime(23, 0)))
        assertTrue(club.spansOn(day(DayOfWeek.FRIDAY)).single().openEnd)
    }

    @Test
    fun `gli orari variabili o non validi non si indovinano`() {
        assertNull(OpeningHoursParser.parse("sunrise-sunset"))
        assertNull(OpeningHoursParser.parse("week 1-26 Mo-Fr 09:00-17:00"))
        assertNull(OpeningHoursParser.parse("Mo-Fr 25:00-26:00"))
        assertNull(OpeningHoursParser.parse("su richiesta"))
        assertNull(OpeningHoursParser.parse(""))
        assertNull(OpeningHoursParser.parse(null))
        // Un'eccezione di chiusura che non si sa leggere viene ignorata, il resto resta valido.
        assertEquals("09:00-17:00", parse("Mo-Fr 09:00-17:00; easter off").hoursOn(DayOfWeek.MONDAY))
    }
}
