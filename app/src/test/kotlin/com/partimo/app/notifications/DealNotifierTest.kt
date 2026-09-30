package com.partimo.app.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.partimo.app.navigation.DashboardDestination
import com.partimo.domain.model.Money
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.deal.Deal
import com.partimo.domain.model.deal.DealAlert
import com.partimo.domain.model.deal.DealKind
import com.partimo.domain.testing.TestData
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Notifiche delle offerte convenienti, verificate con Robolectric (Android 14). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DealNotifierTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val notifier = DealNotifier(context)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val watch = TestData.priceWatch()

    private fun eur(amount: Int) = Money.of(amount, "EUR")

    private val alert = DealAlert(
        watch = watch,
        deals = listOf(
            Deal(DealKind.FLIGHT, "Wizz Air", price = eur(150), usualPrice = eur(200)),
            Deal(DealKind.STAY, "Hotel Centrale", price = eur(80), usualPrice = eur(100), reviewScore = 8.7),
        ),
    )

    @Test
    fun `mostra volo e alloggio convenienti e toccandola si apre il viaggio`() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertTrue(notifier.notify(alert, TestData.TODAY))

        val notification = shadowOf(manager).allNotifications.single()
        val content = shadowOf(notification)
        assertEquals("🔥 Vienna a dicembre: offerta davvero conveniente", content.contentTitle)
        assertTrue("MXP → VIE" in content.contentText && "−25%" in content.contentText, content.contentText.toString())
        assertTrue("Hotel Centrale" in content.bigText && "−20%" in content.bigText, content.bigText.toString())
        assertNotNull(manager.getNotificationChannel(DealNotifier.CHANNEL_ID))

        val openIntent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(DashboardDestination.from(watch.destination, watch.period), DealNotifier.dashboardRouteFrom(openIntent))
    }

    @Test
    fun `nei prossimi giorni il titolo lo dice esplicitamente`() {
        val title = notifier.title(watch.copy(period = TravelPeriod.NextDays), TestData.TODAY)

        assertEquals("🔥 Vienna nei prossimi giorni: offerta davvero conveniente", title)
    }

    @Test
    fun `senza il permesso delle notifiche non mostra nulla`() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertFalse(notifier.notify(alert, TestData.TODAY))
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
}
