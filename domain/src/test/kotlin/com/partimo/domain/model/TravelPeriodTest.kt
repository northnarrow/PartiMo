package com.partimo.domain.model

import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TravelPeriodTest {

    private val today = LocalDate.of(2026, Month.SEPTEMBER, 30)

    @Test
    fun `si possono scegliere i prossimi giorni e tutti i dodici mesi`() {
        val periods = TravelPeriod.selectable(today)

        assertEquals(TravelPeriod.NextDays, periods.first())
        val months = periods.drop(1).map { (it as TravelPeriod.InMonth).month }
        assertEquals(YearMonth.of(2026, Month.OCTOBER), months.first())
        assertEquals(YearMonth.of(2027, Month.SEPTEMBER), months.last())
        assertEquals(Month.entries.toSet(), months.map { it.month }.toSet())
    }

    @Test
    fun `il mese corrente compare solo se la partenza di metà mese non è passata`() {
        val early = TravelPeriod.selectable(LocalDate.of(2026, Month.SEPTEMBER, 5))

        assertEquals(TravelPeriod.InMonth(YearMonth.of(2026, Month.SEPTEMBER)), early[1])
        assertEquals(TravelPeriod.InMonth(YearMonth.of(2027, Month.AUGUST)), early.last())
    }

    @Test
    fun `nei mesi futuri si parte a metà mese per quattro notti`() {
        val december = TravelPeriod.InMonth(YearMonth.of(2026, Month.DECEMBER))

        assertEquals(LocalDate.of(2026, Month.DECEMBER, 10), december.departureDate(today))
        assertEquals(LocalDate.of(2026, Month.DECEMBER, 14), december.returnDate(today))
    }

    @Test
    fun `nei prossimi giorni e nel mese in corso ormai avanzato si parte domani`() {
        val september = TravelPeriod.InMonth(YearMonth.of(2026, Month.SEPTEMBER))
        val lateSeptember = LocalDate.of(2026, Month.SEPTEMBER, 20)

        assertEquals(today.plusDays(1), TravelPeriod.NextDays.departureDate(today))
        assertEquals(today.plusDays(5), TravelPeriod.NextDays.returnDate(today))
        assertEquals(lateSeptember.plusDays(1), september.departureDate(lateSeptember))
    }

    @Test
    fun `la chiave permette di salvare e ricostruire il periodo`() {
        val december = TravelPeriod.InMonth(YearMonth.of(2026, Month.DECEMBER))

        assertEquals("2026-12", december.key)
        assertEquals(december, TravelPeriod.fromKey(december.key))
        assertEquals(TravelPeriod.NextDays, TravelPeriod.fromKey("NEXT_DAYS"))
        assertNull(TravelPeriod.fromKey("dicembre"))
    }

    @Test
    fun `un mese passato è concluso`() {
        assertTrue(TravelPeriod.InMonth(YearMonth.of(2026, Month.AUGUST)).isOver(today))
        assertFalse(TravelPeriod.InMonth(YearMonth.of(2026, Month.SEPTEMBER)).isOver(today))
        assertFalse(TravelPeriod.NextDays.isOver(today))
    }
}
