package com.partimo.domain.service

import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.saved.SavedTrip
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Promemoria prima della partenza. */
enum class ReminderKind {
    /** Una settimana prima: documenti, meteo, valigia. */
    WEEK_BEFORE,

    /** Il giorno prima (o il giorno stesso): si parte. */
    DAY_BEFORE,
}

/** Promemoria da mostrare per un viaggio salvato; [key] lo identifica per non ripeterlo. */
data class TripReminder(val trip: SavedTrip, val kind: ReminderKind, val departure: LocalDate, val daysLeft: Int) {
    val key: String get() = "${trip.id}:${kind.name}"
}

/**
 * Quali promemoria mostrare oggi per i viaggi salvati. Valgono solo per i viaggi in un mese preciso,
 * con la partenza prevista il giorno [TravelPeriod.PREFERRED_DEPARTURE_DAY]: "Prossimi giorni" non ha
 * una data fissa. Il promemoria della settimana resta valido fino a due giorni prima, così arriva
 * anche se il telefono era spento il giorno giusto; ognuno arriva una volta sola.
 */
object TripReminders {

    private val WEEK_WINDOW = 2..7
    private val DAY_WINDOW = 0..1

    fun due(trips: List<SavedTrip>, today: LocalDate, alreadySent: Set<String>): List<TripReminder> = trips.mapNotNull { trip ->
        val period = trip.period as? TravelPeriod.InMonth ?: return@mapNotNull null
        val departure = period.month.atDay(TravelPeriod.PREFERRED_DEPARTURE_DAY)
        val daysLeft = ChronoUnit.DAYS.between(today, departure).toInt()
        val kind = when (daysLeft) {
            in WEEK_WINDOW -> ReminderKind.WEEK_BEFORE
            in DAY_WINDOW -> ReminderKind.DAY_BEFORE
            else -> return@mapNotNull null
        }
        TripReminder(trip, kind, departure, daysLeft).takeUnless { it.key in alreadySent }
    }

    /** `true` se almeno un viaggio salvato avrà ancora un promemoria: serve il controllo giornaliero. */
    fun hasUpcoming(trips: List<SavedTrip>, today: LocalDate): Boolean = trips.any { trip ->
        val period = trip.period as? TravelPeriod.InMonth ?: return@any false
        !period.month.atDay(TravelPeriod.PREFERRED_DEPARTURE_DAY).isBefore(today)
    }
}
