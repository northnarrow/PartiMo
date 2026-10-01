package com.partimo.domain.model.budget

import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

/** Categorie delle spese di viaggio. */
enum class ExpenseCategory { TRANSPORT, LODGING, FOOD, ACTIVITIES, SHOPPING, OTHER }

/** Spesa del viaggio, nella valuta in cui è stata pagata. */
data class Expense(
    val id: String,
    val amount: BigDecimal,
    /** ISO 4217 (es. "CZK"). */
    val currency: String,
    val category: ExpenseCategory,
    val date: LocalDate,
    val note: String = "",
) {
    init {
        require(amount.signum() > 0) { "Importo non valido: $amount" }
        require(currency.length == 3 && currency.all { it in 'A'..'Z' }) { "Valuta non valida: $currency" }
    }
}

/** Budget di un viaggio: il tetto di spesa (nella valuta dell'utente) e le spese registrate. */
data class TripBudget(
    /** Viaggio a cui appartiene, come [com.partimo.domain.model.saved.SavedTrip.idOf]. */
    val tripId: String,
    val limit: BigDecimal? = null,
    val expenses: List<Expense> = emptyList(),
)

/** Totali del budget nella valuta dell'utente. */
data class BudgetSummary(
    val currency: String,
    val total: BigDecimal,
    val byCategory: Map<ExpenseCategory, BigDecimal>,
    val limit: BigDecimal? = null,
    /** Importo di ogni spesa nella valuta dell'utente, per id. */
    val converted: Map<String, BigDecimal> = emptyMap(),
    /** Spese che non si sono potute convertire (cambio non disponibile): restano fuori dai totali. */
    val unconverted: List<Expense> = emptyList(),
) {
    val remaining: BigDecimal? get() = limit?.subtract(total)

    val isOverBudget: Boolean get() = limit != null && total > limit

    /** Quota del budget già spesa, tra 0 e 1 (oltre il budget resta 1); `null` senza budget. */
    val usedShare: Float?
        get() = limit?.takeIf { it.signum() > 0 }?.let { total.divide(it, MathContext.DECIMAL64).toFloat().coerceIn(0f, 1f) }
}
