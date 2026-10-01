package com.partimo.app.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.partimo.app.MainActivity
import com.partimo.app.PartiMoApp
import com.partimo.app.R
import com.partimo.app.ui.bookings.emoji
import com.partimo.app.ui.bookings.routeText
import com.partimo.app.ui.common.Formatters
import com.partimo.domain.model.booking.Booking
import com.partimo.domain.model.booking.BookingKind
import com.partimo.domain.model.booking.BookingReminder
import com.partimo.domain.model.booking.BookingReminderKind
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Promemoria delle prenotazioni: check-in online, «è ora di andare in aeroporto», un treno o un'attività tra
 * poco, il check-in dell'alloggio. Toccandoli si aprono le prenotazioni, con codice e documento a portata di mano.
 */
class BookingReminderNotifier(private val context: Context) {

    fun ensureChannel() {
        // Importanza alta: un promemoria per partire deve vedersi subito, anche sopra le altre app.
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_bookings),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.notification_channel_bookings_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Mostra il promemoria; `false` se le notifiche non sono consentite. */
    @SuppressLint("MissingPermission") // Il permesso è verificato da canNotify().
    fun notify(reminder: BookingReminder, now: Instant, phoneZone: ZoneId): Boolean {
        if (!DealNotifier(context).canNotify()) return false
        ensureChannel()
        val text = text(reminder, now, phoneZone)
        val details = details(reminder.booking)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_partimo)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setContentTitle(title(reminder))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOf(text, details).filter { it.isNotEmpty() }.joinToString("\n")))
            .setContentIntent(openBookingsIntent(reminder))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        // Una notifica per prenotazione: il promemoria successivo sostituisce il precedente.
        NotificationManagerCompat.from(context).notify(NOTIFICATION_TAG, reminder.booking.id.hashCode(), notification)
        return true
    }

    internal fun title(reminder: BookingReminder): String = reminder.booking.kind.emoji() + " " + reminder.booking.title

    internal fun text(reminder: BookingReminder, now: Instant, phoneZone: ZoneId): String {
        val booking = reminder.booking
        val start = startText(booking, now, phoneZone)
        return when (reminder.kind) {
            BookingReminderKind.CHECK_IN -> context.getString(R.string.booking_reminder_check_in, start)
            BookingReminderKind.LEAVE_FOR_AIRPORT -> context.getString(R.string.booking_reminder_leave, start)
            BookingReminderKind.STARTS_SOON -> context.getString(
                when (booking.kind) {
                    BookingKind.TRAIN, BookingKind.BUS -> R.string.booking_reminder_departs
                    BookingKind.CAR_RENTAL -> R.string.booking_reminder_pick_up
                    else -> R.string.booking_reminder_starts
                },
                start,
            )
            BookingReminderKind.CHECK_IN_TODAY -> booking.startTime
                ?.let { context.getString(R.string.booking_reminder_check_in_today_from, Formatters.time(it)) }
                ?: context.getString(R.string.booking_reminder_check_in_today)
            BookingReminderKind.TOMORROW -> context.getString(R.string.booking_reminder_tomorrow)
        }
    }

    /** "oggi alle 21:10", "domani alle 8:25", "ven 11 dic alle 21:10", con il giorno contato nel fuso del luogo. */
    private fun startText(booking: Booking, now: Instant, phoneZone: ZoneId): String {
        val today = now.atZone(booking.timeZone ?: phoneZone).toLocalDate()
        val time = booking.startTime?.let(Formatters::time)
        return when {
            time == null -> Formatters.weekdayDayMonth(booking.startDate)
            booking.startDate == today -> context.getString(R.string.booking_when_today, time)
            booking.startDate == today.plusDays(1) -> context.getString(R.string.booking_when_tomorrow, time)
            else -> context.getString(R.string.booking_when_date, Formatters.weekdayDayMonth(booking.startDate), time)
        }
    }

    /** Tratta, codice di prenotazione e indirizzo: quello che serve sotto mano. */
    internal fun details(booking: Booking): String = listOfNotNull(
        booking.routeText,
        booking.reference?.let { context.getString(R.string.booking_reminder_reference, it) },
        booking.address,
    ).joinToString(" · ")

    private fun openBookingsIntent(reminder: BookingReminder): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_BOOKINGS, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            reminder.key.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val CHANNEL_ID = "booking_reminders"
        private const val NOTIFICATION_TAG = "booking"

        /** Extra dell'intent che apre le prenotazioni (tocco su un promemoria). */
        const val EXTRA_OPEN_BOOKINGS = "com.partimo.app.extra.OPEN_BOOKINGS"

        fun opensBookings(intent: Intent?): Boolean = intent?.getBooleanExtra(EXTRA_OPEN_BOOKINGS, false) == true
    }
}

/**
 * Mostra i promemoria delle prenotazioni arrivati all'ora giusta e pianifica il controllo successivo, così la
 * catena continua anche se l'app non viene aperta.
 */
class BookingReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? PartiMoApp)?.container ?: return Result.failure()
        return try {
            val reminders = container.bookingReminders.takeDue()
            val notifier = BookingReminderNotifier(applicationContext)
            val now = Instant.now(container.clock)
            val shown = reminders.count { notifier.notify(it, now, container.clock.zone) }
            Log.i(TAG, "Promemoria delle prenotazioni: ${reminders.size} da mostrare, $shown mostrati")
            BookingReminderScheduler(applicationContext).scheduleNext(container.bookingReminders.nextCheck(), now)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Promemoria delle prenotazioni non riusciti, verranno ritentati", e)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "BookingReminderWorker"
    }
}

/**
 * Un solo lavoro in coda, all'ora del prossimo promemoria (vedi [com.partimo.domain.usecase.BookingRemindersUseCase.nextCheck]).
 * Ogni modifica alle prenotazioni lo sostituisce; il worker accoda il successivo dopo di sé.
 */
class BookingReminderScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context)

    /** Dopo una modifica alle prenotazioni (o all'avvio): sostituisce il controllo in coda. */
    fun schedule(next: Instant?, now: Instant = Instant.now()) {
        if (next == null) {
            workManager.cancelUniqueWork(WORK_NAME)
        } else {
            workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request(next, now))
        }
    }

    /** Dal worker in esecuzione: il controllo successivo parte quando questo è finito. */
    fun scheduleNext(next: Instant?, now: Instant) {
        next ?: return
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request(next, now))
    }

    private fun request(next: Instant, now: Instant): OneTimeWorkRequest {
        val delay = Duration.between(now, next).coerceAtLeast(Duration.ZERO)
        return OneTimeWorkRequestBuilder<BookingReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, RETRY_DELAY_MINUTES, TimeUnit.MINUTES)
            .addTag(WORK_NAME)
            .build()
    }

    companion object {
        const val WORK_NAME = "partimo-booking-reminders"
        private const val RETRY_DELAY_MINUTES = 15L
    }
}
