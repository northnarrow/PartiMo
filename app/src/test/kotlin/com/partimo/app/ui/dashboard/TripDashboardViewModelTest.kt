package com.partimo.app.ui.dashboard

import app.cash.turbine.test
import com.partimo.app.testing.MainDispatcherRule
import com.partimo.app.testing.successData
import com.partimo.app.ui.common.UiState
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.Money
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.dining.PriceLevel
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.place.DeparturePoint
import com.partimo.domain.testing.FakeAccommodationRepository
import com.partimo.domain.testing.FakeFlightRepository
import com.partimo.domain.testing.FakeLodgingRepository
import com.partimo.domain.testing.FakePoiRepository
import com.partimo.domain.testing.FakePriceWatchRepository
import com.partimo.domain.testing.FakeRestaurantRepository
import com.partimo.domain.testing.FakeTransitRepository
import com.partimo.domain.testing.FakeUserPreferencesRepository
import com.partimo.domain.testing.FakeWeatherRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.usecase.FindBudgetRestaurantsUseCase
import com.partimo.domain.usecase.FindLodgingsUseCase
import com.partimo.domain.usecase.GetSeasonalHighlightsUseCase
import com.partimo.domain.usecase.ObserveDepartureUseCase
import com.partimo.domain.usecase.ObservePriceAlertUseCase
import com.partimo.domain.usecase.PlanTransitRouteUseCase
import com.partimo.domain.usecase.SearchAccommodationsUseCase
import com.partimo.domain.usecase.SearchFlightsUseCase
import com.partimo.domain.usecase.SetPriceAlertUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TripDashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val december = TravelPeriod.InMonth(YearMonth.of(2026, Month.DECEMBER))
    private val flights = FakeFlightRepository(
        DataResult.Success(
            listOf(
                TestData.flightOffer("best", "100", durationMinutes = 90, stops = 0),
                TestData.flightOffer("worst", "300", durationMinutes = 300, stops = 2),
            ),
        ),
    )
    private val stays = FakeAccommodationRepository(DataResult.Success(listOf(TestData.stayOffer("stay", "400"))))
    private val lodgings = FakeLodgingRepository(
        DataResult.Success(
            listOf(
                TestData.lodging("far", location = GeoPoint(48.2206, 16.3960)),
                TestData.lodging("near", location = SampleDestinations.VIENNA.center, stars = 4),
            ),
        ),
    )
    private val pois = FakePoiRepository(
        DataResult.Success(
            listOf(
                TestData.poi("view", "Kahlenberg", category = PoiCategory.VIEWPOINT),
                TestData.poi("museum", "Kunsthistorisches Museum", category = PoiCategory.MUSEUM),
            ),
        ),
    )
    private val weather = FakeWeatherRepository()
    private val transit = FakeTransitRepository(DataResult.Success(listOf(TestData.simpleRoute())))
    private val restaurants = FakeRestaurantRepository(
        DataResult.Success(
            listOf(
                TestData.restaurant("budget", PriceLevel.INEXPENSIVE, rating = 4.6),
                TestData.restaurant("luxury", PriceLevel.VERY_EXPENSIVE, rating = 4.9),
            ),
        ),
    )
    private val preferences = FakeUserPreferencesRepository(initial = TestData.departure("Milano", "MXP"))
    private val watches = FakePriceWatchRepository()

    private fun createViewModel(initialPeriod: TravelPeriod = december) = TripDashboardViewModel(
        searchFlights = SearchFlightsUseCase(flights, clock = TestData.FIXED_CLOCK),
        searchAccommodations = SearchAccommodationsUseCase(stays, clock = TestData.FIXED_CLOCK),
        findLodgings = FindLodgingsUseCase(lodgings),
        getSeasonalHighlights = GetSeasonalHighlightsUseCase(pois, weather, clock = TestData.FIXED_CLOCK),
        planTransitRoute = PlanTransitRouteUseCase(transit, TestData.FIXED_CLOCK),
        findBudgetRestaurants = FindBudgetRestaurantsUseCase(restaurants),
        observeDeparture = ObserveDepartureUseCase(preferences),
        observePriceAlert = ObservePriceAlertUseCase(watches),
        setPriceAlert = SetPriceAlertUseCase(watches, TestData.FIXED_CLOCK),
        clock = TestData.FIXED_CLOCK,
        destination = SampleDestinations.VIENNA,
        initialPeriod = initialPeriod,
    )

    @Test
    fun `all'avvio carica tutte le sezioni partendo dalla città scelta dall'utente`() = runTest {
        val state = createViewModel().uiState.value

        assertEquals(LocalDate.of(2026, Month.DECEMBER, 10), state.trip.departureDate)
        assertEquals("best", state.flights.successData().first().offer.id)
        assertEquals(1, state.stays.successData().size)
        assertTrue(state.highlights.successData().recommendations.isNotEmpty())
        assertEquals(1, state.transit.successData().size)
        assertEquals(listOf("budget"), state.restaurants.successData().map { it.id })
        assertTrue(state.stayOffersAvailable && state.restaurantRatingsAvailable)
        assertEquals(UiState.Empty, state.lodgings)
        assertTrue(lodgings.queries.isEmpty(), "Con il provider di prenotazione si mostrano le offerte con prezzo")
        assertFalse(state.isRefreshing)
        assertNotNull(state.pricesUpdatedAt)

        val flightQuery = flights.queries.single()
        assertEquals("MXP", flightQuery.originIata)
        assertEquals("VIE", flightQuery.destinationIata)
        assertEquals(SampleDestinations.VIENNA.arrivalHub, transit.queries.single().origin)
    }

    @Test
    fun `senza chiavi API mostra strutture e ristoranti reali, senza prezzi né valutazioni`() = runTest {
        stays.providesOffers = false
        restaurants.providesRatings = false

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertFalse(state.stayOffersAvailable)
        assertEquals(UiState.Empty, state.stays)
        assertTrue(stays.queries.isEmpty(), "Senza provider di prenotazione non si cercano offerte")
        assertEquals(listOf("near", "far"), state.lodgings.successData().map { it.id })
        assertEquals(SampleDestinations.VIENNA.center, lodgings.queries.single().location)
        assertFalse(state.restaurantRatingsAvailable)
        assertEquals(listOf("budget", "luxury"), state.restaurants.successData().map { it.id }, "Nessun filtro di qualità senza valutazioni")

        viewModel.retry(DashboardSection.STAYS)
        viewModel.refresh()

        assertEquals(3, lodgings.queries.size)
        assertIs<UiState.Success<*>>(viewModel.uiState.value.lodgings)
        assertNull(viewModel.uiState.value.refreshSummary?.stay, "Senza prezzi non c'è variazione da segnalare")
    }

    @Test
    fun `si possono scegliere i prossimi giorni e tutti i dodici mesi`() = runTest {
        val periods = createViewModel().uiState.value.periods

        assertEquals(TravelPeriod.NextDays, periods.first())
        assertEquals(13, periods.size)
        assertEquals(Month.entries.toSet(), periods.filterIsInstance<TravelPeriod.InMonth>().map { it.month.month }.toSet())
    }

    @Test
    fun `senza partenza i voli aspettano la scelta dell'utente e poi si caricano da soli`() = runTest {
        val noDeparture = FakeUserPreferencesRepository(initial = null)
        val viewModel = TripDashboardViewModel(
            searchFlights = SearchFlightsUseCase(flights, clock = TestData.FIXED_CLOCK),
            searchAccommodations = SearchAccommodationsUseCase(stays, clock = TestData.FIXED_CLOCK),
            findLodgings = FindLodgingsUseCase(lodgings),
            getSeasonalHighlights = GetSeasonalHighlightsUseCase(pois, weather, clock = TestData.FIXED_CLOCK),
            planTransitRoute = PlanTransitRouteUseCase(transit, TestData.FIXED_CLOCK),
            findBudgetRestaurants = FindBudgetRestaurantsUseCase(restaurants),
            observeDeparture = ObserveDepartureUseCase(noDeparture),
            observePriceAlert = ObservePriceAlertUseCase(watches),
            setPriceAlert = SetPriceAlertUseCase(watches, TestData.FIXED_CLOCK),
            clock = TestData.FIXED_CLOCK,
            destination = SampleDestinations.VIENNA,
            initialPeriod = december,
        )

        assertNull(viewModel.uiState.value.trip.departure)
        assertEquals(UiState.Empty, viewModel.uiState.value.flights)
        assertTrue(flights.queries.isEmpty())
        assertIs<UiState.Success<*>>(viewModel.uiState.value.stays)

        noDeparture.setDeparture(TestData.departure("Roma", "FCO"))

        assertEquals("FCO", viewModel.uiState.value.trip.originIata)
        assertEquals("FCO", flights.queries.single().originIata)
        assertIs<UiState.Success<*>>(viewModel.uiState.value.flights)
    }

    @Test
    fun `cambiando partenza si ricaricano solo i voli`() = runTest {
        val viewModel = createViewModel()

        preferences.setDeparture(DeparturePoint("Bergamo", TestData.airport("BGY")))

        assertEquals(listOf("MXP", "BGY"), flights.queries.map { it.originIata })
        assertEquals(1, stays.queries.size)
        assertEquals("Bergamo", viewModel.uiState.value.trip.departure?.cityName)
    }

    @Test
    fun `una sezione lenta non blocca le altre`() = runTest {
        flights.delayMillis = 5_000

        val viewModel = createViewModel()

        assertEquals(UiState.Loading, viewModel.uiState.value.flights)
        assertIs<UiState.Success<*>>(viewModel.uiState.value.stays)

        advanceUntilIdle()

        assertIs<UiState.Success<*>>(viewModel.uiState.value.flights)
    }

    @Test
    fun `lo stato emette prima il caricamento e poi i dati`() = runTest {
        flights.delayMillis = 1_000
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Loading, awaitItem().flights)
            assertIs<UiState.Success<*>>(awaitItem().flights)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `un errore resta confinato alla propria sezione`() = runTest {
        restaurants.result = DataResult.Failure(DataError.NoConnection)

        val state = createViewModel().uiState.value

        assertEquals(UiState.Error(DataError.NoConnection), state.restaurants)
        assertIs<UiState.Success<*>>(state.flights)
    }

    @Test
    fun `nessun risultato produce lo stato vuoto`() = runTest {
        transit.result = DataResult.Success(emptyList())

        assertEquals(UiState.Empty, createViewModel().uiState.value.transit)
    }

    @Test
    fun `cambiando mese i dati vengono ricaricati con le nuove date`() = runTest {
        val viewModel = createViewModel()
        val july = TravelPeriod.InMonth(YearMonth.of(2027, Month.JULY))

        viewModel.onPeriodSelected(july)

        val state = viewModel.uiState.value
        assertEquals(july, state.period)
        assertEquals(LocalDate.of(2027, Month.JULY, 10), state.trip.departureDate)
        assertEquals(LocalDate.of(2027, Month.JULY, 10), flights.queries.last().departureDate)
        assertEquals(Month.JULY, pois.queries.last().travelMonth)
    }

    @Test
    fun `un mese ormai passato ripiega sui prossimi giorni`() = runTest {
        val state = createViewModel(initialPeriod = TravelPeriod.InMonth(YearMonth.of(2026, Month.MARCH))).uiState.value

        assertEquals(TravelPeriod.NextDays, state.period)
        assertEquals(TestData.TODAY.plusDays(1), state.trip.departureDate)
    }

    @Test
    fun `i pulsanti delle sezioni cambiano la sezione mostrata`() = runTest {
        val viewModel = createViewModel()
        assertEquals(DashboardSection.FLIGHTS, viewModel.uiState.value.selectedSection)

        viewModel.onSectionSelected(DashboardSection.RESTAURANTS)

        assertEquals(DashboardSection.RESTAURANTS, viewModel.uiState.value.selectedSection)
    }

    @Test
    fun `il filtro spot fotografici mostra solo i luoghi instagrammabili`() = runTest {
        val viewModel = createViewModel()

        viewModel.onPhotoSpotsOnlyChanged(true)

        val state = viewModel.uiState.value
        assertTrue(state.photoSpotsOnly)
        assertEquals(listOf("view"), state.highlights.successData().recommendations.map { it.poi.id })
    }

    @Test
    fun `aggiorna ignora la cache e segnala il ribasso last minute`() = runTest {
        val viewModel = createViewModel()
        flights.result = DataResult.Success(listOf(TestData.flightOffer("last-minute", "79")))

        viewModel.refresh()

        assertEquals(listOf(false, true), flights.forceRefreshFlags)
        assertEquals(listOf(false, true), stays.forceRefreshFlags)
        val summary = assertNotNull(viewModel.uiState.value.refreshSummary)
        assertEquals(Money.of(100, "EUR"), summary.flight?.previous)
        assertEquals(Money.of(79, "EUR"), summary.flight?.current)
        assertTrue(summary.flight?.isDrop == true)
        assertFalse(viewModel.uiState.value.isRefreshing)

        viewModel.onRefreshSummaryShown()
        assertNull(viewModel.uiState.value.refreshSummary)
    }

    @Test
    fun `riprova ricarica solo la sezione indicata`() = runTest {
        flights.result = DataResult.Failure(DataError.Timeout)
        val viewModel = createViewModel()
        assertEquals(UiState.Error(DataError.Timeout), viewModel.uiState.value.flights)

        flights.result = DataResult.Success(listOf(TestData.flightOffer("recovered", "100")))
        viewModel.retry(DashboardSection.FLIGHTS)

        assertEquals(listOf("recovered"), viewModel.uiState.value.flights.successData().map { it.offer.id })
        assertEquals(1, stays.queries.size, "Le altre sezioni non devono essere ricaricate")
    }

    @Test
    fun `la campanella attiva l'avviso con i prezzi attuali come riferimento`() = runTest {
        stays.result = DataResult.Success(listOf(TestData.stayOffer("good", "360", nights = 4, reviewScore = 8.8)))
        val viewModel = createViewModel()

        viewModel.onAlertToggled(notificationsAllowed = true)

        val watch = watches.current.single()
        assertEquals("MXP", watch.departure.airport.iata)
        assertEquals(december, watch.period)
        assertEquals(Money.of(100, "EUR"), watch.flightPrices.single().price)
        assertEquals(Money.of(90, "EUR"), watch.stayPrices.single().price)
        assertTrue(viewModel.uiState.value.alertEnabled)
        assertEquals(DashboardMessage.ALERT_ENABLED, viewModel.uiState.value.message)

        viewModel.onMessageShown()
        viewModel.onAlertToggled(notificationsAllowed = true)

        assertTrue(watches.current.isEmpty())
        assertFalse(viewModel.uiState.value.alertEnabled)
        assertEquals(DashboardMessage.ALERT_DISABLED, viewModel.uiState.value.message)
    }

    @Test
    fun `l'avviso segue il periodo mostrato e segnala le notifiche disattivate`() = runTest {
        val viewModel = createViewModel()
        viewModel.onAlertToggled(notificationsAllowed = false)
        assertEquals(DashboardMessage.ALERT_ENABLED_WITHOUT_NOTIFICATIONS, viewModel.uiState.value.message)

        viewModel.onPeriodSelected(TravelPeriod.NextDays)
        assertFalse(viewModel.uiState.value.alertEnabled, "L'avviso vale solo per dicembre")

        viewModel.onPeriodSelected(december)
        assertTrue(viewModel.uiState.value.alertEnabled)
    }

    @Test
    fun `senza partenza la campanella chiede di sceglierla`() = runTest {
        val viewModel = TripDashboardViewModel(
            searchFlights = SearchFlightsUseCase(flights, clock = TestData.FIXED_CLOCK),
            searchAccommodations = SearchAccommodationsUseCase(stays, clock = TestData.FIXED_CLOCK),
            findLodgings = FindLodgingsUseCase(lodgings),
            getSeasonalHighlights = GetSeasonalHighlightsUseCase(pois, weather, clock = TestData.FIXED_CLOCK),
            planTransitRoute = PlanTransitRouteUseCase(transit, TestData.FIXED_CLOCK),
            findBudgetRestaurants = FindBudgetRestaurantsUseCase(restaurants),
            observeDeparture = ObserveDepartureUseCase(FakeUserPreferencesRepository(initial = null)),
            observePriceAlert = ObservePriceAlertUseCase(watches),
            setPriceAlert = SetPriceAlertUseCase(watches, TestData.FIXED_CLOCK),
            clock = TestData.FIXED_CLOCK,
            destination = SampleDestinations.VIENNA,
        )

        viewModel.onAlertToggled(notificationsAllowed = true)

        assertEquals(DashboardMessage.ALERT_NEEDS_DEPARTURE, viewModel.uiState.value.message)
        assertTrue(watches.current.isEmpty())
    }
}
