package com.partimo.app.notifications

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.partimo.app.PartiMoApp
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/** Promemoria dei viaggi salvati: un controllo al giorno, anche ad app chiusa e senza rete. */
class TripReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? PartiMoApp)?.container ?: return Result.failure()
        return try {
            val reminders = container.tripReminders.due()
            val notifier = TripReminderNotifier(applicationContext)
            val shown = reminders.count { notifier.notify(it) }
            // Anche quelli non mostrati (notifiche spente) si segnano: un promemoria in ritardo non serve.
            container.tripReminders.markShown(reminders)
            Log.i(TAG, "Promemoria dei viaggi: ${reminders.size} da mostrare, $shown mostrati")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Controllo dei promemoria non riuscito, verrà ritentato", e)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "TripReminderWorker"
    }
}

/**
 * Prepara i viaggi salvati per l'uso senza rete: con il Wi-Fi scarica luoghi, eventi, ristoranti,
 * alloggi, guida, meteo, cambio e le foto dei luoghi dei viaggi che partono entro un mese.
 */
class OfflinePrefetchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? PartiMoApp)?.container ?: return Result.failure()
        return try {
            val today = LocalDate.now(container.clock)
            val trips = container.observeSavedTrips().first().filter { trip ->
                val departure = trip.departureDate(today)
                !departure.isBefore(today) && ChronoUnit.DAYS.between(today, departure) <= PREFETCH_HORIZON_DAYS
            }
            val imageLoader = SingletonImageLoader.get(applicationContext)
            trips.forEach { trip ->
                val result = container.prefetchTrip(trip.destination, trip.departureDate(today), trip.returnDate(today))
                result.photoUrls.forEach { url -> imageLoader.execute(ImageRequest.Builder(applicationContext).data(url).build()) }
                Log.i(TAG, "${trip.destination.name}: ${result.succeeded} fonti pronte offline, ${result.failed} non riuscite, ${result.photoUrls.size} foto")
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Preparazione offline non riuscita, verrà ritentata", e)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "OfflinePrefetchWorker"

        /** I viaggi più lontani si preparano più avanti, con dati più freschi. */
        const val PREFETCH_HORIZON_DAYS = 31L
    }
}

/** Pianificazione dei promemoria e della preparazione offline dei viaggi salvati. */
class TripWorkScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context)

    fun scheduleReminders() {
        val request = PeriodicWorkRequestBuilder<TripReminderWorker>(1, TimeUnit.DAYS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_DELAY_MINUTES, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(REMINDERS_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelReminders() {
        workManager.cancelUniqueWork(REMINDERS_WORK)
    }

    /** Una volta al giorno con il Wi-Fi (o una rete senza limiti) e la batteria non scarica. */
    fun schedulePrefetch() {
        val request = PeriodicWorkRequestBuilder<OfflinePrefetchWorker>(1, TimeUnit.DAYS)
            .setConstraints(prefetchConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_DELAY_MINUTES, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(PREFETCH_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelPrefetch() {
        workManager.cancelUniqueWork(PREFETCH_WORK)
    }

    /** Subito dopo aver salvato un viaggio: appena c'è il Wi-Fi. */
    fun prefetchNow() {
        val request = OneTimeWorkRequestBuilder<OfflinePrefetchWorker>()
            .setConstraints(prefetchConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_DELAY_MINUTES, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniqueWork(PREFETCH_NOW_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private fun prefetchConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .setRequiresBatteryNotLow(true)
        .build()

    companion object {
        const val REMINDERS_WORK = "partimo-trip-reminders"
        const val PREFETCH_WORK = "partimo-offline-prefetch"
        const val PREFETCH_NOW_WORK = "partimo-offline-prefetch-now"
        const val RETRY_DELAY_MINUTES = 30L
    }
}
