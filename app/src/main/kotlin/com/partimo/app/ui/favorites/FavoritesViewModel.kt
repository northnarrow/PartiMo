package com.partimo.app.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.partimo.app.di.AppContainer
import com.partimo.domain.model.Destination
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.saved.Favorite
import com.partimo.domain.usecase.ObserveSavedTripUseCase
import com.partimo.domain.usecase.ToggleFavoriteUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class FavoritesUiState(
    val destination: Destination,
    val period: TravelPeriod,
    val from: LocalDate,
    val to: LocalDate,
    val favorites: List<Favorite> = emptyList(),
    /** `false` finché i preferiti salvati non sono stati letti. */
    val loaded: Boolean = false,
)

/** ViewModel dei preferiti di un viaggio salvato: si aggiorna da solo quando cambiano. */
class FavoritesViewModel(
    observeSavedTrip: ObserveSavedTripUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    destination: Destination,
    period: TravelPeriod,
    from: LocalDate,
    to: LocalDate,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState(destination, period, from, to))
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeSavedTrip(destination, period).collect { trip ->
                _uiState.update { it.copy(favorites = trip?.favorites.orEmpty(), loaded = true) }
            }
        }
    }

    fun onRemove(favorite: Favorite) {
        val state = _uiState.value
        viewModelScope.launch { toggleFavorite(state.destination, state.period, favorite) }
    }

    companion object {
        fun factory(
            container: AppContainer,
            destination: Destination,
            period: TravelPeriod,
            from: LocalDate,
            to: LocalDate,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { FavoritesViewModel(container.observeSavedTrip, container.toggleFavorite, destination, period, from, to) }
        }
    }
}
