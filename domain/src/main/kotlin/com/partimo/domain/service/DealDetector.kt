package com.partimo.domain.service

import com.partimo.domain.model.Money
import com.partimo.domain.model.deal.PricePoint
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Stabilisce quando un prezzo è "davvero conveniente" per un viaggio seguito.
 *
 * Il riferimento è il prezzo abituale: la mediana degli ultimi [historySize] controlli, robusta
 * rispetto a singoli picchi. Un prezzo è un affare se è almeno [minDiscount] sotto il prezzo
 * abituale; per non ripetere lo stesso avviso deve anche essere almeno [renotifyStep] sotto
 * l'ultimo prezzo già notificato. Senza storico non esiste un riferimento affidabile: nessun avviso.
 */
class DealDetector(
    private val minDiscount: Double = DEFAULT_MIN_DISCOUNT,
    private val historySize: Int = DEFAULT_HISTORY_SIZE,
    private val renotifyStep: Double = DEFAULT_RENOTIFY_STEP,
) {

    /** Esito della valutazione di un prezzo. */
    data class Assessment(
        /** Prezzo abituale, `null` se non c'è ancora storico confrontabile. */
        val usualPrice: Money?,
        /** Prezzo sotto la soglia di convenienza (indipendentemente dagli avvisi già inviati). */
        val isBelowThreshold: Boolean,
        /** Affare da notificare adesso. */
        val shouldNotify: Boolean,
    )

    fun assess(current: Money, history: List<PricePoint>, lastNotified: Money?): Assessment {
        val usual = usualPrice(history, current.currencyCode)
            ?: return Assessment(usualPrice = null, isBelowThreshold = false, shouldNotify = false)
        val threshold = usual.amount.multiply(BigDecimal.valueOf(1 - minDiscount))
        val belowThreshold = current.amount <= threshold
        val alreadyNotified = lastNotified != null &&
            lastNotified.currencyCode == current.currencyCode &&
            current.amount > lastNotified.amount.multiply(BigDecimal.valueOf(1 - renotifyStep))
        return Assessment(usualPrice = usual, isBelowThreshold = belowThreshold, shouldNotify = belowThreshold && !alreadyNotified)
    }

    /** Mediana degli ultimi controlli nella stessa valuta del prezzo attuale. */
    fun usualPrice(history: List<PricePoint>, currencyCode: String): Money? {
        val amounts = history
            .takeLast(historySize)
            .filter { it.price.currencyCode == currencyCode }
            .map { it.price.amount }
            .sorted()
        if (amounts.isEmpty()) return null
        val middle = amounts.size / 2
        val median = if (amounts.size % 2 == 1) {
            amounts[middle]
        } else {
            amounts[middle - 1].add(amounts[middle]).divide(BigDecimal(2), 2, RoundingMode.HALF_UP)
        }
        return Money.of(median, currencyCode)
    }

    companion object {
        /** Almeno il 15% in meno del solito. */
        const val DEFAULT_MIN_DISCOUNT = 0.15
        const val DEFAULT_HISTORY_SIZE = 10

        /** Un nuovo avviso per lo stesso viaggio richiede un ulteriore calo del 5%. */
        const val DEFAULT_RENOTIFY_STEP = 0.05
    }
}
