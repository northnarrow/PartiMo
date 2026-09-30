package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.common.QueryIssue
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.place.AirportSize
import com.partimo.domain.model.place.TravelExperience
import com.partimo.domain.model.place.TravelTheme
import com.partimo.domain.model.weather.WeatherCondition
import com.partimo.domain.testing.FakeAirportRepository
import com.partimo.domain.testing.FakeCitySearchRepository
import com.partimo.domain.testing.FakeDestinationCatalogRepository
import com.partimo.domain.testing.FakeWeatherRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.testing.TestData.airport
import com.partimo.domain.testing.TestData.catalogDestination
import com.partimo.domain.testing.TestData.city
import com.partimo.domain.testing.failureError
import com.partimo.domain.testing.successData
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchCitiesUseCaseTest {

    private val repository = FakeCitySearchRepository()
    private val useCase = SearchCitiesUseCase(repository)

    @Test
    fun `una ricerca troppo corta non chiama il servizio`() = runTest {
        assertEquals(DataError.InvalidQuery(QueryIssue.QUERY_TOO_SHORT), useCase(" p ").failureError())
        assertTrue(repository.queries.isEmpty())
    }

    @Test
    fun `normalizza spazi e bordi prima di cercare`() = runTest {
        useCase("  new   york ")

        assertEquals(listOf("new york"), repository.queries)
    }

    @Test
    fun `ordina per corrispondenza esatta e poi per popolazione`() = runTest {
        repository.result = DataResult.Success(
            listOf(
                city("Parigi", population = null, id = "parigi-indonesia"),
                city("Paris", population = 2_100_000, id = "paris-texas"),
                city("Parigi", population = 2_138_551, id = "parigi-francia"),
            ),
        )

        val ids = useCase("parigi").successData().map { it.id }

        assertEquals(listOf("parigi-francia", "parigi-indonesia", "paris-texas"), ids)
    }

    @Test
    fun `il confronto ignora maiuscole e accenti`() = runTest {
        repository.result = DataResult.Success(
            listOf(
                city("Reykjavik Heights", population = 900_000, id = "heights"),
                city("Reykjavík", population = 135_000, id = "reykjavik"),
            ),
        )

        assertEquals("reykjavik", useCase("reykjavik").successData().first().id)
    }

    @Test
    fun `rimuove i duplicati e rispetta il limite`() = runTest {
        repository.result = DataResult.Success(
            listOf(city("Roma", id = "roma"), city("Roma", id = "roma"), city("Romano", id = "romano"), city("Romania", id = "ro")),
        )

        val cities = useCase("roma", limit = 2).successData()

        assertEquals(listOf("roma", "romano"), cities.map { it.id })
    }
}

class ResolveDestinationUseCaseTest {

    private val repository = FakeAirportRepository()
    private val useCase = ResolveDestinationUseCase(repository)
    private val rome = city("Roma", GeoPoint(41.9028, 12.4964), countryCode = "IT", timeZone = ZoneId.of("Europe/Rome"))

    @Test
    fun `crea la destinazione con l'aeroporto come nodo di arrivo`() = runTest {
        val fiumicino = airport("FCO", GeoPoint(41.8045, 12.252), AirportSize.LARGE, name = "Fiumicino")
        repository.result = DataResult.Success(listOf(fiumicino))

        val destination = useCase(rome).successData()

        assertEquals("Roma", destination.name)
        assertEquals("IT", destination.countryCode)
        assertEquals("FCO", destination.airportIata)
        assertEquals(rome.location, destination.center)
        assertEquals(fiumicino.location, destination.arrivalHub)
        assertEquals("Fiumicino", destination.arrivalHubName)
        assertEquals(ZoneId.of("Europe/Rome"), destination.timeZone)
    }

    @Test
    fun `preferisce il grande aeroporto se non è molto più lontano`() {
        val ciampino = airport("CIA", GeoPoint(41.7994, 12.5949), AirportSize.MEDIUM)
        val fiumicino = airport("FCO", GeoPoint(41.8045, 12.252), AirportSize.LARGE)

        assertEquals("FCO", useCase.selectAirport(rome.location, listOf(ciampino, fiumicino))?.iata)
    }

    @Test
    fun `l'hub internazionale ha la precedenza su un grande aeroporto più vicino`() {
        val parisCenter = GeoPoint(48.8566, 2.3522)
        val orly = airport("ORY", GeoPoint(48.7295, 2.359), AirportSize.LARGE)
        val charlesDeGaulle = airport("CDG", GeoPoint(49.009, 2.5541), AirportSize.HUB)

        assertEquals("CDG", useCase.selectAirport(parisCenter, listOf(orly, charlesDeGaulle))?.iata)
    }

