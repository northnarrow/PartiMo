package com.partimo.domain.model.guide

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant

/** Lato della strada su cui si guida. */
enum class DrivingSide { LEFT, RIGHT }

/**
 * Prese e corrente elettrica. [plugTypes] sono le lettere della classificazione IEC (es. "C", "F");
 * [voltage] e [frequency] sono testi perché in alcuni paesi convivono più valori (es. "127/220", "50/60").
 */
data class PowerInfo(val plugTypes: List<String>, val voltage: String, val frequency: String) {

    /** Le spine italiane ed europee (tipo C, F o L) entrano senza adattatore. */
    val fitsItalianPlugs: Boolean get() = plugTypes.any { it in ITALIAN_PLUGS }

    /** Tensione intorno ai 100–127 V: conviene controllare che i caricatori siano 100–240 V. */
    val lowVoltage: Boolean
        get() = voltage.split('/').mapNotNull { it.trim().toIntOrNull() }.let { values -> values.isNotEmpty() && values.all { it < 200 } }

    private companion object {
        val ITALIAN_PLUGS = setOf("C", "F", "L")
    }
}

/** Numeri di emergenza: [general] è il numero unico (es. 112); gli altri solo se diversi o in aggiunta. */
data class EmergencyNumbers(
    val general: String? = null,
    val police: String? = null,
    val ambulance: String? = null,
    val fire: String? = null,
) {
    val isEmpty: Boolean get() = general == null && police == null && ambulance == null && fire == null
}

/** Informazioni pratiche su un paese: lingua, valuta, prefisso, guida, prese e numeri utili. */
data class CountryInfo(
    /** ISO 3166-1 alpha-2 (es. "AT"). */
    val countryCode: String,
    /** ISO 3166-1 alpha-3 (es. "AUT"), usato dalla scheda di Viaggiare Sicuri. */
    val countryCode3: String?,
    /** Nome nella lingua dell'app (es. "Austria"). */
    val name: String,
    /** ISO 4217 (es. "EUR"). */
    val currencyCode: String?,
    val currencyName: String?,
    val currencySymbol: String?,
    /** Lingue principali, nella lingua dell'app (es. "tedesco"). */
    val languages: List<String> = emptyList(),
    /** Le stesse lingue come codici ISO 639-1 (es. "de"), la prima è la più diffusa: servono al traduttore. */
    val languageCodes: List<String> = emptyList(),
    /** Prefisso telefonico internazionale (es. "+43"). */
    val callingCode: String? = null,
    val drivingSide: DrivingSide? = null,
    val power: PowerInfo? = null,
    val emergency: EmergencyNumbers? = null,
) {
    val usesEuro: Boolean get() = currencyCode == EURO
}

const val EURO = "EUR"

/** Tassi di cambio rispetto a [base]: quante unità di ogni valuta valgono un'unità di [base]. */
data class ExchangeRates(
    val base: String,
    val rates: Map<String, BigDecimal>,
    val updatedAt: Instant? = null,
) {
    /** Valore di un'unità di [from] espresso in [to]; `null` se manca una delle due valute. */
    fun rate(from: String, to: String): BigDecimal? {
        if (from == to) return BigDecimal.ONE
        val fromRate = if (from == base) BigDecimal.ONE else rates[from] ?: return null
        val toRate = if (to == base) BigDecimal.ONE else rates[to] ?: return null
        if (fromRate.signum() <= 0) return null
        return toRate.divide(fromRate, MathContext.DECIMAL64)
    }

    fun convert(amount: BigDecimal, from: String, to: String): BigDecimal? =
        rate(from, to)?.let { amount.multiply(it).setScale(SCALE, RoundingMode.HALF_UP) }

    private companion object {
        const val SCALE = 4
    }
}

/** Cambio tra due valute in un dato momento. */
data class ExchangeRate(val from: String, val to: String, val rate: BigDecimal, val updatedAt: Instant?) {
    fun convert(amount: BigDecimal): BigDecimal = amount.multiply(rate)

    fun inverse(): ExchangeRate = ExchangeRate(to, from, BigDecimal.ONE.divide(rate, MathContext.DECIMAL64), updatedAt)
}

/** Capitolo di una guida di viaggio (es. "Come spostarsi"), con i suoi paragrafi e le sottosezioni (es. "In metropolitana"). */
data class GuideSection(
    val title: String,
    val paragraphs: List<String>,
    val subsections: List<GuideSection> = emptyList(),
) {
    val isEmpty: Boolean get() = paragraphs.isEmpty() && subsections.all { it.isEmpty }
}

/** Guida di viaggio della città (es. Wikivoyage): introduzione e capitoli utili a chi parte. */
data class TravelGuide(
    val title: String,
    /** Lingua del testo (ISO 639-1): può essere l'inglese se manca la guida nella lingua dell'app. */
    val language: String,
    val url: String,
    val introduction: List<String>,
    val sections: List<GuideSection>,
)
