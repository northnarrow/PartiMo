package com.partimo.data.remote.osm

import com.partimo.data.repository.DefaultAccommodationRepository
import com.partimo.data.repository.DefaultLodgingRepository
import com.partimo.data.repository.DefaultRestaurantRepository
import com.partimo.data.source.NoStayOffersDataSource
import com.partimo.data.testing.inMemoryCache
import com.partimo.data.testing.jsonHeaders
import com.partimo.data.testing.mockHttpClient
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.WheelchairAccess
import com.partimo.domain.model.dining.RestaurantSearchQuery
import com.partimo.domain.model.stay.AccommodationSearchQuery
import com.partimo.domain.model.stay.LodgingQuery
import com.partimo.domain.model.stay.LodgingType
import com.partimo.domain.testing.TestData
import com.partimo.domain.testing.successData
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OsmDataSourcesTest {

    private val vienna = TestData.VIENNA_CENTER
    private val matera = GeoPoint(40.6664, 16.6043)
    private val userAgent = "PartiMoTest/1.0 (https://example.test/partimo)"
    private val endpoints = listOf("https://overpass-1.test/api/interpreter", "https://overpass-2.test/api/interpreter")

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/osm/$name")) { "Fixture mancante: $name" }.readText()

    private fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        OverpassApi(mockHttpClient(handler), userAgent, endpoints)

    private fun MockRequestHandleScope.json(body: String) = respond(body, HttpStatusCode.OK, jsonHeaders)

    private fun HttpRequestData.overpassQuery(): String = (body as FormDataContent).formData["data"].orEmpty()

    // ---- Ristoranti -----------------------------------------------------------------------------

    @Test
    fun `cerca i locali attorno al centro con una query Overpass e uno User-Agent identificabile`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val source = OsmRestaurantDataSource(api { request -> requests += request; json(fixture("restaurants_vienna.json")) }, inMemoryCache(), "it")

        source.searchRestaurants(RestaurantSearchQuery(vienna, areaName = "Vienna"), forceRefresh = false)

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(endpoints.first(), request.url.toString())
        assertEquals(userAgent, request.headers[HttpHeaders.UserAgent])
        val query = request.overpassQuery()
        assertTrue("\"amenity\"~\"^(restaurant|fast_food|food_court)$\"" in query, query)
        assertTrue("(around:1500,48.20820,16.37380)" in query, query)
        assertTrue(query.startsWith("[out:json]") && "out center tags" in query, query)
    }

    @Test
    fun `con i dati reali di Vienna i locali sono ordinati per completezza e vicinanza, catene in fondo`() = runTest {
        val source = OsmRestaurantDataSource(api { json(fixture("restaurants_vienna.json")) }, inMemoryCache(), "it")

        val restaurants = source.searchRestaurants(RestaurantSearchQuery(vienna, areaName = "Vienna"), forceRefresh = false).data

        assertEquals(OsmRestaurantDataSource.MAX_RESULTS, restaurants.size)
        val chains = setOf("McDonald's", "Subway", "Akakiko", "Burger King", "Vapiano")
        assertTrue(restaurants.take(10).none { it.name in chains }, "Catene in cima: ${restaurants.take(10).map { it.name }}")
        assertTrue(restaurants.all { it.rating == null && it.priceLevel == null && it.id.startsWith("osm:") })

        val pizza = restaurants.first { it.name == "Pizza Bizi" }
        assertEquals("Pizza", pizza.cuisine)
        assertEquals("Rotenturmstraße 4", pizza.address)
        assertEquals("https://www.pizzabizi.at/", pizza.website)
        assertEquals("Mo-Su,PH 11:00-24:00", pizza.openingHours)
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Pizza%20Bizi%2C%20Rotenturmstra%C3%9Fe%204%2C%20Vienna",
            pizza.mapsUrl,
        )
    }

    @Test
    fun `l'accessibilità in carrozzina si legge dal tag wheelchair`() {
        assertEquals(WheelchairAccess.YES, OsmLabels.wheelchair("yes"))
        assertEquals(WheelchairAccess.YES, OsmLabels.wheelchair("designated"))
        assertEquals(WheelchairAccess.LIMITED, OsmLabels.wheelchair(" Limited "))
        assertEquals(WheelchairAccess.NO, OsmLabels.wheelchair("no"))
        assertEquals(null, OsmLabels.wheelchair("unknown"))
    }

    @Test
    fun `le cucine di OpenStreetMap diventano etichette italiane`() {
        assertEquals("Colazioni · Regionale", OsmLabels.cuisine("breakfast;regional;international", "restaurant"))
        assertEquals("Coreana · Giapponese", OsmLabels.cuisine("korean; japanese", "restaurant"))
        assertEquals("Smørrebrød", OsmLabels.cuisine("smørrebrød", "restaurant"))
        assertEquals("Fast food", OsmLabels.cuisine(null, "fast_food"))
        assertNull(OsmLabels.cuisine(null, "restaurant"))
    }

    @Test
    fun `se un'istanza Overpass è sovraccarica prova la successiva`() = runTest {
        val hosts = mutableListOf<String>()
        val source = OsmRestaurantDataSource(
            api { request ->
                hosts += request.url.host
                if (request.url.host == "overpass-1.test") respond("Too many requests", HttpStatusCode.TooManyRequests) else json(fixture("restaurants_vienna.json"))
            },
            inMemoryCache(),
            "it",
        )

        val restaurants = source.searchRestaurants(RestaurantSearchQuery(vienna), forceRefresh = false).data

        assertEquals(listOf("overpass-1.test", "overpass-2.test"), hosts)
        assertTrue(restaurants.isNotEmpty())
    }

    @Test
    fun `una richiesta rifiutata o una risposta incompleta non vengono ritentate altrove`() = runTest {
        var calls = 0
        val rejected = OsmRestaurantDataSource(api { calls++; respond("Bad request", HttpStatusCode.BadRequest) }, inMemoryCache(), "it")
        val incomplete = OsmRestaurantDataSource(
            api { json("""{"elements": [], "remark": "runtime error: Query timed out in \"query\" at line 1 after 26 seconds."}""") },
            inMemoryCache(),
            "it",
        )
        val dispatcher = StandardTestDispatcher(testScheduler)

        val rejectedResult = DefaultRestaurantRepository(rejected, dispatcher, providesRatings = false).searchRestaurants(RestaurantSearchQuery(vienna))
        val incompleteResult = DefaultRestaurantRepository(incomplete, dispatcher, providesRatings = false).searchRestaurants(RestaurantSearchQuery(vienna))

        assertEquals(DataResult.Failure(DataError.Client(400)), rejectedResult)
        assertEquals(1, calls)
        assertEquals(DataResult.Failure(DataError.InvalidResponse), incompleteResult)
    }

    // ---- Strutture ricettive --------------------------------------------------------------------

    @Test
    fun `con i dati reali di Matera riconosce tipi, stelle e aree con il loro centro`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val source = OsmLodgingDataSource(api { request -> requests += request; json(fixture("lodgings_matera.json")) }, inMemoryCache(), "it")
        val repository = DefaultLodgingRepository(source, StandardTestDispatcher(testScheduler))

        val lodgings = repository.findLodgings(LodgingQuery(matera)).successData()

        val query = requests.single().overpassQuery()
        assertTrue("\"tourism\"~\"^(hotel|hostel|guest_house|apartment|motel)$\"" in query, query)
        assertTrue("(around:2000,40.66640,16.60430)" in query, query)
        assertEquals(120, lodgings.size)
        val types = lodgings.groupingBy { it.type }.eachCount()
        assertEquals(mapOf(LodgingType.GUEST_HOUSE to 60, LodgingType.HOTEL to 44, LodgingType.APARTMENT to 13, LodgingType.HOSTEL to 3), types)
        val palace = lodgings.single { it.name == "Palace Hotel" }
        assertEquals("osm:way/1123138428", palace.id)
        assertEquals(4, palace.starRating)
        assertEquals(40.66338, palace.location.latitude, 0.0001)
    }

    @Test
    fun `le stelle si leggono dalla prima cifra, fuori scala vengono ignorate`() {
        assertEquals(4, OsmLabels.stars("4S"))
        assertEquals(3, OsmLabels.stars("3.5"))
        assertNull(OsmLabels.stars("7"))
        assertNull(OsmLabels.stars("stelle"))
        assertNull(OsmLabels.stars(null))
    }

    @Test
    fun `senza provider di prenotazione non ci sono offerte con prezzo`() = runTest {
        val repository = DefaultAccommodationRepository(NoStayOffersDataSource, StandardTestDispatcher(testScheduler), providesOffers = false)
        val query = AccommodationSearchQuery(vienna, LocalDate.of(2026, Month.DECEMBER, 10), LocalDate.of(2026, Month.DECEMBER, 14))

        assertFalse(repository.providesOffers)
        assertEquals(emptyList(), repository.searchAccommodations(query).successData())
    }
}
