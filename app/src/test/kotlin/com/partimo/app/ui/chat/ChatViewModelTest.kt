package com.partimo.app.ui.chat

import com.partimo.app.testing.MainDispatcherRule
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.event.EventKind
import com.partimo.domain.model.plan.ChatMessage
import com.partimo.domain.model.plan.ChatRole
import com.partimo.domain.service.SeasonalCalendar
import com.partimo.domain.testing.FakeEventRepository
import com.partimo.domain.testing.FakeHolidayRepository
import com.partimo.domain.testing.FakePoiRepository
import com.partimo.domain.testing.FakeTravelAssistantRepository
import com.partimo.domain.testing.FakeWeatherRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.usecase.AskTravelAssistantUseCase
import com.partimo.domain.usecase.GetSeasonalHighlightsUseCase
import com.partimo.domain.usecase.GetTripEventsUseCase
import com.partimo.domain.usecase.LoadTripKnowledgeUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import java.time.LocalDate
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val assistant = FakeTravelAssistantRepository(answerResult = DataResult.Success("Prova il Tafelspitz."))
    private val events = FakeEventRepository(
        result = DataResult.Success(listOf(TestData.event("mercatino", SeasonalCalendar.CHRISTMAS_MARKET_SEASON, kind = EventKind.CHRISTMAS_MARKET))),
    )

    private fun createViewModel(): ChatViewModel {
        val knowledge = LoadTripKnowledgeUseCase(
            GetSeasonalHighlightsUseCase(FakePoiRepository(), FakeWeatherRepository(), clock = TestData.FIXED_CLOCK),
            GetTripEventsUseCase(events, FakeHolidayRepository()),
        )
        return ChatViewModel(
            AskTravelAssistantUseCase(assistant),
            knowledge,
            TestData.destination(),
            LocalDate.of(2026, Month.DECEMBER, 10),
            LocalDate.of(2026, Month.DECEMBER, 14),
        )
    }

    @Test
    fun `la domanda scritta riceve la risposta, con gli eventi del viaggio come contesto`() = runTest {
        val viewModel = createViewModel()

        viewModel.onInputChanged("  Cosa mangio a Vienna?  ")
        assertTrue(viewModel.uiState.value.canSend)
        viewModel.send()
        assertEquals("", viewModel.uiState.value.input)
        assertTrue(viewModel.uiState.value.isAnswering)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAnswering)
        assertEquals(
            listOf(ChatMessage(ChatRole.USER, "Cosa mangio a Vienna?"), ChatMessage(ChatRole.ASSISTANT, "Prova il Tafelspitz.")),
            state.messages,
        )
        assertEquals(listOf("mercatino"), assistant.chatKnowledge.single().events.map { it.id })
    }

    @Test
    fun `le domande successive portano con sé la conversazione`() = runTest {
        val viewModel = createViewModel()
        viewModel.ask("Prima domanda")
        advanceUntilIdle()
        viewModel.ask("Seconda domanda")
        advanceUntilIdle()

        assertEquals(
            listOf("Prima domanda", "Prova il Tafelspitz.", "Seconda domanda"),
            assistant.conversations.last().map { it.text },
        )
        assertEquals(4, viewModel.uiState.value.messages.size)
    }

    @Test
    fun `dopo un errore riprova la stessa domanda`() = runTest {
        assistant.answerResult = DataResult.Failure(DataError.Timeout)
        val viewModel = createViewModel()
        viewModel.ask("Come arrivo in centro?")
        advanceUntilIdle()
        assertEquals(DataError.Timeout, viewModel.uiState.value.error)

        assistant.answerResult = DataResult.Success("Con il treno CAT in 16 minuti.")
        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.error)
        assertEquals(listOf("Come arrivo in centro?", "Con il treno CAT in 16 minuti."), state.messages.map { it.text })
        assertEquals(2, assistant.conversations.size)
        assertEquals(listOf("Come arrivo in centro?"), assistant.conversations.last().map { it.text })
    }

    @Test
    fun `non si invia nulla mentre arriva una risposta o senza testo`() = runTest {
        val viewModel = createViewModel()
        viewModel.send()
        viewModel.ask("Prima")
        viewModel.ask("Seconda, troppo presto")
        advanceUntilIdle()

        assertEquals(1, assistant.conversations.size)
        assertEquals(listOf("Prima", "Prova il Tafelspitz."), viewModel.uiState.value.messages.map { it.text })
    }
}
