package com.partimo.data.remote.osm

import com.partimo.data.repository.DefaultAccommodationRepository
import com.partimo.data.repository.DefaultLodgingRepository
import com.partimo.data.repository.DefaultRestaurantRepository
import com.partimo.data.source.NoStayOffersDataSource
import com.partimo.data.testing.MutableClock
import com.partimo.data.testing.inMemoryCache
import com.partimo.data.testing.jsonHeaders
import com.partimo.data.testing.mockHttpClient
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.LocalDate
import java.time.Month
import kotlin.coroutines.cancellation.CancellationException
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

    /** Overpass con il motore finto nel tempo virtuale del test: le richieste di riserva partono solo se servono. */
    private fun TestScope.api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        OverpassApi(mockHttpClient(StandardTestDispatcher(testScheduler), handler), userAgent, endpoints)

    private fun TestScope.nominatim(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        NominatimApi(mockHttpClient(StandardTestDispatcher(testScheduler), handler), userAgent, timeSource = testScheduler.timeSource)

    /** Risposte registrate di Nominatim per Matera, secondo la ricerca (hotel, hostel, guest house). */
    private fun MockRequestHandleScope.nominatimMatera(request: HttpRequestData): HttpResponseData =
        json(fixture("nominatim_matera_" + request.url.parameters["q"].orEmpty().replace(' ', '_') + ".json"))

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
    fun `con Nominatim i locali di Vienna arrivano da una sola ricerca rapida, con cucina e orari`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val source = OsmRestaurantDataSource(
            api { error("Overpass non serve se Nominatim risponde") },
            inMemoryCache(),
            "it",
            nominatim { request -> requests += request; json(fixture("nominatim_vienna_restaurant.json")) },
        )

        val restaurants = source.searchRestaurants(RestaurantSearchQuery(vienna, areaName = "Vienna"), forceRefresh = false).data

        assertEquals("restaurant", requests.single().url.parameters["q"])
        assertEquals("16.35358,48.22167,16.39402,48.19473", requests.single().url.parameters["viewbox"], "Il quadrato di 1,5 km attorno al centro")
        assertEquals(OsmRestaurantDataSource.MAX_RESULTS, restaurants.size)
        assertTrue(restaurants.count { it.cuisine != null } >= 15, "Cucina dagli extratags")
        assertTrue(restaurants.count { it.openingHours != null } >= 15, "Orari dagli extratags")
        assertTrue(restaurants.all { it.mapsUrl?.startsWith("https://www.google.com/maps/search/") == true })
    }

    @Test
    fun `se Nominatim non risponde i locali arrivano dalla query completa di Overpass`() = runTest {
        val source = OsmRestaurantDataSource(
            api { json(fixture("restaurants_vienna.json")) },
            inMemoryCache(),
            "it",
            nominatim { respond("Too many requests", HttpStatusCode.TooManyRequests) },
        )

        assertEquals(OsmRestaurantDataSource.MAX_RESULTS, source.searchRestaurants(RestaurantSearchQuery(vienna), forceRefresh = false).data.size)
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
    fun `una richiesta rifiutata non viene ritentata altrove`() = runTest {
        var calls = 0
        val rejected = OsmRestaurantDataSource(api { calls++; respond("Bad request", HttpStatusCode.BadRequest) }, inMemoryCache(), "it")

        val result = DefaultRestaurantRepository(rejected, StandardTestDispatcher(testScheduler), providesRatings = false).searchRestaurants(RestaurantSearchQuery(vienna))

        assertEquals(DataResult.Failure(DataError.Client(400)), result)
        assertEquals(1, calls)
    }

    @Test
    fun `una query interrotta da un'istanza sovraccarica si chiede alla successiva`() = runTest {
        val interrupted = """{"elements": [], "remark": "runtime error: Query timed out in \"query\" at line 1 after 26 seconds."}"""
        val hosts = mutableListOf<String>()
        val retried = OsmRestaurantDataSource(
            api { request -> hosts += request.url.host; if (request.url.host == "overpass-1.test") json(interrupted) else json(fixture("restaurants_vienna.json")) },
            inMemoryCache(),
            "it",
        )
        val everywhere = OsmRestaurantDataSource(api { json(interrupted) }, inMemoryCache(), "it")
        val dispatcher = StandardTestDispatcher(testScheduler)

        val restaurants = DefaultRestaurantRepository(retried, dispatcher, providesRatings = false).searchRestaurants(RestaurantSearchQuery(vienna)).successData()
        val failure = DefaultRestaurantRepository(everywhere, dispatcher, providesRatings = false).searchRestaurants(RestaurantSearchQuery(vienna))

        assertEquals(listOf("overpass-1.test", "overpass-2.test"), hosts)
        assertTrue(restaurants.isNotEmpty())
        assertEquals(DataResult.Failure(DataError.InvalidResponse), failure, "Incompleta ovunque: niente elenco a metà")
    }

    @Test
    fun `se un'istanza non risponde parte la stessa query sulla successiva e vince la prima risposta`() = runTest {
        var slowCancelled = false
        val overpass = api { request ->
            if (request.url.host == "overpass-1.test") {
                try {
                    delay(60_000)
                } catch (e: CancellationException) {
                    slowCancelled = true
                    throw e
                }
            }
            json(fixture("restaurants_vienna.json"))
        }

        val restaurants = OsmRestaurantDataSource(overpass, inMemoryCache(), "it").searchRestaurants(RestaurantSearchQuery(vienna), forceRefresh = false).data

        assertTrue(restaurants.isNotEmpty())
        assertEquals(OverpassApi.HEDGE_AFTER_MILLIS, currentTime, "La seconda istanza parte dopo l'attesa, non dopo il timeout")
        assertTrue(slowCancelled, "La richiesta lenta si annulla")
    }

    @Test
    fun `le query dell'app passano una alla volta`() = runTest {
        var running = 0
        var maxRunning = 0
        val overpass = api {
            running++
            maxRunning = maxOf(maxRunning, running)
            delay(1_000)
            running--
            json(fixture("restaurants_vienna.json"))
        }

        listOf(async { overpass.query("ristoranti") }, async { overpass.query("alloggi") }).awaitAll()

        assertEquals(1, maxRunning, "L'istanza pubblica non vede mai due query insieme dallo stesso telefono")
        assertEquals(2_000L, currentTime)
    }

    // ---- Strutture ricettive --------------------------------------------------------------------

    @Test
    fun `con Nominatim le strutture di Matera arrivano in tre ricerche rapide, distanziate di un secondo`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val source = OsmLodgingDataSource(
            api { error("Overpass non serve se Nominatim risponde") },
            inMemoryCache(),
            "it",
            nominatim { request -> requests += request; nominatimMatera(request) },
        )

        val lodgings = source.findLodgings(LodgingQuery(matera), forceRefresh = false).data

        assertEquals(listOf("hotel", "hostel", "guest house"), requests.map { it.url.parameters["q"] })
        val parameters = requests.first().url.parameters
        assertEquals("16.58061,40.68437,16.62799,40.64843", parameters["viewbox"], "Il quadrato di 2 km attorno al centro")
        assertEquals(listOf("1", "40", "1", "1", "1", "it"), listOf("bounded", "limit", "extratags", "addressdetails", "namedetails", "accept-language").map { parameters[it] })
        assertEquals(userAgent, requests.first().headers[HttpHeaders.UserAgent])
        assertEquals(2 * NominatimApi.MIN_INTERVAL_MILLIS, currentTime, "Al massimo una richiesta al secondo")
        assertEquals(78, lodgings.size, "Senza doppioni tra le ricerche")
        assertEquals(mapOf(LodgingType.GUEST_HOUSE to 40, LodgingType.HOTEL to 35, LodgingType.HOSTEL to 3), lodgings.groupingBy { it.type }.eachCount())
        val aquatio = lodgings.single { it.name == "Aquatio Cave Luxury Hotel" }
        assertEquals("osm:node/6485319926", aquatio.id)
        assertEquals(5, aquatio.starRating)
        assertEquals("Via Conche 12", aquatio.address)
        assertEquals("osm:way/1123138428", lodgings.single { it.name == "Palace Hotel" }.id, "Lo stesso id di Overpass")
    }

    @Test
    fun `se Nominatim non risponde o non trova nulla si usa la query completa di Overpass`() = runTest {
        val unavailable = OsmLodgingDataSource(
            api { json(fixture("lodgings_matera.json")) },
            inMemoryCache(),
            "it",
            nominatim { respond("Service Unavailable", HttpStatusCode.ServiceUnavailable) },
        )
        val empty = OsmLodgingDataSource(api { json(fixture("lodgings_matera.json")) }, inMemoryCache(), "it", nominatim { json("[]") })

        assertEquals(120, unavailable.findLodgings(LodgingQuery(matera), forceRefresh = false).data.size)
        assertEquals(120, empty.findLodgings(LodgingQuery(matera), forceRefresh = false).data.size)
    }

    @Test
    fun `l'elenco salvato resta valido una settimana e, scaduto, si mostra mentre si aggiorna`() = runTest {
        val clock = MutableClock()
        var offline = false
        val source = OsmLodgingDataSource(
            api { respond("Gateway Timeout", HttpStatusCode.GatewayTimeout) },
            inMemoryCache(clock),
            "it",
            nominatim { request -> if (offline) respond("Service Unavailable", HttpStatusCode.ServiceUnavailable) else nominatimMatera(request) },
        )
        assertNull(source.savedLodgings(LodgingQuery(matera)))
        source.findLodgings(LodgingQuery(matera), forceRefresh = false)

        clock.advance(Duration.ofDays(6))
        offline = true
        assertEquals(DataOrigin.CACHE, source.findLodgings(LodgingQuery(matera), forceRefresh = false).origin, "Dopo 6 giorni nessuna richiesta")

        clock.advance(Duration.ofDays(2))
        assertEquals(78, source.savedLodgings(LodgingQuery(matera))?.size, "Scaduto ma ancora da mostrare")
        val refreshed = source.findLodgings(LodgingQuery(matera), forceRefresh = false)
        assertEquals(DataOrigin.STALE_CACHE, refreshed.origin, "Senza rete resta l'elenco salvato")
    }

    @Test
    fun `senza Nominatim con i dati reali di Matera riconosce tipi, stelle e aree con il loro centro`() = runTest {
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
