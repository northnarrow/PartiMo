package com.partimo.app.ui.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.partimo.app.di.AppContainer
import com.partimo.app.navigation.TripArgs
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.common.toFavorite
import com.partimo.app.ui.common.toUiState
import com.partimo.domain.model.Destination
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.poi.PoiDetails
import com.partimo.domain.model.poi.PointOfInterest
import com.partimo.domain.usecase.GetPoiDetailsUseCase
import com.partimo.domain.usecase.ObserveSavedTripUseCase
import com.partimo.domain.usecase.ToggleFavoriteUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Stato della scheda di un luogo: i dati del luogo sono subito disponibili, descrizione e storia si caricano. */
data class PlaceDetailUiState(
    val poi: PointOfInterest,
    val details: UiState<PoiDetails> = UiState.Loading,
    /** Il luogo è tra i preferiti del viaggio; `null` se la scheda non è legata a un viaggio. */
    val isFavorite: Boolean? = null,
) {
    /** Foto da mostrare: quella della voce, in alta risoluzione, appena disponibile. */
    val imageUrl: String?
        get() = (details as? UiState.Success)?.data?.imageUrl ?: poi.photoUrl
}

/**
 * ViewModel della scheda di un luogo da vedere: carica breve descrizione e storia (Wikipedia).
 * Il pulsante "Naviga" non dipende dal caricamento: le coordinate del luogo sono già note.
 */
class PlaceDetailViewModel(
    private val getPoiDetails: GetPoiDetailsUseCase,
    poi: PointOfInterest,
    /** Meta e periodo del viaggio da cui si è aperta la scheda, per salvare il luogo tra i preferiti. */
    private val trip: Pair<Destination, TravelPeriod>? = null,
    observeSavedTrip: ObserveSavedTripUseCase? = null,
    private val toggleFavorite: ToggleFavoriteUseCase? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaceDetailUiState(poi))
    val uiState: StateFlow<PlaceDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(forceRefresh = false)
        if (trip != null && observeSavedTrip != null && toggleFavorite != null) {
            val key = poi.toFavorite().key
            viewModelScope.launch {
                observeSavedTrip(trip.first, trip.second).collect { saved ->
                    _uiState.update { it.copy(isFavorite = saved?.isFavorite(key) == true) }
                }
            }
        }
    }

    /** Stella dei preferiti: salva il luogo nel viaggio (salvando anche il viaggio) o lo toglie. */
    fun onToggleFavorite() {
        val (destination, period) = trip ?: return
        val toggle = toggleFavorite ?: return
        viewModelScope.launch { toggle(destination, period, _uiState.value.poi.toFavorite()) }
    }

    /** Dopo un errore riprova ignorando la cache. */
    fun retry() {
        load(forceRefresh = true)
    }

    private fun load(forceRefresh: Boolean) {
        loadJob?.cancel()
        _uiState.update { it.copy(details = UiState.Loading) }
        loadJob = viewModelScope.launch {
            val details = getPoiDetails(_uiState.value.poi, forceRefresh).toUiState()
            _uiState.update { it.copy(details = details) }
        }
    }

    companion object {
        fun factory(container: AppContainer, poi: PointOfInterest, trip: TripArgs? = null): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val tripRef = trip?.let { args -> args.travelPeriod()?.let { period -> args.destination() to period } }
                PlaceDetailViewModel(
                    getPoiDetails = container.getPoiDetails,
                    poi = poi,
                    trip = tripRef,
                    observeSavedTrip = container.observeSavedTrip,
                    toggleFavorite = container.toggleFavorite,
                )
            }
        }
    }
}
