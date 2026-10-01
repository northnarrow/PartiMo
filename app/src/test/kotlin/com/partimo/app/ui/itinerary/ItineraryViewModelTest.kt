package com.partimo.app.ui.itinerary

import com.partimo.app.navigation.TripArgs
import com.partimo.app.testing.MainDispatcherRule
import com.partimo.app.testing.successData
import com.partimo.app.ui.common.UiState
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.TripContext
import com.partimo.domain.model.plan.DayPart
import com.partimo.domain.model.plan.DayPlan
import com.partimo.domain.model.plan.PackingGroup
import com.partimo.domain.model.plan.PlanStop
import com.partimo.domain.model.plan.TripInterest
import com.partimo.domain.model.plan.TripPace
import com.partimo.domain.model.plan.TripPlan
import com.partimo.domain.model.plan.TripPreferences
import com.partimo.domain.testing.FakeChecklistRepository
import com.partimo.domain.testing.FakeEventRepository
import com.partimo.domain.testing.FakeHolidayRepository
import com.partimo.domain.testing.FakePoiRepository
import com.partimo.domain.testing.FakeTravelAssistantRepository
import com.partimo.domain.testing.FakeWeatherRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.usecase.GetSeasonalHighlightsUseCase
import com.partimo.domain.usecase.GetTripEventsUseCase
import com.partimo.domain.usecase.LoadTripKnowledgeUseCase
import com.partimo.domain.usecase.PackingChecklistUseCase
import com.partimo.domain.usecase.PlanTripUseCase
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ItineraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val vienna = TestData.destination()
    private val from = LocalDate.of(2026, Month.DECEMBER, 10)
    private val to = LocalDate.of(2026, Month.DECEMBER, 14)
    private val plan = TripPlan(
        days = listOf(DayPlan(from, "Arrivo", listOf(PlanStop(DayPart.AFTERNOON, "Duomo", "Visita")))),
        packing = listOf(PackingGroup("Abbigliamento", listOf("Cappotto", "Guanti"))),
        tips = listOf("Usa i mezzi"),
    )
    private val assistant = FakeTravelAssistantRepository(planResult = DataResult.Success(plan))
    private val checklist = FakeChecklistRepository()

    private fun createViewModel(): ItineraryViewModel {
        val knowledge = LoadTripKnowledgeUseCase(
            GetSeasonalHighlightsUseCase(FakePoiRepository(), FakeWeatherRepository(), clock = TestData.FIXED_CLOCK),
            GetTripEventsUseCase(FakeEventRepository(), FakeHolidayRepository()),
        )
        return ItineraryViewModel(PlanTripUseCase(assistant, knowledge), PackingChecklistUseCase(checklist), vienna, from, to)
    }

    @Test
    fun `all'apertura chiede l'itinerario del viaggio, dalla cache se c'è`() = runTest {
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.isGenerating)

        advanceUntilIdle()

        assertEquals(plan, viewModel.uiState.value.plan.successData())
        val (knowledge, preferences, forceRefresh) = assistant.planRequests.single()
        assertEquals(from to to, knowledge.from to knowledge.to)
        assertEquals(TripPreferences(), preferences)
        assertFalse(forceRefresh)
        assertEquals(5, viewModel.uiState.value.dayCount)
    }

    @Test
    fun `cambiando ritmo e interessi si può chiedere un itinerario aggiornato`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onPaceSelected(TripPace.INTENSE)
        viewModel.onInterestToggled(TripInterest.FOOD)
        viewModel.onInterestToggled(TripInterest.ART)
        viewModel.onInterestToggled(TripInterest.FOOD)
        assertTrue(viewModel.uiState.value.preferencesChanged)

        viewModel.applyPreferences()
        advanceUntilIdle()

        val expected = TripPreferences(pace = TripPace.INTENSE, interests = setOf(TripInterest.ART))
        assertEquals(expected, assistant.planRequests.last().second)
        assertFalse(viewModel.uiState.value.preferencesChanged)
    }

    @Test
    fun `rigenera chiede una proposta nuova, riprova no`() = runTest {
        assistant.planResult = DataResult.Failure(DataError.RateLimited)
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(UiState.Error(DataError.RateLimited), viewModel.uiState.value.plan)

        assistant.planResult = DataResult.Success(plan)
        viewModel.retry()
        advanceUntilIdle()
        viewModel.regenerate()
        advanceUntilIdle()

        assertEquals(listOf(false, false, true), assistant.planRequests.map { it.third })
        assertEquals(plan, viewModel.uiState.value.plan.successData())
    }

    @Test
    fun `le voci della valigia restano spuntate`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val coat = packingItemKey(plan.packing.single(), "Cappotto")

        viewModel.onPackedToggled(coat)
        advanceUntilIdle()
        assertEquals(setOf(coat), viewModel.uiState.value.packedItems)

        // Riaprendo l'itinerario dello stesso viaggio le voci sono ancora lì.
        val reopened = createViewModel()
        advanceUntilIdle()
        assertEquals(setOf(coat), reopened.uiState.value.packedItems)

        reopened.onPackedToggled(coat)
        advanceUntilIdle()
        assertEquals(emptySet(), reopened.uiState.value.packedItems)
    }

    @Test
    fun `senza chiave l'assistente non è disponibile e non parte nessuna richiesta`() = runTest {
        assistant.isAvailable = false
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAvailable)
        assertTrue(assistant.planRequests.isEmpty())
    }
}

class TripArgsTest {

    @Test
    fun `la rotta conserva destinazione e date esatte del viaggio`() {
        val trip = TripContext(TestData.destination(), LocalDate.of(2026, Month.DECEMBER, 10), LocalDate.of(2026, Month.DECEMBER, 14))

        val args = TripArgs.fromJson(TripArgs.from(trip).toJson())!!

        assertEquals(trip.destination, args.destination())
        assertEquals(trip.departureDate, args.fromDate())
        assertEquals(trip.returnDate, args.toDate())
    }

    @Test
    fun `un testo non valido non apre nessun viaggio`() {
        assertEquals(null, TripArgs.fromJson("{}"))
        val args = TripArgs.from(TripContext(TestData.destination(), LocalDate.of(2026, Month.DECEMBER, 10), LocalDate.of(2026, Month.DECEMBER, 14)))
        assertEquals(null, TripArgs.fromJson(args.copy(to = "2026-12-01").toJson()), "Ritorno prima della partenza")
    }
}
