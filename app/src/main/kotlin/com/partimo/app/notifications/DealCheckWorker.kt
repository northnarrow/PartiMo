package com.partimo.app.notifications

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.partimo.app.PartiMoApp
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Controllo in background dei viaggi seguiti: cerca voli e alloggi aggiornati e notifica le offerte
 * davvero convenienti. Eseguito da WorkManager anche ad app chiusa.
 */
class DealCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? PartiMoApp)?.container ?: return Result.failure()
        return try {
            val alerts = container.checkPriceWatches()
            val notifier = DealNotifier(applicationContext)
            val today = LocalDate.now(container.clock)
            val shown = alerts.count { notifier.notify(it, today) }
            Log.i(TAG, "Controllo offerte completato: ${alerts.size} affari, $shown notifiche")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Controllo delle offerte non riuscito, verrà ritentato", e)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "DealCheckWorker"
    }
}

/** Pianifica il controllo ogni [CHECK_INTERVAL_HOURS] ore, con rete disponibile, finché ci sono viaggi seguiti. */
class DealCheckScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context)

    fun schedule() {
        val request = PeriodicWorkRequestBuilder<DealCheckWorker>(CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_DELAY_MINUTES, TimeUnit.MINUTES)
            .build()
        // KEEP: un controllo già pianificato non viene riavviato a ogni apertura dell'app.
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "partimo-deal-check"
        const val CHECK_INTERVAL_HOURS = 6L
        const val RETRY_DELAY_MINUTES = 15L
    }
}
