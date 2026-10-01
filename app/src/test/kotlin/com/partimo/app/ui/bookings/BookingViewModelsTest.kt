package com.partimo.app.ui.bookings

import com.partimo.app.files.BookingDocuments
import com.partimo.app.files.FileTooLargeException
import com.partimo.app.files.StoredDocument
import com.partimo.app.testing.MainDispatcherRule
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.booking.Booking
import com.partimo.domain.model.booking.BookingDraft
import com.partimo.domain.model.booking.BookingKind
import com.partimo.domain.model.ocr.RecognizedText
import com.partimo.domain.testing.FakeBookingRepository
import com.partimo.domain.testing.FakeFlightCodesRepository
import com.partimo.domain.testing.FakeTextRecognitionRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.usecase.DeleteBookingUseCase
import com.partimo.domain.usecase.ObserveBookingsUseCase
import com.partimo.domain.usecase.ReadBookingDocumentUseCase
import com.partimo.domain.usecase.ReadBookingTextUseCase
import com.partimo.domain.usecase.SaveBookingUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Documenti delle prenotazioni in memoria. */
private class FakeBookingDocuments : BookingDocuments {
    val stored = mutableMapOf<String, String>()
    val deleted = mutableListOf<String>()
    var failure: Exception? = null
    private var count = 0

    override suspend fun import(uri: String, mimeType: String?): StoredDocument {
        failure?.let { throw it }
        val document = StoredDocument("booking-${++count}.pdf", mimeType ?: "application/pdf")
        stored[document.name] = document.mimeType
        return document
    }

    override fun shareableUri(name: String): String? = if (name in stored) "content://com.partimo.app.files/bookings/$name" else null

    override suspend fun delete(name: String) {
        stored.remove(name)
        deleted += name
    }

    override suspend fun deleteUnused(used: Set<String>) {
        stored.keys.retainAll(used)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BookingViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val repository = FakeBookingRepository()
    private val codes = FakeFlightCodesRepository()
    private val ocr = FakeTextRecognitionRepository()
    private val documents = FakeBookingDocuments()
    private val readText = ReadBookingTextUseCase(codes, TestData.FIXED_CLOCK)

    private val confirmation = """
        Conferma di prenotazione
        Codice di prenotazione: K7M2QX
        ven, 11 dic 2026
        FR 7178 Milano Bergamo (BGY) - Vienna (VIE)
        Partenza 21:10 Arrivo 22:55
        dom, 13 dic 2026
        FR 7179 Vienna (VIE) - Milano Bergamo (BGY)
        Partenza 08:25 Arrivo 10:10
    """.trimIndent()

    private fun booking(id: String, date: LocalDate, kind: BookingKind = BookingKind.FLIGHT, end: LocalDate? = null, attachment: String? = null) =
        Booking(id = id, kind = kind, title = "Prenotazione $id", startDate = date, startTime = LocalTime.of(9, 0), endDate = end, attachment = attachment)

    private fun editor(bookingId: String? = null, shared: SharedBookingContent? = null, defaultDate: LocalDate? = null) = BookingEditorViewModel(
        observeBookings = ObserveBookingsUseCase(repository),
        saveBooking = SaveBookingUseCase(repository, codes),
        deleteBooking = DeleteBookingUseCase(repository),
        readBookingText = readText,
        readBookingDocument = ReadBookingDocumentUseCase(ocr, readText),
        documents = documents,
        clock = TestData.FIXED_CLOCK,
        bookingId = bookingId,
        shared = shared,
        defaultDate = defaultDate,
    )

    @Test
    fun `la linea del tempo raggruppa per giorno e tiene a parte quelle passate`() = runTest {
        val today = LocalDate.now(TestData.FIXED_CLOCK)
        repository.update {
            listOf(
                booking("treno", today.minusDays(3)),
                booking("andata", today.plusDays(2)),
                booking("hotel", today.plusDays(2), BookingKind.LODGING, end = today.plusDays(4)),
                booking("ritorno", today.plusDays(4)),
                booking("ieri", today.minusDays(1)),
            )
        }
        val viewModel = BookingsViewModel(ObserveBookingsUseCase(repository), TestData.FIXED_CLOCK)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(today.plusDays(2), today.plusDays(4)), state.upcoming.map { it.date })
        assertEquals(listOf("andata", "hotel"), state.upcoming.first().bookings.map { it.id }, "L'alloggio dopo il volo dello stesso giorno")
        assertEquals(listOf("ieri", "treno"), state.past.map { it.id }, "Le passate dalla più recente")
        assertFalse(state.showPast)
        viewModel.onTogglePast()
        assertTrue(viewModel.uiState.value.showPast)
    }