    @Test
    fun `sceglie l'aeroporto vicino se quello grande è troppo distante`() {
        val pisaCenter = GeoPoint(43.7228, 10.4017)
        val pisa = airport("PSA", GeoPoint(43.6839, 10.3927), AirportSize.MEDIUM)
        val bologna = airport("BLQ", GeoPoint(44.5354, 11.2887), AirportSize.LARGE)

        assertEquals("PSA", useCase.selectAirport(pisaCenter, listOf(bologna, pisa))?.iata)
    }

    @Test
    fun `senza aeroporti nel raggio restituisce un errore`() = runTest {
        repository.result = DataResult.Success(emptyList())

        assertEquals(DataError.InvalidQuery(QueryIssue.NO_AIRPORT_NEARBY), useCase(rome).failureError())
    }

    @Test
    fun `propaga gli errori del repository`() = runTest {
        repository.result = DataResult.Failure(DataError.InvalidResponse)

        assertEquals(DataError.InvalidResponse, useCase(rome).failureError())
    }
}

class RecommendDestinationsUseCaseTest {

    private val summer = setOf(Month.MAY, Month.JUNE, Month.JULY, Month.AUGUST, Month.SEPTEMBER)
    private val catalog = FakeDestinationCatalogRepository(
        DataResult.Success(
            listOf(
                catalogDestination(
                    "Vienna",
                    pleasantMonths = setOf(Month.MAY, Month.JUNE, Month.SEPTEMBER),
                    experiences = listOf(
                        TravelExperience(TravelTheme.CHRISTMAS_MARKETS, setOf(Month.NOVEMBER, Month.DECEMBER)),
                        TravelExperience(TravelTheme.CULTURE),
                    ),
                ),
                catalogDestination(
                    "Rovaniemi",
                    experiences = listOf(
                        TravelExperience(TravelTheme.CHRISTMAS_MARKETS, setOf(Month.DECEMBER)),
                        TravelExperience(TravelTheme.NORTHERN_LIGHTS, setOf(Month.DECEMBER, Month.JANUARY)),
                        TravelExperience(TravelTheme.NATURE),
                    ),
                ),
                catalogDestination(
                    "Bali",
                    pleasantMonths = summer,
                    experiences = listOf(TravelExperience(TravelTheme.BEACH, summer)),
                ),
                catalogDestination(
                    "Lisbona",
                    pleasantMonths = setOf(Month.APRIL, Month.MAY, Month.SEPTEMBER, Month.OCTOBER),
                    experiences = listOf(TravelExperience(TravelTheme.FOOD)),
                ),
            ),
        ),
    )
    private val weather = FakeWeatherRepository()
    private val useCase = RecommendDestinationsUseCase(catalog, weather)
    private val december = LocalDate.of(2026, Month.DECEMBER, 12)

    @Test
    fun `a dicembre consiglia le mete con esperienze stagionali`() = runTest {
        val names = useCase(december).successData().map { it.destination.city.name }

        assertEquals(listOf("Rovaniemi", "Vienna"), names)
    }

    @Test
    fun `in estate consiglia mare e clima piacevole`() = runTest {
        val suggestions = useCase(LocalDate.of(2027, Month.JULY, 10)).successData()

        assertEquals("Bali", suggestions.first().destination.city.name)
        assertTrue(suggestions.first().pleasantClimate)
        assertFalse(suggestions.any { it.destination.city.name == "Rovaniemi" })
    }

    @Test
    fun `espone i motivi del consiglio`() = runTest {
        val vienna = useCase(december).successData().first { it.destination.city.name == "Vienna" }

        assertEquals(listOf(TravelTheme.CHRISTMAS_MARKETS), vienna.seasonalHighlights)
        assertEquals(listOf(TravelTheme.CULTURE), vienna.yearRoundHighlights)
        assertFalse(vienna.pleasantClimate)
    }

    @Test
    fun `le pagine successive ruotano sulla classifica`() = runTest {
        val pages = (0..2).map { page -> useCase(december, count = 1, page = page).successData().single().destination.city.name }

        assertEquals(listOf("Rovaniemi", "Vienna", "Rovaniemi"), pages)
    }

    @Test
    fun `aggiunge il meteo attuale se disponibile`() = runTest {
        weather.result = DataResult.Success(TestData.weather(WeatherCondition.CLEAR, temperature = -3.0))

        assertTrue(useCase(december).successData().all { it.currentWeather != null })
    }

    @Test
    fun `senza meteo i consigli restano validi`() = runTest {
        weather.result = DataResult.Failure(DataError.NoConnection)

        val suggestions = useCase(december).successData()

        assertTrue(suggestions.isNotEmpty())
        assertNull(suggestions.first().currentWeather)
        assertNotNull(suggestions.first().destination)
    }
}
