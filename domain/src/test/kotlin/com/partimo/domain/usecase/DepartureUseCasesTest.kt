package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.common.QueryIssue
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.place.AirportSize
import com.partimo.domain.testing.FakeAirportRepository
import com.partimo.domain.testing.FakeUserPreferencesRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.testing.successData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DepartureUseCasesTest {

    private val milan = TestData.city("Milano", location = GeoPoint(45.4642, 9.19), countryCode = "IT")
    private val malpensa = TestData.airport("MXP", GeoPoint(45.6306, 8.7281), AirportSize.HUB, "Milan Malpensa International Airport")
    private val linate = TestData.airport("LIN", GeoPoint(45.4451, 9.2767), AirportSize.LARGE, "Milano Linate Airport")
    private val bergamo = TestData.airport("BGY", GeoPoint(45.6694, 9.7089), AirportSize.LARGE, "Il Caravaggio International Airport")

    @Test
    fun `propone prima l'aeroporto consigliato e poi gli altri per distanza`() = runTest {
        val repository = FakeAirportRepository(DataResult.Success(listOf(bergamo, linate, malpensa)))

        val options = FindDepartureAirportsUseCase(repository)(milan).successData()

        assertEquals(listOf("MXP", "LIN", "BGY"), options.map { it.airport.iata })
        assertEquals(listOf(true, false, false), options.map { it.recommended })
        assertEquals(7, options[1].distanceKm)
        assertEquals(milan.location, repository.requestedLocations.single())
    }

    @Test
    fun `senza aeroporti vicini restituisce un errore`() = runTest {
        val result = FindDepartureAirportsUseCase(FakeAirportRepository(DataResult.Success(emptyList())))(milan)

        assertEquals(DataResult.Failure(DataError.InvalidQuery(QueryIssue.NO_AIRPORT_NEARBY)), result)
    }

    @Test
    fun `il punto di partenza scelto viene salvato e osservato`() = runTest {
        val preferences = FakeUserPreferencesRepository()
        val observe = ObserveDepartureUseCase(preferences)
        assertNull(observe().first())

        SaveDepartureUseCase(preferences)(TestData.departure("Milano", "MXP"))

        assertEquals("MXP", observe().first()?.airport?.iata)
    }

    @Test
    fun `il nome breve dell'aeroporto toglie i suffissi generici`() {
        assertEquals("Milan Malpensa", malpensa.shortName)
        assertEquals("Milano Linate", linate.shortName)
        assertEquals("Aeroporto VIE", TestData.airport("VIE").shortName)
    }
}
