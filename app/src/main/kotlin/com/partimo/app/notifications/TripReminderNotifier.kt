package com.partimo.app.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.partimo.app.MainActivity
import com.partimo.app.R
import com.partimo.app.navigation.DashboardDestination
import com.partimo.domain.service.ReminderKind
import com.partimo.domain.service.TripReminder

/**
 * Promemoria dei viaggi salvati: una settimana prima ("prepara documenti e valigia") e il giorno
 * prima della partenza. Toccandoli si apre la dashboard del viaggio, con guida e valigia a un tocco.
 */
class TripReminderNotifier(private val context: Context) {

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_reminders),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notification_channel_reminders_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Mostra il promemoria; `false` se le notifiche non sono consentite. */
    @SuppressLint("MissingPermission") // Il permesso è verificato da canNotify().
    fun notify(reminder: TripReminder): Boolean {
        if (!DealNotifier(context).canNotify()) return false
        ensureChannel()
        val text = text(reminder)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_partimo)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setContentTitle(title(reminder))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openTripIntent(reminder))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(context).notify(reminder.key.hashCode(), notification)
        return true
    }

    internal fun title(reminder: TripReminder): String {
        val city = reminder.trip.destination.name
        return when {
            reminder.kind == ReminderKind.WEEK_BEFORE -> context.getString(R.string.reminder_title_days, city, reminder.daysLeft)
            reminder.daysLeft <= 0 -> context.getString(R.string.reminder_title_today, city)
            else -> context.getString(R.string.reminder_title_tomorrow, city)
        }
    }

    internal fun text(reminder: TripReminder): String = when (reminder.kind) {
        ReminderKind.WEEK_BEFORE -> context.getString(R.string.reminder_text_week)
        ReminderKind.DAY_BEFORE -> context.getString(R.string.reminder_text_day)
    }

    private fun openTripIntent(reminder: TripReminder): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(DealNotifier.EXTRA_DASHBOARD_ROUTE, DashboardDestination.from(reminder.trip.destination, reminder.trip.period).toJson())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            reminder.key.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val CHANNEL_ID = "trip_reminders"
    }
}
