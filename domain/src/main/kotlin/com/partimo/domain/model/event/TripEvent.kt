package com.partimo.domain.model.event

import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.poi.WikipediaPage
import java.time.LocalDate
import java.time.Month
import java.time.MonthDay

/** Tipo di evento: decide l'icona e come si spiega quando si tiene. */
enum class EventKind {
    /** Mercatino di Natale: se la fonte non indica le date vale il periodo tipico dell'Avvento. */
    CHRISTMAS_MARKET,

    /** Festival, fiera o ricorrenza che si ripete ogni anno. */
    RECURRING_EVENT,

    /** Festività nazionale: negozi, musei e trasporti possono avere orari ridotti. */
    PUBLIC_HOLIDAY,
}

/** Quando si tiene un evento. */
sealed interface EventTiming {

    /** `true` se l'evento è in corso nel giorno [date]. */
    fun isOn(date: LocalDate): Boolean

    /** Date precise di un anno (es. una festività). */
    data class OnDates(val start: LocalDate, val end: LocalDate) : EventTiming {
        init {
            require(!end.isBefore(start)) { "L'evento finisce prima di cominciare: $start–$end" }
        }

        override fun isOn(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
    }

    /** Ogni anno tra due giorni, anche a cavallo di Capodanno (es. mercatini: 15 novembre–24 dicembre). */
    data class Yearly(val start: MonthDay, val end: MonthDay) : EventTiming {
        override fun isOn(date: LocalDate): Boolean {
            val day = MonthDay.from(date)
            return if (start <= end) day in start..end else day >= start || day <= end
        }
    }

    /** Ogni anno in questi giorni (es. concerto di Capodanno il 1° gennaio; Palio di Siena il 2 luglio e il 16 agosto). */
    data class YearlyDays(val days: Set<MonthDay>) : EventTiming {
        init {
            require(days.isNotEmpty()) { "Servono i giorni dell'evento" }
        }

        override fun isOn(date: LocalDate): Boolean = MonthDay.from(date) in days
    }

    /** Ogni anno in questi mesi, senza giorni precisi (es. "a ottobre"). */
    data class InMonths(val months: Set<Month>) : EventTiming {
        init {
            require(months.isNotEmpty()) { "Servono i mesi dell'evento" }
        }

        override fun isOn(date: LocalDate): Boolean = date.month in months
    }
}

/** Primo giorno tra [from] e [to] (inclusi) in cui l'evento è in corso; `null` se non cade nel periodo. */
fun EventTiming.firstDayWithin(from: LocalDate, to: LocalDate): LocalDate? =
    generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.firstOrNull(::isOn)

/** `true` se l'evento cade almeno in parte tra [from] e [to] (inclusi). */
fun EventTiming.overlaps(from: LocalDate, to: LocalDate): Boolean = firstDayWithin(from, to) != null

/**
 * Evento della destinazione: mercatino di Natale, festival, ricorrenza o festività nazionale.
 * Quelli con un luogo si aprono come i luoghi da vedere (descrizione, storia e "Naviga").
 */
data class TripEvent(
    val id: String,
    val name: String,
    val kind: EventKind,
    val timing: EventTiming,
    /** `true` se le date sono quelle tipiche del tipo di evento, non quelle dichiarate dalla fonte. */
    val approximateTiming: Boolean = false,
    val description: String? = null,
    /** Dove si tiene; `null` per le festività, che valgono in tutto il paese. */
    val location: GeoPoint? = null,
    /** Piazza, parco o edificio che ospita l'evento (es. "Marienplatz"). */
    val venueName: String? = null,
    val photoUrl: String? = null,
    val wikipediaPage: WikipediaPage? = null,
    /** Nome nella lingua del paese, se diverso da [name] (es. "Mariä Empfängnis"). */
    val localName: String? = null,
)

/** Eventi che si ripetono ogni anno attorno a un punto (di solito il centro città). */
data class EventQuery(
    val location: GeoPoint,
    val radiusMeters: Int = 15_000,
)

/** Eventi durante il soggiorno, dal giorno di arrivo a quello di partenza. */
data class TripEvents(
    val from: LocalDate,
    val to: LocalDate,
    /** Prima mercatini e festival, poi le festività; a parità di tipo in ordine di data. */
    val events: List<TripEvent>,
)
