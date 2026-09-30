package com.partimo.data.local

import com.partimo.data.remote.geocoding.OpenMeteoGeocodingDataSource
import com.partimo.data.repository.DefaultAirportRepository
import com.partimo.data.testing.inMemoryCache
import com.partimo.data.testing.jsonHeaders
import com.partimo.data.testing.mockHttpClient
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.place.AirportSize
import com.partimo.domain.model.place.CatalogDestination
import com.partimo.domain.repository.DestinationCatalogRepository
import com.partimo.domain.testing.FakeWeatherRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.testing.successData
import com.partimo.domain.usecase.RecommendDestinationsUseCase
import com.partimo.domain.usecase.ResolveDestinationUseCase
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.File
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenMeteoGeocodingDataSourceTest {

    // Risposta reale di Open-Meteo Geocoding per "parigi" (language=it), ridotta.
    private val responseJson = """
        {"results":[
          {"id":2988507,"name":"Parigi","latitude":48.85341,"longitude":2.3488,"elevation":42.0,"feature_code":"PPLC",
           "country_code":"FR","timezone":"Europe/Paris","population":2138551,"country":"Francia","admin1":"Île-de-France"},
          {"id":1632325,"name":"Parigi","latitude":-7.7015,"longitude":108.496,"feature_code":"PPLA2","country_code":"ID",
           "timezone":"Asia/Jakarta","country":"Indonesia","admin1":"Giava Occidentale"},
          {"id":1,"name":"Regione di prova","latitude":1.0,"longitude":1.0,"feature_code":"ADM1","country_code":"FR"},
          {"id":2,"name":"Senza fuso","latitude":10.0,"longitude":10.0,"feature_code":"PPL","country_code":"xx","timezone":"Non/Valido"}
        ],"generationtime_ms":0.84}
    """.trimIndent()

    @Test
    fun `interpreta le città trovate e scarta ciò che non è un centro abitato`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = mockHttpClient { request ->
            requests += request
            respond(responseJson, HttpStatusCode.OK, jsonHeaders)
        }
        val source = OpenMeteoGeocodingDataSource(client, inMemoryCache(), languageCode = "it", baseUrl = "https://geo.test/v1/")

        val cities = source.searchCities("parigi", limit = 10).data

        assertEquals(listOf("geonames:2988507", "geonames:1632325", "geonames:2"), cities.map { it.id })
        val paris = cities.first()
        assertEquals("Francia", paris.country)
        assertEquals("FR", paris.countryCode)
        assertEquals("Île-de-France", paris.region)
        assertEquals(ZoneId.of("Europe/Paris"), paris.timeZone)
        assertEquals(2_138_551, paris.population)
        assertEquals(ZoneOffset.UTC, cities.last().timeZone, "Un fuso non valido ripiega su UTC")

        val request = requests.single()
        assertEquals("/v1/search", request.url.encodedPath)
        assertEquals("parigi", request.url.parameters["name"])
        assertEquals("it", request.url.parameters["language"])
    }

    @Test
    fun `una ricerca senza risultati restituisce una lista vuota e viene messa in cache`() = runTest {
        var calls = 0
        val client = mockHttpClient {
            calls++
            respond("""{"generationtime_ms":0.06}""", HttpStatusCode.OK, jsonHeaders)
        }
        val source = OpenMeteoGeocodingDataSource(client, inMemoryCache(), languageCode = "it", baseUrl = "https://geo.test/v1/")

        assertTrue(source.searchCities("xyzqwk", 10).data.isEmpty())
        assertEquals(DataOrigin.CACHE, source.searchCities("xyzqwk", 10).origin)
        assertEquals(1, calls)
    }
}

class BundledAirportsDataSourceTest {

    private val csv = """
        # commento
        VIE;Vienna International Airport;Vienna;AT;48.1103;16.5697;L
        FCO;Rome Fiumicino Airport;Rome;IT;41.8045;12.252;L
        CIA;Rome Ciampino Airport;Rome;IT;41.7994;12.5949;M
        riga;non;valida
    """.trimIndent()

    private val source = BundledAirportsDataSource { csv.byteInputStream() }
    private val romeCenter = GeoPoint(41.9028, 12.4964)

    @Test
    fun `restituisce gli aeroporti nel raggio ordinati per distanza`() = runTest {
        val result = source.airportsNear(romeCenter, radiusKm = 100.0)

        assertEquals(listOf("CIA", "FCO"), result.data.map { it.iata })
        assertEquals(DataOrigin.LOCAL, result.origin)
        assertEquals(AirportSize.MEDIUM, result.data.first().size)
    }

    @Test
    fun `cerca un aeroporto per codice IATA`() = runTest {
        assertEquals("Vienna International Airport", source.findByIata("vie")?.name)
        assertNull(source.findByIata("XXX"))
    }

    @Test
    fun `il dataset incluso nell'app è completo e sceglie gli aeroporti giusti`() = runTest {
        val bundled = File("src/main/assets/airports.csv")
        val airports = parseAirportsCsv(bundled.readLines().asSequence())
        assertTrue(airports.size > 3_000, "Aeroporti nel dataset: ${airports.size}")

        val dataSource = BundledAirportsDataSource { bundled.inputStream() }
        val resolve = ResolveDestinationUseCase(DefaultAirportRepository(dataSource, StandardTestDispatcher(testScheduler)))
        val expected = mapOf(
            TestData.city("Roma", GeoPoint(41.9028, 12.4964)) to "FCO",
            TestData.city("Parigi", GeoPoint(48.8566, 2.3522)) to "CDG",
            TestData.city("Tokyo", GeoPoint(35.6762, 139.6503)) to "HND",
            TestData.city("Seul", GeoPoint(37.5665, 126.9780)) to "ICN",
            TestData.city("Vienna", GeoPoint(48.2082, 16.3738)) to "VIE",
            TestData.city("Città del Capo", GeoPoint(-33.9249, 18.4241)) to "CPT",
            TestData.city("Reykjavík", GeoPoint(64.1466, -21.9426)) to "KEF",
            TestData.city("Pisa", GeoPoint(43.7228, 10.4017)) to "PSA",
        )
        expected.forEach { (city, iata) ->
            assertEquals(iata, resolve(city).successData().airportIata, "Aeroporto per ${city.name}")
        }
    }
}

class CuratedDestinationCatalogTest {

    private val destinations = CuratedDestinationCatalog.DESTINATIONS

    @Test
    fun `le mete del catalogo sono univoche e complete`() {
        assertTrue(destinations.size >= 40)
        assertEquals(destinations.size, destinations.map { it.city.id }.toSet().size)
        destinations.forEach { destination ->
            assertTrue(destination.tagline.isNotBlank(), destination.city.name)
            assertTrue(destination.experiences.isNotEmpty(), destination.city.name)
            assertEquals(2, destination.city.countryCode.length, destination.city.name)
        }
    }

    @Test
    fun `ogni mese dell'anno ha almeno sei mete da consigliare`() = runTest {
        val repository = object : DestinationCatalogRepository {
            override suspend fun destinations(): DataResult<List<CatalogDestination>> = DataResult.Success(destinations)
        }
        val useCase = RecommendDestinationsUseCase(repository, FakeWeatherRepository())

        Month.entries.forEach { month ->
            val suggestions = useCase(LocalDate.of(2027, month, 10), count = 100).successData()
            assertTrue(suggestions.size >= 6, "$month: ${suggestions.size} mete")
        }
    }
}
