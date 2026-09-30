package com.partimo.data.demo

import com.partimo.data.network.Fetched
import com.partimo.data.repository.DefaultFlightRepository
import com.partimo.data.repository.DefaultRestaurantRepository
import com.partimo.data.source.FlightOffersDataSource
import com.partimo.data.testing.MutableClock
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.dining.BudgetDiningCriteria
import com.partimo.domain.model.dining.RestaurantSearchQuery
import com.partimo.domain.model.flight.FlightOffer
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.flight.FlightSearchQuery
import com.partimo.domain.model.stay.AccommodationSearchQuery
import com.partimo.domain.model.transit.TransitRouteQuery
import com.partimo.domain.testing.TestData
import com.partimo.domain.testing.successData
import com.partimo.domain.usecase.FindBudgetRestaurantsUseCase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemoDataSourcesTest {

    private val catalog = DemoCatalog()
    private val departure = LocalDate.of(2026, Month.DECEMBER, 12)

    @Test
    fun `i voli demo rispettano date, passeggeri e tratte richieste`() = runTest {
        val query = FlightSearchQuery("MXP", "VIE", departure, departure.plusDays(4), adults = 2)

        val result = DemoFlightDataSource(catalog, latencyMillis = 0).searchOffers(query, forceRefresh = false)

        assertEquals(DataOrigin.DEMO, result.origin)
        assertTrue(result.data.isNotEmpty())
        assertTrue(result.data.all { it.slices.size == 2 && it.outbound.departureTime.toLocalDate() == departure })
        assertTrue(result.data.all { it.outbound.originIata == "MXP" && it.inbound?.originIata == "VIE" })
    }

    @Test
    fun `gli alloggi demo calcolano il prezzo sulle notti richieste`() = runTest {
        val query = AccommodationSearchQuery(TestData.VIENNA_CENTER, departure, departure.plusDays(4))

        val stays = DemoStayDataSource(catalog, latencyMillis = 0).searchStays(query, forceRefresh = false).data

        assertTrue(stays.all { it.nights == 4 })
    }

    @Test
    fun `i percorsi demo partono all'orario richiesto e hanno coincidenze valide`() = runTest {
        val query = TransitRouteQuery(TestData.VIENNA_HUB, TestData.VIENNA_CENTER, TestData.NOW)

        val routes = DemoTransitDataSource(catalog, latencyMillis = 0).routes(query, forceRefresh = false).data

        assertEquals(3, routes.size)
        assertTrue(routes.all { it.departureTime == TestData.NOW })
        assertTrue(routes.flatMap { it.connections }.none { it.isMissed })
    }

    @Test
    fun `il filtro budget del dominio scarta i ristoranti demo fuori criterio`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultRestaurantRepository(DemoRestaurantDataSource(catalog, latencyMillis = 0), dispatcher)
        val all = catalog.restaurants(RestaurantSearchQuery(TestData.VIENNA_CENTER))

        val budget = FindBudgetRestaurantsUseCase(repository)(TestData.VIENNA_CENTER).successData()

        assertTrue(budget.size in 1 until all.size)
        assertTrue(budget.all { BudgetDiningCriteria().matches(it) })
    }

    @Test
    fun `durata e prezzo dei voli demo crescono con la distanza`() = runTest {
        val airports = mapOf(
            "MXP" to GeoPoint(45.6306, 8.7281),
            "VIE" to GeoPoint(48.1103, 16.5697),
            "NRT" to GeoPoint(35.7686, 140.3887),
        )
        val demo = DemoCatalog(airportLocator = { airports[it] })

        val shortHaul = demo.flights(FlightSearchQuery("MXP", "VIE", departure, departure.plusDays(4)))
        val longHaul = demo.flights(FlightSearchQuery("MXP", "NRT", departure, departure.plusDays(4)))

        assertTrue(shortHaul.minOf { it.outbound.duration } < Duration.ofHours(2))
        assertTrue(longHaul.minOf { it.outbound.duration } > Duration.ofHours(11))
        assertTrue(longHaul.minOf { it.totalPrice.amount } > shortHaul.maxOf { it.totalPrice.amount })
    }

    @Test
    fun `i prezzi demo seguono un mercato simulato con offerte last minute`() = runTest {
        val clock = MutableClock()
        val demo = DemoCatalog(airportLocator = { mapOf("MXP" to GeoPoint(45.6306, 8.7281), "VIE" to GeoPoint(48.1103, 16.5697))[it] }, clock = clock)
        val query = FlightSearchQuery("MXP", "VIE", departure, departure.plusDays(4))
        suspend fun prices() = demo.flights(query).associate { it.carrierIata to it.totalPrice.amount.toDouble() }

        val first = prices()
        assertEquals(first, prices(), "Nello stesso intervallo i prezzi non cambiano")

        // In un giorno di intervalli da 10 minuti i prezzi oscillano e compaiono promozioni (−30%).
        val samples = (1..144).map {
            clock.advance(Duration.ofMinutes(10))
            prices()
        }
        assertTrue(samples.any { it != first }, "I prezzi devono cambiare nel tempo")
        val ratios = samples.flatMap { sample -> sample.map { (carrier, price) -> price / first.getValue(carrier) } }
        assertTrue(ratios.any { it < 0.8 }, "Attese offerte last minute")
        assertTrue(ratios.all { it > 0.55 && it < 1.8 }, "Variazioni fuori scala: ${ratios.min()}–${ratios.max()}")
    }

    @Test
    fun `alloggi e ristoranti demo si trovano vicino al centro richiesto`() = runTest {
        val lisbon = GeoPoint(38.7223, -9.1393)

        val stays = catalog.stays(AccommodationSearchQuery(lisbon, departure, departure.plusDays(3)))
        val restaurants = catalog.restaurants(RestaurantSearchQuery(lisbon))

        assertTrue(stays.all { it.location!!.distanceTo(lisbon) < 5_000 })
        assertTrue(restaurants.all { it.location!!.distanceTo(lisbon) < 7_000 })
    }

    @Test
    fun `il repository converte le eccezioni della sorgente in errori di dominio`() = runTest {
        val failingSource = object : FlightOffersDataSource {
            override suspend fun searchOffers(query: FlightSearchQuery, forceRefresh: Boolean): Fetched<List<FlightOffer>> =
                throw IOException("offline")
        }
        val repository = DefaultFlightRepository(failingSource, StandardTestDispatcher(testScheduler))

        val result = repository.searchFlights(FlightSearchQuery("MXP", "VIE", departure), forceRefresh = false)

        assertEquals(DataResult.Failure(DataError.NoConnection), result)
    }
}
