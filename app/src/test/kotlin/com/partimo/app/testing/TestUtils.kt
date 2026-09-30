package com.partimo.app.testing

import com.partimo.app.ui.common.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Sostituisce Dispatchers.Main (usato da viewModelScope) con un dispatcher di test.
 * `runTest` ne condivide lo scheduler, quindi `advanceUntilIdle` controlla anche le coroutine del ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

/** Dati di un [UiState.Success] o fallimento del test con un messaggio chiaro. */
fun <T> UiState<T>.successData(): T = when (this) {
    is UiState.Success -> data
    else -> throw AssertionError("Atteso UiState.Success, ottenuto $this")
}
