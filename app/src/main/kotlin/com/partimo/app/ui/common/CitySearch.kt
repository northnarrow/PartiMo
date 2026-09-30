package com.partimo.app.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.partimo.domain.model.place.CityPlace
import com.partimo.domain.usecase.SearchCitiesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Ricerca di città con debounce, condivisa dai ViewModel che cercano una città (meta e partenza).
 * Ogni nuovo testo annulla la ricerca precedente, così non arrivano mai risultati superati; sotto
 * i due caratteri non si cerca e [onResults] riceve `null`.
 */
class CitySearch(
    scope: CoroutineScope,
    private val searchCities: SearchCitiesUseCase,
    private val onResults: (UiState<List<CityPlace>>?) -> Unit,
) {

    /**
     * Testo del campo di ricerca. È uno stato Compose e non un Flow: il TextField va aggiornato in
     * modo sincrono, altrimenti durante la digitazione veloce il cursore può "saltare".
     */
    var query by mutableStateOf("")
        private set

    private val requests = MutableStateFlow(Request(text = ""))

    init {
        scope.launch { requests.collectLatest { request -> run(request.text) } }
    }

    fun onQueryChange(text: String) {
        query = text
        requests.value = Request(text)
    }

    fun clear() = onQueryChange("")

    fun retry() {
        requests.update { it.copy(attempt = it.attempt + 1) }
    }

    private suspend fun run(text: String) {
        val trimmed = text.trim()
        if (trimmed.length < SearchCitiesUseCase.MIN_QUERY_LENGTH) {
            onResults(null)
            return
        }
        onResults(UiState.Loading)
        delay(DEBOUNCE_MILLIS)
        onResults(searchCities(trimmed).toListUiState())
    }

    /** [attempt] distingue un "Riprova" da un testo identico già cercato. */
    private data class Request(val text: String, val attempt: Int = 0)

    companion object {
        const val DEBOUNCE_MILLIS = 300L
    }
}
