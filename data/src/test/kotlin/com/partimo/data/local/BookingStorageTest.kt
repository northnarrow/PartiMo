package com.partimo.data.local

import com.partimo.data.local.preferences.DataStoreBookingRepository
import com.partimo.data.local.preferences.DataStoreUserDataRepository
import com.partimo.data.local.preferences.StoredJson
import com.partimo.domain.model.booking.Booking
import com.partimo.domain.model.booking.BookingKind
import com.partimo.domain.testing.TestData
import com.partimo.domain.usecase.ExportUserDataUseCase
import com.partimo.domain.usecase.ImportUserDataUseCase
import com.partimo.domain.usecase.SaveBookingUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Prenotazioni salvate con DataStore e nel file di backup; codici dei voli inclusi nell'app. */
class BookingStorageTest {

    private val flight = Booking(
        id = "b1",
        kind = BookingKind.FLIGHT,
        title = "Ryanair FR 7178",
        startDate = LocalDate.of(2026, 12, 11),
        startTime = LocalTime.of(21, 10),
        endDate = LocalDate.of(2026, 12, 11),
        endTime = LocalTime.of(22, 55),
        timeZone = ZoneId.of("Europe/Rome"),
        origin = "BGY",
        destination = "VIE",
        reference = "K7M2QX",
        provider = "Ryanair",
        notes = "Posto 12A",
        attachment = "booking-b1.pdf",
        attachmentType = "application/pdf",
    )
    private val hotel = Booking(id = "b2", kind = BookingKind.LODGING, title = "Hotel Sacher Wien", startDate = LocalDate.of(2026, 12, 11), address = "Philharmoniker Straße 4")
    private val codes = BundledFlightCodesRepository(
        BundledFlightCodes(
            openAirports = { File("src/main/assets/airport_cities.csv").inputStream() },
            openAirlines = { File("src/main/assets/airlines.csv").inputStream() },
        ),
    )

    @Test
    fun `le prenotazioni si salvano con tutti i campi e sparisce la chiave quando non ce ne sono`() = runTest {
        val store = InMemoryPreferencesDataStore()
        val repository = DataStoreBookingRepository(store)

        repository.update { listOf(flight, hotel) }
        assertEquals(listOf(flight, hotel), repository.bookings.first())

        repository.update { emptyList() }
        assertTrue(store.data.first().asMap().isEmpty())
    }

    @Test
    fun `un allegato con un percorso al posto del nome viene ignorato e le voci illeggibili si scartano`() {
        val stored = StoredJson.encodeBookings(listOf(flight.copy(attachment = "../../shared_prefs/x.xml"), hotel))
            .replace("\"title\":\"Hotel Sacher Wien\",\"startDate\":\"2026-12-11\"", "\"title\":\"Hotel Sacher Wien\",\"startDate\":\"non è una data\"")

        val decoded = StoredJson.decodeBookings(stored)

        assertNull(decoded.single().attachment)
        assertEquals("b1", decoded.single().id)
    }

    @Test
    fun `nel backup ci sono le prenotazioni ma non il nome del documento, che resta sul telefono`() = runTest {
        val phone = InMemoryPreferencesDataStore()
        DataStoreBookingRepository(phone).update { listOf(flight, hotel) }
        val export = ExportUserDataUseCase(DataStoreUserDataRepository(phone), TestData.FIXED_CLOCK)()
        assertEquals(2, export.summary.bookings)

        val newPhone = InMemoryPreferencesDataStore()
        ImportUserDataUseCase(DataStoreUserDataRepository(newPhone))(export.content)

        val restored = DataStoreBookingRepository(newPhone).bookings.first()
        assertEquals(listOf(flight.copy(attachment = null, attachmentType = null), hotel), restored)
    }

    @Test
    fun `i codici dei voli inclusi danno aeroporti, compagnie e il fuso dell'aeroporto di partenza`() = runTest {
        assertTrue("BGY" in codes.airportCodes() && "VIE" in codes.airportCodes())
        assertEquals("Ryanair", codes.airlines()["FR"])
        assertEquals(ZoneId.of("Europe/Vienna"), codes.airportTimeZone("VIE"))

        val saved = SaveBookingUseCase(DataStoreBookingRepository(InMemoryPreferencesDataStore()), codes)(flight.copy(timeZone = null, origin = "VIE"))
        assertEquals(ZoneId.of("Europe/Vienna"), saved.timeZone)
    }
}
