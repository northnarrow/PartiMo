package com.partimo.data.remote.travelpayouts

import com.partimo.data.local.BundledFlightCodes
import com.partimo.data.repository.DefaultFlightRepository
import com.partimo.data.testing.MutableClock
import com.partimo.data.testing.inMemoryCache
import com.partimo.data.testing.jsonHeaders
import com.partimo.data.testing.mockHttpClient
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.Travellers
import com.partimo.domain.model.flight.FlexibleDates
import com.partimo.domain.model.flight.FlightSearchQuery
import com.partimo.domain.testing.failureError
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Prezzi dei voli di Aviasales, provati sulle risposte reali della Data API (registrate il 1° ottobre 2026). */
class TravelpayoutsFlightDataSourceTest {

    private val requests = mutableListOf<HttpRequestData>()
    private val clock = MutableClock()
    private val codes = BundledFlightCodes(
        openAirports = { File("src/main/assets/airport_cities.csv").inputStream() },
        openAirlines = { File("src/main/assets/airlines.csv").inputStream() },
    )

    private fun fixture(name: String): String = requireNotNull(javaClass.getResource("/travelpayouts/$name")).readText()

    private fun source(respond: (HttpRequestData) -> Pair<String, HttpStatusCode>) = TravelpayoutsFlightDataSource(
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

    /** Un viaggio a Vienna «a dicembre»: vanno bene tutte le partenze del mese. */
    private val december = FlightSearchQuery(
        originIata = "FCO",
        destinationIata = "VIE",
        departureDate = LocalDate.of(2026, Month.DECEMBER, 10),
        returnDate = LocalDate.of(2026, Month.DECEMBER, 14),
        flexibleDates = FlexibleDates(LocalDate.of(2026, Month.DECEMBER, 1)..LocalDate.of(2026, Month.DECEMBER, 31), TravelPeriod.FLEXIBLE_STAY_NIGHTS),
    )

    @Test
    fun `cerca per città tutto il mese e tiene i soggiorni da due a sette notti`() = runTest {
        val offers = source("prices_rom_vie_2026_12.json").searchOffers(december, forceRefresh = false).data

        val request = requests.single()
        assertEquals("/aviasales/v3/prices_for_dates", request.url.encodedPath)
        assertEquals("ROM", request.url.parameters["origin"], "Fiumicino e Ciampino insieme")
        assertEquals("VIE", request.url.parameters["destination"])
        assertEquals("2026-12", request.url.parameters["departure_at"])
        assertEquals("false", request.url.parameters["one_way"])
        assertEquals("eur", request.url.parameters["currency"])
        assertEquals("token-di-prova", request.headers["X-Access-Token"])
        assertFalse("token-di-prova" in request.url.toString(), "Il token non va nell'URL")

        // Su 13 tariffe restano quelle da 2 a 7 notti con al massimo uno scalo per tratta.
        assertEquals(listOf("88.00", "104.00", "153.00", "165.00", "166.00", "181.00", "228.00"), offers.map { it.totalPrice.amount.toPlainString() })
        val cheapest = offers.first()
        assertEquals("Ryanair", cheapest.carrierName)
        assertEquals("https://pics.avs.io/96/96/FR.png", cheapest.carrierLogoUrl)
        assertEquals(LocalDateTime.of(2026, Month.DECEMBER, 11, 21, 10), cheapest.outbound.departureTime)
        assertEquals(LocalDateTime.of(2026, Month.DECEMBER, 11, 22, 55), cheapest.outbound.arrivalTime)
        assertEquals(listOf("FR7178"), cheapest.outbound.flightNumbers)
        assertEquals("VIE", cheapest.inbound?.originIata)
        assertEquals(LocalDateTime.of(2026, Month.DECEMBER, 13, 10, 10), cheapest.inbound?.arrivalTime)
        assertEquals(2, cheapest.stayNights)
        assertTrue(cheapest.bookingUrl!!.startsWith("https://www.aviasales.com/search/ROM1112VIE13121?t="), cheapest.bookingUrl)
        assertEquals(LocalDate.of(2026, Month.SEPTEMBER, 30), cheapest.priceFoundOn)

        val ciampino = offers.single { it.outbound.originIata == "CIA" }
        assertEquals(1, ciampino.outbound.stops)
        assertEquals(Duration.ofMinutes(185), ciampino.outbound.duration)
        assertEquals("Austrian Airlines", offers.single { it.carrierIata == "OS" }.carrierName)
    }

    @Test
    fun `da Milano a Londra si trovano i voli low cost di Bergamo e Stansted con gli orari locali`() = runTest {
        val november = FlightSearchQuery(
            originIata = "MXP",
            destinationIata = "LHR",
            departureDate = LocalDate.of(2026, Month.NOVEMBER, 10),
            returnDate = LocalDate.of(2026, Month.NOVEMBER, 14),
            flexibleDates = FlexibleDates(LocalDate.of(2026, Month.NOVEMBER, 1)..LocalDate.of(2026, Month.NOVEMBER, 30), TravelPeriod.FLEXIBLE_STAY_NIGHTS),
        )

        val offers = source("prices_mil_lon_2026_11.json").searchOffers(november, forceRefresh = false).data

        assertEquals("MIL", requests.single().url.parameters["origin"])
        assertEquals("LON", requests.single().url.parameters["destination"])
        assertEquals(11, offers.size, "Esclusa la tariffa con il ritorno dopo 15 notti")
        val first = offers.first()
        assertEquals("BGY", first.outbound.originIata)
        assertEquals("STN", first.outbound.destinationIata)
        // Partenza alle 6 ora italiana, 2 h 05 di volo: a Londra sono le 7:05.
        assertEquals(LocalDateTime.of(2026, Month.NOVEMBER, 9, 7, 5), first.outbound.arrivalTime)
        // Ritorno alle 19:30 ora di Londra, 1 h 55 di volo: in Italia sono le 22:25.
        assertEquals(LocalDateTime.of(2026, Month.NOVEMBER, 12, 19, 30), first.inbound?.departureTime)
        assertEquals(LocalDateTime.of(2026, Month.NOVEMBER, 12, 22, 25), first.inbound?.arrivalTime)
    }

    @Test
    fun `con le date esatte il ritorno dopo il cambio dell'ora arriva all'ora solare`() = runTest {
        val exact = FlightSearchQuery(
            originIata = "FCO",
            destinationIata = "VIE",
            departureDate = LocalDate.of(2026, Month.OCTOBER, 22),
            returnDate = LocalDate.of(2026, Month.OCTOBER, 27),
        )

        val offer = source("prices_rom_vie_2026_10.json").searchOffers(exact, forceRefresh = false).data.single()

        assertEquals("2026-10-22", requests.single().url.parameters["departure_at"], "Solo il giorno di partenza...")
        assertEquals("2026-10-27", requests.single().url.parameters["return_at"], "...e quello di ritorno")
        assertEquals("75.00", offer.totalPrice.amount.toPlainString())
        assertEquals(LocalDateTime.of(2026, Month.OCTOBER, 22, 10, 15), offer.outbound.arrivalTime)
        // Il 25 ottobre torna l'ora solare: 12:40 a Vienna più 4 h 15 con uno scalo fa le 16:55 a Roma.
        assertEquals(LocalDateTime.of(2026, Month.OCTOBER, 27, 16, 55), offer.inbound?.arrivalTime)
        assertEquals(1, offer.inbound?.stops)
    }

    @Test
    fun `una finestra a cavallo di due mesi li cerca entrambi`() = runTest {
        val source = source { request ->
            val body = if (request.url.parameters["departure_at"] == "2026-10") fixture("prices_rom_vie_2026_10.json") else EMPTY
            body to HttpStatusCode.OK
        }
        val nextDays = december.copy(
            departureDate = LocalDate.of(2026, Month.OCTOBER, 28),
            returnDate = LocalDate.of(2026, Month.NOVEMBER, 1),
            flexibleDates = FlexibleDates(LocalDate.of(2026, Month.OCTOBER, 28)..LocalDate.of(2026, Month.NOVEMBER, 3), TravelPeriod.FLEXIBLE_STAY_NIGHTS),
        )

        val offers = source.searchOffers(nextDays, forceRefresh = false).data

        assertEquals(listOf("2026-10", "2026-11"), requests.map { it.url.parameters["departure_at"] })
        assertEquals(listOf("116.00", "131.00"), offers.map { it.totalPrice.amount.toPlainString() })
        assertTrue(offers.all { it.outbound.originIata == "CIA" })
    }

    @Test
    fun `con le date scelte si cercano quei giorni e il mese per le date vicine`() = runTest {
        val dates = TravelPeriod.Dates(LocalDate.of(2026, Month.DECEMBER, 11), LocalDate.of(2026, Month.DECEMBER, 13))
        val query = december.copy(departureDate = dates.departure, returnDate = dates.returning, flexibleDates = dates.flexibleDates(LocalDate.of(2026, Month.OCTOBER, 1)))
        val source = source { request ->
            val body = if (request.url.parameters["return_at"] != null) EMPTY else fixture("prices_rom_vie_2026_12.json")
            body to HttpStatusCode.OK
        }

        val offers = source.searchOffers(query, forceRefresh = false).data

        assertEquals(listOf("2026-12-11" to "2026-12-13", "2026-12" to null), requests.map { it.url.parameters["departure_at"] to it.url.parameters["return_at"] })
        // Partenze dall'8 al 14 dicembre, da 0 a 4 notti: delle tariffe del mese resta solo quella dell'11-13.
        assertEquals(listOf("88.00"), offers.map { it.totalPrice.amount.toPlainString() })
        assertTrue(query.isOnTripDates(offers.single()))
    }

    @Test
    fun `le tariffe delle date scelte restano anche con tante tariffe più basse nei giorni vicini`() = runTest {
        val dates = TravelPeriod.Dates(LocalDate.of(2026, Month.DECEMBER, 11), LocalDate.of(2026, Month.DECEMBER, 13))
        val query = december.copy(departureDate = dates.departure, returnDate = dates.returning, flexibleDates = dates.flexibleDates(LocalDate.of(2026, Month.OCTOBER, 1)))
        fun fare(price: Int, departure: String, returning: String, number: Int) =
            """{"origin_airport":"FCO","destination_airport":"VIE","price":$price,"airline":"FR","flight_number":"$number",""" +
                """"departure_at":"${departure}T21:10:00+01:00","return_at":"${returning}T08:25:00+01:00","transfers":0,""" +
                """"return_transfers":0,"duration_to":105,"duration_back":105}"""
        val nearby = (1..45).map { fare(30 + it, "2026-12-10", "2026-12-12", 1000 + it) }
        val body = """{"success":true,"currency":"eur","data":[${(nearby + fare(120, "2026-12-11", "2026-12-13", 7178)).joinToString(",")}]}"""

        val offers = source { body to HttpStatusCode.OK }.searchOffers(query, forceRefresh = false).data

        assertEquals(40, offers.size)
        assertTrue(query.isOnTripDates(offers.first()), "La tariffa delle date scelte viene prima")
        assertEquals("120.00", offers.first().totalPrice.amount.toPlainString())
    }

    @Test
    fun `i prezzi restano in cache tre ore e il prezzo è per tutti i viaggiatori`() = runTest {
        val source = source("prices_rom_vie_2026_12.json")

        val first = source.searchOffers(december, forceRefresh = false)
        val cached = source.searchOffers(december.copy(travellers = Travellers(adults = 2)), forceRefresh = false)
        clock.advance(Duration.ofHours(3).plusMinutes(1))
        val refreshed = source.searchOffers(december, forceRefresh = false)

        assertEquals(DataOrigin.REMOTE, first.origin)
        assertEquals(DataOrigin.CACHE, cached.origin)
        assertEquals("176.00", cached.data.first().totalPrice.amount.toPlainString())
        assertEquals(DataOrigin.REMOTE, refreshed.origin)
        assertEquals(2, requests.size)
    }

    @Test
    fun `per una famiglia il prezzo è per tutti i posti e Aviasales si apre con adulti, bambini e neonati`() = runTest {
        val family = december.copy(travellers = Travellers(adults = 2, childAges = listOf(7, 1)))

        val cheapest = source("prices_rom_vie_2026_12.json").searchOffers(family, forceRefresh = false).data.first()

        assertEquals("264.00", cheapest.totalPrice.amount.toPlainString(), "88 € per tre posti: il neonato viaggia in braccio")
        assertEquals(3, cheapest.passengers)
        assertEquals("88.00", cheapest.pricePerPassenger.amount.toPlainString())
        assertTrue(cheapest.bookingUrl!!.startsWith("https://www.aviasales.com/search/ROM1112VIE1312211?t="), cheapest.bookingUrl)
    }

    @Test
    fun `i passeggeri di Aviasales sono adulti, bambini da 2 a 11 anni e neonati`() {
        assertEquals("1", AviasalesPassengers.code(Travellers.SOLO))
        assertEquals("2", AviasalesPassengers.code(Travellers(adults = 2)))
        assertEquals("21", AviasalesPassengers.code(Travellers(adults = 2, childAges = listOf(7))))
        assertEquals("101", AviasalesPassengers.code(Travellers(adults = 1, childAges = listOf(0))))
        assertEquals("2", AviasalesPassengers.code(Travellers(adults = 1, childAges = listOf(14))), "Dai 12 anni si paga come un adulto")

        val family = Travellers(adults = 2, childAges = listOf(5, 1))
        assertEquals("/search/ROM1112VIE1312211?t=FR123&search_date=30092026", AviasalesPassengers.applyTo("/search/ROM1112VIE13121?t=FR123&search_date=30092026", family))
        assertEquals("/search/ROM1112VIE211", AviasalesPassengers.applyTo("/search/ROM1112VIE1", family), "Sola andata")
        assertEquals("/search/ROM1112VIE13121", AviasalesPassengers.applyTo("/search/ROM1112VIE13121", Travellers.SOLO))
        assertEquals("/flights/?origin=ROM", AviasalesPassengers.applyTo("/flights/?origin=ROM", family), "Percorso sconosciuto: invariato")
    }

    @Test
    fun `senza collegamento si apre la ricerca della tratta e una compagnia sconosciuta resta col codice`() = runTest {
        val body = """
            {"success":true,"currency":"eur","data":[{"origin":"ROM","destination":"VIE","origin_airport":"FCO",
            "destination_airport":"VIE","price":90,"airline":"QE","flight_number":123,"departure_at":"2026-12-11T21:10:00+01:00",
            "return_at":"2026-12-14T08:25:00+01:00","transfers":0,"return_transfers":0,"duration":210,"duration_to":105,"duration_back":105}]}
        """.trimIndent()

        val offer = source { body to HttpStatusCode.OK }.searchOffers(december, forceRefresh = false).data.single()

        assertEquals("https://www.aviasales.com/search/FCO1112VIE14121", offer.bookingUrl)
        assertEquals("QE", offer.carrierName)
        assertEquals(listOf("QE123"), offer.outbound.flightNumbers)
        assertNull(offer.priceFoundOn)
    }

    @Test
    fun `un token rifiutato e una risposta senza successo diventano errori`() = runTest {
        val rejected = DefaultFlightRepository(source { "Unauthorized" to HttpStatusCode.Unauthorized }, Dispatchers.Unconfined)
        val failed = DefaultFlightRepository(source { """{"success":false,"error":"bad request"}""" to HttpStatusCode.OK }, Dispatchers.Unconfined)

        assertEquals(DataError.Unauthorized, rejected.searchFlights(december).failureError())
        assertEquals(DataError.InvalidResponse, failed.searchFlights(december).failureError())
    }

    private companion object {
        const val EMPTY = """{"data":[],"currency":"eur","success":true}"""
    }
}
