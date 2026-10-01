package com.partimo.app.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.partimo.app.navigation.DashboardDestination
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.saved.SavedTrip
import com.partimo.domain.service.ReminderKind
import com.partimo.domain.service.TripReminder
import com.partimo.domain.testing.TestData
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Promemoria dei viaggi salvati, verificati con Robolectric (Android 14). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TripReminderNotifierTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val notifier = TripReminderNotifier(context)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val trip = SavedTrip(TestData.destination(), TravelPeriod.InMonth(YearMonth.of(2026, Month.DECEMBER)), Instant.parse("2026-09-30T08:00:00Z"))
    private val departure = LocalDate.of(2026, Month.DECEMBER, 10)

    @Test
    fun `una settimana prima ricorda documenti e valigia e apre il viaggio`() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val reminder = TripReminder(trip, ReminderKind.WEEK_BEFORE, departure, daysLeft = 7)

        assertTrue(notifier.notify(reminder))

        val notification = shadowOf(manager).allNotifications.single()
        val content = shadowOf(notification)
        assertEquals("🧳 Vienna tra 7 giorni", content.contentTitle)
        assertTrue("valigia" in content.contentText, content.contentText.toString())
        assertNotNull(manager.getNotificationChannel(TripReminderNotifier.CHANNEL_ID))
        val openIntent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(DashboardDestination.from(trip.destination, trip.period), DealNotifier.dashboardRouteFrom(openIntent))
    }

    @Test
    fun `il giorno prima e il giorno stesso si parte`() {
        assertEquals("✈️ Domani si parte per Vienna!", notifier.title(TripReminder(trip, ReminderKind.DAY_BEFORE, departure, daysLeft = 1)))
        assertEquals("✈️ Oggi si parte per Vienna!", notifier.title(TripReminder(trip, ReminderKind.DAY_BEFORE, departure, daysLeft = 0)))
    }

    @Test
    fun `senza il permesso delle notifiche non mostra nulla`() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertFalse(notifier.notify(TripReminder(trip, ReminderKind.DAY_BEFORE, departure, daysLeft = 1)))
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
}
