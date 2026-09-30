package com.partimo.app.ui.common

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.common.DataResult

/** Stato di una sezione della UI: caricamento, dati, nessun risultato o errore. */
sealed interface UiState<out T> {

    data object Loading : UiState<Nothing>

    /** Nessun risultato: diverso da un errore, la richiesta è andata a buon fine. */
    data object Empty : UiState<Nothing>

    data class Success<out T>(
        val data: T,
        val origin: DataOrigin = DataOrigin.REMOTE,
    ) : UiState<T>

    data class Error(val error: DataError) : UiState<Nothing>
}

/** Converte un risultato di dominio nello stato UI; [isEmpty] stabilisce quando mostrare lo stato vuoto. */
inline fun <T> DataResult<T>.toUiState(isEmpty: (T) -> Boolean = { false }): UiState<T> = when (this) {
    is DataResult.Success -> if (isEmpty(data)) UiState.Empty else UiState.Success(data, origin)
    is DataResult.Failure -> UiState.Error(error)
}

fun <T> DataResult<List<T>>.toListUiState(): UiState<List<T>> = toUiState { it.isEmpty() }
