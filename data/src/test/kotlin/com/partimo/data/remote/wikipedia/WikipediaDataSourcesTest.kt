package com.partimo.data.remote.wikipedia

import com.partimo.data.repository.DefaultPoiRepository
import com.partimo.data.testing.inMemoryCache
import com.partimo.data.testing.jsonHeaders
import com.partimo.data.testing.mockHttpClient
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.poi.PoiQuery
import com.partimo.domain.model.poi.WikipediaPage
import com.partimo.domain.testing.TestData
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WikipediaDataSourcesTest {

    private val rome = GeoPoint(41.8933, 12.4829)
    private val lisbon = GeoPoint(38.7223, -9.1393)
    private val userAgent = "PartiMoTest/1.0 (https://example.test/partimo)"

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/wikipedia/$name")) { "Fixture mancante: $name" }.readText()

    private fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        WikipediaApi(mockHttpClient(handler), userAgent, baseUrlFor = { language -> "https://$language.wiki.test/w/api.php" })

    private fun MockRequestHandleScope.json(body: String) = respond(body, HttpStatusCode.OK, jsonHeaders)

    private fun HttpRequestData.param(name: String): String? = url.parameters[name]

    /** Risposta con voci di prova: titolo → descrizione (tutte con coordinate e foto, salvo diversa indicazione). */
    private fun pages(vararg pages: Triple<String, String?, String>, withPhotos: Boolean = true): String = pages
        .mapIndexed { index, (title, description, wikidata) ->
            val photo = if (withPhotos) """, "thumbnail": {"source": "https://img.test/${index + 1}.jpg", "width": 960, "height": 640}""" else ""
            val desc = description?.let { """, "description": "$it"""" }.orEmpty()
            """{"pageid": ${index + 100}, "ns": 0, "title": "$title", "index": ${index + 1},
                "coordinates": [{"lat": 38.71, "lon": -9.14, "primary": true}]$desc$photo,
                "pageprops": {"wikibase_item": "$wikidata"}}"""
        }
        .joinToString(prefix = """{"batchcomplete": true, "query": {"pages": [""", postfix = "]}}")

    // ---- Luoghi vicini ------------------------------------------------------------------------

    @Test
    fun `cerca le voci vicine alla città con un User-Agent identificabile`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val source = WikipediaPoiDataSource(api { request -> requests += request; json(fixture("nearby_rome_it.json")) }, inMemoryCache(), listOf("it", "en"))

        source.pointsOfInterest(PoiQuery(rome, Month.OCTOBER, areaName = "Roma"), forceRefresh = false)

        val request = requests.single()
        assertEquals("it.wiki.test", request.url.host)
        assertEquals(userAgent, request.headers[HttpHeaders.UserAgent])
        assertEquals("query", request.param("action"))
        assertEquals("search", request.param("generator"))
        assertEquals("nearcoord:10000m,41.89330,12.48290", request.param("gsrsearch"), "Raggio doppio rispetto ai 5 km della ricerca")
        assertEquals(WikipediaApi.POPULARITY_PROFILE, request.param("gsrqiprofile"))
        assertEquals("coordinates|description|pageimages|pageprops", request.param("prop"))
    }

    @Test
    fun `con i dati reali di Roma tiene solo i luoghi da visitare, dal più noto`() = runTest {
        val source = WikipediaPoiDataSource(api { json(fixture("nearby_rome_it.json")) }, inMemoryCache(), listOf("it", "en"))

        val result = source.pointsOfInterest(PoiQuery(rome, Month.OCTOBER, areaName = "Roma"), forceRefresh = false)
        val names = result.data.map { it.name }

        assertEquals(DataOrigin.REMOTE, result.origin)
        assertEquals(listOf("Pantheon", "Colosseo", "Vittoriano", "Castel Sant'Angelo"), names.take(4))
        assertTrue(names.containsAll(listOf("Fontana di Trevi", "Piazza Navona", "Piazza di Spagna")), "Luoghi mancanti: $names")
        val excluded = listOf("Roma", "Lazio", "Città del Vaticano", "Caso Moro", "Pietà vaticana", "Giudizio universale", "Palazzetto dello Sport")
        assertTrue(excluded.none { it in names }, "Voci da scartare presenti: ${excluded.filter { it in names }}")
        assertTrue(result.data.size <= WikipediaPoiDataSource.MAX_RESULTS)

        val pantheon = result.data.first()
        assertEquals(1.0, pantheon.popularity)
        assertEquals(PoiCategory.RELIGIOUS_SITE, pantheon.category)
        assertEquals(WikipediaPage("it", "Pantheon (Roma)"), pantheon.wikipediaPage)
        assertEquals("Tempio della Roma antica e basilica minore cattolica", pantheon.description)
        assertTrue(pantheon.photoUrl.orEmpty().startsWith("https://"), "Foto reale attesa: ${pantheon.photoUrl}")
        assertTrue(result.data.zipWithNext().all { (a, b) -> a.popularity!! > b.popularity!! }, "Notorietà decrescente attesa")
    }

    @Test
    fun `con pochi luoghi nella lingua dell'app aggiunge quelli inglesi senza duplicati`() = runTest {
        val requestedHosts = mutableListOf<String>()
        val italian = pages(
            Triple("Cattedrale di Lisbona", "cattedrale di Lisbona", "Q432290"),
            Triple("Alfama", "quartiere di Lisbona", "Q985517"),
        )
        val source = WikipediaPoiDataSource(
            api { request ->
                requestedHosts += request.url.host
                json(if (request.url.host.startsWith("it.")) italian else fixture("nearby_lisbon_en.json"))
            },
            inMemoryCache(),
            listOf("it", "en"),
        )

        val places = source.pointsOfInterest(PoiQuery(lisbon, Month.MAY, areaName = "Lisbona"), forceRefresh = false).data
        val names = places.map { it.name }

        assertEquals(listOf("it.wiki.test", "en.wiki.test"), requestedHosts)
        assertEquals(listOf("Cattedrale di Lisbona", "Alfama"), names.take(2), "Prima le voci nella lingua dell'app")
        assertFalse("Lisbon Cathedral" in names || "Alfama" in names.drop(2), "Stesso luogo (Wikidata) in due lingue")
        assertTrue(names.containsAll(listOf("São Jorge Castle", "Santa Justa Lift", "Rossio")), "Luoghi inglesi mancanti: $names")
        assertTrue(listOf("Lisbon", "Kingdom of Portugal", "Banco de Portugal", "Ribeira Palace").none { it in names })
        assertEquals("en", places.first { it.name == "Rossio" }.wikipediaPage?.language)
    }

    @Test
    fun `se la lingua di riserva non risponde restano i luoghi già trovati`() = runTest {
        val italian = pages(Triple("Cattedrale di Lisbona", "cattedrale di Lisbona", "Q432290"))
        val source = WikipediaPoiDataSource(
            api { request ->
                if (request.url.host.startsWith("it.")) json(italian) else respond("", HttpStatusCode.ServiceUnavailable)
            },
            inMemoryCache(),
            listOf("it", "en"),
        )

        val places = source.pointsOfInterest(PoiQuery(lisbon, Month.MAY), forceRefresh = false).data

        assertEquals(listOf("Cattedrale di Lisbona"), places.map { it.name })
    }

    @Test
    fun `se ci sono abbastanza foto scarta le voci senza immagine`() = runTest {
        val withPhotos = (1..9).map { Triple("Chiesa numero $it", "chiesa di Lisbona", "Q$it") }
        val body = pages(*withPhotos.toTypedArray()).replace(
            """{"pageid": 100""",
            """{"pageid": 99, "ns": 0, "title": "Museo senza foto", "index": 0, "description": "museo di Lisbona",
                "coordinates": [{"lat": 38.71, "lon": -9.14}], "pageprops": {"wikibase_item": "Q0"}},
               {"pageid": 100""",
        )
        val source = WikipediaPoiDataSource(api { json(body) }, inMemoryCache(), listOf("it"))

        val places = source.pointsOfInterest(PoiQuery(lisbon, Month.MAY), forceRefresh = false).data

        assertEquals(9, places.size)
        assertTrue(places.all { it.photoUrl != null })
    }

    @Test
    fun `la seconda richiesta arriva dalla cache e un errore dell'API diventa un errore di dominio`() = runTest {
        var calls = 0
        val cache = inMemoryCache()
        val source = WikipediaPoiDataSource(api { calls++; json(fixture("nearby_rome_it.json")) }, cache, listOf("it"))
        val query = PoiQuery(rome, Month.OCTOBER)

        source.pointsOfInterest(query, forceRefresh = false)
        val cached = source.pointsOfInterest(query, forceRefresh = false)

        assertEquals(1, calls)
        assertEquals(DataOrigin.CACHE, cached.origin)

        val failing = WikipediaPoiDataSource(
            api { json("""{"error": {"code": "badvalue", "info": "Valore non riconosciuto"}}""") },
            inMemoryCache(),
            listOf("it"),
        )
        val result = DefaultPoiRepository(failing, StandardTestDispatcher(testScheduler)).getPointsOfInterest(query)

        assertEquals(DataResult.Failure(DataError.InvalidResponse), result)
    }

    @Test
    fun `usa la lingua dell'app se supportata, poi l'inglese`() {
        assertEquals(listOf("it", "en"), wikipediaLanguages("it"))
        assertEquals(listOf("en"), wikipediaLanguages("en"))
        assertEquals(listOf("en"), wikipediaLanguages("de"))
        assertEquals("en", WikipediaApi.sanitizeLanguage("it.evil.test/"))
    }

    // ---- Voce del singolo luogo ---------------------------------------------------------------

    @Test
    fun `scarica la voce del luogo con introduzione, storia e crediti della foto`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val source = WikipediaArticleDataSource(
            api { request ->
                requests += request
                json(if (request.param("prop") == "imageinfo") fixture("imageinfo_colosseo.json") else fixture("article_colosseo_it.json"))
            },
            inMemoryCache(),
            listOf("it", "en"),
        )
        val colosseo = TestData.poi("wikipedia:it:1510212", "Colosseo", wikipediaPage = WikipediaPage("it", "Colosseo"))

        val article = assertNotNull(source.findArticle(colosseo, forceRefresh = false).data)

        assertEquals(listOf("Colosseo", "File:Colosseo_2020.jpg"), requests.map { it.param("titles") })
        assertEquals("1", requests.first().param("explaintext"))
        assertEquals("https://it.wikipedia.org/wiki/Colosseo", article.url)
        assertEquals("antico anfiteatro romano a Roma", article.shortDescription)
        assertEquals(3, article.introduction.size)
        assertTrue(article.introduction.first().startsWith("Il Colosseo, originariamente conosciuto come Anfiteatro Flavio"))
        assertEquals(listOf(null, "Costruzione", "Inaugurazione ed altre modifiche"), article.history.map { it.title })
        assertTrue(article.history[1].paragraphs.first().startsWith("La costruzione iniziò fra il 70 e il 72"))
        assertTrue(article.imageUrl.orEmpty().contains("Colosseo_2020.jpg"))
        assertEquals("FeaturedPics", article.imageCredit?.author)
        assertEquals("CC BY-SA 4.0", article.imageCredit?.license)
        assertEquals("https://commons.wikimedia.org/wiki/File:Colosseo_2020.jpg", article.imageCredit?.sourceUrl)
    }

    @Test
    fun `per un luogo di Google cerca la voce per nome vicino alle coordinate`() = runTest {
        val searches = mutableListOf<HttpRequestData>()
        val source = WikipediaArticleDataSource(
            api { request ->
                when {
                    request.param("generator") == "search" -> {
                        searches += request
                        json(
                            """{"query": {"pages": [
                                {"title": "Chiesa di Santa Maria in Aracoeli", "index": 1, "coordinates": [{"lat": 41.8938, "lon": 12.4833}]},
                                {"title": "Fontana di Trevi", "index": 2, "coordinates": [{"lat": 41.90083, "lon": 12.48333}]}
                            ]}}""",
                        )
                    }
                    request.param("prop") == "imageinfo" -> respond("", HttpStatusCode.InternalServerError)
                    else -> json(fixture("article_colosseo_it.json").replace("\"Colosseo\"", "\"Fontana di Trevi\""))
                }
            },
            inMemoryCache(),
            listOf("it", "en"),
        )
        val trevi = TestData.poi("ChIJ1UCDJ1NgLxMRtrsCzOHxdvY", "Fontana di Trevi", location = GeoPoint(41.9009, 12.4833))

        val article = assertNotNull(source.findArticle(trevi, forceRefresh = false).data)

        assertEquals("Fontana di Trevi nearcoord:1000m,41.90090,12.48330", searches.single().param("gsrsearch"))
        assertEquals("Fontana di Trevi", article.title)
        assertNull(article.imageCredit, "Senza crediti la voce si mostra comunque")
    }

    @Test
    fun `senza una voce che corrisponda non restituisce la storia di un altro luogo`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val source = WikipediaArticleDataSource(
            api { request ->
                requests += request
                json("""{"query": {"pages": [{"title": "Palazzo Ferstel", "index": 1, "coordinates": [{"lat": 48.2105, "lon": 16.3660}]}]}}""")
            },
            inMemoryCache(),
            listOf("it", "en"),
        )
        val cafe = TestData.poi("ChIJcafe", "Café Central", location = GeoPoint(48.2100, 16.3650))

        val result = source.findArticle(cafe, forceRefresh = false)

        assertNull(result.data)
        assertEquals(listOf("search", "search"), requests.map { it.param("generator") }, "Cerca in italiano e in inglese, niente voce")
    }

    @Test
    fun `una voce inesistente non produce una scheda`() = runTest {
        val source = WikipediaArticleDataSource(
            api { json("""{"query": {"pages": [{"ns": 0, "title": "Luogo inesistente", "missing": true}]}}""") },
            inMemoryCache(),
            listOf("it"),
        )

        val result = source.findArticle(TestData.poi("x", wikipediaPage = WikipediaPage("it", "Luogo inesistente")), forceRefresh = false)

        assertNull(result.data)
    }

    @Test
    fun `i nomi coincidono se hanno le stesse parole significative`() {
        assertTrue(PlaceNames.match("Basilica di San Pietro", "Basilica di San Pietro in Vaticano"))
        assertTrue(PlaceNames.match("Castel Sant’Angelo", "Castel Sant'Angelo"))
        assertTrue(PlaceNames.match("Schönbrunn", "Schonbrunn"))
        assertFalse(PlaceNames.match("Museo", "Museo Nazionale Romano"), "Una sola parola generica non basta")
        assertFalse(PlaceNames.match("Café Central", "Palazzo Ferstel"))
        assertEquals("Pantheon", displayName("Pantheon (Roma)"))
        assertEquals("Mario Rossi & figli", plainText("""<a href="//commons.test/User:Mario">Mario   Rossi</a> &amp; figli"""))
    }
}
