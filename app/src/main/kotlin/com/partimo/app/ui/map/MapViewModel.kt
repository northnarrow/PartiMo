package com.partimo.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.partimo.app.di.AppContainer
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.common.toFavorite
import com.partimo.app.ui.common.toListUiState
import com.partimo.app.ui.common.toPointOfInterest
import com.partimo.app.ui.dashboard.components.lodgingMapsUrl
import com.partimo.app.ui.dashboard.components.toPointOfInterest
import com.partimo.domain.common.DataResult
import com.partimo.domain.common.map
import com.partimo.domain.model.Destination
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.dining.Restaurant
import com.partimo.domain.model.event.TripEvents
import com.partimo.domain.model.poi.PointOfInterest
import com.partimo.domain.model.poi.SeasonalHighlights
import com.partimo.domain.model.saved.Favorite
import com.partimo.domain.model.saved.FavoriteKind
import com.partimo.domain.model.stay.Lodging
import com.partimo.domain.usecase.FindBudgetRestaurantsUseCase
import com.partimo.domain.usecase.FindLodgingsUseCase
import com.partimo.domain.usecase.GetSeasonalHighlightsUseCase
import com.partimo.domain.usecase.GetTripEventsUseCase
import com.partimo.domain.usecase.ObserveSavedTripUseCase
import com.partimo.domain.usecase.ToggleFavoriteUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Punto della mappa: un luogo, un evento, un ristorante o un alloggio del viaggio. */
data class MapPoint(
    /** Lo stesso elemento come preferito (tipo, nome, foto, pagina): la sua chiave identifica il punto. */
    val item: Favorite,
    val location: GeoPoint,
    /** Scheda da aprire per luoghi ed eventi, con i dati completi della dashboard. */
    val place: PointOfInterest? = null,
) {
    val key: String get() = item.key
    val kind: FavoriteKind get() = item.kind
}

/** Da dove arrivano i punti: ogni fonte si carica per conto suo (le stesse ricerche della dashboard, già in cache). */
enum class MapSource { HIGHLIGHTS, EVENTS, RESTAURANTS, LODGINGS }

data class MapUiState(
    val destination: Destination,
    val from: LocalDate,
    val to: LocalDate,
    val sources: Map<MapSource, UiState<List<MapPoint>>> = MapSource.entries.associateWith { UiState.Loading },
    /** Tipi di punti nascosti dai filtri. */
    val hiddenKinds: Set<FavoriteKind> = emptySet(),
    /** `true` se si possono salvare i preferiti (viaggio con un periodo). */
    val favoritesEnabled: Boolean = false,
    val favorites: List<Favorite> = emptyList(),
    val favoritesOnly: Boolean = false,
    val selectedKey: String? = null,
    /** Cresce a ogni richiesta di inquadrare i punti visibili: a fine caricamento e con "Inquadra tutto". */
    val fitRequest: Int = 0,
    /** Posizione dell'utente, se l'ha chiesta con "Dove sono". */
    val myLocation: GeoPoint? = null,
    /** Cresce a ogni richiesta di centrare la mappa sull'utente. */
    val locateRequest: Int = 0,
) {
    /** Distanza in metri tra l'utente e il punto selezionato, se si conoscono entrambi. */
    val selectedDistanceMeters: Double? get() = myLocation?.let { me -> selected?.location?.distanceTo(me) }

    val favoriteKeys: Set<String> get() = favorites.mapTo(HashSet()) { it.key }

    /** Punti caricati più i preferiti salvati che non sono tra questi (es. un luogo trovato in un altro mese). */
    val points: List<MapPoint>
        get() {
            val loaded = sources.values.flatMap { (it as? UiState.Success)?.data.orEmpty() }.distinctBy { it.key }
            val loadedKeys = loaded.mapTo(HashSet()) { it.key }
            val saved = favorites
                .filter { it.key !in loadedKeys }
                .mapNotNull { favorite -> favorite.location?.let { MapPoint(favorite, it, favorite.toPointOfInterest()) } }
            return loaded + saved
        }

    val visiblePoints: List<MapPoint>
        get() {
            val favoriteKeys = favoriteKeys
            return points.filter { it.kind !in hiddenKinds && (!favoritesOnly || it.key in favoriteKeys) }
        }

    /** Punto toccato, se è ancora visibile con i filtri attuali. */
    val selected: MapPoint? get() = selectedKey?.let { key -> visiblePoints.firstOrNull { it.key == key } }

    val isLoading: Boolean get() = sources.values.any { it is UiState.Loading }

    val hasErrors: Boolean get() = sources.values.any { it is UiState.Error }

    fun countOf(kind: FavoriteKind): Int = points.count { it.kind == kind }
}

/**
 * ViewModel della mappa del viaggio: luoghi da vedere, eventi, ristoranti e alloggi (con i preferiti
 * salvati) come punti, filtrabili per tipo. I dati sono quelli della dashboard, quindi di solito
 * arrivano subito dalla cache.
 */
