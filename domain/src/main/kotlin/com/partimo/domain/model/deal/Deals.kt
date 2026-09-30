package com.partimo.domain.model.deal

import com.partimo.domain.model.Destination
import com.partimo.domain.model.Money
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.place.DeparturePoint
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/** Prezzo osservato durante un controllo delle offerte. */
data class PricePoint(val price: Money, val observedAt: Instant)

/**
 * Viaggio seguito dall'utente: PartiMo ne controlla periodicamente i prezzi e avvisa quando voli o
 * alloggi diventano davvero convenienti rispetto al solito.
 */
data class PriceWatch(
    val departure: DeparturePoint,
    val destination: Destination,
    val period: TravelPeriod,
    val createdAt: Instant,
    /** Prezzo del volo più economico a ogni controllo, dal più vecchio al più recente. */
    val flightPrices: List<PricePoint> = emptyList(),
    /** Prezzo a notte dell'alloggio ben recensito più economico a ogni controllo. */
    val stayPrices: List<PricePoint> = emptyList(),
    /** Ultimo prezzo già notificato: evita di segnalare due volte lo stesso affare. */
    val lastNotifiedFlight: Money? = null,
    val lastNotifiedStay: Money? = null,
) {
    val id: String get() = idFor(departure.airport.iata, destination, period)

    companion object {
        /** Un avviso per combinazione partenza + meta + periodo. */
        fun idFor(departureIata: String, destination: Destination, period: TravelPeriod): String =
            listOf(departureIata, destination.airportIata, destination.name, period.key).joinToString("|")
    }
}

enum class DealKind { FLIGHT, STAY }

/** Offerta davvero conveniente trovata durante un controllo. */
data class Deal(
    val kind: DealKind,
    /** Compagnia aerea o nome della struttura. */
    val title: String,
    /** Prezzo attuale: totale andata e ritorno per i voli, a notte per gli alloggi. */
    val price: Money,
    /** Prezzo abituale di riferimento, ricavato dai controlli precedenti. */
    val usualPrice: Money,
    val reviewScore: Double? = null,
) {
    /** Sconto rispetto al prezzo abituale, in punti percentuali interi (es. 20 = −20%). */
    val discountPercent: Int
        get() = if (usualPrice.amount.signum() == 0) {
            0
        } else {
            BigDecimal.ONE.subtract(price.amount.divide(usualPrice.amount, 4, RoundingMode.HALF_UP))
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .toInt()
        }
}

/** Avviso da notificare: un viaggio seguito con le offerte convenienti appena trovate. */
data class DealAlert(val watch: PriceWatch, val deals: List<Deal>)

/** Variazione del prezzo migliore tra due caricamenti consecutivi (es. dopo "Aggiorna"). */
data class PriceChange(val previous: Money, val current: Money) {

    /** Differenza con segno (negativa se il prezzo è sceso); zero se le valute non sono confrontabili. */
    val difference: BigDecimal
        get() = if (previous.currencyCode == current.currencyCode) current.amount.subtract(previous.amount) else BigDecimal.ZERO

    val isDrop: Boolean get() = difference.signum() < 0
    val isRise: Boolean get() = difference.signum() > 0
}
