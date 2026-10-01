package com.partimo.data.remote.currency

import com.partimo.data.repository.DefaultExchangeRateRepository
import com.partimo.data.testing.inMemoryCache
import com.partimo.data.testing.jsonHeaders
import com.partimo.data.testing.mockHttpClient
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.testing.failureError
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ExchangeRateApiDataSourceTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun fixture(): String = requireNotNull(javaClass.getResource("/currency/latest_eur.json")).readText()

    private fun source(body: String = fixture(), status: HttpStatusCode = HttpStatusCode.OK) = ExchangeRateApiDataSource(
        client = mockHttpClient { request ->
            requests += request
            respond(body, status, jsonHeaders)
        },
        cache = inMemoryCache(),
        userAgent = "PartiMoTest/1.0",
        baseUrl = "https://fx.test/v6",
    )

    @Test
    fun `i tassi reali dall'euro coprono anche le valute fuori dalla BCE e restano in cache`() = runTest {
        val source = source()

        val first = source.latestRates("eur", forceRefresh = false)
        val second = source.latestRates("EUR", forceRefresh = false)

        val rates = first.data
        assertEquals("EUR", rates.base)
        assertEquals(0, BigDecimal("24.431512").compareTo(rates.rates.getValue("CZK")))
        assertEquals(0, BigDecimal("10.999787").compareTo(rates.rates.getValue("MAD")), "Dirham marocchino, non quotato dalla BCE")
        assertEquals(Instant.ofEpochSecond(1_790_812_951), rates.updatedAt)
        assertEquals(DataOrigin.CACHE, second.origin)
        assertEquals("https://fx.test/v6/latest/EUR", requests.single().url.toString())
        assertEquals("PartiMoTest/1.0", requests.single().headers[HttpHeaders.UserAgent])
    }

    @Test
    fun `una risposta di errore del servizio è una risposta non valida`() = runTest {
        val repository = DefaultExchangeRateRepository(source("""{"result":"error","error-type":"unsupported-code"}"""), Dispatchers.Unconfined)

        assertEquals(DataError.InvalidResponse, repository.latestRates("EUR").failureError())
    }

    @Test
    fun `troppe richieste diventano il limite di richieste`() = runTest {
        val repository = DefaultExchangeRateRepository(source("", HttpStatusCode.TooManyRequests), Dispatchers.Unconfined)

        assertEquals(DataError.RateLimited, repository.latestRates("EUR").failureError())
    }
}