    @Test
    fun `dalla dashboard si vedono solo le prenotazioni delle date del viaggio`() = runTest {
        val from = LocalDate.of(2026, 12, 11)
        repository.update {
            listOf(
                booking("hotel", from.minusDays(1), BookingKind.LODGING, end = from.plusDays(1)),
                booking("volo", from),
                booking("altro viaggio", from.plusDays(20)),
            )
        }
        val viewModel = BookingsViewModel(ObserveBookingsUseCase(repository), TestData.FIXED_CLOCK, from..from.plusDays(2), "Vienna")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("hotel", "volo"), state.upcoming.flatMap { day -> day.bookings.map { it.id } })
        assertEquals("Vienna", state.tripName)
        assertFalse(state.isEmpty)
    }

    @Test
    fun `incollando la conferma si compilano andata e ritorno da controllare e salvare una alla volta`() = runTest {
        val viewModel = editor()

        viewModel.onPastedTextChanged(confirmation)
        viewModel.onReadPastedText()
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals("Ryanair FR 7178", state.draft.title)
        assertEquals(1, state.position)
        assertEquals(2, state.readCount)
        assertEquals("", state.pastedText)

        viewModel.onSave()
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals("Ryanair FR 7179", state.draft.title)
        assertEquals(BookingEditorMessage.SAVED_NEXT, state.message)
        assertEquals(2, state.position)
        assertFalse(state.finished)

        viewModel.onDraftChanged(state.draft.copy(notes = "Posto 12A"))
        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.finished)
        assertEquals(listOf("Ryanair FR 7178", "Ryanair FR 7179"), repository.current.map { it.title })
        assertEquals(ZoneId.of("Europe/Vienna"), repository.current.last().timeZone, "Il ritorno parte da Vienna")
        assertEquals("Posto 12A", repository.current.last().notes)
    }

    @Test
    fun `un testo senza prenotazioni lascia il modulo da compilare a mano`() = runTest {
        val viewModel = editor(defaultDate = LocalDate.of(2026, 12, 11))

        viewModel.onPastedTextChanged("Ciao, ci vediamo a Vienna!")
        viewModel.onReadPastedText()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(BookingEditorMessage.NOTHING_FOUND, state.message)
        assertEquals(BookingDraft(startDate = LocalDate.of(2026, 12, 11)), state.draft)
        viewModel.onSave()
        advanceUntilIdle()
        assertTrue(repository.current.isEmpty(), "Senza titolo non si salva")
    }

    @Test
    fun `il PDF condiviso si copia, si legge e resta allegato alle prenotazioni lette`() = runTest {
        ocr.result = DataResult.Success(RecognizedText(confirmation))
        val viewModel = editor(shared = SharedBookingContent(uri = "content://downloads/biglietto.pdf", mimeType = "application/pdf"))
        advanceUntilIdle()

        assertEquals(StoredDocument("booking-1.pdf", "application/pdf"), viewModel.uiState.value.attachment)
        assertEquals("content://com.partimo.app.files/bookings/booking-1.pdf", ocr.requests.single().first.uri, "Si legge la copia dell'app")
        viewModel.onSave()
        advanceUntilIdle()
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(listOf("booking-1.pdf", "booking-1.pdf"), repository.current.map { it.attachment }, "Andata e ritorno con lo stesso biglietto")
        assertTrue(documents.deleted.isEmpty())
    }

    @Test
    fun `un documento illeggibile resta allegato e un documento troppo grande lo si segnala`() = runTest {
        ocr.result = DataResult.Failure(DataError.NoConnection)
        val viewModel = editor()

        viewModel.onDocumentPicked("content://media/foto.jpg", "image/jpeg")
        advanceUntilIdle()
        assertEquals(BookingEditorMessage.MODEL_DOWNLOADING, viewModel.uiState.value.message)
        assertEquals("image/jpeg", viewModel.uiState.value.attachment?.mimeType)

        viewModel.onRemoveAttachment()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.attachment)
        assertEquals(listOf("booking-1.pdf"), documents.deleted, "Tolto prima di salvare: il file copiato si cancella")

        documents.failure = FileTooLargeException(20)
        viewModel.onDocumentPicked("content://media/video.mp4", "image/jpeg")
        advanceUntilIdle()
        assertEquals(BookingEditorMessage.DOCUMENT_TOO_LARGE, viewModel.uiState.value.message)
        documents.failure = IOException("Permesso scaduto")
        viewModel.onDocumentPicked("content://media/altro.jpg", "image/jpeg")
        advanceUntilIdle()
        assertEquals(BookingEditorMessage.READ_FAILED, viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isReading)
    }

    @Test
    fun `si modifica una prenotazione salvata e togliendola si cancella il suo documento`() = runTest {
        documents.stored["booking-volo.pdf"] = "application/pdf"
        repository.update { listOf(booking("volo", LocalDate.of(2026, 12, 11), attachment = "booking-volo.pdf")) }
        val viewModel = editor(bookingId = "volo")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isNew)
        assertEquals("Prenotazione volo", state.draft.title)
        assertEquals("booking-volo.pdf", state.attachment?.name)

        viewModel.onDraftChanged(state.draft.copy(title = "Ryanair FR 7178", reference = "K7M2QX"))
        viewModel.onSave()
        advanceUntilIdle()
        assertEquals(listOf("Ryanair FR 7178"), repository.current.map { it.title })
        assertEquals("K7M2QX", repository.current.single().reference)

        val again = editor(bookingId = "volo")
        advanceUntilIdle()
        again.onDelete()
        advanceUntilIdle()
        assertTrue(repository.current.isEmpty())
        assertEquals(listOf("booking-volo.pdf"), documents.deleted)
        assertTrue(again.uiState.value.finished)
    }
}
