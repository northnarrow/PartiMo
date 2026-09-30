package com.partimo.domain.model

import com.partimo.domain.common.QueryIssue
import com.partimo.domain.model.flight.FlightSearchQuery
import com.partimo.domain.model.poi.Season
import com.partimo.domain.model.stay.AccommodationSearchQuery
import com.partimo.domain.testing.TestData
import java.math.BigDecimal
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoneyTest {

    @Test
    fun `i factory normalizzano a due decimali e la valuta in maiuscolo`() {
        assertEquals(Money(BigDecimal("129.50"), "EUR"), Money.of("129.5", "eur"))
    }

    @Test
    fun `la divisione arrotonda al centesimo`() {
        assertEquals(Money.of("33.33", "EUR"), Money.of(100, "EUR") / 3)
    }

    @Test
    fun `importi negativi o valute non valide sono rifiutati`() {
        assertFailsWith<IllegalArgumentException> { Money.of("-1", "EUR") }
        assertFailsWith<IllegalArgumentException> { Money.of("10", "EURO") }
    }
}

class GeoPointTest {

    @Test
    fun `calcola la distanza con la formula dell'haversine`() {
        val meters = TestData.VIENNA_CENTER.distanceTo(TestData.VIENNA_HUB)
        assertTrue(meters in 2_400.0..2_700.0, "Distanza inattesa: $meters")
    }

    @Test
    fun `riconosce l'emisfero`() {
        assertEquals(Hemisphere.NORTHERN, TestData.VIENNA_CENTER.hemisphere)
        assertEquals(Hemisphere.SOUTHERN, TestData.SYDNEY_CENTER.hemisphere)
    }

    @Test
    fun `coordinate fuori intervallo sono rifiutate`() {
        assertFailsWith<IllegalArgumentException> { GeoPoint(91.0, 0.0) }
    }
}

class SeasonTest {

    @Test
    fun `dicembre è inverno a nord ed estate a sud`() {
        assertEquals(Season.WINTER, Season.of(Month.DECEMBER, Hemisphere.NORTHERN))
        assertEquals(Season.SUMMER, Season.of(Month.DECEMBER, Hemisphere.SOUTHERN))
    }

    @Test
    fun `l'inverno australe va da giugno ad agosto`() {
        assertEquals(setOf(Month.JUNE, Month.JULY, Month.AUGUST), Season.WINTER.months(Hemisphere.SOUTHERN))
    }

    @Test
    fun `ogni mese appartiene a una sola stagione`() {
        Month.entries.forEach { month ->
            assertEquals(1, Season.entries.count { month in it.months() }, "Mese $month")
        }
    }
}

class QueryValidationTest {

    private val today = TestData.TODAY
    private val flightQuery = FlightSearchQuery("MXP", "VIE", today.plusDays(10), today.plusDays(14))

    @Test
    fun `una ricerca voli corretta è valida`() {
        assertNull(flightQuery.validate(today))
    }

    @Test
    fun `la ricerca voli segnala il primo problema`() {
        assertEquals(QueryIssue.INVALID_AIRPORT_CODE, flightQuery.copy(originIata = "MX").validate(today))
        assertEquals(QueryIssue.SAME_ORIGIN_AND_DESTINATION, flightQuery.copy(destinationIata = "mxp").validate(today))
        assertEquals(QueryIssue.DATE_IN_THE_PAST, flightQuery.copy(departureDate = today.minusDays(1)).validate(today))
        assertEquals(
            QueryIssue.RETURN_BEFORE_DEPARTURE,
            flightQuery.copy(returnDate = flightQuery.departureDate.minusDays(1)).validate(today),
        )
        assertEquals(QueryIssue.INVALID_TRAVELLER_COUNT, flightQuery.copy(adults = 0).validate(today))
    }

    @Test
    fun `la ricerca alloggi verifica date e ospiti`() {
        val stay = AccommodationSearchQuery(TestData.VIENNA_CENTER, today.plusDays(10), today.plusDays(14))
        assertNull(stay.validate(today))
        assertEquals(4, stay.nights)
        assertEquals(QueryIssue.INVALID_STAY_DATES, stay.copy(checkOut = stay.checkIn).validate(today))
        assertEquals(QueryIssue.STAY_TOO_LONG, stay.copy(checkOut = stay.checkIn.plusDays(45)).validate(today))
        assertEquals(QueryIssue.INVALID_TRAVELLER_COUNT, stay.copy(adults = 1, rooms = 2).validate(today))
    }
}
