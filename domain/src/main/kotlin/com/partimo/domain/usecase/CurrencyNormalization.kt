package com.partimo.domain.usecase

/**
 * Alcuni provider (es. Duffel) restituiscono i prezzi nella valuta del vettore o della struttura.
 * Poiché punteggi e ordinamenti richiedono importi confrontabili, si mantengono solo le offerte
 * nella valuta richiesta; se non ce ne sono, quelle nella valuta più frequente.
 * Un servizio di cambio valuta potrà sostituire questa strategia senza toccare i casi d'uso.
 */
internal fun <T> List<T>.inSingleCurrency(preferred: String, currencyOf: (T) -> String): List<T> {
    if (isEmpty()) return this
    val byCurrency = groupBy { currencyOf(it).uppercase() }
    return byCurrency[preferred.uppercase()] ?: byCurrency.maxBy { (_, offers) -> offers.size }.value
}
