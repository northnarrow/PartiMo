package com.partimo.domain.service

import com.partimo.domain.model.booking.Booking
import com.partimo.domain.model.booking.BookingKind
import com.partimo.domain.model.booking.BookingReminderKind
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookingRemindersTest {

    private val rome = ZoneId.of("Europe/Rome")
    private val vienna = ZoneId.of("Europe/Vienna")
    private val now = Instant.parse("2026-12-01T09:00:00Z")
    private val flightDay = LocalDate.of(2026, 12, 11)

    private fun booking(id: String, kind: BookingKind, time: LocalTime? = LocalTime.of(21, 10), zone: ZoneId? = null, date: LocalDate = flightDay) =
        Booking(id = id, kind = kind, title = "Prenotazione $id", startDate = date, startTime = time, timeZone = zone)

    @Test
    fun `un volo ha il check-in 24 ore prima e il promemoria per l'aeroporto 3 ore prima`() {
        val reminders = BookingReminders.plan(listOf(booking("volo", BookingKind.FLIGHT)), now, rome)

        assertEquals(listOf(BookingReminderKind.CHECK_IN, BookingReminderKind.LEAVE_FOR_AIRPORT), reminders.map { it.kind })
        assertEquals(Instant.parse("2026-12-10T20:10:00Z"), reminders[0].at, "21:10 a Roma (UTC+1) meno 24 ore")
        assertEquals(Instant.parse("2026-12-11T17:10:00Z"), reminders[1].at)
        assertEquals("volo:CHECK_IN", reminders[0].key)
    }

    @Test
    fun `il ritorno da Vienna segue il fuso dell'aeroporto di partenza`() {
        val back = booking("ritorno", BookingKind.FLIGHT, time = LocalTime.of(8, 25), zone = vienna)

        val leave = BookingReminders.plan(listOf(back), now, fallbackZone = ZoneId.of("America/New_York")).last()

        assertEquals(Instant.parse("2026-12-11T04:25:00Z"), leave.at)
    }

    @Test
    fun `treni e attività un'ora prima, alloggi la mattina del check-in, senza ora la sera prima`() {
        val bookings = listOf(
            booking("treno", BookingKind.TRAIN, time = LocalTime.of(8, 0)),
            booking("hotel", BookingKind.LODGING, time = LocalTime.of(15, 0)),
            booking("museo", BookingKind.ACTIVITY, time = null),
        )

        val reminders = BookingReminders.plan(bookings, now, rome).associate { it.booking.id to (it.kind to it.at) }

        assertEquals(BookingReminderKind.STARTS_SOON to Instant.parse("2026-12-11T06:00:00Z"), reminders["treno"])
        assertEquals(BookingReminderKind.CHECK_IN_TODAY to Instant.parse("2026-12-11T08:00:00Z"), reminders["hotel"])
        assertEquals(BookingReminderKind.TOMORROW to Instant.parse("2026-12-10T17:00:00Z"), reminders["museo"])
    }

    @Test
    fun `i promemoria passati non si pianificano e l'ordine è dal più vicino`() {
        val soon = booking("domani", BookingKind.FLIGHT, time = LocalTime.of(12, 0), date = LocalDate.of(2026, 12, 2))
        val later = booking("dopo", BookingKind.TRAIN, time = LocalTime.of(9, 0), date = LocalDate.of(2026, 12, 20))
        val past = booking("ieri", BookingKind.TRAIN, date = LocalDate.of(2026, 11, 30))

        val reminders = BookingReminders.plan(listOf(later, past, soon), now, rome)

        assertEquals(listOf("domani:CHECK_IN", "domani:LEAVE_FOR_AIRPORT", "dopo:STARTS_SOON"), reminders.map { it.key })
        assertTrue(reminders.zipWithNext().all { (a, b) -> !a.at.isAfter(b.at) })
    }

    @Test
    fun `un alloggio con il check-in all'alba avvisa un'ora prima invece che alle 9`() {
        val hostel = booking("ostello", BookingKind.LODGING, time = LocalTime.of(7, 0))

        assertEquals(Instant.parse("2026-12-11T05:00:00Z"), BookingReminders.plan(listOf(hostel), now, rome).single().at)
    }

    @Test
    fun `un promemoria scattato si mostra finché la prenotazione non è iniziata`() {
        val flight = booking("volo", BookingKind.FLIGHT)
        val museum = booking("museo", BookingKind.ACTIVITY, time = null)
        val evening = Instant.parse("2026-12-11T19:00:00Z") // 20:00 a Roma: il volo delle 21:10 non è ancora partito

        val due = BookingReminders.due(listOf(flight, museum), evening, rome, shown = setOf("volo:CHECK_IN"))

        assertEquals(listOf("museo:TOMORROW", "volo:LEAVE_FOR_AIRPORT"), due.map { it.key }, "Il museo senza ora vale fino a mezzanotte")
        val late = Instant.parse("2026-12-11T20:30:00Z")
        assertEquals(listOf("museo:TOMORROW"), BookingReminders.due(listOf(flight, museum), late, rome, emptySet()).map { it.key }, "Volo partito: niente promemoria")
    }
}
