package com.partimo.data.network

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Esegue [attempts] in ordine e restituisce il primo risultato riuscito.
 *
 * - Un tentativo fallito per un errore per cui [shouldTryNext] è `true` fa partire subito il successivo;
 *   con un altro errore si rinuncia e lo si propaga.
 * - Un tentativo che non risponde entro [hedgeAfterMillis] fa partire in parallelo anche il successivo
 *   (richiesta "di riserva"): vince il primo che riesce, gli altri vengono annullati.
 * - Se falliscono tutti si propaga l'errore dell'ultimo che ha risposto.
 */
internal suspend fun <T> firstSuccessful(
    attempts: List<suspend () -> T>,
    hedgeAfterMillis: Long,
    shouldTryNext: (Throwable) -> Boolean,
): T = coroutineScope {
    require(attempts.isNotEmpty()) { "Serve almeno un tentativo" }
    // Esiti e scadenze arrivano dallo stesso canale: nessun esito va perso per un timeout.
    val events = Channel<AttemptEvent<T>>(Channel.UNLIMITED)
    val jobs = mutableListOf<Job>()
    var hedgeTimer: Job? = null

    fun startNext() {
        val attempt = attempts[jobs.size]
        jobs += launch {
            val outcome = try {
                Result.success(attempt())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            events.send(AttemptEvent.Finished(outcome))
        }
        hedgeTimer?.cancel()
        hedgeTimer = if (jobs.size < attempts.size) {
            launch {
                delay(hedgeAfterMillis)
                events.send(AttemptEvent.Hedge)
            }
        } else {
            null
        }
    }

    try {
        startNext()
        var failures = 0
        for (event in events) {
            when (event) {
                AttemptEvent.Hedge -> if (jobs.size < attempts.size) startNext()
                is AttemptEvent.Finished -> {
                    val error = event.outcome.exceptionOrNull() ?: return@coroutineScope event.outcome.getOrThrow()
                    failures++
                    when {
                        !shouldTryNext(error) -> throw error
                        jobs.size < attempts.size -> startNext()
                        failures == jobs.size -> throw error
                    }
                }
            }
        }
        error("Canale dei tentativi chiuso inaspettatamente")
    } finally {
        hedgeTimer?.cancel()
        jobs.forEach { it.cancel() }
    }
}

private sealed interface AttemptEvent<out T> {
    data class Finished<T>(val outcome: Result<T>) : AttemptEvent<T>

    data object Hedge : AttemptEvent<Nothing>
}
