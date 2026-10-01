package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.Money
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.deal.DealKind
import com.partimo.domain.testing.FakeAccommodationRepository
import com.partimo.domain.testing.FakeFlightRepository
import com.partimo.domain.testing.FakePriceWatchRepository
import com.partimo.domain.testing.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CheckPriceWatchesUseCaseTest {

    private val flights = FakeFlightRepository()
    private val stays = FakeAccommodationRepository()

    private fun useCase(repository: FakePriceWatchRepository) = CheckPriceWatchesUseCase(
        repository = repository,
        searchFlights = SearchFlightsUseCase(flights, clock = TestData.FIXED_CLOCK),
        searchAccommodations = SearchAccommodationsUseCase(stays, clock = TestData.FIXED_CLOCK),
        clock = TestData.FIXED_CLOCK,
    )

    private fun eur(amount: String) = Money.of(amount, "EUR")

    @Test
    fun `un volo molto sotto il prezzo abituale genera un avviso`() = runTest {
        val repository = FakePriceWatchRepository(listOf(TestData.priceWatch(flightPrices = listOf("200", "210", "190"))))
        flights.result = DataResult.Success(
            listOf(TestData.flightOffer("cheap", "150"), TestData.flightOffer("normal", "230")),
        )

        val alerts = useCase(repository)()

        val deal = alerts.single().deals.single()
        assertEquals(DealKind.FLIGHT, deal.kind)
        assertEquals(eur("150"), deal.price)
        assertEquals(eur("200"), deal.usualPrice)
        assertEquals(25, deal.discountPercent)

        // Ricerca senza cache con le date del periodo seguito.
        val query = flights.queries.single()
        assertEquals("MXP", query.originIata)
        assertEquals("VIE", query.destinationIata)
        assertEquals(LocalDate.of(2026, Month.DECEMBER, 10), query.departureDate)
        assertEquals(LocalDate.of(2026, Month.DECEMBER, 1)..LocalDate.of(2026, Month.DECEMBER, 31), query.flexibleDepartures, "Il volo più conveniente del mese")
        assertEquals(listOf(true), flights.forceRefreshFlags)
        assertEquals(listOf(true), stays.forceRefreshFlags)

        val saved = repository.current.single()
        assertEquals(4, saved.flightPrices.size)
        assertEquals(eur("150"), saved.lastNotifiedFlight)
    }

    @Test
    fun `per gli alloggi conta solo la struttura ben recensita più economica`() = runTest {
        val repository = FakePriceWatchRepository(listOf(TestData.priceWatch(stayPrices = listOf("100", "100"))))
        stays.result = DataResult.Success(
            listOf(
                TestData.stayOffer("bad", totalPrice = "200", nights = 4, reviewScore = 6.1),
                TestData.stayOffer("good", totalPrice = "320", nights = 4, reviewScore = 8.6),
            ),
        )

        val deal = useCase(repository)().single().deals.single()

        assertEquals(DealKind.STAY, deal.kind)
        assertEquals(eur("80"), deal.price)
        assertEquals(20, deal.discountPercent)
        assertEquals(8.6, deal.reviewScore)
    }

    @Test
    fun `prezzi nella norma aggiornano lo storico senza avvisi`() = runTest {
        val repository = FakePriceWatchRepository(listOf(TestData.priceWatch(flightPrices = listOf("200"))))
        flights.result = DataResult.Success(listOf(TestData.flightOffer("normal", "190")))

        assertTrue(useCase(repository)().isEmpty())
        assertEquals(listOf(eur("200"), eur("190")), repository.current.single().flightPrices.map { it.price })
    }

    @Test
    fun `lo stesso affare viene segnalato una sola volta`() = runTest {
        val repository = FakePriceWatchRepository(listOf(TestData.priceWatch(flightPrices = listOf("200", "200", "200"))))
        flights.result = DataResult.Success(listOf(TestData.flightOffer("cheap", "150")))
        val check = useCase(repository)

        assertEquals(1, check().size)
        assertTrue(check().isEmpty(), "Stesso prezzo: nessun nuovo avviso")

        flights.result = DataResult.Success(listOf(TestData.flightOffer("cheaper", "130")))
        assertEquals(eur("130"), check().single().deals.single().price)
    }

    @Test
    fun `gli avvisi di mesi passati vengono rimossi senza cercare`() = runTest {
        val expired = TestData.priceWatch(period = TravelPeriod.InMonth(YearMonth.of(2026, Month.AUGUST)))
        val repository = FakePriceWatchRepository(listOf(expired))

        assertTrue(useCase(repository)().isEmpty())
        assertTrue(repository.current.isEmpty())
        assertTrue(flights.queries.isEmpty())
    }

    @Test
    fun `una ricerca fallita non altera lo storico`() = runTest {
        val watch = TestData.priceWatch(flightPrices = listOf("200"))
        val repository = FakePriceWatchRepository(listOf(watch))
        flights.result = DataResult.Failure(DataError.NoConnection)

        assertTrue(useCase(repository)().isEmpty())
        assertEquals(watch.flightPrices, repository.current.single().flightPrices)
    }
}

class PriceAlertToggleTest {

    private val repository = FakePriceWatchRepository()
    private val setAlert = SetPriceAlertUseCase(repository, TestData.FIXED_CLOCK)
    private val observeAlert = ObservePriceAlertUseCase(repository)
    private val departure = TestData.departure()
    private val destination = TestData.destination()

    @Test
    fun `attivare l'avviso usa i prezzi attuali come primo riferimento`() = runTest {
        setAlert(true, departure, destination, TestData.DECEMBER_2026, Money.of(180, "EUR"), Money.of(95, "EUR"))

        val watch = repository.current.single()
        assertEquals(Money.of(180, "EUR"), watch.flightPrices.single().price)
        assertEquals(Money.of(95, "EUR"), watch.stayPrices.single().price)
        assertNull(watch.lastNotifiedFlight)
        assertTrue(observeAlert(departure, destination, TestData.DECEMBER_2026).first())
        assertFalse(observeAlert(departure, destination, TravelPeriod.NextDays).first())
    }

    @Test
    fun `riattivare un avviso esistente conserva lo storico e disattivarlo lo rimuove`() = runTest {
        setAlert(true, departure, destination, TestData.DECEMBER_2026, Money.of(180, "EUR"))
        setAlert(true, departure, destination, TestData.DECEMBER_2026, Money.of(999, "EUR"))
        assertEquals(Money.of(180, "EUR"), repository.current.single().flightPrices.single().price)

        setAlert(false, departure, destination, TestData.DECEMBER_2026)
        assertTrue(repository.current.isEmpty())
        assertFalse(observeAlert(departure, destination, TestData.DECEMBER_2026).first())
    }

    @Test
    fun `senza punto di partenza il viaggio non può essere seguito`() = runTest {
        assertFalse(observeAlert(null, destination, TestData.DECEMBER_2026).first())
    }
}
