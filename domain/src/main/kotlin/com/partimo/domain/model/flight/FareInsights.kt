package com.partimo.domain.model.flight

import com.partimo.domain.model.Money
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.place.CityPlace
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Tariffa più bassa di andata e ritorno trovata per un mese o un giorno di partenza, per una persona: prezzi
 * raccolti dalle ricerche dei viaggiatori (Aviasales), da verificare prima di prenotare.
 */
data class FareSnapshot(
    val price: Money,
    val departureDate: LocalDate,
    val returnDate: LocalDate?,
    /** Scali della tratta peggiore. */
    val stops: Int = 0,
    val carrierIata: String? = null,
) {
    /** Notti tra andata e ritorno; `null` per la sola andata. */
    val stayNights: Long? get() = returnDate?.let { ChronoUnit.DAYS.between(departureDate, it) }
}

/** Fascia di prezzo di un giorno o di un mese rispetto agli altri: per colorare il calendario. */
enum class FareLevel {
    LOW,
    MEDIUM,
    HIGH,
    ;

    companion object {
        /**
         * Fasce dei prezzi in terzi: il terzo più economico è [LOW], quello più caro [HIGH]. Con meno di tre
         * prezzi non c'è un confronto: è tutto [MEDIUM] tranne il più basso.
         */
        fun <K> levels(fares: Map<K, FareSnapshot>): Map<K, FareLevel> {
            if (fares.isEmpty()) return emptyMap()
            val sorted = fares.values.map { it.price.amount }.sorted()
            val cheapest = sorted.first()
            if (sorted.size < MIN_FOR_THIRDS) return fares.mapValues { (_, fare) -> if (fare.price.amount == cheapest) LOW else MEDIUM }
            val lowLimit = sorted[(sorted.size - 1) / 3]
            val highLimit = sorted[(sorted.size - 1) * 2 / 3]
            return fares.mapValues { (_, fare) ->
                when {
                    fare.price.amount <= lowLimit -> LOW
                    fare.price.amount > highLimit -> HIGH
                    else -> MEDIUM
                }
            }
        }

        private const val MIN_FOR_THIRDS = 3
    }
}

/** Calendario dei prezzi di un mese: la tariffa più bassa per ogni giorno di partenza che ne ha una. */
data class PriceCalendar(
    val month: YearMonth,
    /** Soggiorni considerati: da un fine settimana a una settimana, o le notti delle date scelte. */
    val stayNights: LongRange,
    val fares: Map<LocalDate, FareSnapshot>,
) {
    val cheapest: FareSnapshot? get() = fares.values.minByOrNull { it.price.amount }

    val levels: Map<LocalDate, FareLevel> by lazy { FareLevel.levels(fares) }
}

/**
 * Ricerca «Ovunque»: le mete più economiche da una città, per le partenze in [departures] con soggiorni di
 * [stayNights] notti oppure, con [exactDates], proprio in quei giorni.
 */
data class AnywhereQuery(
    val originIata: String,
    val departures: ClosedRange<LocalDate>,
    val stayNights: LongRange,
    val exactDates: Pair<LocalDate, LocalDate>? = null,
) {
    /** `true` se un volo con queste date risponde alla ricerca. */
    fun matches(departure: LocalDate, returning: LocalDate?): Boolean {
        if (returning == null) return false
        exactDates?.let { (from, to) -> return departure == from && returning == to }
        return departure in departures && ChronoUnit.DAYS.between(departure, returning) in stayNights
    }

    companion object {
        /** Le stesse date della dashboard: la settimana o il mese con soggiorni flessibili, oppure le date scelte. */
        fun of(originIata: String, period: TravelPeriod, today: LocalDate): AnywhereQuery = when (period) {
            is TravelPeriod.Dates -> AnywhereQuery(
                originIata = originIata,
                departures = period.departure..period.departure,
                stayNights = period.nights..period.nights,
                exactDates = period.departure to period.returning,
            )
            else -> period.flexibleDates(today).let { dates -> AnywhereQuery(originIata, dates.departures, dates.stayNights) }
        }
    }
}

/** Meta conveniente trovata da «Ovunque»: la città, il volo più economico per andarci e la sua pagina. */
data class CheapDestination(
    val city: CityPlace,
    /** Codice della città per i voli (es. "LON" per tutti gli aeroporti di Londra). */
    val cityCode: String,
    /** Aeroporto d'arrivo del volo più economico. */
    val airportIata: String,
    val fare: FareSnapshot,
    val bookingUrl: String? = null,
)
