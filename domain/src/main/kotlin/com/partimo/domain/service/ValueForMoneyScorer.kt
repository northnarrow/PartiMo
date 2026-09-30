package com.partimo.domain.service

import com.partimo.domain.model.ScoredOffer
import com.partimo.domain.model.flight.FlightOffer
import com.partimo.domain.model.stay.AccommodationOffer

/**
 * Calcola il punteggio "qualità/prezzo" delle offerte di una stessa ricerca.
 *
 * Ogni criterio è normalizzato in [0, 1] rispetto ai risultati (min-max), poi combinato con pesi
 * configurabili. Le offerte devono condividere la stessa valuta: la normalizzazione della valuta
 * è responsabilità dei casi d'uso.
 */
class ValueForMoneyScorer(
    private val flightWeights: FlightWeights = FlightWeights(),
    private val stayWeights: StayWeights = StayWeights(),
) {

    /** Voli: prezzo più basso, durata minore e meno scali = rapporto migliore. */
    data class FlightWeights(val price: Double = 0.55, val duration: Double = 0.30, val stops: Double = 0.15)

    /** Alloggi: prezzo per notte, qualità percepita (recensioni) e categoria in stelle. */
    data class StayWeights(val price: Double = 0.50, val quality: Double = 0.40, val stars: Double = 0.10)

    fun scoreFlights(offers: List<FlightOffer>): List<ScoredOffer<FlightOffer>> {
        if (offers.isEmpty()) return emptyList()
        val prices = offers.map { it.totalPrice.amount.toDouble() }
        val durations = offers.map { it.totalDuration.toMinutes().toDouble() }
        val worstStops = offers.maxOf { it.maxStops }
        val weights = flightWeights
        val totalWeight = weights.price + weights.duration + weights.stops

        return offers.mapIndexed { index, offer ->
            val priceScore = invertedNormalization(prices[index], prices)
            val durationScore = invertedNormalization(durations[index], durations)
            val stopsScore = if (worstStops == 0) 1.0 else 1.0 - offer.maxStops.toDouble() / worstStops
            val score = (weights.price * priceScore + weights.duration * durationScore + weights.stops * stopsScore) / totalWeight
            ScoredOffer(offer, score)
        }
    }

    fun scoreStays(offers: List<AccommodationOffer>): List<ScoredOffer<AccommodationOffer>> {
        if (offers.isEmpty()) return emptyList()
        val nightlyPrices = offers.map { it.pricePerNight.amount.toDouble() }
        // Media dei punteggi della ricerca: è il "prior" della media bayesiana.
        val meanReviewScore = offers.mapNotNull { it.reviewScore }.average().takeUnless { it.isNaN() } ?: NEUTRAL_REVIEW_SCORE
        val weights = stayWeights
        val totalWeight = weights.price + weights.quality + weights.stars

        return offers.mapIndexed { index, offer ->
            val priceScore = invertedNormalization(nightlyPrices[index], nightlyPrices)
            val qualityScore = bayesianReviewScore(offer, meanReviewScore) / MAX_REVIEW_SCORE
            val starsScore = (offer.starRating ?: NEUTRAL_STARS).coerceIn(0, MAX_STARS).toDouble() / MAX_STARS
            val score = (weights.price * priceScore + weights.quality * qualityScore + weights.stars * starsScore) / totalWeight
            ScoredOffer(offer, score)
        }
    }

    /**
     * Media bayesiana: un 9,5 con 3 recensioni vale meno di un 9,0 con 800 recensioni.
     * Con poche recensioni il punteggio viene "tirato" verso la media della ricerca.
     */
    private fun bayesianReviewScore(offer: AccommodationOffer, prior: Double): Double {
        val score = offer.reviewScore ?: return prior
        val votes = (offer.reviewCount ?: DEFAULT_REVIEW_COUNT).toDouble()
        return (votes / (votes + CONFIDENCE_REVIEWS)) * score + (CONFIDENCE_REVIEWS / (votes + CONFIDENCE_REVIEWS)) * prior
    }

    /** Valore minimo del gruppo → 1, massimo → 0. Se tutti i valori coincidono restituisce 1. */
    private fun invertedNormalization(value: Double, all: List<Double>): Double {
        val min = all.min()
        val max = all.max()
        return if (max - min < EPSILON) 1.0 else (max - value) / (max - min)
    }

    private companion object {
        const val EPSILON = 1e-9
        const val MAX_REVIEW_SCORE = 10.0
        const val NEUTRAL_REVIEW_SCORE = 7.0
        const val MAX_STARS = 5
        const val NEUTRAL_STARS = 3

        /** Recensioni necessarie perché il punteggio proprio pesi quanto la media della ricerca. */
        const val CONFIDENCE_REVIEWS = 25.0

        /** Alcuni provider non espongono il numero di recensioni: si assume una confidenza media. */
        const val DEFAULT_REVIEW_COUNT = 25
    }
}
