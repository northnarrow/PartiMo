package com.partimo.domain.common

/**
 * Esito di un'operazione sui dati.
 *
 * I repository non propagano eccezioni verso i casi d'uso o la UI: restituiscono sempre un
 * [DataResult], che la presentation traduce negli stati Loading/Success/Error/Empty.
 */
sealed interface DataResult<out T> {

    /** Dati disponibili. [origin] indica se arrivano dalla rete, dalla cache o dalla sorgente demo. */
    data class Success<out T>(
        val data: T,
        val origin: DataOrigin = DataOrigin.REMOTE,
    ) : DataResult<T>

    /** Operazione fallita con un errore tipizzato. */
    data class Failure(val error: DataError) : DataResult<Nothing>
}

/** Provenienza di un dato: permette alla UI di segnalare dati offline o dimostrativi. */
enum class DataOrigin {
    /** Risposta appena ricevuta dall'API. */
    REMOTE,

    /** Risposta ancora valida (TTL non scaduto) letta dalla cache: nessuna chiamata di rete. */
    CACHE,

    /** Rete non disponibile: dato scaduto servito dalla cache come fallback. */
    STALE_CACHE,

    /** Dati dimostrativi generati localmente (chiave API non configurata). */
    DEMO,

    /** Dati inclusi nell'app (es. dataset degli aeroporti, catalogo delle mete). */
    LOCAL,
}

inline fun <T, R> DataResult<T>.map(transform: (T) -> R): DataResult<R> = when (this) {
    is DataResult.Success -> DataResult.Success(transform(data), origin)
    is DataResult.Failure -> this
}

fun <T> DataResult<T>.getOrNull(): T? = (this as? DataResult.Success)?.data
