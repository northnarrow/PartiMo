package com.partimo.data.remote

import com.partimo.data.remote.duffel.DuffelApi
import com.partimo.data.remote.duffel.DuffelFlightDataSource
import com.partimo.data.remote.duffel.DuffelStayDataSource
import com.partimo.data.testing.bodyText
import com.partimo.data.testing.inMemoryCache
import com.partimo.data.testing.jsonHeaders
import com.partimo.data.testing.mockHttpClient
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.model.Money
import com.partimo.domain.model.Travellers
import com.partimo.domain.model.flight.FlightSearchQuery
import com.partimo.domain.model.stay.AccommodationSearchQuery
import com.partimo.domain.testing.TestData
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.LocalDate
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals

class DuffelDataSourcesTest {

    private val offersJson = """
        {"data": {"id": "orq_1", "offers": [
          {"id": "off_1", "total_amount": "129.40", "total_currency": "EUR",
           "owner": {"name": "Austrian Airlines", "iata_code": "OS", "logo_symbol_url": "https://img.test/os.svg"},
           "expires_at": "2026-09-30T10:20:00Z", "total_emissions_kg": "98.4",
           "conditions": {"refund_before_departure": {"allowed": true}},
           "slices": [
             {"origin": {"iata_code": "MXP"}, "destination": {"iata_code": "VIE"}, "duration": "PT1H30M",
              "segments": [{"departing_at": "2026-12-12T08:05:00", "arriving_at": "2026-12-12T09:35:00",
                            "marketing_carrier": {"iata_code": "OS"}, "marketing_carrier_flight_number": "512"}]},
             {"origin": {"iata_code": "VIE"}, "destination": {"iata_code": "MXP"}, "duration": "PT3H55M",
              "segments": [
                {"departing_at": "2026-12-16T15:20:00", "arriving_at": "2026-12-16T16:35:00",
                 "marketing_carrier": {"iata_code": "OS"}, "marketing_carrier_flight_number": "111"},
                {"departing_at": "2026-12-16T17:40:00", "arriving_at": "2026-12-16T19:15:00",
                 "marketing_carrier": {"iata_code": "OS"}, "marketing_carrier_flight_number": "222"}]}
           ]},
          {"id": "off_broken", "total_amount": "non-valido", "total_currency": "EUR",
           "slices": [{"segments": [{"departing_at": "2026-12-12T10:00:00", "arriving_at": "2026-12-12T11:30:00"}]}]}
        ]}}
    """.trimIndent()

    private val staysJson = """
        {"data": {"results": [
          {"id": "srr_1", "cheapest_rate_total_amount": "480.00", "cheapest_rate_currency": "EUR",
           "accommodation": {"id": "acc_1", "name": "Hotel Test", "rating": 4, "review_score": 8.8,
             "photos": [{"url": "https://img.test/hotel.jpg"}],
             "location": {"geographic_coordinates": {"latitude": 48.2, "longitude": 16.37},
                          "address": {"line_one": "Ring 1", "city_name": "Vienna"}}}},
          {"id": "srr_no_price", "accommodation": {"id": "acc_2", "name": "Senza tariffa"}}
        ]}}
    """.trimIndent()

    private val flightQuery = FlightSearchQuery(
        originIata = "MXP",
        destinationIata = "VIE",
        departureDate = LocalDate.of(2026, Month.DECEMBER, 12),
        returnDate = LocalDate.of(2026, Month.DECEMBER, 16),
        travellers = Travellers(adults = 2, childAges = listOf(9, 1)),
    )

    @Test
    fun `crea la offer request e interpreta le offerte scartando quelle malformate`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = mockHttpClient { request ->
            requests += request
            respond(offersJson, HttpStatusCode.OK, jsonHeaders)
        }
        val source = DuffelFlightDataSource(DuffelApi(client, "duffel_test_token", "https://duffel.test/"), inMemoryCache())

        val offer = source.searchOffers(flightQuery, forceRefresh = false).data.single()

        assertEquals("off_1", offer.id)
        assertEquals(Money.of("129.40", "EUR"), offer.totalPrice)
        assertEquals("Austrian Airlines", offer.carrierName)
        assertEquals(Duration.ofMinutes(90), offer.outbound.duration)
        assertEquals(0, offer.outbound.stops)
        assertEquals(1, offer.inbound?.stops)
        assertEquals(listOf("OS512"), offer.outbound.flightNumbers)
        assertEquals(true, offer.refundable)
        assertEquals(98, offer.co2EmissionsKg)

        val request = requests.single()
        assertEquals("/air/offer_requests", request.url.encodedPath)
        assertEquals("true", request.url.parameters["return_offers"])
        assertEquals("Bearer duffel_test_token", request.headers[HttpHeaders.Authorization])
        assertEquals("v2", request.headers["Duffel-Version"])

        val data = Json.parseToJsonElement(request.bodyText()).jsonObject.getValue("data").jsonObject
        assertEquals(2, data.getValue("slices").jsonArray.size)
        assertEquals(
            listOf("""{"type":"adult"}""", """{"type":"adult"}""", """{"age":9}""", """{"age":1}"""),
            data.getValue("passengers").jsonArray.map { it.toString() },
            "Per i minori Duffel vuole solo l'età",
        )
        assertEquals("economy", data.getValue("cabin_class").jsonPrimitive.content)
        assertEquals(1, data.getValue("max_connections").jsonPrimitive.int)
    }

    @Test
    fun `una ricerca identica viene servita dalla cache`() = runTest {
        var calls = 0
        val client = mockHttpClient {
            calls++
            respond(offersJson, HttpStatusCode.OK, jsonHeaders)
        }
        val source = DuffelFlightDataSource(DuffelApi(client, "token", "https://duffel.test/"), inMemoryCache())

        source.searchOffers(flightQuery, forceRefresh = false)
        val second = source.searchOffers(flightQuery, forceRefresh = false)

        assertEquals(DataOrigin.CACHE, second.origin)
        assertEquals(1, calls)
    }

    @Test
    fun `interpreta la ricerca alloggi di Duffel Stays`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = mockHttpClient { request ->
            requests += request
            respond(staysJson, HttpStatusCode.OK, jsonHeaders)
        }
        val source = DuffelStayDataSource(DuffelApi(client, "token", "https://duffel.test/"), inMemoryCache())
        val query = AccommodationSearchQuery(
            location = TestData.VIENNA_CENTER,
            checkIn = LocalDate.of(2026, Month.DECEMBER, 12),
            checkOut = LocalDate.of(2026, Month.DECEMBER, 16),
            travellers = Travellers(adults = 2, childAges = listOf(6)),
        )

        val stay = source.searchStays(query, forceRefresh = false).data.single()

        assertEquals("Hotel Test", stay.name)
        assertEquals(4, stay.nights)
        assertEquals(Money.of("120.00", "EUR"), stay.pricePerNight)
        assertEquals(4, stay.starRating)
        assertEquals("Ring 1, Vienna", stay.address)

        val data = Json.parseToJsonElement(requests.single().bodyText()).jsonObject.getValue("data").jsonObject
        assertEquals("2026-12-12", data.getValue("check_in_date").jsonPrimitive.content)
        assertEquals(
            listOf("""{"type":"adult"}""", """{"type":"adult"}""", """{"type":"child","age":6}"""),
            data.getValue("guests").jsonArray.map { it.toString() },
        )
        assertEquals(5, data.getValue("location").jsonObject.getValue("radius").jsonPrimitive.int)
    }
}
