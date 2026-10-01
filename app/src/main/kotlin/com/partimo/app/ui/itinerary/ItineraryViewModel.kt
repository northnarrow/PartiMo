package com.partimo.app.ui.itinerary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.partimo.app.di.AppContainer
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.common.toUiState
import com.partimo.domain.common.DataError
import com.partimo.domain.model.Destination
import com.partimo.domain.model.plan.PackingGroup
import com.partimo.domain.model.plan.TripInterest
import com.partimo.domain.model.plan.TripPace
import com.partimo.domain.model.plan.TripPlan
import com.partimo.domain.model.plan.TripPreferences
import com.partimo.domain.usecase.PackingChecklistUseCase
import com.partimo.domain.usecase.PlanTripUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Schede dell'itinerario. */
enum class ItineraryTab { DAYS, PACKING, TIPS }

data class ItineraryUiState(
    val destination: Destination,
    val from: LocalDate,
    val to: LocalDate,
    /** `false` senza chiave dell'assistente: la schermata spiega come attivarlo. */
    val isAvailable: Boolean = true,
    val preferences: TripPreferences = TripPreferences(),
    /** Preferenze con cui è stato chiesto l'itinerario mostrato (o in preparazione). */
    val requestedPreferences: TripPreferences = preferences,
    val plan: UiState<TripPlan> = UiState.Loading,
    val selectedTab: ItineraryTab = ItineraryTab.DAYS,
    /** Voci della valigia già pronte, nel formato di [packingItemKey]. */
    val packedItems: Set<String> = emptySet(),
) {
    val dayCount: Int get() = (to.toEpochDay() - from.toEpochDay()).toInt() + 1

    /** L'utente ha cambiato ritmo o interessi: può chiedere un itinerario aggiornato. */
    val preferencesChanged: Boolean get() = preferences != requestedPreferences

    val isGenerating: Boolean get() = plan == UiState.Loading
}

/** Identifica una voce della valigia anche se lo stesso oggetto compare in due gruppi. */
fun packingItemKey(group: PackingGroup, item: String): String = "${group.category} › $item"

/**
 * ViewModel dell'itinerario proposto dall'assistente: lo chiede all'apertura (riaprendolo arriva
 * dalla cache, senza consumare la quota gratuita), lo rigenera su richiesta e ricorda le voci della
 * valigia già pronte.
 */
class ItineraryViewModel(
    private val planTrip: PlanTripUseCase,
    private val packingChecklist: PackingChecklistUseCase,
    destination: Destination,
    from: LocalDate,
    to: LocalDate,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItineraryUiState(destination, from, to, isAvailable = planTrip.isAvailable))
    val uiState: StateFlow<ItineraryUiState> = _uiState.asStateFlow()

    private var planJob: Job? = null

    init {
        viewModelScope.launch {
            packingChecklist.packedItems(destination, from).collect { items -> _uiState.update { it.copy(packedItems = items) } }
        }
        if (planTrip.isAvailable) {
            loadPlan(forceRefresh = false)
        } else {
            _uiState.update { it.copy(plan = UiState.Error(DataError.Unauthorized)) }
        }
    }

    fun onPaceSelected(pace: TripPace) {
        _uiState.update { it.copy(preferences = it.preferences.copy(pace = pace)) }
    }

    fun onInterestToggled(interest: TripInterest) {
        _uiState.update { state ->
            val interests = state.preferences.interests
            state.copy(preferences = state.preferences.copy(interests = if (interest in interests) interests - interest else interests + interest))
        }
    }

    /** Itinerario con le nuove preferenze (se l'aveva già chiesto, arriva dalla cache). */
    fun applyPreferences() {
        loadPlan(forceRefresh = false)
    }

    /** "Rigenera": una proposta nuova con le stesse preferenze. */
    fun regenerate() {
        loadPlan(forceRefresh = true)
    }

    fun retry() {
        loadPlan(forceRefresh = false)
    }

    fun onTabSelected(tab: ItineraryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun onPackedToggled(itemKey: String) {
        val state = _uiState.value
        viewModelScope.launch {
            packingChecklist.setPacked(state.destination, state.from, itemKey, packed = itemKey !in state.packedItems)
        }
    }

    private fun loadPlan(forceRefresh: Boolean) {
        if (!planTrip.isAvailable) return
        planJob?.cancel()
        val preferences = _uiState.value.preferences
        _uiState.update { it.copy(plan = UiState.Loading, requestedPreferences = preferences) }
        planJob = viewModelScope.launch {
            val state = _uiState.value
            val plan = planTrip(state.destination, state.from, state.to, preferences, forceRefresh).toUiState()
            _uiState.update { it.copy(plan = plan) }
        }
    }

    companion object {
        fun factory(container: AppContainer, destination: Destination, from: LocalDate, to: LocalDate): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ItineraryViewModel(container.planTrip, container.packingChecklist, destination, from, to) }
            }
    }
}
