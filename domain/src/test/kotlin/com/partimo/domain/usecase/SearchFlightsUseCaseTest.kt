package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.common.DataResult
import com.partimo.domain.common.QueryIssue
import com.partimo.domain.model.flight.FlexibleDates
import com.partimo.domain.model.flight.FlightFilter
import com.partimo.domain.model.flight.FlightPriceSource
import com.partimo.domain.model.flight.FlightSearchQuery
import com.partimo.domain.model.flight.FlightSortOption
import com.partimo.domain.testing.FakeFlightRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.testing.TestData.flightOffer
import com.partimo.domain.testing.failureError
import com.partimo.domain.testing.successData
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.Month
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchFlightsUseCaseTest {

    private val repository = FakeFlightRepository()
    private val useCase = SearchFlightsUseCase(repository, clock = TestData.FIXED_CLOCK)
    private val query = FlightSearchQuery(
        originIata = "MXP",
        destinationIata = "VIE",
        departureDate = LocalDate.of(2026, Month.DECEMBER, 12),
        returnDate = LocalDate.of(2026, Month.DECEMBER, 16),
    )

    @BeforeTest
    fun setUp() {
        repository.result = DataResult.Success(
            listOf(
                flightOffer("slow-cheap", "90", durationMinutes = 360, stops = 2),
                flightOffer("balanced", "110", durationMinutes = 95, stops = 0),
                flightOffer("fast-expensive", "320", durationMinutes = 85, stops = 0),
            ),
        )
    }

    @Test
    fun `ordina le offerte per miglior rapporto qualità prezzo`() = runTest {
        val ranked = useCase(query).successData()

        assertEquals(listOf("balanced", "slow-cheap", "fast-expensive"), ranked.map { it.offer.id })
        assertTrue(ranked.zipWithNext().all { (a, b) -> a.valueScore >= b.valueScore })
    }

    @Test
    fun `supporta ordinamento per prezzo e per durata`() = runTest {
        assertEquals("slow-cheap", useCase(query, sortBy = FlightSortOption.CHEAPEST).successData().first().offer.id)
        assertEquals("fast-expensive", useCase(query, sortBy = FlightSortOption.FASTEST).successData().first().offer.id)
    }

    @Test
    fun `applica i filtri su scali e prezzo massimo`() = runTest {
        val filtered = useCase(query, filter = FlightFilter(maxStops = 0, maxPrice = "200".toBigDecimal())).successData()

        assertEquals(listOf("balanced"), filtered.map { it.offer.id })
    }

    @Test
    fun `una ricerca non valida non consuma chiamate API`() = runTest {
        val error = useCase(query.copy(destinationIata = "MXP")).failureError()

        assertEquals(DataError.InvalidQuery(QueryIssue.SAME_ORIGIN_AND_DESTINATION), error)
        assertTrue(repository.queries.isEmpty())
    }

    @Test
    fun `una partenza nel passato viene rifiutata`() = runTest {
        val error = useCase(query.copy(departureDate = TestData.TODAY.minusDays(1), returnDate = null)).failureError()

        assertEquals(DataError.InvalidQuery(QueryIssue.DATE_IN_THE_PAST), error)
    }

    @Test
    fun `propaga gli errori del repository`() = runTest {
        repository.result = DataResult.Failure(DataError.NoConnection)

        assertEquals(DataError.NoConnection, useCase(query).failureError())
    }

    @Test
    fun `confronta solo offerte nella valuta richiesta`() = runTest {
        repository.result = DataResult.Success(
            listOf(
                flightOffer("eur", "120"),
                flightOffer("gbp", "80", currency = "GBP"),
            ),
        )

        assertEquals(listOf("eur"), useCase(query).successData().map { it.offer.id })
    }

    @Test
    fun `mantiene l'origine del dato e inoltra il forceRefresh`() = runTest {
        repository.result = DataResult.Success(listOf(flightOffer("a", "100")), DataOrigin.CACHE)

        val result = useCase(query, forceRefresh = true)

        assertEquals(DataOrigin.CACHE, (result as DataResult.Success).origin)
        assertEquals(listOf(true), repository.forceRefreshFlags)
    }

    @Test
    fun `con le date scelte dall'utente i voli in quei giorni vengono prima di quelli dei giorni vicini`() = runTest {
        fun roundTrip(id: String, price: String, out: LocalDate, back: LocalDate) = flightOffer(id, price, departure = out.atTime(8, 0)).let { offer ->
            offer.copy(slices = offer.slices + offer.outbound.copy(originIata = "VIE", destinationIata = "MXP", departureTime = back.atTime(18, 0), arrivalTime = back.atTime(19, 30)))
        }
        repository.result = DataResult.Success(
            listOf(
                roundTrip("vicino-economico", "60", LocalDate.of(2026, Month.DECEMBER, 11), LocalDate.of(2026, Month.DECEMBER, 15)),
                roundTrip("esatto", "95", LocalDate.of(2026, Month.DECEMBER, 12), LocalDate.of(2026, Month.DECEMBER, 16)),
            ),
        )
        val nearby = FlexibleDates(LocalDate.of(2026, Month.DECEMBER, 9)..LocalDate.of(2026, Month.DECEMBER, 15), 2L..6L, exactDatesFirst = true)

        assertEquals(listOf("esatto", "vicino-economico"), useCase(query.copy(flexibleDates = nearby)).successData().map { it.offer.id })
        assertEquals(
            listOf("vicino-economico", "esatto"),
            useCase(query.copy(flexibleDates = nearby.copy(exactDatesFirst = false)), sortBy = FlightSortOption.CHEAPEST).successData().map { it.offer.id },
            "Per un mese intero conta solo il prezzo",
        )
    }

    @Test
    fun `dice da dove arrivano i prezzi`() {
        assertEquals(FlightPriceSource.LIVE_OFFERS, useCase.priceSource)
        val recent = SearchFlightsUseCase(FakeFlightRepository(priceSource = FlightPriceSource.RECENT_SEARCHES), clock = TestData.FIXED_CLOCK)
        assertEquals(FlightPriceSource.RECENT_SEARCHES, recent.priceSource)
    }
}
