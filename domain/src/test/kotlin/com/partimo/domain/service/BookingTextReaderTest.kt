package com.partimo.domain.service

import com.partimo.domain.model.booking.BookingKind
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Lettura delle conferme di prenotazione: testi come quelli delle email e dei PDF delle compagnie. */
class BookingTextReaderTest {

    private val reader = BookingTextReader(
        airports = setOf("BGY", "VIE", "MXP", "LIN", "LGW", "FCO", "BCN", "DEL"),
        airlines = mapOf("FR" to "Ryanair", "U2" to "easyJet", "AZ" to "ITA Airways", "OS" to "Austrian Airlines", "AT" to "Royal Air Maroc"),
    )
    private val today = LocalDate.of(2026, 10, 1)

    @Test
    fun `una conferma Ryanair con andata e ritorno diventa due voli con orari, aeroporti e codice`() {
        val text = """
            Conferma di prenotazione
            Codice di prenotazione: K7M2QX
            Volo di andata
            ven, 11 dic 2026
            FR 7178 Milano Bergamo (BGY) - Vienna (VIE)
            Partenza 21:10 Arrivo 22:55
            Volo di ritorno
            dom, 13 dic 2026
            FR 7179 Vienna (VIE) - Milano Bergamo (BGY)
            Partenza 08:25 Arrivo 10:10
            Passeggeri: Mario Rossi
        """.trimIndent()

        val (outbound, inbound) = reader.read(text, today)

        assertEquals(BookingKind.FLIGHT, outbound.kind)
        assertEquals("Ryanair FR 7178", outbound.title)
        assertEquals(LocalDate.of(2026, 12, 11), outbound.startDate)
        assertEquals(LocalTime.of(21, 10), outbound.startTime)
        assertEquals(LocalTime.of(22, 55), outbound.endTime)
        assertEquals("BGY" to "VIE", outbound.origin to outbound.destination)
        assertEquals("K7M2QX", outbound.reference)
        assertEquals("Ryanair", outbound.provider)
        assertEquals("Ryanair FR 7179", inbound.title)
        assertEquals(LocalDate.of(2026, 12, 13), inbound.startDate)
        assertEquals(LocalTime.of(8, 25), inbound.startTime)
        assertEquals("VIE" to "BGY", inbound.origin to inbound.destination)
        assertEquals("K7M2QX", inbound.reference)
    }

    @Test
    fun `una conferma in inglese con il codice ICAO e la data all'americana`() {
        val text = """
            Your booking reference: EZY4567
            Flight EZY8131 London Gatwick (LGW) to Milan Malpensa (MXP)
            Departs Thu Dec 10, 2026 07:15 - Arrives 10:20
        """.trimIndent()

        val flight = reader.read(text, today).single()

        assertEquals("Volo EZY 8131", flight.title)
        assertEquals(LocalDate.of(2026, 12, 10), flight.startDate)
        assertEquals(LocalTime.of(7, 15), flight.startTime)
        assertEquals(LocalTime.of(10, 20), flight.endTime)
        assertEquals("LGW" to "MXP", flight.origin to flight.destination)
        assertEquals("EZY4567", flight.reference)
    }

    @Test
    fun `un volo notturno arriva il giorno dopo`() {
        val text = "AZ 610 Roma Fiumicino (FCO) → Milano Linate (LIN) 12/12/2026 23:30 00:40"

        val flight = reader.read(text, today).single()

        assertEquals(LocalDate.of(2026, 12, 12), flight.startDate)
        assertEquals(LocalDate.of(2026, 12, 13), flight.endDate)
        assertEquals("ITA Airways AZ 610", flight.title)
    }

    @Test
    fun `una conferma di Booking diventa un alloggio con check-in, check-out, indirizzo e numero di conferma`() {
        val text = """
            Grazie! La tua prenotazione a Vienna è confermata.
            Booking.com
            Hotel Sacher Wien
            Philharmoniker Straße 4, 1010 Vienna, Austria
            Numero di conferma: 4123.567.890
            Check-in: venerdì 11 dicembre 2026 (dalle 15:00)
            Check-out: domenica 13 dicembre 2026 (fino alle 12:00)
            2 notti, 1 camera
        """.trimIndent()

        val stay = reader.read(text, today).single()

        assertEquals(BookingKind.LODGING, stay.kind)
        assertEquals("Hotel Sacher Wien", stay.title)
        assertEquals(LocalDate.of(2026, 12, 11), stay.startDate)
        assertEquals(LocalTime.of(15, 0), stay.startTime)
        assertEquals(LocalDate.of(2026, 12, 13), stay.endDate)
        assertEquals(LocalTime.of(12, 0), stay.endTime)
        assertEquals("Philharmoniker Straße 4, 1010 Vienna, Austria", stay.address)
        assertEquals("4123.567.890", stay.reference)
        assertEquals("Booking.com", stay.provider)
    }

