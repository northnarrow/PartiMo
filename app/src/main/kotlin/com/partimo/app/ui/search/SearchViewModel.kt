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
import com.partimo.domain.model.place.CityPlace
import com.partimo.domain.model.place.DestinationSuggestion
import com.partimo.domain.usecase.ObserveDepartureUseCase
import com.partimo.domain.usecase.RecommendDestinationsUseCase
import com.partimo.domain.usecase.ResolveDestinationUseCase
import com.partimo.domain.usecase.SearchCitiesUseCase
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState(today = LocalDate.now(clock)))
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val citySearch = CitySearch(viewModelScope, searchCities) { results -> _uiState.update { it.copy(results = results) } }

    /** Testo del campo di ricerca (stato Compose, aggiornato in modo sincrono). */
    val query: String get() = citySearch.query

    private var recommendationPage = 0
    private var recommendationsJob: Job? = null
    private var preparationJob: Job? = null

    init {
        viewModelScope.launch {
            observeDeparture().collect { departure -> _uiState.update { it.copy(departure = departure) } }
        }
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
    }

    fun onRecommend() {
        recommendationPage = 0
        loadRecommendations()
    }

    fun onMoreRecommendations() {
        recommendationPage++
        loadRecommendations()
    }

    fun onCitySelected(city: CityPlace) {
        preparationJob?.cancel()
        _uiState.update { it.copy(preparingCityId = city.id, preparationError = null) }
        preparationJob = viewModelScope.launch {
            when (val result = resolveDestination(city)) {
                is DataResult.Success -> _uiState.update {
                    it.copy(preparingCityId = null, pendingNavigation = PendingNavigation(result.data, it.period))
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
                )
            }
        }
    }
}
