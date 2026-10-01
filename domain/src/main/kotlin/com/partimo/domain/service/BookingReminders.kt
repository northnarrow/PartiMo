package com.partimo.domain.service

import com.partimo.domain.model.booking.Booking
import com.partimo.domain.model.booking.BookingKind
import com.partimo.domain.model.booking.BookingReminder
import com.partimo.domain.model.booking.BookingReminderKind
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Promemoria delle prenotazioni: per un volo il check-in online 24 ore prima e «parti per l'aeroporto» 3 ore
 * prima; per treni, pullman e attività un'ora prima; per un alloggio la mattina del check-in (o un'ora prima,
 * se il check-in è prestissimo). Senza l'ora, un promemoria la sera prima.
 */
object BookingReminders {

    val CHECK_IN_BEFORE: Duration = Duration.ofHours(24)
    val LEAVE_FOR_AIRPORT_BEFORE: Duration = Duration.ofHours(3)
    val STARTS_SOON_BEFORE: Duration = Duration.ofHours(1)
    val CHECK_IN_TODAY_AT: LocalTime = LocalTime.of(9, 0)
    val TOMORROW_AT: LocalTime = LocalTime.of(18, 0)

    /** Il lavoro in background può partire con qualche istante d'anticipo rispetto all'ora prevista. */
    private val DUE_TOLERANCE: Duration = Duration.ofMinutes(1)

    /** Promemoria futuri di [bookings], dal più vicino; [fallbackZone] vale per le prenotazioni senza fuso. */
    fun plan(bookings: List<Booking>, now: Instant, fallbackZone: ZoneId): List<BookingReminder> = bookings
        .flatMap { booking -> remindersOf(booking, fallbackZone) }
        .filter { it.at.isAfter(now) }
        .sortedBy { it.at }

    /**
     * Promemoria da mostrare ora: già scattati (con un minuto di tolleranza), non tra quelli già mostrati
     * ([shown], per chiave) e ancora utili, cioè con la prenotazione non ancora iniziata.
     */
    fun due(bookings: List<Booking>, now: Instant, fallbackZone: ZoneId, shown: Set<String>): List<BookingReminder> = bookings
        .filter { booking -> now.isBefore(relevantUntil(booking, fallbackZone)) }
        .flatMap { booking -> remindersOf(booking, fallbackZone) }
        .filter { !it.at.isAfter(now + DUE_TOLERANCE) && it.key !in shown }
        .sortedBy { it.at }

    /** Un promemoria serve fino all'inizio della prenotazione; senza l'ora, fino alla fine di quel giorno. */
    private fun relevantUntil(booking: Booking, fallbackZone: ZoneId): Instant = if (booking.startTime != null) {
        booking.startInstant(fallbackZone)
    } else {
        booking.startDate.plusDays(1).atStartOfDay(booking.timeZone ?: fallbackZone).toInstant()
    }

    private fun remindersOf(booking: Booking, fallbackZone: ZoneId): List<BookingReminder> {
        val zone = booking.timeZone ?: fallbackZone
        val start = booking.startInstant(fallbackZone)
        fun at(kind: BookingReminderKind, instant: Instant) = BookingReminder(booking, kind, instant)
        val dayBefore = at(BookingReminderKind.TOMORROW, booking.startDate.minusDays(1).atTime(TOMORROW_AT).atZone(zone).toInstant())
        return when (booking.kind) {
            BookingKind.LODGING -> {
                val morning = booking.startDate.atTime(CHECK_IN_TODAY_AT).atZone(zone).toInstant()
                val early = booking.startTime?.let { start - STARTS_SOON_BEFORE }
                listOf(at(BookingReminderKind.CHECK_IN_TODAY, if (early != null && early.isBefore(morning)) early else morning))
            }
            BookingKind.FLIGHT -> if (booking.startTime == null) {
                listOf(dayBefore)
            } else {
                listOf(at(BookingReminderKind.CHECK_IN, start - CHECK_IN_BEFORE), at(BookingReminderKind.LEAVE_FOR_AIRPORT, start - LEAVE_FOR_AIRPORT_BEFORE))
            }
            BookingKind.TRAIN, BookingKind.BUS, BookingKind.CAR_RENTAL, BookingKind.ACTIVITY, BookingKind.OTHER ->
                if (booking.startTime == null) listOf(dayBefore) else listOf(at(BookingReminderKind.STARTS_SOON, start - STARTS_SOON_BEFORE))
        }
    }
}
