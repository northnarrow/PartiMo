package com.partimo.domain.model

import com.partimo.domain.model.flight.FlexibleDates
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Periodo del viaggio scelto dall'utente: partenza nei prossimi giorni (last minute), uno qualunque dei
 * dodici mesi successivi oppure date precise di andata e ritorno. Per i primi due le date concrete si
 * ricavano dalla data odierna.
 */
sealed interface TravelPeriod {

    /**
     * Chiave stabile per salvare il periodo (rotte di navigazione, avvisi):
     * "NEXT_DAYS", "2026-12" o "2026-12-10_2026-12-14".
     */
    val key: String

    /** Partenza domani: offerte last minute; i consigli tengono conto anche del meteo attuale. */
    data object NextDays : TravelPeriod {
        override val key: String = "NEXT_DAYS"
    }

    /** Viaggio in un mese preciso. */
    data class InMonth(val month: YearMonth) : TravelPeriod {
        override val key: String get() = month.toString()
    }

    /** Date di andata e ritorno scelte dall'utente. */
    data class Dates(val departure: LocalDate, val returning: LocalDate) : TravelPeriod {
        init {
            require(!returning.isBefore(departure)) { "Il ritorno precede l'andata" }
        }

        override val key: String get() = "${departure}_$returning"

        /** Notti del viaggio. */
        val nights: Long get() = ChronoUnit.DAYS.between(departure, returning)
    }

    /** Nei mesi futuri si parte a metà mese; nel mese corrente, se quel giorno è passato, domani. */
    fun departureDate(today: LocalDate): LocalDate {
        val earliest = today.plusDays(1)
        return when (this) {
            NextDays -> earliest
            is InMonth -> month.atDay(PREFERRED_DEPARTURE_DAY).takeUnless { it.isBefore(earliest) } ?: earliest
            is Dates -> departure
        }
    }

    fun returnDate(today: LocalDate): LocalDate = when (this) {
        is Dates -> returning
        else -> departureDate(today).plusDays(TRIP_LENGTH_DAYS)
    }

    /**
     * Date che vanno bene per i voli quando i prezzi arrivano da molte date (Aviasales): la prossima
     * settimana o tutto il mese (da domani se è quello in corso) per soggiorni da un fine settimana a una
     * settimana; con le date scelte dall'utente, i giorni vicini con un soggiorno simile, dopo quelli esatti.
     */
    fun flexibleDates(today: LocalDate): FlexibleDates {
        val earliest = today.plusDays(1)
        return when (this) {
            NextDays -> FlexibleDates(earliest..earliest.plusDays(NEXT_DAYS_WINDOW_DAYS - 1), FLEXIBLE_STAY_NIGHTS)
            is InMonth -> FlexibleDates(maxOf(month.atDay(1), earliest)..month.atEndOfMonth(), FLEXIBLE_STAY_NIGHTS)
            is Dates -> FlexibleDates(
                departures = maxOf(departure.minusDays(NEARBY_DAYS), earliest)..departure.plusDays(NEARBY_DAYS),
                stayNights = maxOf(0L, nights - NEARBY_NIGHTS)..(nights + NEARBY_NIGHTS),
                exactDatesFirst = true,
            )
        }
    }

    /**
     * `true` quando il viaggio è ormai passato (il mese, o la data di ritorno): per un avviso non ha più
     * senso controllare i prezzi.
     */
    fun isOver(today: LocalDate): Boolean = when (this) {
        NextDays -> false
        is InMonth -> month.isBefore(YearMonth.from(today))
        is Dates -> returning.isBefore(today)
    }

    /** `true` se si parte entro domani o si è già in viaggio: servono informazioni "di adesso" (aperto ora). */
    fun isImminent(today: LocalDate): Boolean = when (this) {
        NextDays -> true
        is InMonth -> false
        is Dates -> !departure.isAfter(today.plusDays(1)) && !returning.isBefore(today)
    }

    companion object {
        const val TRIP_LENGTH_DAYS = 4L

        /** Giorni di partenza considerati per "prossimi giorni" quando le date sono flessibili. */
        const val NEXT_DAYS_WINDOW_DAYS = 7L

        /** Soggiorni accettati con le date flessibili: da un fine settimana a una settimana. */
        val FLEXIBLE_STAY_NIGHTS: LongRange = 2L..7L

        /** Con le date scelte dall'utente si guardano anche le partenze fino a tre giorni prima o dopo... */
        const val NEARBY_DAYS = 3L

        /** ...con al massimo due notti in più o in meno. */
        const val NEARBY_NIGHTS = 2L

        /** Viaggio più lungo che si può scegliere con le date esatte (l'itinerario dell'IA resta gestibile). */
        const val MAX_NIGHTS = 14L

        /** Giorno di partenza proposto nei mesi futuri. */
        const val PREFERRED_DEPARTURE_DAY = 10

        const val SELECTABLE_MONTHS = 12

        /**
         * "Prossimi giorni" seguito da dodici mesi consecutivi, così ogni mese dell'anno è selezionabile.
         * Il mese corrente compare solo se la partenza di metà mese non è già passata.
         */
        fun selectable(today: LocalDate): List<TravelPeriod> {
            val current = YearMonth.from(today)
            val first = if (today.plusDays(1).isAfter(current.atDay(PREFERRED_DEPARTURE_DAY))) current.plusMonths(1) else current
            return listOf(NextDays) + (0 until SELECTABLE_MONTHS).map { InMonth(first.plusMonths(it.toLong())) }
        }

        /** Ricostruisce un periodo dalla sua [key]; `null` se la chiave non è valida. */
        fun fromKey(key: String): TravelPeriod? = when {
            key == NextDays.key -> NextDays
            '_' in key -> runCatching {
                val (departure, returning) = key.split('_', limit = 2)
                Dates(LocalDate.parse(departure), LocalDate.parse(returning))
            }.getOrNull()
            else -> runCatching { InMonth(YearMonth.parse(key)) }.getOrNull()
        }
    }
}
