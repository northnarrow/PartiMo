package com.partimo.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.partimo.app.MainActivity
import com.partimo.app.R
import com.partimo.app.navigation.DashboardDestination
import com.partimo.app.ui.common.Formatters
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.deal.DealAlert
import com.partimo.domain.model.deal.DealKind
import com.partimo.domain.model.deal.PriceWatch
import java.time.LocalDate

/**
 * Notifiche delle offerte davvero convenienti. Ogni viaggio seguito ha la sua notifica, sostituita a
 * ogni nuovo affare; toccandola si apre la dashboard di quel viaggio.
 */
class DealNotifier(private val context: Context) {

    /** Crea (o aggiorna) il canale "Offerte convenienti", visibile nelle impostazioni dell'app. */
    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_deals),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notification_channel_deals_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** `true` se l'utente permette a PartiMo di mostrare notifiche (su Android 13+ serve il permesso). */
    fun canNotify(): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Mostra l'avviso; restituisce `false` se le notifiche non sono consentite. */
    @SuppressLint("MissingPermission") // Il permesso è verificato da canNotify().
    fun notify(alert: DealAlert, today: LocalDate): Boolean {
        if (!canNotify() || alert.deals.isEmpty()) return false
        ensureChannel()
        val lines = dealLines(alert)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_partimo)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setContentTitle(title(alert.watch, today))
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(openTripIntent(alert.watch))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(alert.watch), notification)
        return true
    }

    internal fun title(watch: PriceWatch, today: LocalDate): String = when (val period = watch.period) {
        TravelPeriod.NextDays -> context.getString(R.string.notification_title_next_days, watch.destination.name)
        is TravelPeriod.InMonth -> context.getString(
            R.string.notification_title_month,
            watch.destination.name,
            Formatters.monthYear(period.month, today.year),
        )
    }

    internal fun dealLines(alert: DealAlert): List<String> = alert.deals.map { deal ->
        when (deal.kind) {
            DealKind.FLIGHT -> context.getString(
                R.string.notification_flight,
                alert.watch.departure.airport.iata,
                alert.watch.destination.airportIata,
                Formatters.money(deal.price),
                deal.discountPercent,
            )
            DealKind.STAY -> context.getString(R.string.notification_stay, deal.title, Formatters.money(deal.price), deal.discountPercent)
        }
    }

    private fun openTripIntent(watch: PriceWatch): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_DASHBOARD_ROUTE, DashboardDestination.from(watch.destination, watch.period).toJson())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            notificationId(watch),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun notificationId(watch: PriceWatch): Int = watch.id.hashCode()

    companion object {
        const val CHANNEL_ID = "deals"
        const val EXTRA_DASHBOARD_ROUTE = "com.partimo.app.extra.DASHBOARD_ROUTE"

        /** Viaggio da aprire se l'app è stata avviata toccando una notifica. */
        fun dashboardRouteFrom(intent: Intent?): DashboardDestination? =
            intent?.getStringExtra(EXTRA_DASHBOARD_ROUTE)?.let(DashboardDestination::fromJson)
    }
}
