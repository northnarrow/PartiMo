package com.partimo.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Importo monetario in una valuta ISO 4217.
 *
 * Usa [BigDecimal] per evitare gli errori di arrotondamento dei Double. Le istanze create con
 * i factory [of] sono normalizzate a due decimali, così l'uguaglianza è coerente.
 */
data class Money(val amount: BigDecimal, val currencyCode: String) {

    init {
        require(amount.signum() >= 0) { "Importo negativo: $amount" }
        require(currencyCode.length == 3) { "Codice valuta non valido: $currencyCode" }
    }

    /** Divide l'importo, ad esempio per ricavare il prezzo per notte dal totale del soggiorno. */
    operator fun div(divisor: Int): Money {
        require(divisor > 0) { "Divisore non valido: $divisor" }
        return copy(amount = amount.divide(BigDecimal(divisor), 2, RoundingMode.HALF_UP))
    }

    companion object {
        fun of(amount: BigDecimal, currencyCode: String): Money =
            Money(amount.setScale(2, RoundingMode.HALF_UP), currencyCode.uppercase())

        fun of(amount: String, currencyCode: String): Money = of(BigDecimal(amount), currencyCode)

        fun of(amount: Int, currencyCode: String): Money = of(BigDecimal(amount), currencyCode)
    }
}
