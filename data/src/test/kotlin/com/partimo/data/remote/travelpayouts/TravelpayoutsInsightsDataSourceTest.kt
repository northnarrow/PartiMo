package com.partimo.data.remote.travelpayouts

import com.partimo.data.local.BundledFlightCodes
import com.partimo.data.repository.DefaultFlightInsightsRepository
import com.partimo.data.testing.MutableClock
import com.partimo.data.testing.inMemoryCache
import com.partimo.data.testing.jsonHeaders
import com.partimo.data.testing.mockHttpClient
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.flight.AnywhereQuery
import com.partimo.domain.model.flight.FareLevel
import com.partimo.domain.testing.failureError
import com.partimo.domain.testing.successData
import com.partimo.domain.usecase.FindCheapDestinationsUseCase
import com.partimo.domain.usecase.GetPriceCalendarUseCase
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mesi, giorni e mete più convenienti, provati sulle risposte reali della Data API (registrate il 1° ottobre 2026). */
class TravelpayoutsInsightsDataSourceTest {

    private val requests = mutableListOf<HttpRequestData>()
    private val clock = MutableClock()
    private val codes = BundledFlightCodes(
        openAirports = { File("src/main/assets/airport_cities.csv").inputStream() },
        openAirlines = { File("src/main/assets/airlines.csv").inputStream() },
        openCities = { File("src/main/assets/cities.csv").inputStream() },
    )
    private val december = YearMonth.of(2026, Month.DECEMBER)
    private val today = LocalDate.of(2026, Month.OCTOBER, 1)

    private fun fixture(name: String): String = requireNotNull(javaClass.getResource("/travelpayouts/$name")).readText()

    private fun source(respond: (HttpRequestData) -> Pair<String, HttpStatusCode>) = TravelpayoutsInsightsDataSource(
        client = mockHttpClient { request ->
            requests += request
            val (body, status) = respond(request)
            respond(body, status, jsonHeaders)
        },
        cache = inMemoryCache(clock),
        token = "token-di-prova",
        codes = codes,
        baseUrl = "https://tp.test/",
    )

    private fun source(fixture: String) = source { fixture(fixture) to HttpStatusCode.OK }

    @Test
    fun `il prezzo più basso di ogni mese è per andata e ritorno da due a sette notti`() = runTest {
        val months = source("grouped_months_mil_vie.json").cheapestByMonth("MXP", "VIE", TravelPeriod.FLEXIBLE_STAY_NIGHTS, forceRefresh = false).data

        val request = requests.single()
        assertEquals("/aviasales/v3/grouped_prices", request.url.encodedPath)
        assertEquals("MIL", request.url.parameters["origin"], "Malpensa cerca per tutta Milano")
        assertEquals("VIE", request.url.parameters["destination"])
        assertEquals("month", request.url.parameters["group_by"])
        assertEquals("2", request.url.parameters["min_trip_duration"])
        assertEquals("7", request.url.parameters["max_trip_duration"])
        assertEquals("eur", request.url.parameters["currency"])
        assertEquals("token-di-prova", request.headers["X-Access-Token"])
        assertFalse("token-di-prova" in request.url.toString())

        assertEquals((0L..8L).map { YearMonth.of(2026, Month.OCTOBER).plusMonths(it) }, months.keys.sorted())
        assertEquals(listOf("33", "39", "72"), listOf(10, 11, 12).map { months.getValue(YearMonth.of(2026, it)).price.amount.stripTrailingZeros().toPlainString() })
        val dec = months.getValue(december)
        assertEquals(LocalDate.of(2026, Month.DECEMBER, 7), dec.departureDate)
        assertEquals(LocalDate.of(2026, Month.DECEMBER, 10), dec.returnDate)
        assertEquals(3L, dec.stayNights)
        assertEquals("FR", dec.carrierIata)
        assertEquals("33.00", months.values.minBy { it.price.amount }.price.amount.toPlainString(), "Ottobre è il mese più conveniente")
    }

    @Test
    fun `il calendario di dicembre ha il prezzo dei giorni con voli e le fasce di prezzo`() = runTest {
        val repository = DefaultFlightInsightsRepository(source("grouped_days_mil_vie_2026_12.json"), Dispatchers.Unconfined)

        val calendar = GetPriceCalendarUseCase(repository)("MXP", "VIE", december, TravelPeriod.InMonth(december)).successData()

        val request = requests.single()
        assertEquals("departure_at", request.url.parameters["group_by"])
        assertEquals("2026-12", request.url.parameters["departure_at"])
        assertEquals(10, calendar.fares.size)
        assertEquals(LocalDate.of(2026, Month.DECEMBER, 7), calendar.cheapest?.departureDate)
        assertEquals("72.00", calendar.cheapest?.price?.amount?.toPlainString())
        assertEquals(FareLevel.LOW, calendar.levels[LocalDate.of(2026, Month.DECEMBER, 7)])
        assertEquals(FareLevel.HIGH, calendar.levels[LocalDate.of(2026, Month.DECEMBER, 28)], "168 €, a ridosso di Capodanno")
        assertEquals(2, calendar.fares.getValue(LocalDate.of(2026, Month.DECEMBER, 4)).stops, "Due scali all'andata")
    }

