package com.partimo.domain.model

import com.partimo.domain.model.flight.FlexibleDates
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
    fun `con le date flessibili si parte entro una settimana o in tutto il mese`() {
        val december = TravelPeriod.InMonth(YearMonth.of(2026, Month.DECEMBER))
        val october = TravelPeriod.InMonth(YearMonth.of(2026, Month.OCTOBER))

        assertEquals(FlexibleDates(today.plusDays(1)..today.plusDays(7), 2L..7L), TravelPeriod.NextDays.flexibleDates(today))
        assertEquals(FlexibleDates(LocalDate.of(2026, Month.DECEMBER, 1)..LocalDate.of(2026, Month.DECEMBER, 31), 2L..7L), december.flexibleDates(today))
        val lateOctober = LocalDate.of(2026, Month.OCTOBER, 20)
        assertEquals(lateOctober.plusDays(1)..LocalDate.of(2026, Month.OCTOBER, 31), october.flexibleDates(lateOctober).departures, "Nel mese in corso da domani")
    }

    @Test
    fun `con le date scelte si parte e si torna in quei giorni, con i giorni vicini come alternativa`() {
        val dates = TravelPeriod.Dates(LocalDate.of(2026, Month.DECEMBER, 10), LocalDate.of(2026, Month.DECEMBER, 14))

        assertEquals(LocalDate.of(2026, Month.DECEMBER, 10), dates.departureDate(today))
        assertEquals(LocalDate.of(2026, Month.DECEMBER, 14), dates.returnDate(today))
        assertEquals(4, dates.nights)
        val flexible = dates.flexibleDates(today)
        assertEquals(LocalDate.of(2026, Month.DECEMBER, 7)..LocalDate.of(2026, Month.DECEMBER, 13), flexible.departures)
        assertEquals(2L..6L, flexible.stayNights)
        assertTrue(flexible.exactDatesFirst)
        // Non si cercano partenze già passate.
        val tomorrow = TravelPeriod.Dates(today.plusDays(1), today.plusDays(2))
        assertEquals(today.plusDays(1)..today.plusDays(4), tomorrow.flexibleDates(today).departures)
        assertEquals(0L..3L, tomorrow.flexibleDates(today).stayNights)
    }

    @Test
    fun `un viaggio con le date è finito dopo il ritorno ed è imminente dal giorno prima della partenza`() {
        val dates = TravelPeriod.Dates(LocalDate.of(2026, Month.DECEMBER, 10), LocalDate.of(2026, Month.DECEMBER, 14))

        assertFalse(dates.isOver(LocalDate.of(2026, Month.DECEMBER, 14)))
        assertTrue(dates.isOver(LocalDate.of(2026, Month.DECEMBER, 15)))
        assertFalse(dates.isImminent(LocalDate.of(2026, Month.DECEMBER, 8)))
        assertTrue(dates.isImminent(LocalDate.of(2026, Month.DECEMBER, 9)), "Il giorno prima")
        assertTrue(dates.isImminent(LocalDate.of(2026, Month.DECEMBER, 12)), "Durante il viaggio")
        assertFalse(dates.isImminent(LocalDate.of(2026, Month.DECEMBER, 15)))
        assertTrue(TravelPeriod.NextDays.isImminent(today))
        assertFalse(TravelPeriod.InMonth(YearMonth.of(2026, Month.OCTOBER)).isImminent(today))
    }

    @Test
    fun `la chiave permette di salvare e ricostruire il periodo`() {
        val december = TravelPeriod.InMonth(YearMonth.of(2026, Month.DECEMBER))

        assertEquals("2026-12", december.key)
        assertEquals(december, TravelPeriod.fromKey(december.key))
        assertEquals(TravelPeriod.NextDays, TravelPeriod.fromKey("NEXT_DAYS"))
        assertNull(TravelPeriod.fromKey("dicembre"))
        val dates = TravelPeriod.Dates(LocalDate.of(2026, Month.DECEMBER, 10), LocalDate.of(2026, Month.DECEMBER, 14))
        assertEquals("2026-12-10_2026-12-14", dates.key)
        assertEquals(dates, TravelPeriod.fromKey(dates.key))
        assertNull(TravelPeriod.fromKey("2026-12-14_2026-12-10"), "Il ritorno non può precedere l'andata")
        assertNull(TravelPeriod.fromKey("2026-12-10_poi"))
    }

    @Test
    fun `un mese passato è concluso`() {
        assertTrue(TravelPeriod.InMonth(YearMonth.of(2026, Month.AUGUST)).isOver(today))
        assertFalse(TravelPeriod.InMonth(YearMonth.of(2026, Month.SEPTEMBER)).isOver(today))
        assertFalse(TravelPeriod.NextDays.isOver(today))
    }
}
