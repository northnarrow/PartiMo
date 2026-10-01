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
import com.partimo.domain.model.Travellers
import com.partimo.domain.model.dining.PriceLevel
import com.partimo.domain.model.event.EventKind
import com.partimo.domain.model.event.EventTiming
import com.partimo.domain.model.flight.FlightPriceSource
import com.partimo.domain.model.place.DeparturePoint
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.saved.Favorite
import com.partimo.domain.model.saved.FavoriteKind
import com.partimo.domain.service.SeasonalCalendar
import com.partimo.domain.testing.FakeAccommodationRepository
import com.partimo.domain.testing.FakeEventRepository
import com.partimo.domain.testing.FakeFlightInsightsRepository
import com.partimo.domain.testing.FakeFlightRepository
import com.partimo.domain.testing.FakeHolidayRepository
import com.partimo.domain.testing.FakeLodgingRepository
import com.partimo.domain.testing.FakePoiRepository
import com.partimo.domain.testing.FakePriceWatchRepository
import com.partimo.domain.testing.FakeRestaurantRepository
import com.partimo.domain.testing.FakeSavedTripRepository
import com.partimo.domain.testing.FakeTransitRepository
import com.partimo.domain.testing.FakeUserPreferencesRepository
import com.partimo.domain.testing.FakeWeatherRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.usecase.FindBudgetRestaurantsUseCase
import com.partimo.domain.usecase.FindLodgingsUseCase
import com.partimo.domain.usecase.GetMonthPricesUseCase
import com.partimo.domain.usecase.GetPriceCalendarUseCase
import com.partimo.domain.usecase.GetSeasonalHighlightsUseCase
import com.partimo.domain.usecase.GetTripEventsUseCase
import com.partimo.domain.usecase.ObserveDepartureUseCase
import com.partimo.domain.usecase.ObservePriceAlertUseCase
import com.partimo.domain.usecase.ObserveSavedTripUseCase
import com.partimo.domain.usecase.ObserveTravellersUseCase
import com.partimo.domain.usecase.PlanTransitRouteUseCase
import com.partimo.domain.usecase.SaveTravellersUseCase
import com.partimo.domain.usecase.SearchAccommodationsUseCase
import com.partimo.domain.usecase.SearchFlightsUseCase
import com.partimo.domain.usecase.SetPriceAlertUseCase
import com.partimo.domain.usecase.SetTripSavedUseCase
import com.partimo.domain.usecase.ToggleFavoriteUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
    private val events = FakeEventRepository(
        DataResult.Success(
            listOf(
                TestData.event("mercatino", SeasonalCalendar.CHRISTMAS_MARKET_SEASON, kind = EventKind.CHRISTMAS_MARKET),
                TestData.event("viennale", EventTiming.InMonths(setOf(Month.OCTOBER))),
            ),
        ),
    )
    private val holidays = FakeHolidayRepository(
        default = DataResult.Success(listOf(TestData.holiday("immacolata", LocalDate.of(2026, Month.DECEMBER, 8)))),
    )
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

    private val savedTrips = FakeSavedTripRepository()

    private val insights = FakeFlightInsightsRepository(
        months = DataResult.Success(
            mapOf(
                YearMonth.of(2026, Month.OCTOBER) to TestData.fare("33", LocalDate.of(2026, Month.OCTOBER, 14)),
                YearMonth.of(2026, Month.DECEMBER) to TestData.fare("72", LocalDate.of(2026, Month.DECEMBER, 7)),
            ),
        ),
        days = DataResult.Success(
            mapOf(
                LocalDate.of(2026, Month.DECEMBER, 7) to TestData.fare("72", LocalDate.of(2026, Month.DECEMBER, 7), LocalDate.of(2026, Month.DECEMBER, 10)),
                LocalDate.of(2026, Month.DECEMBER, 11) to TestData.fare("92", LocalDate.of(2026, Month.DECEMBER, 11), LocalDate.of(2026, Month.DECEMBER, 13)),
            ),
        ),
    )

    private fun createViewModel(
        initialPeriod: TravelPeriod = december,
        flightRepository: FakeFlightRepository = flights,
        insightsRepository: FakeFlightInsightsRepository = insights,
    ) = TripDashboardViewModel(
        searchFlights = SearchFlightsUseCase(flightRepository, clock = TestData.FIXED_CLOCK),
        searchAccommodations = SearchAccommodationsUseCase(stays, clock = TestData.FIXED_CLOCK),
        findLodgings = FindLodgingsUseCase(lodgings),
        getSeasonalHighlights = GetSeasonalHighlightsUseCase(pois, weather, clock = TestData.FIXED_CLOCK),
        getTripEvents = GetTripEventsUseCase(events, holidays),
        planTransitRoute = PlanTransitRouteUseCase(transit, TestData.FIXED_CLOCK),
        findBudgetRestaurants = FindBudgetRestaurantsUseCase(restaurants),
        observeDeparture = ObserveDepartureUseCase(preferences),
        observePriceAlert = ObservePriceAlertUseCase(watches),
        setPriceAlert = SetPriceAlertUseCase(watches, TestData.FIXED_CLOCK),
        clock = TestData.FIXED_CLOCK,
        destination = SampleDestinations.VIENNA,
        initialPeriod = initialPeriod,
        observeSavedTrip = ObserveSavedTripUseCase(savedTrips),
        setTripSaved = SetTripSavedUseCase(savedTrips, TestData.FIXED_CLOCK),
        toggleFavorite = ToggleFavoriteUseCase(savedTrips, TestData.FIXED_CLOCK),
        observeTravellers = ObserveTravellersUseCase(preferences),
        saveTravellers = SaveTravellersUseCase(preferences),
        getMonthPrices = GetMonthPricesUseCase(insightsRepository),
        getPriceCalendar = GetPriceCalendarUseCase(insightsRepository),
    )

    @Test
    fun `i mesi hanno il prezzo più basso e il calendario porta le date del giorno toccato`() = runTest {
        val viewModel = createViewModel()
        val start = viewModel.uiState.value
        assertEquals(Triple("MXP", "VIE", TravelPeriod.FLEXIBLE_STAY_NIGHTS), insights.monthRequests.single())
        assertEquals(YearMonth.of(2026, Month.OCTOBER), start.cheapestMonth)
        assertEquals(Money.of(72, "EUR"), start.monthPrices.getValue(YearMonth.of(2026, Month.DECEMBER)).price)
        assertTrue(start.priceCalendarAvailable)

        viewModel.onOpenPriceCalendar()
        advanceUntilIdle()
        val calendar = assertNotNull(viewModel.uiState.value.priceCalendar)
        assertEquals(YearMonth.of(2026, Month.DECEMBER), calendar.month, "Si apre sul mese mostrato")
        assertEquals(YearMonth.of(2026, Month.OCTOBER), calendar.firstMonth)
        assertEquals(2, calendar.calendar.successData().fares.size)

        viewModel.onPriceCalendarMonthChanged(1)
        advanceUntilIdle()
        assertEquals(YearMonth.of(2027, Month.JANUARY), viewModel.uiState.value.priceCalendar?.month)
        viewModel.onPriceCalendarMonthChanged(-1)
        advanceUntilIdle()
        assertEquals(listOf(Month.DECEMBER, Month.JANUARY, Month.DECEMBER), insights.dayRequests.map { it.first.month })

        val fare = calendar.calendar.successData().fares.getValue(LocalDate.of(2026, Month.DECEMBER, 11))
        viewModel.onPriceCalendarDaySelected(fare)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.priceCalendar)
        assertEquals(TravelPeriod.Dates(LocalDate.of(2026, Month.DECEMBER, 11), LocalDate.of(2026, Month.DECEMBER, 13)), state.period)
        assertEquals(LocalDate.of(2026, Month.DECEMBER, 11), flights.queries.last().departureDate, "I voli di quelle date")
    }

    @Test
    fun `senza token niente prezzi dei mesi né calendario`() = runTest {
        val unavailable = FakeFlightInsightsRepository(isAvailable = false)
        val viewModel = createViewModel(insightsRepository = unavailable)

        viewModel.onOpenPriceCalendar()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.priceCalendarAvailable)
        assertTrue(state.monthPrices.isEmpty())
        assertTrue(unavailable.monthRequests.isEmpty())
    }

    @Test
    fun `i viaggiatori scelti valgono per voli e alloggi e cambiandoli i prezzi si ricaricano`() = runTest {
        val family = Travellers(adults = 2, childAges = listOf(6))
        val viewModel = createViewModel()
        assertEquals(Travellers.SOLO, flights.queries.single().travellers)

        viewModel.onTravellersSelected(family)
        advanceUntilIdle()

        assertEquals(family, preferences.travellers.first())
        assertEquals(family, viewModel.uiState.value.trip.travellers)
        assertEquals(2, flights.queries.size)
        assertEquals(family, flights.queries.last().travellers)
        assertEquals(family, stays.queries.last().travellers)
        assertEquals(1, stays.queries.last().rooms, "Una camera per due adulti e un bambino")
        assertEquals(1, pois.queries.size, "I luoghi da vedere non dipendono da chi parte")
    }

    @Test
    fun `l'avviso segue il prezzo di un posto, come i controlli fatti per una persona`() = runTest {
        flights.result = DataResult.Success(listOf(TestData.flightOffer("famiglia", "300").copy(passengers = 3)))
        val viewModel = createViewModel()

        viewModel.onAlertToggled(notificationsAllowed = true)
        advanceUntilIdle()

        assertEquals(Money.of("100", "EUR"), watches.current.single().flightPrices.single().price)
    }

    @Test
    fun `il segnalibro salva il viaggio del periodo mostrato e le stelle i suoi preferiti`() = runTest {
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.favoritesEnabled)
        assertEquals(emptySet(), viewModel.uiState.value.favoriteKeys)

        viewModel.onToggleTripSaved()
        advanceUntilIdle()
        assertEquals(DashboardMessage.TRIP_SAVED, viewModel.uiState.value.message)
        assertEquals("AT:Vienna:2026-12", viewModel.uiState.value.savedTrip?.id)

        val museum = Favorite("museum", FavoriteKind.PLACE, "Kunsthistorisches Museum")
        viewModel.onToggleFavorite(museum)
        advanceUntilIdle()
        assertEquals(setOf("PLACE:museum"), viewModel.uiState.value.favoriteKeys)

        // Un altro periodo è un altro viaggio: niente segnalibro né stelle.
        viewModel.onPeriodSelected(TravelPeriod.NextDays)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.savedTrip)

        viewModel.onPeriodSelected(december)
        advanceUntilIdle()
        viewModel.onToggleTripSaved()
        advanceUntilIdle()
        assertEquals(DashboardMessage.TRIP_REMOVED, viewModel.uiState.value.message)
        assertTrue(savedTrips.current.isEmpty())
    }

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
        assertEquals(LocalDate.of(2026, Month.DECEMBER, 1)..LocalDate.of(2026, Month.DECEMBER, 31), flightQuery.flexibleDates?.departures, "Tutto dicembre")
        assertEquals(FlightPriceSource.LIVE_OFFERS, state.flightPriceSource)
        assertEquals(SampleDestinations.VIENNA.arrivalHub, transit.queries.single().origin)
    }

    @Test
    fun `con i prezzi trovati su Aviasales la dashboard lo sa anche prima dei risultati`() = runTest {
        val recent = FakeFlightRepository(priceSource = FlightPriceSource.RECENT_SEARCHES)

        val state = createViewModel(initialPeriod = TravelPeriod.NextDays, flightRepository = recent).uiState.value

        assertEquals(FlightPriceSource.RECENT_SEARCHES, state.flightPriceSource)
        assertEquals(TestData.TODAY.plusDays(1)..TestData.TODAY.plusDays(7), recent.queries.single().flexibleDates?.departures, "La prossima settimana")
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
    fun `gli eventi del soggiorno arrivano con le date del viaggio, senza ricaricarli col filtro foto`() = runTest {
        val viewModel = createViewModel()

        val trip = viewModel.uiState.value.events.successData()
        assertEquals(LocalDate.of(2026, Month.DECEMBER, 10), trip.from)
        assertEquals(LocalDate.of(2026, Month.DECEMBER, 14), trip.to)
        assertEquals(listOf("mercatino"), trip.events.map { it.id }, "Viennale (ottobre) e Immacolata (8 dicembre) sono fuori dal soggiorno")
        assertEquals(SampleDestinations.VIENNA.center, events.queries.single().location)

        viewModel.onPhotoSpotsOnlyChanged(true)
        assertEquals(1, events.queries.size, "Il filtro foto ricarica solo i luoghi")

        viewModel.retryEvents()
        assertEquals(2, events.queries.size)
        assertEquals(2, pois.queries.size, "«Riprova» degli eventi non ricarica i luoghi")
    }

    @Test
    fun `un errore degli eventi non tocca i luoghi da vedere`() = runTest {
        events.result = DataResult.Failure(DataError.RateLimited)
        holidays.default = DataResult.Failure(DataError.NoConnection)

        val state = createViewModel().uiState.value

        assertEquals(UiState.Error(DataError.RateLimited), state.events)
        assertIs<UiState.Success<*>>(state.highlights)
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
            getTripEvents = GetTripEventsUseCase(events, holidays),
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
    fun `con le date scelte tutta la dashboard usa quei giorni e i voli li mettono per primi`() = runTest {
        val dates = TravelPeriod.Dates(LocalDate.of(2026, Month.DECEMBER, 11), LocalDate.of(2026, Month.DECEMBER, 13))

        val state = createViewModel(initialPeriod = dates).uiState.value

        assertEquals(dates, state.period)
        assertEquals(listOf(TravelPeriod.NextDays, dates), state.periods.take(2), "Le date compaiono tra i periodi")
        assertEquals(dates.departure, state.trip.departureDate)
        assertEquals(dates.returning, state.trip.returnDate)
        val query = flights.queries.single()
        assertEquals(dates.departure, query.departureDate)
        assertEquals(dates.returning, query.returnDate)
        assertTrue(query.flexibleDates?.exactDatesFirst == true)
        assertEquals(LocalDate.of(2026, Month.DECEMBER, 8)..LocalDate.of(2026, Month.DECEMBER, 14), query.flexibleDates?.departures)
        assertNull(state.nowAtDestination, "Si parte tra più di due mesi")
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
        assertEquals(LocalDate.of(2027, Month.JULY, 31), flights.queries.last().flexibleDates?.departures?.endInclusive)
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
            getTripEvents = GetTripEventsUseCase(events, holidays),
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
