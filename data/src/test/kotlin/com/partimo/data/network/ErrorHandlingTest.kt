package com.partimo.data.network

import com.partimo.data.testing.jsonHeaders
import com.partimo.data.testing.mockHttpClient
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.common.DataResult
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ErrorHandlingTest {

    private suspend fun errorForStatus(status: HttpStatusCode): DataError {
        val client = mockHttpClient { respond("{}", status, jsonHeaders) }
        val exception = runCatching { client.get("https://provider.test/resource") }.exceptionOrNull()
        return requireNotNull(exception) { "Attesa un'eccezione per lo status $status" }.toDataError()
    }

    @Test
    fun `gli status HTTP diventano errori di dominio`() = runTest {
        assertEquals(DataError.Unauthorized, errorForStatus(HttpStatusCode.Unauthorized))
        assertEquals(DataError.Unauthorized, errorForStatus(HttpStatusCode.Forbidden))
        assertEquals(DataError.RateLimited, errorForStatus(HttpStatusCode.TooManyRequests))
        assertEquals(DataError.Server(503), errorForStatus(HttpStatusCode.ServiceUnavailable))
        assertEquals(DataError.Client(404), errorForStatus(HttpStatusCode.NotFound))
    }

    @Test
    fun `errori di rete, timeout e parsing vengono classificati`() {
        assertEquals(DataError.NoConnection, IOException("offline").toDataError())
        assertEquals(DataError.NoConnection, UnknownHostException("api.provider.test").toDataError())
        assertEquals(DataError.Timeout, SocketTimeoutException("lento").toDataError())
        assertEquals(DataError.InvalidResponse, SerializationException("json inatteso").toDataError())
        assertTrue(DataError.Unknown(null) == IllegalStateException().toDataError())
    }

    @Test
    fun `solo gli errori transitori abilitano il fallback sulla cache`() {
        assertTrue(IOException().isTransient())
        assertTrue(SocketTimeoutException().isTransient())
        assertFalse(SerializationException("x").isTransient())
    }

    @Test
    fun `safeApiCall converte le eccezioni e preserva l'origine`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)

        assertEquals(DataResult.Success(1, DataOrigin.CACHE), safeApiCall(dispatcher) { Fetched(1, DataOrigin.CACHE) })
        assertEquals(
            DataResult.Failure(DataError.NoConnection),
            safeApiCall<Int>(dispatcher) { throw IOException("offline") },
        )
    }

    @Test
    fun `mapNotNullSafely scarta gli elementi malformati`() {
        val parsed = listOf("1", "x", "3").mapNotNullSafely { it.toInt() }

        assertEquals(listOf(1, 3), parsed)
    }
}
