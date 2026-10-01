package com.partimo.domain.model.flight

import com.partimo.domain.common.QueryIssue
import com.partimo.domain.model.Money
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class CabinClass { ECONOMY, PREMIUM_ECONOMY, BUSINESS, FIRST }

/** Parametri di ricerca voli (andata o andata/ritorno). */
data class FlightSearchQuery(
    val originIata: String,
    val destinationIata: String,
    val departureDate: LocalDate,
    val returnDate: LocalDate? = null,
    val adults: Int = 1,
    val cabinClass: CabinClass = CabinClass.ECONOMY,
    /** Numero massimo di scali per tratta richiesto al provider. */
    val maxConnections: Int = 1,
    /** Valuta preferita: il ranking confronta solo prezzi nella stessa valuta. */
    val currencyCode: String = "EUR",
    /**
     * Date flessibili: oltre alle date indicate vanno bene le partenze in questa finestra, con un soggiorno
     * di [FLEXIBLE_STAY_NIGHTS] notti. Le usano i provider che raccolgono i prezzi di molte date
     * (Aviasales); quelli che cercano in tempo reale restano sulle date indicate. `null` = solo quelle.
     */
    val flexibleDepartures: ClosedRange<LocalDate>? = null,
) {
    /** Restituisce il primo problema di validazione, oppure `null` se la ricerca è valida. */
    fun validate(today: LocalDate): QueryIssue? = when {
        !originIata.isIataCode() || !destinationIata.isIataCode() -> QueryIssue.INVALID_AIRPORT_CODE
        originIata.equals(destinationIata, ignoreCase = true) -> QueryIssue.SAME_ORIGIN_AND_DESTINATION
        departureDate.isBefore(today) -> QueryIssue.DATE_IN_THE_PAST
        returnDate != null && returnDate.isBefore(departureDate) -> QueryIssue.RETURN_BEFORE_DEPARTURE
        adults !in 1..MAX_PASSENGERS -> QueryIssue.INVALID_TRAVELLER_COUNT
        else -> null
    }

    /** `true` se un volo che parte e torna in questi giorni risponde alla ricerca (vedi [flexibleDepartures]). */
    fun matchesDates(departure: LocalDate, returning: LocalDate?): Boolean {
        val window = flexibleDepartures ?: return departure == departureDate && returning == returnDate
        if (departure !in window) return false
        if (returnDate == null || returning == null) return returnDate == null && returning == null
        return ChronoUnit.DAYS.between(departure, returning) in FLEXIBLE_STAY_NIGHTS
    }

    private fun String.isIataCode(): Boolean = length == 3 && all(Char::isLetter)

    companion object {
        const val MAX_PASSENGERS = 9

        /** Soggiorni accettati con le date flessibili: da un fine settimana a una settimana. */
        val FLEXIBLE_STAY_NIGHTS: LongRange = 2L..7L
    }
}

/** Tratta (andata o ritorno), eventualmente composta da più segmenti con scalo. */
data class FlightSlice(
    val originIata: String,
    val destinationIata: String,
    /** Orari locali degli aeroporti di partenza e arrivo. */
    val departureTime: LocalDateTime,
    val arrivalTime: LocalDateTime,
    val duration: Duration,
    val stops: Int,
    val flightNumbers: List<String> = emptyList(),
) {
    val isDirect: Boolean get() = stops == 0
}

/** Offerta di volo prenotabile restituita dal provider. */
data class FlightOffer(
    val id: String,
    val carrierName: String,
    val carrierIata: String? = null,
    val carrierLogoUrl: String? = null,
    val totalPrice: Money,
    val slices: List<FlightSlice>,
    val expiresAt: Instant? = null,
    val co2EmissionsKg: Int? = null,
    val refundable: Boolean? = null,
    /** Pagina del provider con questo volo, per verificarne il prezzo e prenotarlo; `null` se non c'è. */
    val bookingUrl: String? = null,
    /**
     * Giorno in cui il prezzo è stato trovato, per le tariffe raccolte dalle ricerche dei viaggiatori
     * (Aviasales): nel frattempo può essere cambiato. `null` per le offerte in tempo reale e per le stime.
     */
    val priceFoundOn: LocalDate? = null,
) {
    init {
        require(slices.isNotEmpty()) { "Un'offerta deve contenere almeno una tratta" }
    }

    val outbound: FlightSlice get() = slices.first()
    val inbound: FlightSlice? get() = slices.getOrNull(1)

    /** Tempo complessivo di volo di tutte le tratte. */
    val totalDuration: Duration get() = slices.fold(Duration.ZERO) { total, slice -> total + slice.duration }

    /** Numero di scali della tratta peggiore. */
    val maxStops: Int get() = slices.maxOf { it.stops }

    /** Notti tra la partenza dell'andata e quella del ritorno; `null` per i voli di sola andata. */
    val stayNights: Int?
        get() = inbound?.let { ChronoUnit.DAYS.between(outbound.departureTime.toLocalDate(), it.departureTime.toLocalDate()).toInt() }
}

/** Filtri opzionali applicati alle offerte dopo la ricerca. */
data class FlightFilter(
    val maxPrice: BigDecimal? = null,
    val maxStops: Int? = null,
    val maxTotalDuration: Duration? = null,
    val refundableOnly: Boolean = false,
) {
    fun matches(offer: FlightOffer): Boolean =
        (maxPrice == null || offer.totalPrice.amount <= maxPrice) &&
            (maxStops == null || offer.maxStops <= maxStops) &&
            (maxTotalDuration == null || offer.totalDuration <= maxTotalDuration) &&
            (!refundableOnly || offer.refundable == true)
}

enum class FlightSortOption { BEST_VALUE, CHEAPEST, FASTEST }

/** Da dove arrivano i prezzi dei voli: cambia come l'app li presenta. */
enum class FlightPriceSource {
    /** Offerte prenotabili cercate in tempo reale sulle date del viaggio (Duffel). */
    LIVE_OFFERS,

    /**
     * Prezzi trovati negli ultimi giorni dalle ricerche dei viaggiatori (Aviasales, tramite Travelpayouts):
     * reali ma da verificare, su date flessibili nel periodo del viaggio.
     */
    RECENT_SEARCHES,

    /** Stime calcolate sul telefono, senza chiavi API. */
    ESTIMATES,
}
