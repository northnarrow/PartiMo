package com.partimo.data.network

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.common.DataResult
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.format.DateTimeParseException
import kotlin.coroutines.cancellation.CancellationException

/** Dato recuperato dal data layer insieme alla sua provenienza (rete, cache, demo). */
data class Fetched<out T>(val data: T, val origin: DataOrigin)

/** Provenienza complessiva di più risposte: basta un dato "stale" perché la UI lo segnali. */
internal fun combinedOrigin(origins: List<DataOrigin>): DataOrigin = when {
    origins.isEmpty() -> DataOrigin.REMOTE
    DataOrigin.STALE_CACHE in origins -> DataOrigin.STALE_CACHE
    origins.all { it == DataOrigin.CACHE } -> DataOrigin.CACHE
    origins.all { it == DataOrigin.DEMO } -> DataOrigin.DEMO
    else -> DataOrigin.REMOTE
}

/**
 * Confine degli errori del data layer: esegue [block] sul dispatcher di I/O e converte ogni
 * eccezione (rete, HTTP, parsing) in un [DataError]. La cancellazione viene sempre propagata.
 */
internal suspend fun <T> safeApiCall(
    dispatcher: CoroutineDispatcher,
    block: suspend () -> Fetched<T>,
): DataResult<T> = withContext(dispatcher) {
    try {
        val fetched = block()
        DataResult.Success(fetched.data, fetched.origin)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DataResult.Failure(e.toDataError())
    }
}

/** Il provider ha rifiutato la chiave API (es. Gemini risponde 400 `API_KEY_INVALID` invece di 401). */
internal class ApiKeyRejectedException(message: String? = null) : Exception(message)

/** Risposta ricevuta ma inutilizzabile: vuota, bloccata dai filtri o non conforme al formato richiesto. */
internal class UnusableResponseException(message: String) : Exception(message)

/** Traduce le eccezioni di Ktor, della JVM e di kotlinx.serialization in errori di dominio. */
internal fun Throwable.toDataError(): DataError = when (this) {
    is ApiKeyRejectedException -> DataError.Unauthorized
    is UnusableResponseException -> DataError.InvalidResponse
    is ResponseException -> httpStatusToDataError(response.status.value)
    // I timeout estendono IOException: vanno riconosciuti prima dei generici errori di I/O.
    is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException -> DataError.Timeout
    is SerializationException, is DateTimeParseException, is IllegalArgumentException -> DataError.InvalidResponse
    is IOException -> DataError.NoConnection
    else -> DataError.Unknown(message)
}

internal fun httpStatusToDataError(code: Int): DataError = when (code) {
    401, 403 -> DataError.Unauthorized
    429 -> DataError.RateLimited
    in 500..599 -> DataError.Server(code)
    else -> DataError.Client(code)
}

/** Errori transitori per cui ha senso servire un dato scaduto dalla cache come fallback. */
internal fun Throwable.isTransient(): Boolean = when (toDataError()) {
    DataError.NoConnection, DataError.Timeout, DataError.RateLimited, is DataError.Server -> true
    else -> false
}

/** Esegue [block] ignorando gli errori (per arricchimenti facoltativi) ma propagando la cancellazione. */
internal suspend fun <T> bestEffort(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

/**
 * Converte gli elementi di una risposta scartando quelli malformati:
 * un singolo record incoerente non deve invalidare l'intera lista.
 */
internal inline fun <T, R : Any> List<T>.mapNotNullSafely(transform: (T) -> R?): List<R> =
    mapNotNull { item -> runCatching { transform(item) }.getOrNull() }
