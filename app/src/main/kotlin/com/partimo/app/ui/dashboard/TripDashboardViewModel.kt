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
import com.partimo.domain.model.Destination
import com.partimo.domain.model.Money
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.TripContext
import com.partimo.domain.model.deal.PriceChange
import com.partimo.domain.model.flight.FlightSearchQuery
import com.partimo.domain.model.poi.PoiTag
import com.partimo.domain.model.stay.AccommodationSearchQuery
import com.partimo.domain.model.transit.TransitRouteQuery
import com.partimo.domain.usecase.FindBudgetRestaurantsUseCase
import com.partimo.domain.usecase.FindLodgingsUseCase
import com.partimo.domain.usecase.GetSeasonalHighlightsUseCase
import com.partimo.domain.usecase.ObserveDepartureUseCase
import com.partimo.domain.usecase.ObservePriceAlertUseCase
import com.partimo.domain.usecase.PlanTransitRouteUseCase
import com.partimo.domain.usecase.SearchAccommodationsUseCase
import com.partimo.domain.usecase.SearchFlightsUseCase
import com.partimo.domain.usecase.SetPriceAlertUseCase
import com.partimo.domain.usecase.TrackedPrices
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * ViewModel della dashboard aggregata (MVVM).
 *
 * Espone un unico [StateFlow] immutabile. Le cinque sezioni vengono caricate in parallelo e in
 * modo indipendente: ognuna aggiorna il proprio stato appena pronta e un errore resta confinato
 * alla sua sezione. I voli dipendono dal punto di partenza scelto dall'utente, osservato come Flow:
 * se lo cambia, i voli si ricaricano da soli.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripDashboardViewModel(
    private val searchFlights: SearchFlightsUseCase,
    private val searchAccommodations: SearchAccommodationsUseCase,
    private val findLodgings: FindLodgingsUseCase,
    private val getSeasonalHighlights: GetSeasonalHighlightsUseCase,
    private val planTransitRoute: PlanTransitRouteUseCase,
    private val findBudgetRestaurants: FindBudgetRestaurantsUseCase,
    private val observeDeparture: ObserveDepartureUseCase,
    private val observePriceAlert: ObservePriceAlertUseCase,
    private val setPriceAlert: SetPriceAlertUseCase,
    private val clock: Clock,
    private val destination: Destination,
    initialPeriod: TravelPeriod = TravelPeriod.NextDays,
    isDemoMode: Boolean = false,
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState(initialPeriod, isDemoMode))
    val uiState: StateFlow<TripDashboardUiState> = _uiState.asStateFlow()

    private val sectionJobs = mutableMapOf<DashboardSection, Job>()
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            var firstLoad = true
            observeDeparture().collect { departure ->
                _uiState.update { it.copy(trip = it.trip.copy(departure = departure)) }
                if (firstLoad) {
                    firstLoad = false
                    loadDashboard(forceRefresh = false)
                } else {
                    loadSection(DashboardSection.FLIGHTS, forceRefresh = false)
                }
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
        _uiState.update { it.copy(period = period, trip = tripFor(period, it.trip)) }
        loadDashboard(forceRefresh = false)
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
            val (flightPrice, stayPrice) = bestPrices()
            setPriceAlert(enable, departure, destination, state.period, flightPrice, stayPrice)
            val message = when {
                !enable -> DashboardMessage.ALERT_DISABLED
                notificationsAllowed -> DashboardMessage.ALERT_ENABLED
                else -> DashboardMessage.ALERT_ENABLED_WITHOUT_NOTIFICATIONS
            }
            _uiState.update { it.copy(message = message) }
        }
    }

    fun onRefreshSummaryShown() {
        _uiState.update { it.copy(refreshSummary = null) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private fun loadDashboard(forceRefresh: Boolean, onComplete: () -> Unit = {}) {
        _uiState.update { it.copy(isRefreshing = forceRefresh) }
        val jobs = DashboardSection.entries.map { section -> loadSection(section, forceRefresh) }
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
            adults = trip.travellers,
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
            adults = trip.travellers,
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

    private suspend fun loadRestaurants(trip: TripContext, forceRefresh: Boolean) {
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

    /** Volo più economico e alloggio ben recensito più economico (a notte): gli stessi prezzi seguiti dagli avvisi. */
    private fun bestPrices(): Pair<Money?, Money?> {
        val state = _uiState.value
        val flight = (state.flights as? UiState.Success)?.data?.let { offers -> TrackedPrices.cheapestFlight(offers.map { it.offer }) }
        val stay = (state.stays as? UiState.Success)?.data?.let { offers -> TrackedPrices.cheapestGoodStay(offers.map { it.offer }) }
        return flight?.totalPrice to stay?.pricePerNight
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
            restaurantRatingsAvailable = findBudgetRestaurants.ratingsAvailable,
            isDemoMode = isDemoMode,
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
                    planTransitRoute = container.planTransitRoute,
                    findBudgetRestaurants = container.findBudgetRestaurants,
                    observeDeparture = container.observeDeparture,
                    observePriceAlert = container.observePriceAlert,
                    setPriceAlert = container.setPriceAlert,
                    clock = container.clock,
                    destination = destination,
                    initialPeriod = initialPeriod,
                    isDemoMode = container.isDemoMode,
                )
            }
        }
    }
}
