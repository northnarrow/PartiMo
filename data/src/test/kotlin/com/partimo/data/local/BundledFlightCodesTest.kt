package com.partimo.data.local

import kotlinx.coroutines.test.runTest
import java.io.File
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BundledFlightCodesTest {

    private val codes = BundledFlightCodes(
        openAirports = { File("src/main/assets/airport_cities.csv").inputStream() },
        openAirlines = { File("src/main/assets/airlines.csv").inputStream() },
        openCities = { File("src/main/assets/cities.csv").inputStream() },
    )

    @Test
    fun `le città dei voli hanno il nome italiano, il paese, le coordinate e il fuso`() = runTest {
        val london = assertNotNull(codes.city("LON"))
        assertEquals("Londra", london.name)
        assertEquals("GB", london.countryCode)
        assertEquals(ZoneId.of("Europe/London"), london.timeZone)
        assertEquals(51.5074, london.location.latitude, 0.001)
        assertEquals("Barcellona", codes.city("bcn")?.name)
        assertNull(codes.city("XXX"))
    }

    @Test
    fun `ogni città degli aeroporti ha il suo nome`() {
        val cities = parseFlightCities(File("src/main/assets/cities.csv").readLines().asSequence())
        val cityCodes = parseAirportCodes(File("src/main/assets/airport_cities.csv").readLines().asSequence()).values.map { it.cityCode }.toSet()

        assertEquals(emptySet(), cityCodes - cities.keys)
    }

    @Test
    fun `gli aeroporti delle grandi città hanno il codice della città e il loro fuso`() = runTest {
        assertEquals(AirportCodes("ROM", ZoneId.of("Europe/Rome")), codes.airport("FCO"))
        assertEquals("ROM", codes.airport("cia")?.cityCode)
        assertEquals("MIL", codes.airport("BGY")?.cityCode, "Bergamo vale come Milano")
        assertEquals(AirportCodes("LON", ZoneId.of("Europe/London")), codes.airport("STN"))
        assertEquals(AirportCodes("VIE", ZoneId.of("Europe/Vienna")), codes.airport("VIE"), "Un solo aeroporto: stesso codice")
        assertNull(codes.airport("XXX"))
    }

    @Test
    fun `i nomi delle compagnie arrivano dal codice IATA`() = runTest {
        assertEquals("Ryanair", codes.airlineName("FR"))
        assertEquals("ITA Airways", codes.airlineName("az"))
        assertEquals("Wizz Air", codes.airlineName("W6"))
        assertNull(codes.airlineName("QE"))
    }

    @Test
    fun `quasi tutti gli aeroporti del catalogo hanno codice e fuso`() {
        val airports = parseAirportsCsv(File("src/main/assets/airports.csv").readLines().asSequence())
        val known = parseAirportCodes(File("src/main/assets/airport_cities.csv").readLines().asSequence())

        val covered = airports.count { it.iata in known }
        assertTrue(covered >= airports.size * 0.98, "Coperti $covered aeroporti su ${airports.size}")
        assertTrue(airports.filter { it.countryCode == "IT" }.all { it.iata in known }, "Tutti quelli italiani")
    }
}
