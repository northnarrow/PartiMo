package com.partimo.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.partimo.app.di.AppContainer
import com.partimo.app.ui.common.CitySearch
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.common.toListUiState
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.Travellers
import com.partimo.domain.model.flight.CheapDestination
import com.partimo.domain.model.place.CityPlace
import com.partimo.domain.model.place.DestinationSuggestion
import com.partimo.domain.model.saved.SavedTrip
import com.partimo.domain.usecase.FindCheapDestinationsUseCase
import com.partimo.domain.usecase.ObserveBookingsUseCase
import com.partimo.domain.usecase.ObserveDepartureUseCase
import com.partimo.domain.usecase.ObserveSavedTripsUseCase
import com.partimo.domain.usecase.ObserveTravellersUseCase
import com.partimo.domain.usecase.RecommendDestinationsUseCase
import com.partimo.domain.usecase.ResolveDestinationUseCase
import com.partimo.domain.usecase.SaveTravellersUseCase
import com.partimo.domain.usecase.SearchCitiesUseCase
import com.partimo.domain.usecase.SetTripSavedUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/**
 * ViewModel della schermata iniziale: ricerca città con debounce, "Consigliami", punto di partenza
 * salvato e preparazione della meta scelta (individuazione dell'aeroporto) prima di aprire la dashboard.
 */
