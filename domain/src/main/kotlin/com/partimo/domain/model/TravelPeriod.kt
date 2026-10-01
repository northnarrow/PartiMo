package com.partimo.domain.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * Periodo del viaggio scelto dall'utente: partenza nei prossimi giorni (last minute) oppure uno
 * qualunque dei dodici mesi successivi. Le date concrete si ricavano dalla data odierna.
 */
sealed interface TravelPeriod {

    /** Chiave stabile per salvare il periodo (rotte di navigazione, avvisi): "NEXT_DAYS" o "2026-12". */
    val key: String

    /** Partenza domani: offerte last minute; i consigli tengono conto anche del meteo attuale. */
    data object NextDays : TravelPeriod {
        override val key: String = "NEXT_DAYS"
    }

    /** Viaggio in un mese preciso. */
    data class InMonth(val month: YearMonth) : TravelPeriod {
        override val key: String get() = month.toString()
    }

    /** Nei mesi futuri si parte a metà mese; nel mese corrente, se quel giorno è passato, domani. */
    fun departureDate(today: LocalDate): LocalDate {
        val earliest = today.plusDays(1)
        return when (this) {
            NextDays -> earliest
            is InMonth -> month.atDay(PREFERRED_DEPARTURE_DAY).takeUnless { it.isBefore(earliest) } ?: earliest
        }
    }

    fun returnDate(today: LocalDate): LocalDate = departureDate(today).plusDays(TRIP_LENGTH_DAYS)

    /**
     * Partenze che vanno bene per il viaggio quando le date sono flessibili (prezzi dei voli su più giorni):
     * la prossima settimana, oppure tutto il mese, da domani se è quello in corso.
     */
    fun departureWindow(today: LocalDate): ClosedRange<LocalDate> {
        val earliest = today.plusDays(1)
        return when (this) {
            NextDays -> earliest..earliest.plusDays(NEXT_DAYS_WINDOW_DAYS - 1)
            is InMonth -> maxOf(month.atDay(1), earliest)..month.atEndOfMonth()
        }
    }

    /** `true` quando il mese è ormai passato: per un avviso non ha più senso controllare i prezzi. */
    fun isOver(today: LocalDate): Boolean = this is InMonth && month.isBefore(YearMonth.from(today))

    companion object {
        const val TRIP_LENGTH_DAYS = 4L

        /** Giorni di partenza considerati per "prossimi giorni" quando le date sono flessibili. */
        const val NEXT_DAYS_WINDOW_DAYS = 7L

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
        fun fromKey(key: String): TravelPeriod? = when (key) {
            NextDays.key -> NextDays
            else -> runCatching { InMonth(YearMonth.parse(key)) }.getOrNull()
        }
    }
}
