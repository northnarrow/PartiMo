package com.partimo.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.partimo.app.di.AppContainer
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.Destination
import com.partimo.domain.model.plan.ChatMessage
import com.partimo.domain.model.plan.ChatRole
import com.partimo.domain.model.plan.TripKnowledge
import com.partimo.domain.usecase.AskTravelAssistantUseCase
import com.partimo.domain.usecase.LoadTripKnowledgeUseCase
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ChatUiState(
    val destination: Destination,
    val from: LocalDate,
    val to: LocalDate,
    val isAvailable: Boolean = true,
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isAnswering: Boolean = false,
    /** Errore dell'ultima domanda, rimasta senza risposta: "Riprova" la invia di nuovo. */
    val error: DataError? = null,
) {
    val canSend: Boolean get() = isAvailable && !isAnswering && input.isNotBlank()
}

/**
 * ViewModel di "Chiedi a PartiMo": conversazione con l'assistente sul viaggio. Ciò che l'app sa del
 * viaggio (luoghi ed eventi) si carica una volta e accompagna ogni domanda.
 */
class ChatViewModel(
    private val askTravelAssistant: AskTravelAssistantUseCase,
    loadTripKnowledge: LoadTripKnowledgeUseCase,
    destination: Destination,
    from: LocalDate,
    to: LocalDate,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState(destination, from, to, isAvailable = askTravelAssistant.isAvailable))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val knowledge: Deferred<TripKnowledge> = viewModelScope.async { loadTripKnowledge(destination, from, to) }
    private var answerJob: Job? = null

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    /** Invia il testo scritto. */
    fun send() {
        val question = _uiState.value.input.trim()
        if (question.isEmpty() || !_uiState.value.canSend) return
        _uiState.update { it.copy(input = "") }
        ask(question)
    }

    /** Invia una delle domande suggerite. */
    fun ask(question: String) {
        val state = _uiState.value
        if (!state.isAvailable || state.isAnswering || question.isBlank()) return
        _uiState.update { it.copy(messages = it.messages + ChatMessage(ChatRole.USER, question.trim()), error = null) }
        requestAnswer()
    }

    /** Dopo un errore chiede di nuovo la risposta all'ultima domanda. */
    fun retry() {
        if (_uiState.value.error == null || _uiState.value.isAnswering) return
        _uiState.update { it.copy(error = null) }
        requestAnswer()
    }

    private fun requestAnswer() {
        answerJob?.cancel()
        _uiState.update { it.copy(isAnswering = true) }
        answerJob = viewModelScope.launch {
            val conversation = _uiState.value.messages
            when (val result = askTravelAssistant(knowledge.await(), conversation)) {
                is DataResult.Success -> _uiState.update {
                    it.copy(messages = it.messages + ChatMessage(ChatRole.ASSISTANT, result.data), isAnswering = false)
                }
                is DataResult.Failure -> _uiState.update { it.copy(error = result.error, isAnswering = false) }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, destination: Destination, from: LocalDate, to: LocalDate): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ChatViewModel(container.askTravelAssistant, container.loadTripKnowledge, destination, from, to) }
            }
    }
}
