package com.partimo.app.ui.departure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.partimo.app.di.AppContainer
import com.partimo.app.ui.common.CitySearch
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.common.toListUiState
import com.partimo.domain.model.place.AirportOption
import com.partimo.domain.model.place.CityPlace
import com.partimo.domain.model.place.DeparturePoint
import com.partimo.domain.usecase.FindDepartureAirportsUseCase
import com.partimo.domain.usecase.ObserveDepartureUseCase
import com.partimo.domain.usecase.SaveDepartureUseCase
import com.partimo.domain.usecase.SearchCitiesUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Stato della scelta del punto di partenza. */
data class DeparturePickerUiState(
    /** Partenza salvata attualmente, mostrata come riferimento. */
    val current: DeparturePoint? = null,
    /** Città trovate; `null` finché il testo è troppo corto. */
    val results: UiState<List<CityPlace>>? = null,
    /** Città scelta: la schermata mostra i suoi aeroporti. */
    val selectedCity: CityPlace? = null,
    val airports: UiState<List<AirportOption>>? = null,
    /** Partenza appena salvata: la UI torna alla schermata precedente e lo notifica. */
    val saved: Boolean = false,
)

/**
 * Scelta del punto di partenza in due passi: l'utente cerca la sua città, poi sceglie l'aeroporto
 * (il consigliato è in cima, ma può preferirne un altro: es. Linate invece di Malpensa).
 */
class DeparturePickerViewModel(
    searchCities: SearchCitiesUseCase,
    private val findDepartureAirports: FindDepartureAirportsUseCase,
    private val observeDeparture: ObserveDepartureUseCase,
    private val saveDeparture: SaveDepartureUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeparturePickerUiState())
    val uiState: StateFlow<DeparturePickerUiState> = _uiState.asStateFlow()

    private val citySearch = CitySearch(viewModelScope, searchCities) { results -> _uiState.update { it.copy(results = results) } }

    /** Testo del campo di ricerca (stato Compose, aggiornato in modo sincrono). */
    val query: String get() = citySearch.query

    private var airportsJob: Job? = null

    init {
        viewModelScope.launch {
            observeDeparture().collect { departure -> _uiState.update { it.copy(current = departure) } }
        }
    }

    fun onQueryChange(text: String) = citySearch.onQueryChange(text)

    fun onClearQuery() = citySearch.clear()

    fun onRetrySearch() = citySearch.retry()

    fun onCitySelected(city: CityPlace) {
        airportsJob?.cancel()
        _uiState.update { it.copy(selectedCity = city, airports = UiState.Loading) }
        airportsJob = viewModelScope.launch {
            val airports = findDepartureAirports(city).toListUiState()
            _uiState.update { it.copy(airports = airports) }
        }
    }

    fun onRetryAirports() {
        _uiState.value.selectedCity?.let(::onCitySelected)
    }

    /** Torna alla ricerca della città, mantenendo il testo già scritto. */
    fun onChangeCity() {
        airportsJob?.cancel()
        _uiState.update { it.copy(selectedCity = null, airports = null) }
    }

    fun onAirportSelected(option: AirportOption) {
        val city = _uiState.value.selectedCity ?: return
        viewModelScope.launch {
            saveDeparture(DeparturePoint(cityName = city.name, airport = option.airport))
            _uiState.update { it.copy(saved = true) }
        }
    }

    fun onSavedHandled() {
        _uiState.update { it.copy(saved = false) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DeparturePickerViewModel(
                    searchCities = container.searchCities,
                    findDepartureAirports = container.findDepartureAirports,
                    observeDeparture = container.observeDeparture,
                    saveDeparture = container.saveDeparture,
                )
            }
        }
    }
}
