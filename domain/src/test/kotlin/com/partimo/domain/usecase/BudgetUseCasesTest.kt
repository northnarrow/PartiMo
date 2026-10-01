package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.common.QueryIssue
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.budget.ExpenseCategory
import com.partimo.domain.model.guide.ExchangeRates
import com.partimo.domain.model.saved.SavedTrip
import com.partimo.domain.testing.FakeBudgetRepository
import com.partimo.domain.testing.FakeExchangeRateRepository
import com.partimo.domain.testing.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BudgetUseCasesTest {

    private val prague = TestData.destination().copy(name = "Praga", countryCode = "CZ")
    private val december = TravelPeriod.InMonth(YearMonth.of(2026, Month.DECEMBER))
    private val day = LocalDate.of(2026, Month.DECEMBER, 11)
    private val repository = FakeBudgetRepository()
    private var ids = 0
    private val edit = EditTripBudgetUseCase(repository, newId = { "spesa-${++ids}" })
    private val observe = ObserveTripBudgetUseCase(repository)
    private val rates = FakeExchangeRateRepository(DataResult.Success(ExchangeRates("EUR", mapOf("CZK" to BigDecimal("24.44"), "USD" to BigDecimal("1.10")))))
    private val summarize = SummarizeBudgetUseCase(rates)

    @Test
    fun `le spese si registrano nel viaggio della meta e del periodo e si possono togliere`() = runTest {
        edit.addExpense(prague, december, BigDecimal("250.00"), "czk", ExpenseCategory.FOOD, day, "  Cena al Lokál  ")
        edit.addExpense(prague, december, BigDecimal("89"), "EUR", ExpenseCategory.LODGING, day)

        val budget = observe(prague, december).first()
        assertEquals(SavedTrip.idOf(prague, december), budget.tripId)
        assertEquals(listOf("spesa-1", "spesa-2"), budget.expenses.map { it.id })
        assertEquals("CZK", budget.expenses.first().currency)
        assertEquals("Cena al Lokál", budget.expenses.first().note)
        assertEquals(BigDecimal("250"), budget.expenses.first().amount)

        edit.removeExpense(prague, december, "spesa-1")
        assertEquals(listOf("spesa-2"), observe(prague, december).first().expenses.map { it.id })
        assertTrue(observe(prague, TravelPeriod.NextDays).first().expenses.isEmpty(), "Ogni periodo è un viaggio diverso")
    }

    @Test
    fun `importi e valute non validi sono rifiutati`() = runTest {
        val invalid = DataResult.Failure(DataError.InvalidQuery(QueryIssue.INVALID_AMOUNT))
        assertEquals(invalid, edit.addExpense(prague, december, BigDecimal.ZERO, "EUR", ExpenseCategory.OTHER, day))
        assertEquals(invalid, edit.addExpense(prague, december, BigDecimal("-5"), "EUR", ExpenseCategory.OTHER, day))
        assertEquals(invalid, edit.addExpense(prague, december, BigDecimal("5"), "euro", ExpenseCategory.OTHER, day))
        assertEquals(invalid, edit.addExpense(prague, december, BigDecimal("2000000"), "EUR", ExpenseCategory.OTHER, day))
        assertTrue(repository.budgets.isEmpty())
    }

    @Test
    fun `i totali sono in euro con i cambi del giorno, per categoria e rispetto al budget`() = runTest {
        edit.addExpense(prague, december, BigDecimal("244.40"), "CZK", ExpenseCategory.FOOD, day)
        edit.addExpense(prague, december, BigDecimal("90"), "EUR", ExpenseCategory.LODGING, day)
        edit.addExpense(prague, december, BigDecimal("11"), "USD", ExpenseCategory.FOOD, day)
        edit.setLimit(prague, december, BigDecimal("100"))

        val summary = summarize(observe(prague, december).first())

        // 244,40 Kč = 10 €, 11 $ = 10 €.
        assertEquals(BigDecimal("110.00"), summary.total)
        assertEquals(BigDecimal("20.00"), summary.byCategory[ExpenseCategory.FOOD])
        assertEquals(BigDecimal("90.00"), summary.byCategory[ExpenseCategory.LODGING])
        assertEquals(BigDecimal("-10.00"), summary.remaining)
        assertTrue(summary.isOverBudget)
        assertEquals(1f, summary.usedShare)
        assertEquals(BigDecimal("10.00"), summary.converted["spesa-1"])
        assertEquals(listOf("EUR" to false), rates.requests)
    }

    @Test
    fun `senza cambi le spese in euro contano e le altre si segnalano`() = runTest {
        rates.result = DataResult.Failure(DataError.NoConnection)
        edit.addExpense(prague, december, BigDecimal("250"), "CZK", ExpenseCategory.FOOD, day)
        edit.addExpense(prague, december, BigDecimal("40"), "EUR", ExpenseCategory.ACTIVITIES, day)

        val summary = summarize(observe(prague, december).first())

        assertEquals(BigDecimal("40.00"), summary.total)
        assertEquals(listOf("spesa-1"), summary.unconverted.map { it.id })
        assertNull(summary.limit)
        assertFalse(summary.isOverBudget)
        assertNull(summary.usedShare)

        edit.setLimit(prague, december, BigDecimal.ZERO)
        assertNull(observe(prague, december).first().limit, "Un budget di zero lo toglie")
    }

    @Test
    fun `solo spese in euro non richiedono i cambi`() = runTest {
        edit.addExpense(prague, december, BigDecimal("12.5"), "EUR", ExpenseCategory.TRANSPORT, day)

        val summary = summarize(observe(prague, december).first())

        assertEquals(BigDecimal("12.50"), summary.total)
        assertTrue(rates.requests.isEmpty())
    }
}
