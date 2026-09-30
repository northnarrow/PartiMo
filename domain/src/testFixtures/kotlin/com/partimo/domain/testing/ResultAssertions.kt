package com.partimo.domain.testing

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult

/** Restituisce i dati di un [DataResult.Success] o fa fallire il test con un messaggio chiaro. */
fun <T> DataResult<T>.successData(): T = when (this) {
    is DataResult.Success -> data
    is DataResult.Failure -> throw AssertionError("Atteso DataResult.Success, ottenuto $this")
}

/** Restituisce l'errore di un [DataResult.Failure] o fa fallire il test. */
fun DataResult<*>.failureError(): DataError = when (this) {
    is DataResult.Failure -> error
    is DataResult.Success -> throw AssertionError("Atteso DataResult.Failure, ottenuto $this")
}
