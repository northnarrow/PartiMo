package com.partimo.app.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.partimo.app.navigation.BookingEditorDestination
import com.partimo.app.navigation.BookingsDestination
import com.partimo.app.navigation.DashboardDestination
import com.partimo.app.navigation.IncomingRoutes
import com.partimo.domain.model.booking.Booking
import com.partimo.domain.model.booking.BookingKind
import com.partimo.domain.model.booking.BookingReminder
import com.partimo.domain.model.booking.BookingReminderKind
import com.partimo.domain.testing.TestData
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Promemoria delle prenotazioni e intent in arrivo (notifiche, condivisioni), verificati con Robolectric (Android 14). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BookingReminderNotifierTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val notifier = BookingReminderNotifier(context)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val rome = ZoneId.of("Europe/Rome")
    private val flight = Booking(
        id = "andata",
        kind = BookingKind.FLIGHT,
        title = "Ryanair FR 7178",
        startDate = LocalDate.of(2026, 12, 11),
        startTime = LocalTime.of(21, 10),
        timeZone = rome,
        origin = "BGY",
        destination = "VIE",
        reference = "K7M2QX",
    )

    @Test
    fun `tre ore prima del volo ricorda di andare in aeroporto e apre le prenotazioni`() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val reminder = BookingReminder(flight, BookingReminderKind.LEAVE_FOR_AIRPORT, Instant.parse("2026-12-11T17:10:00Z"))

        assertTrue(notifier.notify(reminder, reminder.at, rome))

        val notification = shadowOf(manager).allNotifications.single()
        val content = shadowOf(notification)
        assertEquals("✈️ Ryanair FR 7178", content.contentTitle)
        assertEquals("È ora di andare in aeroporto: il volo parte oggi alle 21:10.", content.contentText)
        assertTrue("BGY → VIE · Codice K7M2QX" in content.bigText.toString(), content.bigText.toString())
        assertNotNull(manager.getNotificationChannel(BookingReminderNotifier.CHANNEL_ID))
        val openIntent = shadowOf(notification.contentIntent).savedIntent
        assertTrue(BookingReminderNotifier.opensBookings(openIntent))
        assertEquals(BookingsDestination(), IncomingRoutes.from(openIntent))
    }

    @Test
    fun `i testi dicono quando si parte, nel fuso del luogo`() {
        val checkIn = BookingReminder(flight, BookingReminderKind.CHECK_IN, Instant.parse("2026-12-10T20:10:00Z"))
        assertEquals("Check-in online aperto: il volo parte domani alle 21:10.", notifier.text(checkIn, checkIn.at, ZoneId.of("Asia/Tokyo")))

        val train = flight.copy(kind = BookingKind.TRAIN, title = "Frecciarossa 9517", startDate = LocalDate.of(2026, 12, 20), startTime = LocalTime.of(8, 0))
        val soon = BookingReminder(train, BookingReminderKind.STARTS_SOON, Instant.parse("2026-12-18T09:00:00Z"))
        assertTrue(notifier.text(soon, soon.at, rome).startsWith("Si parte "), notifier.text(soon, soon.at, rome))
        assertTrue(notifier.text(soon, soon.at, rome).endsWith(" alle 08:00."), notifier.text(soon, soon.at, rome))

        val hotel = Booking(id = "hotel", kind = BookingKind.LODGING, title = "Hotel Sacher Wien", startDate = LocalDate.of(2026, 12, 11), startTime = LocalTime.of(15, 0), address = "Philharmoniker Str. 4")
        val checkInToday = BookingReminder(hotel, BookingReminderKind.CHECK_IN_TODAY, Instant.parse("2026-12-11T08:00:00Z"))
        assertEquals("Check-in oggi, dalle 15:00.", notifier.text(checkInToday, checkInToday.at, rome))
        assertEquals("🏨 Hotel Sacher Wien", notifier.title(checkInToday))
        assertEquals("Philharmoniker Str. 4", notifier.details(hotel))
    }

    @Test
    fun `senza il permesso delle notifiche non mostra nulla`() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val reminder = BookingReminder(flight, BookingReminderKind.CHECK_IN, Instant.parse("2026-12-10T20:10:00Z"))

        assertFalse(notifier.notify(reminder, reminder.at, rome))
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test
    fun `condividere con PartiMo una mail, un PDF o una foto apre il modulo della prenotazione`() {
        val mail = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Conferma della prenotazione K7M2QX")
            .putExtra(Intent.EXTRA_TEXT, "FR 7178 BGY - VIE")
        assertEquals(BookingEditorDestination(sharedText = "Conferma della prenotazione K7M2QX\nFR 7178 BGY - VIE"), IncomingRoutes.from(mail))

        val pdf = Uri.parse("content://downloads/biglietto.pdf")
        val shared = IncomingRoutes.from(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, pdf)) as BookingEditorDestination
        assertEquals(pdf.toString(), shared.sharedUri)
        assertEquals("application/pdf", shared.shared()?.mimeType)
        assertTrue(shared.isShared)

        val photo = IncomingRoutes.from(Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, Uri.parse("content://media/12")))
        assertEquals(BookingEditorDestination(sharedUri = "content://media/12", sharedMimeType = "image/jpeg"), photo)

        assertNull(IncomingRoutes.from(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "   ")), "Niente da leggere")
        assertNull(IncomingRoutes.from(Intent(Intent.ACTION_MAIN)))
        assertNull(IncomingRoutes.from(null))
    }

    @Test
    fun `le notifiche delle offerte aprono ancora la dashboard del viaggio`() {
        val route = DashboardDestination.from(TestData.destination(), TestData.DECEMBER_2026)
        val intent = Intent().putExtra(DealNotifier.EXTRA_DASHBOARD_ROUTE, route.toJson())

        assertEquals(route, IncomingRoutes.from(intent))
    }

    @Test
    fun `le prenotazioni di un viaggio hanno le sue date`() {
        assertEquals(LocalDate.of(2026, 12, 10)..LocalDate.of(2026, 12, 14), BookingsDestination("2026-12-10", "2026-12-14", "Vienna").tripDates())
        assertNull(BookingsDestination("2026-12-14", "2026-12-10").tripDates(), "Date al contrario: tutte le prenotazioni")
        assertNull(BookingsDestination().tripDates())
        assertEquals(LocalDate.of(2026, 12, 11), BookingEditorDestination(defaultDate = "2026-12-11").defaultLocalDate())
        assertFalse(BookingEditorDestination(bookingId = "volo").isShared)
    }
}
