package com.partimo.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.partimo.app.di.AppContainer
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.common.toListUiState
import com.partimo.app.ui.common.toUiState
import com.partimo.domain.common.getOrNull
import com.partimo.domain.model.Destination
import com.partimo.domain.model.Money
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.Travellers
import com.partimo.domain.model.TripContext
import com.partimo.domain.model.deal.PriceChange
import com.partimo.domain.model.flight.FareSnapshot
import com.partimo.domain.model.flight.FlightSearchQuery
import com.partimo.domain.model.poi.PoiTag
import com.partimo.domain.model.saved.Favorite
import com.partimo.domain.model.stay.AccommodationSearchQuery
import com.partimo.domain.model.transit.TransitRouteQuery
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
import com.partimo.domain.usecase.TrackedPrices
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * ViewModel della dashboard aggregata (MVVM).
 *
 * Espone un unico [StateFlow] immutabile. Le cinque sezioni (più gli eventi del soggiorno) vengono
 * caricate in parallelo e in modo indipendente: ognuna aggiorna il proprio stato appena pronta e un
 * errore resta confinato alla sua sezione. I voli dipendono dal punto di partenza scelto dall'utente, osservato come Flow:
 * se lo cambia, i voli si ricaricano da soli.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripDashboardViewModel(
    private val searchFlights: SearchFlightsUseCase,
    private val searchAccommodations: SearchAccommodationsUseCase,
    private val findLodgings: FindLodgingsUseCase,
    private val getSeasonalHighlights: GetSeasonalHighlightsUseCase,
    private val getTripEvents: GetTripEventsUseCase,
    private val planTransitRoute: PlanTransitRouteUseCase,
    private val findBudgetRestaurants: FindBudgetRestaurantsUseCase,
    private val observeDeparture: ObserveDepartureUseCase,
    private val observePriceAlert: ObservePriceAlertUseCase,
    private val setPriceAlert: SetPriceAlertUseCase,
    private val clock: Clock,
    private val destination: Destination,
    initialPeriod: TravelPeriod = TravelPeriod.NextDays,
    isDemoMode: Boolean = false,
    assistantAvailable: Boolean = false,
    /** Viaggi salvati e preferiti: facoltativi (senza, la dashboard non mostra segnalibro e stelle). */
    private val observeSavedTrip: ObserveSavedTripUseCase? = null,
    private val setTripSaved: SetTripSavedUseCase? = null,
    private val toggleFavorite: ToggleFavoriteUseCase? = null,
    /** Chi parte: senza, i prezzi sono per una persona. */
    private val observeTravellers: ObserveTravellersUseCase? = null,
    private val saveTravellers: SaveTravellersUseCase? = null,
    /** Prezzi di Aviasales per mese e per giorno: facoltativi (senza, niente prezzi sui mesi né calendario). */
    private val getMonthPrices: GetMonthPricesUseCase? = null,
    private val getPriceCalendar: GetPriceCalendarUseCase? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        initialState(initialPeriod, isDemoMode).copy(
            assistantAvailable = assistantAvailable,
            favoritesEnabled = observeSavedTrip != null && setTripSaved != null && toggleFavorite != null,
            priceCalendarAvailable = getPriceCalendar?.isAvailable == true,
        ),
    )
    val uiState: StateFlow<TripDashboardUiState> = _uiState.asStateFlow()

    private val sectionJobs = mutableMapOf<DashboardSection, Job>()
    private var eventsJob: Job? = null
    private var refreshJob: Job? = null
    private var monthPricesJob: Job? = null
    private var calendarJob: Job? = null

    init {
        viewModelScope.launch {
            var firstLoad = true
            val travellers = observeTravellers?.invoke() ?: flowOf(Travellers.SOLO)
            combine(observeDeparture(), travellers) { departure, people -> departure to people }.collect { (departure, people) ->
                val previous = _uiState.value.trip
                _uiState.update { it.copy(trip = it.trip.copy(departure = departure, travellers = people)) }
                if (firstLoad) {
                    firstLoad = false
                    loadDashboard(forceRefresh = false)
                    loadMonthPrices(forceRefresh = false)
                    return@collect
                }
                if (departure != previous.departure) loadMonthPrices(forceRefresh = false)
                // Partenza e viaggiatori cambiano i prezzi dei voli; i viaggiatori anche quelli degli alloggi.
                if (departure != previous.departure || people != previous.travellers) loadSection(DashboardSection.FLIGHTS, forceRefresh = false)
                if (people != previous.travellers && _uiState.value.stayOffersAvailable) loadSection(DashboardSection.STAYS, forceRefresh = false)
            }
        }
        observeSavedTrip?.let { observe ->
            viewModelScope.launch {
                // Segnalibro e stelle seguono il periodo mostrato: ogni periodo è un viaggio diverso.
                _uiState
                    .map { it.period }
                    .distinctUntilChanged()
                    .flatMapLatest { period -> observe(destination, period) }
                    .collect { trip -> _uiState.update { it.copy(savedTrip = trip) } }
            }
        }
        viewModelScope.launch {
            // La campanella segue la combinazione partenza + periodo attualmente mostrata.
            _uiState
                .map { it.trip.departure to it.period }
                .distinctUntilChanged()
                .flatMapLatest { (departure, period) -> observePriceAlert(departure, destination, period) }
                .collect { enabled -> _uiState.update { it.copy(alertEnabled = enabled) } }
        }
    }

    fun onPeriodSelected(period: TravelPeriod) {
        if (period == _uiState.value.period) return
        _uiState.update { it.copy(period = period, trip = tripFor(period, it.trip), nowAtDestination = nowAtDestination(period)) }
        loadDashboard(forceRefresh = false)
    }

    /** Nuovi viaggiatori scelti nella dashboard: valgono per tutti i viaggi, i prezzi si ricaricano da soli. */
    fun onTravellersSelected(travellers: Travellers) {
        val save = saveTravellers ?: return
        viewModelScope.launch { save(travellers) }
    }

    /** Apre il calendario dei prezzi sul mese del periodo mostrato. */
    fun onOpenPriceCalendar() {
        if (getPriceCalendar == null || _uiState.value.trip.departure == null) return
        val state = _uiState.value
        val months = state.periods.filterIsInstance<TravelPeriod.InMonth>().map { it.month }
        val first = YearMonth.from(state.today.plusDays(1))
        val last = months.maxOrNull()?.takeIf { it.isAfter(first) } ?: first.plusMonths(TravelPeriod.SELECTABLE_MONTHS.toLong())
        val month = when (val period = state.period) {
            is TravelPeriod.InMonth -> period.month
            is TravelPeriod.Dates -> YearMonth.from(period.departure)
            TravelPeriod.NextDays -> first
        }.coerceIn(first, last)
        _uiState.update { it.copy(priceCalendar = PriceCalendarState(month, first, last)) }
        loadCalendar(month)
    }

    /** Mese precedente ([delta] = -1) o successivo (+1) nel calendario. */
    fun onPriceCalendarMonthChanged(delta: Int) {
        val calendar = _uiState.value.priceCalendar ?: return
        val month = calendar.month.plusMonths(delta.toLong()).coerceIn(calendar.firstMonth, calendar.lastMonth)
        if (month == calendar.month) return
        _uiState.update { it.copy(priceCalendar = calendar.copy(month = month, calendar = UiState.Loading)) }
        loadCalendar(month)
    }

    fun onPriceCalendarDismissed() {
        calendarJob?.cancel()
        _uiState.update { it.copy(priceCalendar = null) }
    }

    /** Giorno scelto nel calendario: le date della sua tariffa diventano quelle del viaggio. */
    fun onPriceCalendarDaySelected(fare: FareSnapshot) {
        val nights = GetPriceCalendarUseCase.stayNightsFor(_uiState.value.period).first
        val returning = fare.returnDate ?: fare.departureDate.plusDays(nights)
        onPriceCalendarDismissed()
        onPeriodSelected(TravelPeriod.Dates(fare.departureDate, returning))
    }

    fun onSectionSelected(section: DashboardSection) {
        _uiState.update { it.copy(selectedSection = section) }
    }

    fun onPhotoSpotsOnlyChanged(enabled: Boolean) {
        if (enabled == _uiState.value.photoSpotsOnly) return
        _uiState.update { it.copy(photoSpotsOnly = enabled) }
        loadSection(DashboardSection.HIGHLIGHTS, forceRefresh = false)
    }

    /**
     * "Aggiorna": ignora le cache ancora valide e ricarica tutto, per scoprire le offerte last minute.
     * Al termine confronta i prezzi migliori con quelli di prima e ne riassume la variazione.
     */
    fun refresh() {
        loadMonthPrices(forceRefresh = true)
        val before = bestPrices()
        loadDashboard(forceRefresh = true) {
            val after = bestPrices()
            _uiState.update {
                it.copy(refreshSummary = RefreshSummary(flight = changeOf(before.first, after.first), stay = changeOf(before.second, after.second)))
            }
        }
    }

    fun retry(section: DashboardSection) {
        loadSection(section, forceRefresh = true)
    }

    /** "Riprova" della sezione eventi: i luoghi da vedere restano come sono. */
    fun retryEvents() {
        loadEvents(forceRefresh = true)
    }

    /**
     * Attiva o disattiva l'avviso sulle offerte convenienti. I prezzi migliori visti adesso diventano
     * il primo riferimento; [notificationsAllowed] indica se il sistema potrà mostrare le notifiche.
     */
    fun onAlertToggled(notificationsAllowed: Boolean) {
        val state = _uiState.value
        val departure = state.trip.departure
        if (departure == null) {
            _uiState.update { it.copy(message = DashboardMessage.ALERT_NEEDS_DEPARTURE) }
            return
        }
        val enable = !state.alertEnabled
        viewModelScope.launch {
            // Gli avvisi seguono il prezzo di un posto, come i controlli periodici (fatti per una persona).
            val (flightPrice, stayPrice) = bestPrices(perPassenger = true)
            setPriceAlert(enable, departure, destination, state.period, flightPrice, stayPrice)
            val message = when {
                !enable -> DashboardMessage.ALERT_DISABLED
                notificationsAllowed -> DashboardMessage.ALERT_ENABLED
                else -> DashboardMessage.ALERT_ENABLED_WITHOUT_NOTIFICATIONS
            }
            _uiState.update { it.copy(message = message) }
        }
    }

    /** Segnalibro: salva il viaggio (lo si ritrova nella schermata iniziale) o lo toglie dai salvati. */
    fun onToggleTripSaved() {
        val save = setTripSaved ?: return
        val state = _uiState.value
        val saving = state.savedTrip == null
        viewModelScope.launch {
            save(destination, state.period, saving)
            _uiState.update { it.copy(message = if (saving) DashboardMessage.TRIP_SAVED else DashboardMessage.TRIP_REMOVED) }
        }
    }

    /** Stella su un luogo, evento, ristorante o alloggio: il primo preferito salva anche il viaggio. */
    fun onToggleFavorite(favorite: Favorite) {
        val toggle = toggleFavorite ?: return
        val period = _uiState.value.period
        viewModelScope.launch { toggle(destination, period, favorite) }
    }

    fun onRefreshSummaryShown() {
        _uiState.update { it.copy(refreshSummary = null) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    /** Prezzo più basso di ogni mese per i chip: serve la città di partenza. */
    private fun loadMonthPrices(forceRefresh: Boolean) {
        val load = getMonthPrices?.takeIf { it.isAvailable } ?: return
        val origin = _uiState.value.trip.originIata ?: return
        monthPricesJob?.cancel()
        monthPricesJob = viewModelScope.launch {
            // Senza prezzi (rete assente o errore) i chip restano come sono: non è un errore da mostrare.
            val prices = load(origin, destination.airportIata, forceRefresh).getOrNull().orEmpty()
            _uiState.update { it.copy(monthPrices = prices) }
        }
    }

    private fun loadCalendar(month: YearMonth) {
        val load = getPriceCalendar ?: return
        val origin = _uiState.value.trip.originIata ?: return
        calendarJob?.cancel()
        calendarJob = viewModelScope.launch {
            val result = load(origin, destination.airportIata, month, _uiState.value.period).toUiState { it.fares.isEmpty() }
            _uiState.update { state -> state.priceCalendar?.takeIf { it.month == month }?.let { state.copy(priceCalendar = it.copy(calendar = result)) } ?: state }
        }
    }

    private fun loadDashboard(forceRefresh: Boolean, onComplete: () -> Unit = {}) {
        _uiState.update { it.copy(isRefreshing = forceRefresh) }
        val jobs = DashboardSection.entries.map { section -> loadSection(section, forceRefresh) } + loadEvents(forceRefresh)
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            jobs.joinAll()
            _uiState.update { it.copy(isRefreshing = false, pricesUpdatedAt = Instant.now(clock)) }
            onComplete()
        }
    }

    private fun loadSection(section: DashboardSection, forceRefresh: Boolean): Job {
        sectionJobs[section]?.cancel()
        markLoading(section)
        val job = viewModelScope.launch {
            val trip = _uiState.value.trip
            when (section) {
                DashboardSection.FLIGHTS -> loadFlights(trip, forceRefresh)
                DashboardSection.STAYS -> loadStays(trip, forceRefresh)
                DashboardSection.HIGHLIGHTS -> loadHighlights(trip, forceRefresh)
                DashboardSection.TRANSIT -> loadTransit(trip, forceRefresh)
                DashboardSection.RESTAURANTS -> loadRestaurants(trip, forceRefresh)
            }
        }
        sectionJobs[section] = job
        return job
    }

    /** Senza punto di partenza i voli non si possono cercare: la UI chiede di sceglierlo. */
    private suspend fun loadFlights(trip: TripContext, forceRefresh: Boolean) {
        val originIata = trip.originIata
        if (originIata == null) {
            _uiState.update { it.copy(flights = UiState.Empty) }
            return
        }
        val query = FlightSearchQuery(
            originIata = originIata,
            destinationIata = trip.destination.airportIata,
            departureDate = trip.departureDate,
            returnDate = trip.returnDate,
            travellers = trip.travellers,
            // Chi raccoglie i prezzi di molte date (Aviasales) propone i voli più convenienti del periodo
            // o, con le date scelte, quelli di quei giorni e poi dei giorni vicini.
            flexibleDates = _uiState.value.period.flexibleDates(LocalDate.now(clock)),
        )
        val state = searchFlights(query, forceRefresh = forceRefresh).toListUiState()
        _uiState.update { it.copy(flights = state) }
    }

    /**
     * Offerte con prezzo dal provider di prenotazione; senza provider, le strutture reali attorno al
     * centro, che la UI collega ai siti di prenotazione con le date del viaggio.
     */
    private suspend fun loadStays(trip: TripContext, forceRefresh: Boolean) {
        if (!_uiState.value.stayOffersAvailable) {
            val state = findLodgings(trip.destination.center, forceRefresh).toListUiState()
            _uiState.update { it.copy(lodgings = state) }
            return
        }
        val query = AccommodationSearchQuery(
            location = trip.destination.center,
            checkIn = trip.departureDate,
            checkOut = trip.returnDate,
            travellers = trip.travellers,
            rooms = AccommodationSearchQuery.roomsFor(trip.travellers),
        )
        val state = searchAccommodations(query, forceRefresh = forceRefresh).toListUiState()
        _uiState.update { it.copy(stays = state) }
    }

    private suspend fun loadHighlights(trip: TripContext, forceRefresh: Boolean) {
        val requiredTags = if (_uiState.value.photoSpotsOnly) setOf(PoiTag.INSTAGRAMMABLE) else emptySet()
        val state = getSeasonalHighlights(
            location = trip.destination.center,
            travelDate = trip.departureDate,
            requiredTags = requiredTags,
            areaName = trip.destination.name,
            forceRefresh = forceRefresh,
        ).toUiState { it.recommendations.isEmpty() }
        _uiState.update { it.copy(highlights = state) }
    }

    /**
     * Eventi tra arrivo e partenza, in cima a "Da vedere" ma con un caricamento proprio: il filtro
     * degli spot fotografici ricarica solo i luoghi. Anche senza eventi la UI mostra i collegamenti
     * per cercarne altri.
     */
    private fun loadEvents(forceRefresh: Boolean): Job {
        eventsJob?.cancel()
        _uiState.update { it.copy(events = UiState.Loading) }
        val job = viewModelScope.launch {
            val trip = _uiState.value.trip
            val state = getTripEvents(trip.destination, trip.departureDate, trip.returnDate, forceRefresh).toUiState()
            _uiState.update { it.copy(events = state) }
        }
        eventsJob = job
        return job
    }

    /** Trasporti in tempo reale: percorso dal nodo di arrivo al centro con partenza adesso. */
    private suspend fun loadTransit(trip: TripContext, forceRefresh: Boolean) {
        val query = TransitRouteQuery(
            origin = trip.destination.arrivalHub,
            destination = trip.destination.center,
            departureTime = Instant.now(clock),
        )
        val state = planTransitRoute(query, forceRefresh).toListUiState()
        _uiState.update { it.copy(transit = state) }
    }

    /** Ora locale della meta, solo per i viaggi imminenti: per quelli lontani "aperto ora" non serve. */
    private fun nowAtDestination(period: TravelPeriod): LocalDateTime? =
        if (period.isImminent(LocalDate.now(clock))) LocalDateTime.now(clock.withZone(destination.timeZone)) else null

    private suspend fun loadRestaurants(trip: TripContext, forceRefresh: Boolean) {
        _uiState.update { it.copy(nowAtDestination = nowAtDestination(it.period)) }
        val state = findBudgetRestaurants(
            location = trip.destination.center,
            areaName = trip.destination.name,
            forceRefresh = forceRefresh,
        ).toListUiState()
        _uiState.update { it.copy(restaurants = state) }
    }

    private fun markLoading(section: DashboardSection) {
        _uiState.update { current ->
            when (section) {
                DashboardSection.FLIGHTS -> current.copy(flights = UiState.Loading)
                DashboardSection.STAYS ->
                    if (current.stayOffersAvailable) current.copy(stays = UiState.Loading) else current.copy(lodgings = UiState.Loading)
                DashboardSection.HIGHLIGHTS -> current.copy(highlights = UiState.Loading)
                DashboardSection.TRANSIT -> current.copy(transit = UiState.Loading)
                DashboardSection.RESTAURANTS -> current.copy(restaurants = UiState.Loading)
            }
        }
    }

    /**
     * Volo più economico e alloggio ben recensito più economico (a notte): gli stessi prezzi seguiti dagli avvisi.
     * Il volo è il totale per tutti i viaggiatori o, con [perPassenger], il prezzo di un posto.
     */
    private fun bestPrices(perPassenger: Boolean = false): Pair<Money?, Money?> {
        val state = _uiState.value
        val flight = (state.flights as? UiState.Success)?.data?.let { offers -> TrackedPrices.cheapestFlight(offers.map { it.offer }) }
        val stay = (state.stays as? UiState.Success)?.data?.let { offers -> TrackedPrices.cheapestGoodStay(offers.map { it.offer }) }
        return (if (perPassenger) flight?.pricePerPassenger else flight?.totalPrice) to stay?.pricePerNight
    }

    private fun changeOf(before: Money?, after: Money?): PriceChange? =
        if (before != null && after != null) PriceChange(before, after) else null

    private fun initialState(requested: TravelPeriod, isDemoMode: Boolean): TripDashboardUiState {
        val today = LocalDate.now(clock)
        // Un periodo ormai passato (es. rotta ripristinata dopo mesi) ripiega sui prossimi giorni.
        val period = requested.takeUnless { it.isOver(today) } ?: TravelPeriod.NextDays
        val selectable = TravelPeriod.selectable(today)
        val periods = if (period in selectable) selectable else listOf(TravelPeriod.NextDays, period) + selectable.drop(1)
        val trip = TripContext(
            destination = destination,
            departureDate = period.departureDate(today),
            returnDate = period.returnDate(today),
        )
        val offersAvailable = searchAccommodations.offersAvailable
        return TripDashboardUiState(
            trip = trip,
            period = period,
            periods = periods,
            today = today,
            // La sezione alloggi usa una sola delle due liste: l'altra resta vuota.
            stays = if (offersAvailable) UiState.Loading else UiState.Empty,
            lodgings = if (offersAvailable) UiState.Empty else UiState.Loading,
            stayOffersAvailable = offersAvailable,
            flightPriceSource = searchFlights.priceSource,
            restaurantRatingsAvailable = findBudgetRestaurants.ratingsAvailable,
            isDemoMode = isDemoMode,
            nowAtDestination = nowAtDestination(period),
        )
    }

    private fun tripFor(period: TravelPeriod, current: TripContext): TripContext {
        val today = LocalDate.now(clock)
        return current.copy(departureDate = period.departureDate(today), returnDate = period.returnDate(today))
    }

    companion object {
        fun factory(
            container: AppContainer,
            destination: Destination,
            initialPeriod: TravelPeriod,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TripDashboardViewModel(
                    searchFlights = container.searchFlights,
                    searchAccommodations = container.searchAccommodations,
                    findLodgings = container.findLodgings,
                    getSeasonalHighlights = container.getSeasonalHighlights,
                    getTripEvents = container.getTripEvents,
                    planTransitRoute = container.planTransitRoute,
                    findBudgetRestaurants = container.findBudgetRestaurants,
                    observeDeparture = container.observeDeparture,
                    observePriceAlert = container.observePriceAlert,
                    setPriceAlert = container.setPriceAlert,
                    clock = container.clock,
                    destination = destination,
                    initialPeriod = initialPeriod,
                    isDemoMode = container.isDemoMode,
                    assistantAvailable = container.planTrip.isAvailable,
                    observeSavedTrip = container.observeSavedTrip,
                    observeTravellers = container.observeTravellers,
                    saveTravellers = container.saveTravellers,
                    getMonthPrices = container.getMonthPrices,
                    getPriceCalendar = container.getPriceCalendar,
                    setTripSaved = container.setTripSaved,
                    toggleFavorite = container.toggleFavorite,
                )
            }
        }
    }
}
