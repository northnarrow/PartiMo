package com.partimo.domain.usecase

import com.partimo.domain.repository.ReminderLogRepository
import com.partimo.domain.repository.SavedTripRepository
import com.partimo.domain.service.TripReminder
import com.partimo.domain.service.TripReminders
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate

/** Promemoria dei viaggi salvati da mostrare oggi (una settimana prima e il giorno prima della partenza). */
class TripRemindersUseCase(
    private val savedTrips: SavedTripRepository,
    private val log: ReminderLogRepository,
    private val clock: Clock,
) {
    suspend fun due(): List<TripReminder> = TripReminders.due(savedTrips.trips.first(), LocalDate.now(clock), log.sentReminders())

    suspend fun markShown(reminders: Collection<TripReminder>) {
        if (reminders.isNotEmpty()) log.markSent(reminders.map { it.key })
    }

    /** `true` se serve ancora il controllo giornaliero dei promemoria. */
    suspend fun hasUpcoming(): Boolean = TripReminders.hasUpcoming(savedTrips.trips.first(), LocalDate.now(clock))
}