class MapViewModel(
    private val getSeasonalHighlights: GetSeasonalHighlightsUseCase,
    private val getTripEvents: GetTripEventsUseCase,
    private val findBudgetRestaurants: FindBudgetRestaurantsUseCase,
    private val findLodgings: FindLodgingsUseCase,
    destination: Destination,
    from: LocalDate,
    to: LocalDate,
    /** Periodo della dashboard: serve per leggere e salvare i preferiti del viaggio. */
    private val period: TravelPeriod? = null,
    favoritesOnly: Boolean = false,
    observeSavedTrip: ObserveSavedTripUseCase? = null,
    private val toggleFavorite: ToggleFavoriteUseCase? = null,
) : ViewModel() {

    private val favoritesEnabled = period != null && observeSavedTrip != null && toggleFavorite != null

    private val _uiState = MutableStateFlow(
        MapUiState(
            destination = destination,
            from = from,
            to = to,
            favoritesEnabled = favoritesEnabled,
            favoritesOnly = favoritesOnly && favoritesEnabled,
        ),
    )
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val jobs = mutableMapOf<MapSource, Job>()

    init {
        if (favoritesEnabled && period != null && observeSavedTrip != null) {
            viewModelScope.launch {
                observeSavedTrip(destination, period).collect { trip -> _uiState.update { it.copy(favorites = trip?.favorites.orEmpty()) } }
            }
        }
        val loads = MapSource.entries.map { load(it, forceRefresh = false) }
        viewModelScope.launch {
            loads.joinAll()
            // A caricamento finito la mappa inquadra tutti i punti.
            onFitAll()
        }
    }

    fun onKindToggled(kind: FavoriteKind) = updateFilters { state ->
        val hidden = state.hiddenKinds
        state.copy(hiddenKinds = if (kind in hidden) hidden - kind else hidden + kind)
    }

    fun onFavoritesOnlyChanged(enabled: Boolean) = updateFilters { it.copy(favoritesOnly = enabled && it.favoritesEnabled) }

    /** Tocco su un punto della mappa; `null` per un tocco fuori dai punti, che chiude la scheda. */
    fun onPointSelected(key: String?) {
        _uiState.update { it.copy(selectedKey = key) }
    }

    /** Posizione dell'utente appena letta: la mappa la mostra e la centra. */
    fun onMyLocation(location: GeoPoint) {
        _uiState.update { it.copy(myLocation = location, locateRequest = it.locateRequest + 1) }
    }

    fun onFitAll() {
        _uiState.update { it.copy(fitRequest = it.fitRequest + 1) }
    }

    /** Stella sul punto selezionato: il primo preferito salva anche il viaggio. */
    fun onToggleFavorite(point: MapPoint) {
        val toggle = toggleFavorite ?: return
        val tripPeriod = period ?: return
        val destination = _uiState.value.destination
        viewModelScope.launch { toggle(destination, tripPeriod, point.item) }
    }

    /** Ricarica le fonti non riuscite, ignorando la cache. */
    fun retry() {
        _uiState.value.sources.filterValues { it is UiState.Error }.keys.forEach { load(it, forceRefresh = true) }
    }

    /** Cambia i filtri; se il punto selezionato non è più visibile la sua scheda si chiude. */
    private fun updateFilters(transform: (MapUiState) -> MapUiState) {
        _uiState.update { state -> transform(state).let { filtered -> filtered.copy(selectedKey = filtered.selected?.key) } }
    }

    private fun load(source: MapSource, forceRefresh: Boolean): Job {
        jobs[source]?.cancel()
        _uiState.update { it.copy(sources = it.sources + (source to UiState.Loading)) }
        val job = viewModelScope.launch {
            val state = _uiState.value
            val destination = state.destination
            val result: DataResult<List<MapPoint>> = when (source) {
                MapSource.HIGHLIGHTS -> getSeasonalHighlights(
                    location = destination.center,
                    travelDate = state.from,
                    areaName = destination.name,
                    forceRefresh = forceRefresh,
                ).map(MapPoints::places)
                MapSource.EVENTS -> getTripEvents(destination, state.from, state.to, forceRefresh).map(MapPoints::events)
                MapSource.RESTAURANTS -> findBudgetRestaurants(
                    location = destination.center,
                    areaName = destination.name,
                    forceRefresh = forceRefresh,
                ).map(MapPoints::restaurants)
                MapSource.LODGINGS -> findLodgings(destination.center, forceRefresh).map { MapPoints.lodgings(it, destination.name) }
            }
            _uiState.update { it.copy(sources = it.sources + (source to result.toListUiState())) }
        }
        jobs[source] = job
        return job
    }

    companion object {
        fun factory(
            container: AppContainer,
            destination: Destination,
            from: LocalDate,
            to: LocalDate,
            period: TravelPeriod?,
            favoritesOnly: Boolean,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MapViewModel(
                    getSeasonalHighlights = container.getSeasonalHighlights,
                    getTripEvents = container.getTripEvents,
                    findBudgetRestaurants = container.findBudgetRestaurants,
                    findLodgings = container.findLodgings,
                    destination = destination,
                    from = from,
                    to = to,
                    period = period,
                    favoritesOnly = favoritesOnly,
                    observeSavedTrip = container.observeSavedTrip,
                    toggleFavorite = container.toggleFavorite,
                )
            }
        }
    }
}

/** Conversione degli elementi della dashboard in punti: restano fuori quelli senza posizione (es. le festività). */
internal object MapPoints {
    fun places(highlights: SeasonalHighlights): List<MapPoint> =
        highlights.recommendations.map { recommendation -> recommendation.poi.let { MapPoint(it.toFavorite(), it.location, it) } }

    fun events(events: TripEvents): List<MapPoint> =
        events.events.mapNotNull { event -> event.toPointOfInterest()?.let { MapPoint(event.toFavorite(), it.location, it) } }

    fun restaurants(restaurants: List<Restaurant>): List<MapPoint> =
        restaurants.mapNotNull { restaurant -> restaurant.location?.let { MapPoint(restaurant.toFavorite(), it) } }

    fun lodgings(lodgings: List<Lodging>, city: String): List<MapPoint> =
        lodgings.map { lodging -> MapPoint(lodging.toFavorite(lodgingMapsUrl(lodging, city)), lodging.location) }
}
