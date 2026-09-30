package com.partimo.domain.model.dining

import com.partimo.domain.model.GeoPoint

/** Fascia di prezzo sulla scala 0–4 usata dai principali provider (es. Google Places). */
enum class PriceLevel(val level: Int) {
    FREE(0),
    INEXPENSIVE(1),
    MODERATE(2),
    EXPENSIVE(3),
    VERY_EXPENSIVE(4),
    ;

    companion object {
        fun fromLevel(level: Int): PriceLevel? = entries.firstOrNull { it.level == level }
    }
}

data class Restaurant(
    val id: String,
    val name: String,
    val priceLevel: PriceLevel?,
    /** Valutazione media su scala 1–5. */
    val rating: Double?,
    val reviewCount: Int? = null,
    val cuisine: String? = null,
    val address: String? = null,
    val location: GeoPoint? = null,
    val photoUrl: String? = null,
    val isOpenNow: Boolean? = null,
    val mapsUrl: String? = null,
)

/**
 * Criteri per la "ristorazione economica di qualità".
 *
 * Valori predefiniti: fascia di prezzo 1–2 e valutazione minima 4,3.
 * I locali senza fascia di prezzo o valutazione vengono esclusi, perché il vincolo non è verificabile.
 */
data class BudgetDiningCriteria(
    val priceLevels: IntRange = DEFAULT_PRICE_LEVELS,
    val minRating: Double = DEFAULT_MIN_RATING,
    /** Numero minimo di recensioni (0 = nessun vincolo): utile contro punteggi poco affidabili. */
    val minReviewCount: Int = 0,
    val openNowOnly: Boolean = false,
) {
    init {
        require(priceLevels.first >= 0 && priceLevels.last <= 4) { "Fascia di prezzo fuori scala: $priceLevels" }
        require(minRating in 0.0..5.0) { "Valutazione minima fuori scala: $minRating" }
    }

    fun matches(restaurant: Restaurant): Boolean {
        val price = restaurant.priceLevel ?: return false
        val rating = restaurant.rating ?: return false
        return price.level in priceLevels &&
            rating >= minRating &&
            (restaurant.reviewCount ?: 0) >= minReviewCount &&
            (!openNowOnly || restaurant.isOpenNow == true)
    }

    companion object {
        val DEFAULT_PRICE_LEVELS = 1..2
        const val DEFAULT_MIN_RATING = 4.3
    }
}

/**
 * Richiesta al provider. I vincoli sono "suggerimenti" per ridurre il payload: il caso d'uso
 * riapplica sempre [BudgetDiningCriteria] sul risultato, qualunque sia il provider.
 */
data class RestaurantSearchQuery(
    val location: GeoPoint,
    val radiusMeters: Int = 1_500,
    val keyword: String? = null,
    val priceLevels: Set<PriceLevel> = emptySet(),
    val minRating: Double? = null,
    val openNow: Boolean = false,
    /** Nome della zona (es. la città), usato per rendere più precise le ricerche testuali. */
    val areaName: String? = null,
)