    @Test
    fun `un biglietto Frecciarossa diventa un treno con tratta, orari e PNR`() {
        val text = """
            Trenitalia - Biglietto
            Frecciarossa 9521
            Milano Centrale → Roma Termini
            Data: 11/12/2026 Partenza 08.00 Arrivo 11.10
            Carrozza 5 Posto 7A
            PNR: QWERTY
        """.trimIndent()

        val train = reader.read(text, today).single()

        assertEquals(BookingKind.TRAIN, train.kind)
        assertEquals("Frecciarossa 9521", train.title)
        assertEquals("Milano Centrale" to "Roma Termini", train.origin to train.destination)
        assertEquals(LocalDate.of(2026, 12, 11), train.startDate)
        assertEquals(LocalTime.of(8, 0), train.startTime)
        assertEquals(LocalTime.of(11, 10), train.endTime)
        assertEquals("QWERTY", train.reference)
        assertEquals("Trenitalia", train.provider)
    }

    @Test
    fun `un biglietto FlixBus diventa un pullman con le date tedesche e la tratta`() {
        val text = """
            FlixBus - Conferma di prenotazione n. 3001234567
            Milano Lampugnano -> Venezia Tronchetto
            11.12.2026 07:30 - 11.12.2026 10:45
        """.trimIndent()

        val bus = reader.read(text, today).single()

        assertEquals(BookingKind.BUS, bus.kind)
        assertEquals("FlixBus Milano Lampugnano → Venezia Tronchetto", bus.title)
        assertEquals(LocalDate.of(2026, 12, 11), bus.startDate)
        assertEquals(LocalTime.of(7, 30), bus.startTime)
        assertEquals(LocalTime.of(10, 45), bus.endTime)
        assertEquals("3001234567", bus.reference)
    }

    @Test
    fun `senza anno vale il prossimo giorno con quella data e una parola qualunque non è un aeroporto`() {
        val text = "Visita guidata del Duomo\n5 gennaio alle 10:30\nDEL MAR: punto d'incontro all'ingresso"

        val visit = reader.read(text, today).single()

        assertEquals(BookingKind.OTHER, visit.kind)
        assertEquals("Visita guidata del Duomo", visit.title)
        assertEquals(LocalDate.of(2027, 1, 5), visit.startDate)
        assertEquals(LocalTime.of(10, 30), visit.startTime)
    }

    @Test
    fun `FR su un biglietto del treno è un Frecciarossa, non un volo Ryanair`() {
        val text = "Trenitalia\nTreno FR 9521\nMilano Centrale → Roma Termini\n11/12/2026 08:00 11:10\nCarrozza 5"

        val train = reader.read(text, today).single()

        assertEquals(BookingKind.TRAIN, train.kind)
        assertEquals("FR 9521", train.title)
    }

    @Test
    fun `due voli in coincidenza nello stesso giorno hanno entrambi la data`() {
        val text = """
            Itinerario - sab 12 dic 2026
            OS 512 Milano Malpensa (MXP) - Vienna (VIE) 07:00 08:30
            OS 25 Vienna (VIE) - Barcellona (BCN) 10:15 12:50
        """.trimIndent()

        val (first, second) = reader.read(text, today)

        assertEquals(LocalDate.of(2026, 12, 12), first.startDate)
        assertEquals(LocalDate.of(2026, 12, 12), second.startDate)
        assertEquals("VIE" to "BCN", second.origin to second.destination)
        assertEquals(LocalTime.of(10, 15), second.startTime)
    }

    @Test
    fun `un indirizzo con il codice postale non diventa un volo e un testo senza date non è una prenotazione`() {
        assertTrue(reader.read("Vi aspettiamo in Via Roma 12, AT 1010 non è un volo", today).isEmpty())
        assertTrue(reader.read("Grazie per averci scelto!", today).isEmpty())
        assertTrue(reader.read("   ", today).isEmpty())
    }
}
