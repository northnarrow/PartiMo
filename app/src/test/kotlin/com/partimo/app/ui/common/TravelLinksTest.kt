package com.partimo.app.ui.common

import java.time.LocalDate
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals

class TravelLinksTest {

    private val departure = LocalDate.of(2026, Month.DECEMBER, 10)
    private val returnDate = LocalDate.of(2026, Month.DECEMBER, 14)

    @Test
    fun `i voli si aprono su Google Voli e Skyscanner con tratta e date compilate`() {
        assertEquals(
            "https://www.google.com/travel/flights?q=Flights%20from%20MXP%20to%20VIE%20on%202026-12-10%20through%202026-12-14&hl=it&curr=EUR",
            TravelLinks.googleFlights("MXP", "VIE", departure, returnDate),
        )
        assertEquals(
            "https://www.skyscanner.it/trasporti/voli/mxp/vie/261210/261214/?adultsv2=2",
            TravelLinks.skyscanner("MXP", "VIE", departure, returnDate, adults = 2),
        )
    }

    @Test
    fun `gli alloggi si aprono su Booking e Airbnb con città, struttura e date`() {
        assertEquals(
            "https://www.booking.com/searchresults.it.html?ss=Vienna&checkin=2026-12-10&checkout=2026-12-14&group_adults=1&no_rooms=1&group_children=0",
            TravelLinks.booking("Vienna", departure, returnDate, adults = 1),
        )
        assertEquals(
            "https://www.booking.com/searchresults.it.html?ss=Hotel%20Sacher%20Wien%2C%20Vienna&checkin=2026-12-10&checkout=2026-12-14&group_adults=1&no_rooms=1&group_children=0",
            TravelLinks.booking("Hotel Sacher Wien, Vienna", departure, returnDate, adults = 1),
        )
        assertEquals(
            "https://www.airbnb.it/s/S%C3%A3o%20Paulo/homes?checkin=2026-12-10&checkout=2026-12-14&adults=1",
            TravelLinks.airbnb("São Paulo", departure, returnDate, adults = 1),
            "Spazi e accenti codificati anche nel percorso",
        )
    }

    @Test
    fun `treni e pullman su Rome2rio e biglietti su Tiqets con nomi codificati`() {
        assertEquals("https://www.rome2rio.com/s/Milano/Vienna", TravelLinks.rome2rio("Milano", "Vienna"))
        assertEquals(
            "https://www.rome2rio.com/s/Reggio-Emilia/Monaco-di-Baviera",
            TravelLinks.rome2rio(" Reggio  Emilia ", "Monaco di Baviera"),
            "Spazi come trattini nel percorso",
        )
        assertEquals("https://www.rome2rio.com/s/Milano/Z%C3%BCrich", TravelLinks.rome2rio("Milano", "Zürich"))
        assertEquals("https://www.tiqets.com/it/search?q=Kunsthistorisches%20Museum", TravelLinks.tiqets("Kunsthistorisches Museum"))
    }
}
