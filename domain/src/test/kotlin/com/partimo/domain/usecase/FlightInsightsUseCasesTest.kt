package com.partimo.domain.usecase

import com.partimo.domain.common.DataResult
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.flight.AnywhereQuery
import com.partimo.domain.model.flight.FareLevel
import com.partimo.domain.model.flight.PriceCalendar
import com.partimo.domain.testing.FakeFlightInsightsRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.testing.successData
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlightInsightsUseCasesTest {

    private val december = YearMonth.of(2026, Month.DECEMBER)
    private fun day(dayOfMonth: Int): LocalDate = december.atDay(dayOfMonth)

    @Test
    fun `i prezzi si dividono in tre fasce, e con pochi prezzi solo il più basso è conveniente`() {
        val fares = (1..9).associate { day(it) to TestData.fare("${it * 10}", day(it)) }

        val levels = FareLevel.levels(fares)

        assertEquals(listOf(day(1), day(2), day(3)), levels.filterValues { it == FareLevel.LOW }.keys.sorted())
        assertEquals(listOf(day(7), day(8), day(9)), levels.filterValues { it == FareLevel.HIGH }.keys.sorted())
        assertEquals(
            mapOf(day(1) to FareLevel.MEDIUM, day(2) to FareLevel.LOW),
            FareLevel.levels(mapOf(day(1) to TestData.fare("90", day(1)), day(2) to TestData.fare("40", day(2)))),
        )
        assertTrue(FareLevel.levels(emptyMap<LocalDate, Nothing>()).isEmpty())
    }

    @Test
    fun `il calendario del mese usa le notti delle date scelte o i soggiorni flessibili`() = runTest {
        val repository = FakeFlightInsightsRepository(
            days = DataResult.Success(
                mapOf(day(7) to TestData.fare("72", day(7)), day(11) to TestData.fare("92", day(11)), LocalDate.of(2027, 1, 2) to TestData.fare("20", LocalDate.of(2027, 1, 2))),
            ),
        )
        val calendar = GetPriceCalendarUseCase(repository)

        val month = calendar("MXP", "VIE", december, TravelPeriod.InMonth(december)).successData()
        val dates = calendar("MXP", "VIE", december, TravelPeriod.Dates(day(11), day(13))).successData()

        assertEquals(listOf(december to TravelPeriod.FLEXIBLE_STAY_NIGHTS, december to 2L..2L), repository.dayRequests)
        assertEquals(setOf(day(7), day(11)), month.fares.keys, "Solo i giorni del mese")
        assertEquals("72.00", month.cheapest?.price?.amount?.toPlainString())
        assertEquals(2L..2L, dates.stayNights)
        assertEquals(PriceCalendar(december, 2L..2L, month.fares), dates)
    }

    @Test
    fun `il mese più conveniente si cerca per soggiorni da un fine settimana a una settimana`() = runTest {
        val repository = FakeFlightInsightsRepository(months = DataResult.Success(mapOf(december to TestData.fare("72", day(7)))))

        val months = GetMonthPricesUseCase(repository)("MXP", "VIE").successData()

        assertEquals("72.00", months.getValue(december).price.amount.toPlainString())
        assertEquals(Triple("MXP", "VIE", TravelPeriod.FLEXIBLE_STAY_NIGHTS), repository.monthRequests.single())
    }

    @Test
    fun `ovunque usa le date della dashboard`() {
        val today = TestData.TODAY
        val nextDays = AnywhereQuery.of("MXP", TravelPeriod.NextDays, today)
        val inMonth = AnywhereQuery.of("MXP", TravelPeriod.InMonth(december), today)
        val dates = AnywhereQuery.of("MXP", TravelPeriod.Dates(day(11), day(13)), today)

        assertEquals(today.plusDays(1)..today.plusDays(7), nextDays.departures)
        assertEquals(day(1)..day(31), inMonth.departures)
        assertEquals(TravelPeriod.FLEXIBLE_STAY_NIGHTS, inMonth.stayNights)
        assertEquals(day(11) to day(13), dates.exactDates)

        assertTrue(inMonth.matches(day(10), day(15)))
        assertFalse(inMonth.matches(day(10), day(10)), "In giornata non è un viaggio")
        assertFalse(inMonth.matches(day(10), day(31)), "Tre settimane sono troppe")
        assertFalse(inMonth.matches(day(10), null), "Sola andata")
        assertTrue(dates.matches(day(11), day(13)))
        assertFalse(dates.matches(day(10), day(13)))
    }

    @Test
    fun `ovunque mostra una meta per città, dalla più economica, nelle date giuste`() = runTest {
        val repository = FakeFlightInsightsRepository(
            destinations = DataResult.Success(
                listOf(
                    TestData.cheapDestination("BCN", "Barcellona", "33", day(10), day(12)),
                    TestData.cheapDestination("PMO", "Palermo", "29", day(10), day(15)),
                    TestData.cheapDestination("GDN", "Danzica", "25", day(21), day(21)),
                    TestData.cheapDestination("BCN", "Barcellona", "40", day(3), day(6)),
                ),
            ),
        )

        val found = FindCheapDestinationsUseCase(repository, TestData.FIXED_CLOCK)("MXP", TravelPeriod.InMonth(december)).successData()

        assertEquals(listOf("Palermo" to "29.00", "Barcellona" to "33.00"), found.map { it.city.name to it.fare.price.amount.toPlainString() })
        assertEquals(day(1)..day(31), repository.destinationQueries.single().departures)
    }
}
