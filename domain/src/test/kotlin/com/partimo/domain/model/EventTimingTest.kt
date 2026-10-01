package com.partimo.domain.model

import com.partimo.domain.model.event.EventTiming
import com.partimo.domain.model.event.firstDayWithin
import com.partimo.domain.model.event.overlaps
import com.partimo.domain.service.SeasonalCalendar
import java.time.LocalDate
import java.time.Month
import java.time.MonthDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventTimingTest {

    private fun date(month: Month, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)

    @Test
    fun `i mercatini di Natale cadono nei soggiorni tra metà novembre e la vigilia`() {
        val season = SeasonalCalendar.CHRISTMAS_MARKET_SEASON

        assertEquals(date(Month.DECEMBER, 10), season.firstDayWithin(date(Month.DECEMBER, 10), date(Month.DECEMBER, 14)))
        assertEquals(date(Month.NOVEMBER, 15), season.firstDayWithin(date(Month.NOVEMBER, 10), date(Month.NOVEMBER, 16)))
        assertFalse(season.overlaps(date(Month.DECEMBER, 27), date(Month.JANUARY, 2, year = 2027)), "Dopo Natale i mercatini chiudono")
        assertFalse(season.overlaps(date(Month.OCTOBER, 10), date(Month.OCTOBER, 14)))
        assertTrue(SeasonalCalendar.isChristmasMarketSeason(date(Month.DECEMBER, 20), date(Month.DECEMBER, 28)))
        assertFalse(SeasonalCalendar.isChristmasMarketSeason(date(Month.DECEMBER, 26), date(Month.DECEMBER, 30)))
    }

    @Test
    fun `un periodo annuale può attraversare il capodanno`() {
        val holidays = EventTiming.Yearly(MonthDay.of(Month.DECEMBER, 20), MonthDay.of(Month.JANUARY, 6))

        assertTrue(holidays.isOn(date(Month.DECEMBER, 31)))
        assertTrue(holidays.isOn(date(Month.JANUARY, 6)))
        assertFalse(holidays.isOn(date(Month.JANUARY, 7)))
        assertFalse(holidays.isOn(date(Month.DECEMBER, 19)))
    }

    @Test
    fun `un evento in certi mesi parte dal primo giorno del mese dentro il soggiorno`() {
        val november = EventTiming.InMonths(setOf(Month.NOVEMBER))

        assertEquals(date(Month.NOVEMBER, 1), november.firstDayWithin(date(Month.OCTOBER, 30), date(Month.NOVEMBER, 3)))
        assertNull(november.firstDayWithin(date(Month.DECEMBER, 10), date(Month.DECEMBER, 14)))
        assertFailsWith<IllegalArgumentException> { EventTiming.InMonths(emptySet()) }
    }

    @Test
    fun `una data precisa conta solo se è nel soggiorno`() {
        val immaculate = EventTiming.OnDates(date(Month.DECEMBER, 8), date(Month.DECEMBER, 8))

        assertEquals(date(Month.DECEMBER, 8), immaculate.firstDayWithin(date(Month.DECEMBER, 6), date(Month.DECEMBER, 10)))
        assertFalse(immaculate.overlaps(date(Month.DECEMBER, 8, year = 2027), date(Month.DECEMBER, 9, year = 2027)), "Vale solo per l'anno indicato")
        assertFailsWith<IllegalArgumentException> { EventTiming.OnDates(date(Month.DECEMBER, 9), date(Month.DECEMBER, 8)) }
    }
}
