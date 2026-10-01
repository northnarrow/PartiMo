package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.backup.UserData
import com.partimo.domain.model.booking.Booking
import com.partimo.domain.model.booking.BookingKind
import com.partimo.domain.model.booking.BookingReminderKind
import com.partimo.domain.model.ocr.DocumentSource
import com.partimo.domain.model.ocr.RecognizedText
import com.partimo.domain.model.ocr.TextScript
import com.partimo.domain.testing.FakeBookingRepository
import com.partimo.domain.testing.FakeFlightCodesRepository
import com.partimo.domain.testing.FakeReminderLogRepository
import com.partimo.domain.testing.FakeTextRecognitionRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.testing.failureError
import com.partimo.domain.testing.successData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookingUseCasesTest {

    private val repository = FakeBookingRepository()
    private val codes = FakeFlightCodesRepository()
    private val outbound = Booking(
        id = "andata",
        kind = BookingKind.FLIGHT,
        title = "Ryanair FR 7178",
        startDate = LocalDate.of(2026, 12, 11),
        startTime = LocalTime.of(21, 10),
        origin = "BGY",
        destination = "VIE",
    )
    private val back = outbound.copy(id = "ritorno", title = "Ryanair FR 7179", startDate = LocalDate.of(2026, 12, 13), startTime = LocalTime.of(8, 25), origin = "VIE", destination = "BGY")
    private val hotel = Booking(id = "hotel", kind = BookingKind.LODGING, title = "Hotel Sacher Wien", startDate = LocalDate.of(2026, 12, 11), endDate = LocalDate.of(2026, 12, 13))

    @Test
    fun `salvando un volo il fuso è quello dell'aeroporto di partenza e salvare di nuovo lo modifica`() = runTest {
        val save = SaveBookingUseCase(repository, codes)

        assertEquals(ZoneId.of("Europe/Rome"), save(outbound).timeZone)
        assertEquals(ZoneId.of("Europe/Vienna"), save(back).timeZone)
        assertNull(save(hotel).timeZone, "Un alloggio usa il fuso del telefono")
        save(outbound.copy(title = "Ryanair FR 7178 (posto 12A)"))

        assertEquals(listOf("andata", "ritorno", "hotel"), repository.current.map { it.id })
        assertEquals("Ryanair FR 7178 (posto 12A)", repository.current.first().title)
    }

    @Test
    fun `le prenotazioni si vedono in ordine di partenza e togliendone una la si ritrova per cancellarne il documento`() = runTest {
        val save = SaveBookingUseCase(repository, codes)
        save(back)
        save(outbound.copy(attachment = "booking-andata.pdf"))
        save(hotel)
        save(Booking(id = "museo", kind = BookingKind.ACTIVITY, title = "Albertina", startDate = LocalDate.of(2026, 12, 11)))

        assertEquals(
            listOf("museo", "andata", "hotel", "ritorno"),
            ObserveBookingsUseCase(repository)().first().map { it.id },
            "Nello stesso giorno prima quelle senza ora e l'alloggio dopo il volo",
        )

        val removed = DeleteBookingUseCase(repository)("andata")
        assertEquals("booking-andata.pdf", removed?.attachment)
        assertNull(DeleteBookingUseCase(repository)("andata"))
        assertEquals(listOf("ritorno", "hotel", "museo"), repository.current.map { it.id })
    }

    @Test
    fun `il testo di una conferma diventa una prenotazione per volo, con gli aeroporti caricati una volta`() = runTest {
        val read = ReadBookingTextUseCase(codes, TestData.FIXED_CLOCK)
        val text = "Codice di prenotazione: K7M2QX\nven, 11 dic 2026\nFR 7178 Milano Bergamo (BGY) - Vienna (VIE)\nPartenza 21:10 Arrivo 22:55"

        val flight = read(text).single()
        read(text)

        assertEquals("Ryanair FR 7178", flight.title)
        assertEquals("BGY", flight.origin)
        assertEquals(1, codes.loads)
    }

    @Test
    fun `un PDF o una foto si leggono con il riconoscimento del testo`() = runTest {
        val ocr = FakeTextRecognitionRepository(DataResult.Success(RecognizedText("Hotel Sacher Wien\nCheck-in: 11 dicembre 2026\nCheck-out: 13 dicembre 2026\n2 notti")))
        val read = ReadBookingDocumentUseCase(ocr, ReadBookingTextUseCase(codes, TestData.FIXED_CLOCK))
        val pdf = DocumentSource("content://download/sacher.pdf", "application/pdf")

        val imported = read(pdf).successData()

        assertEquals(listOf("Hotel Sacher Wien"), imported.drafts.map { it.title })
        assertEquals(pdf to TextScript.LATIN, ocr.requests.single())
        assertTrue(pdf.isPdf)

        ocr.result = DataResult.Success(RecognizedText("   "))
        assertEquals(DataError.InvalidResponse, read(pdf).failureError(), "Nessun testo: non è una conferma leggibile")
        ocr.result = DataResult.Failure(DataError.Unknown("modello non ancora scaricato"))
        assertEquals(DataError.Unknown("modello non ancora scaricato"), read(pdf).failureError())
    }

    @Test
    fun `i promemoria si pianificano dalle prenotazioni salvate`() = runTest {
        SaveBookingUseCase(repository, codes)(outbound)
        val reminders = BookingRemindersUseCase(repository, FakeReminderLogRepository(), TestData.FIXED_CLOCK)

        assertEquals(listOf(BookingReminderKind.CHECK_IN, BookingReminderKind.LEAVE_FOR_AIRPORT), reminders.upcoming().map { it.kind })
        assertEquals(reminders.upcoming().first().at, reminders.nextCheck())
        assertTrue(reminders.takeDue().isEmpty())
    }

    @Test
    fun `al momento giusto il promemoria si mostra una volta sola e dopo il telefono spento solo l'ultimo`() = runTest {
        val save = SaveBookingUseCase(repository, codes)
        save(outbound)
        save(hotel)
        val log = FakeReminderLogRepository(setOf("booking:vecchia:CHECK_IN", "AT:Vienna:2026-12:WEEK_BEFORE"))
        // Il telefono si riaccende alle 19:00 del giorno del volo (21:10): check-in e partenza sono già scattati.
        val clock = Clock.fixed(Instant.parse("2026-12-11T18:00:00Z"), ZoneId.of("Europe/Rome"))
        val reminders = BookingRemindersUseCase(repository, log, clock)

        assertEquals(Instant.parse("2026-12-11T18:00:00Z"), reminders.nextCheck(), "Promemoria in sospeso: subito")
        val due = reminders.takeDue()

        assertEquals(listOf("hotel:CHECK_IN_TODAY", "andata:LEAVE_FOR_AIRPORT"), due.map { it.key }, "Un alloggio senza ora vale fino a sera")
        assertEquals(
            setOf("booking:andata:CHECK_IN", "booking:andata:LEAVE_FOR_AIRPORT", "booking:hotel:CHECK_IN_TODAY", "AT:Vienna:2026-12:WEEK_BEFORE"),
            log.sent,
            "Segnati anche quelli non mostrati; dimenticate le chiavi delle prenotazioni tolte, non quelle dei viaggi",
        )
        assertTrue(reminders.takeDue().isEmpty())
        assertNull(reminders.nextCheck(), "Nessun altro promemoria da pianificare")
    }

    @Test
    fun `nel backup le prenotazioni del file sostituiscono le stesse ma il documento del telefono resta`() {
        val phone = UserData(bookings = listOf(outbound.copy(attachment = "booking-andata.pdf", attachmentType = "application/pdf")))
        val file = UserData(bookings = listOf(outbound.copy(title = "Ryanair FR 7178 · posto 12A"), hotel))

        val merged = phone.mergedWith(file)

        assertEquals(listOf("Ryanair FR 7178 · posto 12A", "Hotel Sacher Wien"), merged.bookings.map { it.title })
        assertEquals("booking-andata.pdf", merged.bookings.first().attachment)
        assertEquals(2, merged.summary.bookings)
    }
}