class SearchViewModel(
    private val searchCities: SearchCitiesUseCase,
    private val resolveDestination: ResolveDestinationUseCase,
    private val recommendDestinations: RecommendDestinationsUseCase,
    private val observeDeparture: ObserveDepartureUseCase,
    private val clock: Clock,
    observeSavedTrips: ObserveSavedTripsUseCase? = null,
    private val setTripSaved: SetTripSavedUseCase? = null,
    observeTravellers: ObserveTravellersUseCase? = null,
    private val saveTravellers: SaveTravellersUseCase? = null,
    /** «Ovunque»: le mete più economiche dalla città di partenza (facoltativo). */
    private val findCheapDestinations: FindCheapDestinationsUseCase? = null,
    observeBookings: ObserveBookingsUseCase? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SearchUiState(today = LocalDate.now(clock), anywhereAvailable = findCheapDestinations?.isAvailable == true),
    )
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val citySearch = CitySearch(viewModelScope, searchCities) { results -> _uiState.update { it.copy(results = results) } }

    /** Testo del campo di ricerca (stato Compose, aggiornato in modo sincrono). */
    val query: String get() = citySearch.query

    private var recommendationPage = 0
    private var recommendationsJob: Job? = null
    private var preparationJob: Job? = null
    private var anywhereJob: Job? = null

    init {
        viewModelScope.launch {
            observeDeparture().collect { departure ->
                val changed = departure != _uiState.value.departure
                _uiState.update { it.copy(departure = departure) }
                // Con un'altra città di partenza le mete più economiche cambiano.
                if (changed && _uiState.value.anywhere != null) loadAnywhere()
            }
        }
        observeSavedTrips?.let { observe ->
            viewModelScope.launch { observe().collect { trips -> _uiState.update { it.copy(savedTrips = trips) } } }
        }
        observeTravellers?.let { observe ->
            viewModelScope.launch { observe().collect { travellers -> _uiState.update { it.copy(travellers = travellers) } } }
        }
        observeBookings?.let { observe ->
            viewModelScope.launch {
                observe().collect { bookings ->
                    val today = LocalDate.now(clock)
                    _uiState.update { state -> state.copy(upcomingBookings = bookings.filterNot { it.isPast(today) }) }
                }
            }
        }
    }

    /** Adulti e bambini scelti nella cella «Chi parte»: valgono per tutti i viaggi. */
    fun onTravellersSelected(travellers: Travellers) {
        val save = saveTravellers ?: return
        viewModelScope.launch { save(travellers) }
    }

    /** Riapre un viaggio salvato: meta e periodo sono già noti, niente ricerca dell'aeroporto. */
    fun onSavedTripSelected(trip: SavedTrip) {
        _uiState.update { it.copy(pendingNavigation = PendingNavigation(trip.destination, trip.period)) }
    }

    fun onRemoveSavedTrip(trip: SavedTrip) {
        val remove = setTripSaved ?: return
        viewModelScope.launch { remove(trip.destination, trip.period, saved = false) }
    }

    fun onQueryChange(text: String) = citySearch.onQueryChange(text)

    fun onClearQuery() = citySearch.clear()

    fun onRetrySearch() = citySearch.retry()

    fun onPeriodSelected(period: TravelPeriod) {
        if (period == _uiState.value.period) return
        _uiState.update { it.copy(period = period) }
        if (_uiState.value.recommendations != null) {
            recommendationPage = 0
            loadRecommendations()
        }
        if (_uiState.value.anywhere != null) loadAnywhere()
    }

    /** «Ovunque»: mete più economiche dalla città di partenza nel periodo scelto. */
    fun onAnywhere() = loadAnywhere()

    fun onAnywhereMaxPrice(maxPrice: Int?) {
        _uiState.update { it.copy(anywhereMaxPrice = maxPrice) }
    }

    /**
     * Meta di «Ovunque» scelta: si prepara come una città cercata e la dashboard si apre sulle date del volo
     * più economico trovato.
     */
    fun onCheapDestinationSelected(destination: CheapDestination) {
        val returning = destination.fare.returnDate ?: return
        prepare(destination.city, TravelPeriod.Dates(destination.fare.departureDate, returning))
    }

    /** Date esatte scelte nelle celle «Andata» e «Ritorno»: diventano il periodo del viaggio. */
    fun onDatesSelected(departure: LocalDate, returning: LocalDate) {
        if (returning.isBefore(departure)) return
        onPeriodSelected(TravelPeriod.Dates(departure, returning))
    }

    fun onRecommend() {
        recommendationPage = 0
        loadRecommendations()
    }

    fun onMoreRecommendations() {
        recommendationPage++
        loadRecommendations()
    }

    fun onCitySelected(city: CityPlace) = prepare(city, period = null)

    /** Individua l'aeroporto della città e apre la dashboard nel periodo indicato (o in quello scelto). */
    private fun prepare(city: CityPlace, period: TravelPeriod?) {
        preparationJob?.cancel()
        _uiState.update { it.copy(preparingCityId = city.id, preparationError = null) }
        preparationJob = viewModelScope.launch {
            when (val result = resolveDestination(city)) {
                is DataResult.Success -> _uiState.update {
                    it.copy(preparingCityId = null, pendingNavigation = PendingNavigation(result.data, period ?: it.period))
                }
                is DataResult.Failure -> _uiState.update {
                    it.copy(preparingCityId = null, preparationError = PreparationError(city.name, result.error))
                }
            }
        }
    }

    fun onSuggestionSelected(suggestion: DestinationSuggestion) = onCitySelected(suggestion.destination.city)

    fun onNavigationHandled() {
        _uiState.update { it.copy(pendingNavigation = null) }
    }

    fun onPreparationErrorDismissed() {
        _uiState.update { it.copy(preparationError = null) }
    }

    private fun loadAnywhere() {
        val find = findCheapDestinations ?: return
        val departure = _uiState.value.departure ?: return
        anywhereJob?.cancel()
        _uiState.update { it.copy(anywhere = UiState.Loading) }
        anywhereJob = viewModelScope.launch {
            val result = find(departure.airport.iata, _uiState.value.period).toListUiState()
            _uiState.update { it.copy(anywhere = result) }
        }
    }

    private fun loadRecommendations() {
        recommendationsJob?.cancel()
        _uiState.update { it.copy(recommendations = UiState.Loading) }
        recommendationsJob = viewModelScope.launch {
            val travelDate = _uiState.value.period.departureDate(LocalDate.now(clock))
            val recommendations = recommendDestinations(travelDate, page = recommendationPage).toListUiState()
            _uiState.update { it.copy(recommendations = recommendations) }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    searchCities = container.searchCities,
                    resolveDestination = container.resolveDestination,
                    recommendDestinations = container.recommendDestinations,
                    observeDeparture = container.observeDeparture,
                    clock = container.clock,
                    observeSavedTrips = container.observeSavedTrips,
                    setTripSaved = container.setTripSaved,
                    observeTravellers = container.observeTravellers,
                    saveTravellers = container.saveTravellers,
                    findCheapDestinations = container.findCheapDestinations,
                    observeBookings = container.observeBookings,
                )
            }
        }
    }
}