    @Test
    fun `ovunque da Milano a dicembre trova le mete più economiche con il nome italiano`() = runTest {
        val repository = DefaultFlightInsightsRepository(source("anywhere_mil_2026_12.json"), Dispatchers.Unconfined)
        val clock = Clock.fixed(today.atStartOfDay(ZoneId.of("Europe/Rome")).toInstant(), ZoneId.of("Europe/Rome"))

        val found = FindCheapDestinationsUseCase(repository, clock)("MXP", TravelPeriod.InMonth(december)).successData()

        val request = requests.single()
        assertEquals("/aviasales/v3/prices_for_dates", request.url.encodedPath)
        assertEquals("MIL", request.url.parameters["origin"])
        assertNull(request.url.parameters["destination"], "Nessuna destinazione: tutte")
        assertEquals("true", request.url.parameters["unique"])
        assertEquals("2026-12", request.url.parameters["departure_at"])
        assertEquals("false", request.url.parameters["one_way"])

        assertEquals(listOf("Palermo", "Tirana", "Bucarest", "Barcellona", "Alicante"), found.take(5).map { it.city.name })
        val palermo = found.first()
        assertEquals("29.00", palermo.fare.price.amount.toPlainString())
        assertEquals(LocalDate.of(2026, Month.DECEMBER, 10), palermo.fare.departureDate)
        assertEquals(5L, palermo.fare.stayNights)
        assertEquals("IT", palermo.city.countryCode)
        assertEquals("PMO", palermo.airportIata)
        assertTrue(palermo.bookingUrl!!.startsWith("https://www.aviasales.com/search/MIL1012PMO15121"), palermo.bookingUrl)
        val stockholm = found.single { it.cityCode == "STO" }
        assertEquals("Stoccolma", stockholm.city.name)
        assertEquals("Svezia", stockholm.city.country)
        assertEquals("ARN", stockholm.airportIata)
        assertTrue(found.none { it.cityCode == "LON" }, "A Londra la tariffa più bassa è per tre settimane: fuori dai soggiorni cercati")
        assertTrue(found.all { it.fare.stayNights in 2L..7L }, "Solo soggiorni da due a sette notti")
        assertTrue(found.size in 60..100)
        assertEquals(found.size, found.map { it.cityCode }.toSet().size)
    }

    @Test
    fun `ovunque con le date scelte cerca proprio quei giorni`() = runTest {
        val query = AnywhereQuery.of("MXP", TravelPeriod.Dates(LocalDate.of(2026, 12, 11), LocalDate.of(2026, 12, 13)), today)

        val found = source("anywhere_mil_2026_12_11_13.json").cheapestDestinations(query, forceRefresh = false).data

        assertEquals("2026-12-11", requests.single().url.parameters["departure_at"])
        assertEquals("2026-12-13", requests.single().url.parameters["return_at"])
        assertEquals(listOf("Napoli", "Barcellona", "Zagabria"), found.take(3).map { it.city.name })
        assertTrue(found.all { it.fare.departureDate == LocalDate.of(2026, 12, 11) && it.fare.returnDate == LocalDate.of(2026, 12, 13) })
    }

    @Test
    fun `i prezzi restano in cache tre ore e un token rifiutato è un errore`() = runTest {
        val source = source("grouped_months_mil_vie.json")
        source.cheapestByMonth("MXP", "VIE", 2L..7L, forceRefresh = false)
        val cached = source.cheapestByMonth("MXP", "VIE", 2L..7L, forceRefresh = false)
        clock.advance(Duration.ofHours(3).plusMinutes(1))
        source.cheapestByMonth("MXP", "VIE", 2L..7L, forceRefresh = false)

        assertEquals(DataOrigin.CACHE, cached.origin)
        assertEquals(2, requests.size)

        val rejected = DefaultFlightInsightsRepository(source { "Unauthorized" to HttpStatusCode.Unauthorized }, Dispatchers.Unconfined)
        assertEquals(DataError.Unauthorized, rejected.cheapestByMonth("MXP", "VIE", 2L..7L).failureError())
        val failed = DefaultFlightInsightsRepository(source { """{"success":false,"error":"bad"}""" to HttpStatusCode.OK }, Dispatchers.Unconfined)
        assertEquals(DataError.InvalidResponse, failed.cheapestByDay("MXP", "VIE", december, 2L..7L).failureError())
    }

    @Test
    fun `senza token le funzioni non ci sono e non chiedono nulla`() = runTest {
        val repository = DefaultFlightInsightsRepository(null, Dispatchers.Unconfined)

        assertFalse(repository.isAvailable)
        assertTrue(repository.cheapestByMonth("MXP", "VIE", 2L..7L).successData().isEmpty())
        assertTrue(requests.isEmpty())
    }
}
