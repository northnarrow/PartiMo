package com.partimo.data.remote.wikivoyage

import com.partimo.data.testing.inMemoryCache
import com.partimo.data.testing.jsonHeaders
import com.partimo.data.testing.mockHttpClient
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.testing.TestData
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WikivoyageGuideDataSourceTest {

    private val userAgent = "PartiMoTest/1.0 (https://example.test/partimo)"
    private val requests = mutableListOf<HttpRequestData>()

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/wikivoyage/$name")) { "Fixture mancante: $name" }.readText()

    private fun source(responses: (HttpRequestData) -> String) = WikivoyageGuideDataSource(
        api = WikivoyageApi(
            client = mockHttpClient { request ->
                requests += request
                respond(responses(request), HttpStatusCode.OK, jsonHeaders)
            },
            userAgent = userAgent,
            baseUrlFor = { language -> "https://$language.wikivoyage.test/w/api.php" },
        ),
        cache = inMemoryCache(),
        languages = listOf("it", "en"),
    )

    @Test
    fun `la guida reale di Vienna arriva divisa in introduzione, capitoli e sottosezioni`() = runTest {
        val fetched = source { fixture("vienna_it.json") }.guide(TestData.destination(), forceRefresh = false)
        val guide = fetched.data!!

        assertEquals("Vienna", guide.title)
        assertEquals("it", guide.language)
        assertEquals("https://it.wikivoyage.org/wiki/Vienna", guide.url)
        assertEquals(listOf("Vienna è la capitale dell'Austria."), guide.introduction)
        val titles = guide.sections.map { it.title }
        assertEquals(listOf("Da sapere", "Come orientarsi", "Come arrivare", "Come spostarsi"), titles.take(4))
        assertTrue("Sicurezza" in titles && "Come restare in contatto" in titles)

        val gettingAround = guide.sections.single { it.title == "Come spostarsi" }
        assertEquals(listOf("Con mezzi pubblici", "In taxi", "Con tour guidati", "In bicicletta", "In flaker"), gettingAround.subsections.map { it.title })
        assertTrue(gettingAround.subsections.first().paragraphs.any { "72 ore" in it })
        // I titoli di quarto livello restano nel testo della sottosezione che li contiene.
        val byPlane = guide.sections.single { it.title == "Come arrivare" }.subsections.first()
        assertEquals("In aereo", byPlane.title)
        assertTrue("Aeroporto di Vienna-Schwechat:" in byPlane.paragraphs)
        assertTrue(guide.sections.single { it.title == "Sicurezza" }.paragraphs.first().startsWith("Vienna è una città molto sicura"))

        val request = requests.single()
        assertEquals("it.wikivoyage.test", request.url.host)
        assertEquals("Vienna", request.url.parameters["titles"])
        assertEquals("wiki", request.url.parameters["exsectionformat"])
        assertEquals("1", request.url.parameters["redirects"])
        assertEquals(userAgent, request.headers[HttpHeaders.UserAgent])
        assertEquals(DataOrigin.REMOTE, fetched.origin)
    }

    @Test
    fun `senza la guida in italiano prova quella in inglese, poi nessuna guida`() = runTest {
        val missing = """{"batchcomplete":true,"query":{"pages":[{"ns":0,"title":"Vienna","missing":true}]}}"""
        val englishGuide = fixture("lisbona_it.json").replace("\"Lisbona\"", "\"Vienna\"")

        val guide = source { request -> if (request.url.host.startsWith("it.")) missing else englishGuide }
            .guide(TestData.destination(), forceRefresh = false).data!!

        assertEquals("en", guide.language)
        assertEquals(listOf("it.wikivoyage.test", "en.wikivoyage.test"), requests.map { it.url.host })

        requests.clear()
        assertNull(source { missing }.guide(TestData.destination(name = "Atlantide"), forceRefresh = false).data)
        assertEquals(2, requests.size)
    }

    @Test
    fun `una pagina di disambiguazione o troppo lontana dalla meta non è la guida`() = runTest {
        val disambiguation = """{"query":{"pages":[{"pageid":1,"title":"Vienna","extract":"Vienna può riferirsi a:","pageprops":{"disambiguation":""}}]}}"""
        val farAway = """{"query":{"pages":[{"pageid":2,"title":"Vienna","extract":"Vienna è una città della Virginia.","coordinates":[{"lat":38.9,"lon":-77.26,"primary":true}]}]}}"""

        assertNull(source { disambiguation }.guide(TestData.destination(), forceRefresh = false).data)
        assertNull(source { farAway }.guide(TestData.destination(), forceRefresh = false).data)
    }

    @Test
    fun `la guida resta in cache`() = runTest {
        val source = source { fixture("vienna_it.json") }

        source.guide(TestData.destination(), forceRefresh = false)
        val second = source.guide(TestData.destination(), forceRefresh = false)

        assertEquals(DataOrigin.CACHE, second.origin)
        assertEquals(1, requests.size)
    }
}
