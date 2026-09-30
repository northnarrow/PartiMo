package com.partimo.app.ui.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.partimo.app.di.AppContainer
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.common.toUiState
import com.partimo.domain.model.poi.PoiDetails
import com.partimo.domain.model.poi.PointOfInterest
import com.partimo.domain.usecase.GetPoiDetailsUseCase
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaceDetailUiState(poi))
    val uiState: StateFlow<PlaceDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(forceRefresh = false)
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
        fun factory(container: AppContainer, poi: PointOfInterest): ViewModelProvider.Factory = viewModelFactory {
            initializer { PlaceDetailViewModel(container.getPoiDetails, poi) }
        }
    }
}
