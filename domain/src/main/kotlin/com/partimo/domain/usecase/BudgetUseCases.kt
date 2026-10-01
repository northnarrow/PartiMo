package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.common.DataResult
import com.partimo.domain.common.QueryIssue
import com.partimo.domain.model.Destination
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.budget.BudgetSummary
import com.partimo.domain.model.budget.Expense
import com.partimo.domain.model.budget.ExpenseCategory
import com.partimo.domain.model.budget.TripBudget
import com.partimo.domain.model.guide.EURO
import com.partimo.domain.model.saved.SavedTrip
import com.partimo.domain.repository.BudgetRepository
import com.partimo.domain.repository.ExchangeRateRepository
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

/** Budget di un viaggio (meta e periodo), aggiornato a ogni spesa. */
class ObserveTripBudgetUseCase(private val repository: BudgetRepository) {
    operator fun invoke(destination: Destination, period: TravelPeriod): Flow<TripBudget> =
        repository.budget(SavedTrip.idOf(destination, period))
}

/** Spese e tetto del budget di un viaggio. */
class EditTripBudgetUseCase(
    private val repository: BudgetRepository,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    /** Registra una spesa; l'importo deve essere positivo e la valuta un codice ISO 4217. */
    suspend fun addExpense(
        destination: Destination,
        period: TravelPeriod,
        amount: BigDecimal,
        currency: String,
        category: ExpenseCategory,
        date: LocalDate,
        note: String = "",
    ): DataResult<Expense> {
        val code = currency.trim().uppercase(Locale.ROOT)
        if (amount.signum() <= 0 || amount > MAX_AMOUNT || code.length != 3 || !code.all { it in 'A'..'Z' }) {
            return DataResult.Failure(DataError.InvalidQuery(QueryIssue.INVALID_AMOUNT))
        }
        val expense = Expense(
            id = newId(),
            amount = amount.normalized(),
            currency = code,
            category = category,
            date = date,
            note = note.trim().take(MAX_NOTE_LENGTH),
        )
        repository.update(SavedTrip.idOf(destination, period)) { budget -> budget.copy(expenses = budget.expenses + expense) }
        return DataResult.Success(expense, DataOrigin.LOCAL)
    }

    suspend fun removeExpense(destination: Destination, period: TravelPeriod, expenseId: String) {
        repository.update(SavedTrip.idOf(destination, period)) { budget -> budget.copy(expenses = budget.expenses.filterNot { it.id == expenseId }) }
    }

    /** Imposta il tetto di spesa nella valuta dell'utente; `null` o zero lo tolgono. */
    suspend fun setLimit(destination: Destination, period: TravelPeriod, limit: BigDecimal?) {
        val value = limit?.takeIf { it.signum() > 0 }?.min(MAX_AMOUNT)?.normalized()
        repository.update(SavedTrip.idOf(destination, period)) { budget -> budget.copy(limit = value) }
    }

    companion object {
        /** Oltre un milione si tratta di un errore di battitura, non di una spesa di viaggio. */
        val MAX_AMOUNT: BigDecimal = BigDecimal(1_000_000)
        const val MAX_NOTE_LENGTH = 80
    }
}

/** Senza zeri inutili ma senza notazione esponenziale: 250,00 → 250, 12,50 → 12,5. */
private fun BigDecimal.normalized(): BigDecimal = stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }

/**
 * Totali del budget nella valuta dell'utente: le spese in altre valute si convertono con i cambi del
 * giorno (dalla cache, anche senza rete); quelle senza cambio restano fuori dai totali e si segnalano.
 */
class SummarizeBudgetUseCase(private val rates: ExchangeRateRepository) {

    suspend operator fun invoke(budget: TripBudget, currency: String = EURO): BudgetSummary {
        val needsRates = budget.expenses.any { it.currency != currency }
        val exchange = if (needsRates) (rates.latestRates(currency) as? DataResult.Success)?.data else null
        val converted = mutableMapOf<String, BigDecimal>()
        val unconverted = mutableListOf<Expense>()
        budget.expenses.forEach { expense ->
            val value = if (expense.currency == currency) expense.amount else exchange?.convert(expense.amount, expense.currency, currency)
            if (value == null) unconverted += expense else converted[expense.id] = value.setScale(2, RoundingMode.HALF_UP)
        }
        val byCategory = budget.expenses
            .filter { it.id in converted }
            .groupBy { it.category }
            .mapValues { (_, expenses) -> expenses.sumOf { converted.getValue(it.id) } }
        return BudgetSummary(
            currency = currency,
            total = converted.values.fold(BigDecimal.ZERO.setScale(2), BigDecimal::add),
            byCategory = byCategory,
            limit = budget.limit,
            converted = converted,
            unconverted = unconverted,
        )
    }
}
