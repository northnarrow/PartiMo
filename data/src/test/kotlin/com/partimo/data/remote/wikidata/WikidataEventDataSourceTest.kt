package com.partimo.data.remote.wikidata

import com.partimo.data.network.HttpClientFactory
import com.partimo.data.repository.DefaultEventRepository
import com.partimo.data.testing.inMemoryCache
import com.partimo.data.testing.jsonHeaders
import com.partimo.data.testing.mockHttpClient
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.event.EventKind
import com.partimo.domain.model.event.EventQuery
import com.partimo.domain.model.event.EventTiming
import com.partimo.domain.model.poi.WikipediaPage
import com.partimo.domain.service.SeasonalCalendar
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.time.Month
import java.time.MonthDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WikidataEventDataSourceTest {

    private val vienna = GeoPoint(48.2082, 16.3738)
    private val munich = GeoPoint(48.1374, 11.5755)
    private val userAgent = "PartiMoTest/1.0 (https://example.test/partimo)"

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/wikidata/$name")) { "Fixture mancante: $name" }.readText()

    private fun source(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        WikidataEventDataSource(WikidataApi(mockHttpClient(handler), userAgent, "https://wikidata.test/sparql"), inMemoryCache(), "it")

    private fun MockRequestHandleScope.json(body: String) = respond(body, HttpStatusCode.OK, jsonHeaders)

    @Test
    fun `interroga Wikidata con una query SPARQL attorno al centro e uno User-Agent identificabile`() = runTest {
        val requests = mutableListOf<HttpRequestData>()

        source { request -> requests += request; json(fixture("events_vienna.json")) }.recurringEvents(EventQuery(vienna), forceRefresh = false)

        val request = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("wikidata.test", request.url.host)
        assertEquals(userAgent, request.headers[HttpHeaders.UserAgent])
        assertEquals("json", request.url.parameters["format"])
        val query = request.url.parameters["query"].orEmpty()
        assertTrue("\"Point(16.37380 48.20820)\"^^geo:wktLiteral) < 15.0" in query, query)
        assertTrue("wdt:P31 wd:Q57607" in query && "wdt:P2922" in query && "wdt:P837" in query, query)
        assertTrue("wikibase:language \"it,en,mul," in query && "<https://it.wikipedia.org/>" in query, query)
    }

    @Test
    fun `con i dati reali di Vienna legge giorni, mesi, luoghi, foto e voci di Wikipedia`() = runTest {
        val events = source { json(fixture("events_vienna.json")) }.recurringEvents(EventQuery(vienna), forceRefresh = false).data
            .associateBy { it.name }

        assertEquals(6, events.size)
        val concert = events.getValue("Concerto di Capodanno di Vienna")
        assertEquals("wikidata:Q339152", concert.id)
        assertEquals(EventKind.RECURRING_EVENT, concert.kind)
        assertEquals(EventTiming.YearlyDays(setOf(MonthDay.of(Month.JANUARY, 1))), concert.timing)
        assertEquals(GeoPoint(48.200489, 16.372739), concert.location, "Coordinate proprie dell'evento (Musikverein)")
        assertEquals(WikipediaPage("it", "Concerto di Capodanno di Vienna"), concert.wikipediaPage)
        assertTrue(concert.photoUrl!!.startsWith("https://commons.wikimedia.org/wiki/Special:FilePath/") && concert.photoUrl!!.endsWith("?width=960"))

        assertEquals(EventTiming.InMonths(setOf(Month.OCTOBER)), events.getValue("Viennale").timing)
        val shorts = events.getValue("Vienna Shorts")
        assertEquals(EventTiming.InMonths(setOf(Month.MAY, Month.JUNE)), shorts.timing)
        assertEquals("MuseumsQuartier", shorts.venueName)
        assertEquals(GeoPoint(48.203333333, 16.358888888), shorts.location, "Senza coordinate proprie vale il luogo che lo ospita")
        assertEquals(WikipediaPage("en", "Vienna Shorts"), shorts.wikipediaPage)
        assertNull(events.getValue("Blue Danube Film Festival").description)
    }

    @Test
    fun `con i dati reali di Monaco riconosce i mercatini di Natale e scarta eventi chiusi, fiere e ritrovi`() = runTest {
        val events = source { json(fixture("events_munich.json")) }.recurringEvents(EventQuery(munich), forceRefresh = false).data
            .associateBy { it.name }

        val market = events.getValue("Münchner Christkindlmarkt")
        assertEquals(EventKind.CHRISTMAS_MARKET, market.kind)
        assertEquals(SeasonalCalendar.CHRISTMAS_MARKET_SEASON, market.timing)
        assertTrue(market.approximateTiming)
        assertEquals("Marienplatz", market.venueName)
        assertEquals(WikipediaPage("en", "Christkindlmarkt at Marienplatz"), market.wikipediaPage)
        assertEquals(EventKind.CHRISTMAS_MARKET, events.getValue("Pasinger Christkindl-Markt").kind)

        val oktoberfest = events.getValue("Oktoberfest")
        assertEquals(EventTiming.InMonths(setOf(Month.SEPTEMBER, Month.OCTOBER)), oktoberfest.timing)
        assertEquals("Theresienwiese", oktoberfest.venueName)

        val fantasy = events.getValue("Fantasy Filmfest")
        assertNull(fantasy.venueName, "Il luogo indicato (Norimberga) è fuori città")
        assertTrue(fantasy.location!!.distanceTo(munich) < 1_000)

        val discarded = listOf("Enchanted International Queer Film Festival", "Expo Real", "ceramitec", "Wikimedia-Stammtisch München")
        assertTrue(discarded.none { it in events }, "Scartati: ${discarded.filter { it in events }}")
    }

    @Test
    fun `i giorni si leggono dalle etichette inglesi e le foto diventano miniature`() {
        assertEquals(MonthDay.of(Month.JANUARY, 1), WikidataEventDataSource.parseDay("January 1"))
        assertEquals(MonthDay.of(Month.DECEMBER, 24), WikidataEventDataSource.parseDay(" December 24 "))
        assertNull(WikidataEventDataSource.parseDay("September"))
        assertEquals(
            "https://commons.wikimedia.org/wiki/Special:FilePath/MUC%20SchwabingWeihnachtsmarktA.jpg?width=960",
            WikidataEventDataSource.commonsThumbnail("http://commons.wikimedia.org/wiki/Special:FilePath/MUC%20SchwabingWeihnachtsmarktA.jpg"),
        )
    }

    @Test
    fun `un servizio sovraccarico non viene interrogato di nuovo in automatico`() = runTest {
        var calls = 0
        // Client di produzione con i tentativi automatici attivi: la query di Wikidata li disattiva.
        val client = HttpClientFactory.create(engine = MockEngine { calls++; respond("Too many requests", HttpStatusCode.TooManyRequests) }, maxRetries = 2)
        val source = WikidataEventDataSource(WikidataApi(client, userAgent, "https://wikidata.test/sparql"), inMemoryCache(), "it")
        val busy = DefaultEventRepository(source, StandardTestDispatcher(testScheduler))

        assertEquals(DataResult.Failure(DataError.RateLimited), busy.recurringEvents(EventQuery(vienna)))
        assertEquals(1, calls)

        calls = 0
        val failingClient = HttpClientFactory.create(engine = MockEngine { calls++; respond("timeout", HttpStatusCode.GatewayTimeout) }, maxRetries = 2)
        val failing = DefaultEventRepository(
            WikidataEventDataSource(WikidataApi(failingClient, userAgent, "https://wikidata.test/sparql"), inMemoryCache(), "it"),
            StandardTestDispatcher(testScheduler),
        )
        assertEquals(DataResult.Failure(DataError.Server(504)), failing.recurringEvents(EventQuery(vienna)))
        assertEquals(1, calls, "Le query lente non si ripetono: il servizio limita le richieste")
    }
}
