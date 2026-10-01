package com.partimo.domain.model

import com.partimo.domain.common.QueryIssue
import com.partimo.domain.model.flight.FlexibleDates
import com.partimo.domain.model.flight.FlightSearchQuery
import com.partimo.domain.model.poi.Season
import com.partimo.domain.model.stay.AccommodationSearchQuery
import com.partimo.domain.testing.TestData
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
        assertEquals(
            QueryIssue.INVALID_TRAVELLER_COUNT,
            flightQuery.copy(travellers = Travellers(adults = 1, childAges = listOf(0, 1))).validate(today),
            "Due neonati in braccio a un solo adulto",
        )
    }

    @Test
    fun `senza date flessibili valgono solo le date del viaggio`() {
        assertTrue(flightQuery.matchesDates(today.plusDays(10), today.plusDays(14)))
        assertFalse(flightQuery.matchesDates(today.plusDays(11), today.plusDays(14)))
        assertFalse(flightQuery.matchesDates(today.plusDays(10), null))
    }

    @Test
    fun `con le date flessibili vanno bene le partenze nella finestra per due-sette notti`() {
        val flexible = flightQuery.copy(flexibleDates = FlexibleDates(today.plusDays(1)..today.plusDays(30), TravelPeriod.FLEXIBLE_STAY_NIGHTS))

        assertTrue(flexible.matchesDates(today.plusDays(3), today.plusDays(5)), "Un fine settimana")
        assertTrue(flexible.matchesDates(today.plusDays(30), today.plusDays(37)), "Una settimana, partendo l'ultimo giorno")
        assertFalse(flexible.matchesDates(today.plusDays(3), today.plusDays(4)), "Una notte sola")
        assertFalse(flexible.matchesDates(today.plusDays(3), today.plusDays(11)), "Più di una settimana")
        assertFalse(flexible.matchesDates(today.plusDays(31), today.plusDays(34)), "Fuori dalla finestra")
        assertFalse(flexible.matchesDates(today.plusDays(3), null), "Sola andata per un viaggio con ritorno")
        assertTrue(flexible.copy(returnDate = null).matchesDates(today.plusDays(3), null))
        assertTrue(flexible.matchesDates(today.plusDays(10), today.plusDays(14)), "Le date del viaggio vanno sempre bene")
    }

    @Test
    fun `un volo è nelle date del viaggio solo se parte e torna proprio in quei giorni`() {
        val offer = TestData.flightOffer("a", "88", departure = today.plusDays(10).atTime(8, 0))
        val back = offer.outbound.copy(departureTime = today.plusDays(14).atTime(18, 0), arrivalTime = today.plusDays(14).atTime(19, 30))

        assertTrue(flightQuery.isOnTripDates(offer.copy(slices = offer.slices + back)))
        assertFalse(flightQuery.isOnTripDates(offer), "Senza ritorno")
        assertFalse(flightQuery.copy(departureDate = today.plusDays(9)).isOnTripDates(offer.copy(slices = offer.slices + back)))
    }

    @Test
    fun `le notti del soggiorno contano dai giorni di partenza di andata e ritorno`() {
        val outbound = TestData.flightOffer("a", "88", departure = LocalDateTime.of(2026, Month.DECEMBER, 11, 21, 10))
        val inbound = outbound.outbound.copy(
            originIata = "VIE",
            destinationIata = "MXP",
            departureTime = LocalDateTime.of(2026, Month.DECEMBER, 13, 8, 25),
            arrivalTime = LocalDateTime.of(2026, Month.DECEMBER, 13, 10, 10),
        )

        assertNull(outbound.stayNights)
        assertEquals(2, outbound.copy(slices = outbound.slices + inbound).stayNights)
    }

    @Test
    fun `la ricerca alloggi verifica date e ospiti`() {
        val stay = AccommodationSearchQuery(TestData.VIENNA_CENTER, today.plusDays(10), today.plusDays(14))
        assertNull(stay.validate(today))
        assertEquals(4, stay.nights)
        assertEquals(QueryIssue.INVALID_STAY_DATES, stay.copy(checkOut = stay.checkIn).validate(today))
        assertEquals(QueryIssue.STAY_TOO_LONG, stay.copy(checkOut = stay.checkIn.plusDays(45)).validate(today))
        assertEquals(QueryIssue.INVALID_TRAVELLER_COUNT, stay.copy(travellers = Travellers(adults = 1), rooms = 2).validate(today))
    }
}

class PointOfInterestTest {

    @Test
    fun `la notorietà deve stare tra 0 e 1`() {
        assertEquals(0.4, TestData.poi("ok", popularity = 0.4).popularity)
        assertFailsWith<IllegalArgumentException> { TestData.poi("troppo", popularity = 1.5) }
        assertFailsWith<IllegalArgumentException> { TestData.poi("negativa", popularity = -0.1) }
    }
}
