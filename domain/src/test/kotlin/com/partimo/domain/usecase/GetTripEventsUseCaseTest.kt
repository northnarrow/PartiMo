package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.event.EventKind
import com.partimo.domain.model.event.EventTiming
import com.partimo.domain.service.SeasonalCalendar
import com.partimo.domain.testing.FakeEventRepository
import com.partimo.domain.testing.FakeHolidayRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.testing.TestData.event
import com.partimo.domain.testing.TestData.holiday
import com.partimo.domain.testing.failureError
import com.partimo.domain.testing.successData
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.Month
import java.time.MonthDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetTripEventsUseCaseTest {

    private val vienna = TestData.destination()
    private val events = FakeEventRepository()
    private val holidays = FakeHolidayRepository()
    private val useCase = GetTripEventsUseCase(events, holidays)

    private fun date(month: Month, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)

    @Test
    fun `tiene solo gli eventi del soggiorno, prima mercatini e festival poi le festività`() = runTest {
        events.result = DataResult.Success(
            listOf(
                event("capodanno", EventTiming.Yearly(MonthDay.of(Month.JANUARY, 1), MonthDay.of(Month.JANUARY, 1))),
                event("viennale", EventTiming.InMonths(setOf(Month.OCTOBER))),
                event("concerti-avvento", EventTiming.InMonths(setOf(Month.DECEMBER))),
                event("rathausplatz", SeasonalCalendar.CHRISTMAS_MARKET_SEASON, kind = EventKind.CHRISTMAS_MARKET),
            ),
        )
        holidays.default = DataResult.Success(
            listOf(
                holiday("immacolata", date(Month.DECEMBER, 8)),
                holiday("prova", date(Month.DECEMBER, 12)),
                holiday("natale", date(Month.DECEMBER, 25)),
            ),
        )

        val trip = useCase(vienna, date(Month.DECEMBER, 10), date(Month.DECEMBER, 14)).successData()

        assertEquals(listOf("rathausplatz", "concerti-avvento", "prova"), trip.events.map { it.id })
        assertEquals(vienna.center, events.queries.single().location)
        assertEquals(listOf("AT" to 2026), holidays.requests)
    }

    @Test
    fun `a cavallo di capodanno chiede le festività di entrambi gli anni e usa sempre la cache degli eventi`() = runTest {
        events.result = DataResult.Success(
            listOf(event("concerto", EventTiming.Yearly(MonthDay.of(Month.JANUARY, 1), MonthDay.of(Month.JANUARY, 1)))),
        )
        holidays.byYear = mapOf(2027 to DataResult.Success(listOf(holiday("capodanno", date(Month.JANUARY, 1, year = 2027)))))

        val trip = useCase(vienna, date(Month.DECEMBER, 30), date(Month.JANUARY, 3, year = 2027), forceRefresh = true).successData()

        assertEquals(listOf("concerto", "capodanno"), trip.events.map { it.id })
        assertEquals(listOf("AT" to 2026, "AT" to 2027), holidays.requests.sortedBy { it.second })
        assertEquals(listOf(false), events.forceRefreshFlags, "Gli eventi ricorrenti non si ricaricano con «Aggiorna»")
    }

    @Test
    fun `se una fonte non risponde restano gli eventi dell'altra`() = runTest {
        events.result = DataResult.Failure(DataError.RateLimited)
        holidays.default = DataResult.Success(listOf(holiday("immacolata", date(Month.DECEMBER, 8))), DataOrigin.CACHE)

        val result = useCase(vienna, date(Month.DECEMBER, 7), date(Month.DECEMBER, 9))

        assertIs<DataResult.Success<*>>(result)
        assertEquals(listOf("immacolata"), result.successData().events.map { it.id })
        assertEquals(DataOrigin.CACHE, result.origin)
    }

    @Test
    fun `se nessuna fonte risponde restituisce l'errore degli eventi`() = runTest {
        events.result = DataResult.Failure(DataError.Timeout)
        holidays.default = DataResult.Failure(DataError.NoConnection)

        val error = useCase(vienna, date(Month.DECEMBER, 7), date(Month.DECEMBER, 9)).failureError()

        assertEquals(DataError.Timeout, error)
    }

    @Test
    fun `i doppioni spariscono e la lista ha un limite`() = runTest {
        val december = EventTiming.InMonths(setOf(Month.DECEMBER))
        events.result = DataResult.Success(listOf(event("a", december), event("a", december)) + (1..20).map { event("e$it", december) })

        val trip = GetTripEventsUseCase(events, holidays, maxEvents = 5)(vienna, date(Month.DECEMBER, 1), date(Month.DECEMBER, 2)).successData()

        assertEquals(5, trip.events.size)
        assertEquals(trip.events.map { it.id }.distinct(), trip.events.map { it.id })
    }
}
