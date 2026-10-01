package com.partimo.data.remote.holidays

import com.partimo.data.repository.DefaultHolidayRepository
import com.partimo.data.testing.inMemoryCache
import com.partimo.data.testing.jsonHeaders
import com.partimo.data.testing.mockHttpClient
import com.partimo.domain.model.event.EventKind
import com.partimo.domain.model.event.EventTiming
import com.partimo.domain.testing.successData
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NagerHolidayDataSourceTest {

    private val userAgent = "PartiMoTest/1.0 (https://example.test/partimo)"

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/holidays/$name")) { "Fixture mancante: $name" }.readText()

    private fun source(language: String = "it", handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        NagerHolidayDataSource(mockHttpClient(handler), inMemoryCache(), userAgent, language, baseUrl = "https://nager.test/api/v3")

    @Test
    fun `le festività nazionali austriache arrivano con il nome italiano e quello locale`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val holidays = source { request ->
            requests += request
            respond(fixture("public_holidays_2026_AT.json"), HttpStatusCode.OK, jsonHeaders)
        }.publicHolidays("at", 2026, forceRefresh = false).data

        assertEquals("https://nager.test/api/v3/PublicHolidays/2026/AT", requests.single().url.toString())
        assertEquals(userAgent, requests.single().headers[HttpHeaders.UserAgent])
        assertEquals(15, holidays.size, "Solo le festività valide in tutto il paese")
        assertTrue(holidays.all { it.kind == EventKind.PUBLIC_HOLIDAY && it.location == null })
        val immaculate = holidays.single { it.timing == EventTiming.OnDates(LocalDate.of(2026, Month.DECEMBER, 8), LocalDate.of(2026, Month.DECEMBER, 8)) }
        assertEquals("Immacolata Concezione", immaculate.name)
        assertEquals("Mariä Empfängnis", immaculate.localName)
        assertEquals("Festa nazionale", holidays.single { it.localName == "Nationalfeiertag" }.name)
    }

    @Test
    fun `in Italia il nome è già italiano e non si ripete`() = runTest {
        val holidays = source { respond(fixture("public_holidays_2026_IT.json"), HttpStatusCode.OK, jsonHeaders) }
            .publicHolidays("IT", 2026, forceRefresh = false).data

        val stephen = holidays.single { (it.timing as EventTiming.OnDates).start == LocalDate.of(2026, Month.DECEMBER, 26) }
        assertEquals("Santo Stefano", stephen.name)
        assertNull(stephen.localName)
    }

    @Test
    fun `con l'app in inglese restano i nomi inglesi`() = runTest {
        val holidays = source(language = "en") { respond(fixture("public_holidays_2026_AT.json"), HttpStatusCode.OK, jsonHeaders) }
            .publicHolidays("AT", 2026, forceRefresh = false).data

        assertEquals("Immaculate Conception", holidays.single { it.localName == "Mariä Empfängnis" }.name)
    }

    @Test
    fun `un paese sconosciuto al servizio non ha festività, non è un errore`() = runTest {
        var calls = 0
        val source = source { calls++; respond("", HttpStatusCode.NotFound) }
        val repository = DefaultHolidayRepository(source, StandardTestDispatcher(testScheduler))

        assertEquals(emptyList(), repository.publicHolidays("ZZ", 2026).successData())
        assertEquals(emptyList(), repository.publicHolidays("italia", 2026).successData(), "Codice non valido: nessuna richiesta")
        assertEquals(1, calls)
    }
}
