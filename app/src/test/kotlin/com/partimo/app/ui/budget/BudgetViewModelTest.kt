package com.partimo.app.ui.budget

import com.partimo.app.testing.MainDispatcherRule
import com.partimo.app.ui.dashboard.SampleDestinations
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.budget.ExpenseCategory
import com.partimo.domain.model.guide.CountryInfo
import com.partimo.domain.model.guide.ExchangeRates
import com.partimo.domain.testing.FakeBudgetRepository
import com.partimo.domain.testing.FakeCountryInfoRepository
import com.partimo.domain.testing.FakeExchangeRateRepository
import com.partimo.domain.usecase.EditTripBudgetUseCase
import com.partimo.domain.usecase.GetCountryInfoUseCase
import com.partimo.domain.usecase.ObserveTripBudgetUseCase
import com.partimo.domain.usecase.SummarizeBudgetUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val prague = SampleDestinations.VIENNA.copy(name = "Praga", countryCode = "CZ", timeZone = ZoneId.of("Europe/Prague"))
    private val december = TravelPeriod.InMonth(YearMonth.of(2026, Month.DECEMBER))
    private val from = LocalDate.of(2026, Month.DECEMBER, 10)
    private val to = LocalDate.of(2026, Month.DECEMBER, 14)
    private val repository = FakeBudgetRepository()
    private val czechia = CountryInfo("CZ", "CZE", "Cechia", "CZK", "corona ceca", "Kč", listOf("ceco"), listOf("cs"))
    private val rates = FakeExchangeRateRepository(DataResult.Success(ExchangeRates("EUR", mapOf("CZK" to BigDecimal("25")))))

    /** Il 12 dicembre 2026 alle 10 a Praga: si è in viaggio. */
    private fun createViewModel(now: Instant = Instant.parse("2026-12-12T09:00:00Z")) = BudgetViewModel(
        observeTripBudget = ObserveTripBudgetUseCase(repository),
        editTripBudget = EditTripBudgetUseCase(repository),
        summarizeBudget = SummarizeBudgetUseCase(rates),
        getCountryInfo = GetCountryInfoUseCase(FakeCountryInfoRepository(DataResult.Success(czechia))),
        clock = Clock.fixed(now, ZoneId.of("UTC")),
        destination = prague,
        period = december,
        from = from,
        to = to,
    )

    @Test
    fun `una spesa in corone si registra nel giorno di oggi e conta in euro`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(listOf("EUR", "CZK"), viewModel.uiState.value.currencies)
        assertEquals(5, viewModel.uiState.value.days.size)

        viewModel.onAddExpense()
        val draft = viewModel.uiState.value.draft!!
        assertEquals("CZK", draft.currency, "La valuta del posto è la più comoda in viaggio")
        assertEquals(LocalDate.of(2026, Month.DECEMBER, 12), draft.date)

        viewModel.onDraftChanged(draft.copy(amountText = "12a"))
        assertEquals("", viewModel.uiState.value.draft?.amountText, "Solo cifre e separatore decimale")
        viewModel.onDraftChanged(draft.copy(amountText = "250", category = ExpenseCategory.FOOD, note = "Cena"))
        viewModel.onSaveDraft()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.draft)
        assertEquals(listOf("Cena"), state.expenses.map { it.note })
        assertEquals(BigDecimal("10.00"), state.summary?.total)
        assertEquals(BigDecimal("10.00"), state.summary?.byCategory?.get(ExpenseCategory.FOOD))
    }

    @Test
    fun `fuori dalle date del viaggio la spesa va al primo giorno, e il budget si imposta e si toglie`() = runTest {
        val viewModel = createViewModel(now = Instant.parse("2026-10-01T09:00:00Z"))
        advanceUntilIdle()

        viewModel.onAddExpense()
        assertEquals(from, viewModel.uiState.value.draft?.date)
        viewModel.onDismissDraft()
        assertNull(viewModel.uiState.value.draft)

        viewModel.onEditLimit()
        assertEquals("", viewModel.uiState.value.limitText)
        viewModel.onLimitChanged("500,5")
        viewModel.onSaveLimit()
        advanceUntilIdle()
        assertEquals(BigDecimal("500.5"), viewModel.uiState.value.summary?.limit)

        viewModel.onEditLimit()
        assertEquals("500,5", viewModel.uiState.value.limitText)
        viewModel.onLimitChanged("")
        viewModel.onSaveLimit()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.summary?.limit, "Un campo vuoto toglie il budget")
    }

    @Test
    fun `le spese si eliminano e un importo non valido non si salva`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAddExpense()
        viewModel.onDraftChanged(viewModel.uiState.value.draft!!.copy(amountText = "0", currency = "EUR"))
        viewModel.onSaveDraft()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.expenses.isEmpty())
        assertTrue(viewModel.uiState.value.draft != null, "La finestra resta aperta")

        viewModel.onDraftChanged(viewModel.uiState.value.draft!!.copy(amountText = "12,50"))
        viewModel.onSaveDraft()
        advanceUntilIdle()
        val expense = viewModel.uiState.value.expenses.single()
        assertEquals(BigDecimal("12.5"), expense.amount)

        viewModel.onRemoveExpense(expense)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.expenses.isEmpty())
        assertEquals(BigDecimal("0.00"), viewModel.uiState.value.summary?.total)
    }
}
