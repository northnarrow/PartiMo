package com.partimo.domain.model.stay

import com.partimo.domain.common.QueryIssue
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.Money
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Parametri di ricerca alloggi attorno a un punto. */
data class AccommodationSearchQuery(
    val location: GeoPoint,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val adults: Int = 1,
    val rooms: Int = 1,
    val radiusKm: Int = 5,
    val currencyCode: String = "EUR",
) {
    val nights: Int get() = ChronoUnit.DAYS.between(checkIn, checkOut).toInt()

    /** Restituisce il primo problema di validazione, oppure `null` se la ricerca è valida. */
    fun validate(today: LocalDate): QueryIssue? = when {
        checkIn.isBefore(today) -> QueryIssue.DATE_IN_THE_PAST
        !checkOut.isAfter(checkIn) -> QueryIssue.INVALID_STAY_DATES
        nights > MAX_NIGHTS -> QueryIssue.STAY_TOO_LONG
        adults !in 1..MAX_GUESTS || rooms !in 1..adults -> QueryIssue.INVALID_TRAVELLER_COUNT
        else -> null
    }

    companion object {
        const val MAX_NIGHTS = 30
        const val MAX_GUESTS = 10
    }
}

/** Offerta di soggiorno con il prezzo della tariffa più conveniente. */
data class AccommodationOffer(
    val id: String,
    val name: String,
    val totalPrice: Money,
    val nights: Int,
    /** Classificazione ufficiale in stelle (1–5). */
    val starRating: Int? = null,
    /** Punteggio medio delle recensioni degli ospiti, scala 0–10. */
    val reviewScore: Double? = null,
    val reviewCount: Int? = null,
    val location: GeoPoint? = null,
    val address: String? = null,
    val photoUrl: String? = null,
    val freeCancellation: Boolean? = null,
) {
    init {
        require(nights >= 1) { "Un soggiorno dura almeno una notte" }
    }

    val pricePerNight: Money get() = totalPrice / nights
}

/** Filtri opzionali applicati alle offerte dopo la ricerca. */
data class AccommodationFilter(
    val maxPricePerNight: BigDecimal? = null,
    val minReviewScore: Double? = null,
    val minStars: Int? = null,
    val freeCancellationOnly: Boolean = false,
) {
    fun matches(offer: AccommodationOffer): Boolean =
        (maxPricePerNight == null || offer.pricePerNight.amount <= maxPricePerNight) &&
            (minReviewScore == null || (offer.reviewScore ?: 0.0) >= minReviewScore) &&
            (minStars == null || (offer.starRating ?: 0) >= minStars) &&
            (!freeCancellationOnly || offer.freeCancellation == true)
}

enum class AccommodationSortOption { BEST_VALUE, CHEAPEST, TOP_RATED }
