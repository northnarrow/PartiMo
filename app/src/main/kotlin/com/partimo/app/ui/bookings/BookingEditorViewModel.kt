package com.partimo.app.ui.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.partimo.app.di.AppContainer
import com.partimo.app.files.BookingDocuments
import com.partimo.app.files.FileTooLargeException
import com.partimo.app.files.StoredDocument
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.booking.BookingDraft
import com.partimo.domain.model.ocr.DocumentSource
import com.partimo.domain.usecase.DeleteBookingUseCase
import com.partimo.domain.usecase.ObserveBookingsUseCase
import com.partimo.domain.usecase.ReadBookingDocumentUseCase
import com.partimo.domain.usecase.ReadBookingTextUseCase
import com.partimo.domain.usecase.SaveBookingUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/** Messaggi del modulo (snackbar). */
enum class BookingEditorMessage {
    /** Nel testo o nel documento non c'è una prenotazione riconoscibile: si compila a mano. */
    NOTHING_FOUND,

    /** Il documento non si è potuto leggere. */
    READ_FAILED,

    /** Il riconoscimento del testo si sta scaricando: serve la rete la prima volta. */
    MODEL_DOWNLOADING,

    DOCUMENT_TOO_LARGE,

    /** Salvata una delle prenotazioni lette: ora la successiva. */
    SAVED_NEXT,
}

/** Contenuto condiviso con PartiMo da un'altra app (mail, PDF, screenshot) per creare una prenotazione. */
data class SharedBookingContent(val text: String? = null, val uri: String? = null, val mimeType: String? = null)

data class BookingEditorUiState(
    val today: LocalDate,
    /** Prenotazione modificata; `null` per una nuova. */
    val editingId: String? = null,
    val draft: BookingDraft = BookingDraft(),
    val attachment: StoredDocument? = null,
    /** Altre prenotazioni lette dallo stesso testo o documento, da controllare dopo questa. */
    val pending: List<BookingDraft> = emptyList(),
    /** Quante prenotazioni ha dato l'ultima lettura (per «2 di 3»). */
    val readCount: Int = 0,
    val pastedText: String = "",
    val isReading: Boolean = false,
    val message: BookingEditorMessage? = null,
    /** Salvataggio o eliminazione fatti: la schermata si chiude. */
    val finished: Boolean = false,
) {
    val isNew: Boolean get() = editingId == null

    /** Posizione della prenotazione mostrata tra quelle lette (1, 2...); 0 senza lettura multipla. */
    val position: Int get() = if (readCount > 1) readCount - pending.size else 0
}

/**
 * Modulo di una prenotazione: si compila a mano o leggendo il testo di una conferma, un PDF o una foto (sul
 * telefono). Un testo con più voli (andata e ritorno) dà più prenotazioni, da controllare e salvare una alla volta.
 */
@Suppress("LongParameterList")
class BookingEditorViewModel(
    private val observeBookings: ObserveBookingsUseCase,
    private val saveBooking: SaveBookingUseCase,
    private val deleteBooking: DeleteBookingUseCase,
    private val readBookingText: ReadBookingTextUseCase,
    private val readBookingDocument: ReadBookingDocumentUseCase,
    private val documents: BookingDocuments,
    clock: Clock,
    bookingId: String? = null,
    shared: SharedBookingContent? = null,
    defaultDate: LocalDate? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BookingEditorUiState(today = LocalDate.now(clock), draft = BookingDraft(startDate = defaultDate)),
    )
    val uiState: StateFlow<BookingEditorUiState> = _uiState.asStateFlow()

    /** Documenti copiati in questa sessione: se l'utente li toglie prima di salvare, si cancellano. */
    private val importedHere = mutableSetOf<String>()

    init {
        bookingId?.let { id ->
            viewModelScope.launch {
                observeBookings().first().firstOrNull { it.id == id }?.let { booking ->
                    _uiState.update {
                        it.copy(
                            editingId = booking.id,
                            draft = BookingDraft.of(booking),
                            attachment = booking.attachment?.let { name -> StoredDocument(name, booking.attachmentType ?: "*/*") },
                        )
                    }
                }
            }
        }
        shared?.text?.takeIf { it.isNotBlank() }?.let { text ->
            _uiState.update { it.copy(pastedText = text) }
            onReadPastedText()
        }
        shared?.uri?.let { uri -> onDocumentPicked(uri, shared.mimeType) }
    }

    fun onDraftChanged(draft: BookingDraft) {
        _uiState.update { it.copy(draft = draft.copy(title = draft.title.take(MAX_FIELD), notes = draft.notes.take(MAX_NOTES))) }
    }

    fun onPastedTextChanged(text: String) {
        _uiState.update { it.copy(pastedText = text.take(ReadBookingTextUseCase.MAX_TEXT_LENGTH)) }
    }

    /** Legge il testo incollato (es. la mail di conferma). */
    fun onReadPastedText() {
        val text = _uiState.value.pastedText
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isReading = true) }
            applyRead(readBookingText(text))
        }
    }

    /** PDF o foto scelti o condivisi: si copiano tra i documenti delle prenotazioni e se ne legge il testo. */
    fun onDocumentPicked(uri: String, mimeType: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isReading = true) }
            val stored = try {
                documents.import(uri, mimeType)
            } catch (e: CancellationException) {
                throw e
            } catch (e: FileTooLargeException) {
                _uiState.update { it.copy(isReading = false, message = BookingEditorMessage.DOCUMENT_TOO_LARGE) }
                return@launch
            } catch (e: Exception) {
                _uiState.update { it.copy(isReading = false, message = BookingEditorMessage.READ_FAILED) }
                return@launch
            }
            importedHere += stored.name
            _uiState.update { it.copy(attachment = stored) }
            val source = DocumentSource(documents.shareableUri(stored.name) ?: uri, stored.mimeType)
            when (val result = readBookingDocument(source)) {
                is DataResult.Success -> applyRead(result.data.drafts)
                is DataResult.Failure -> _uiState.update {
                    // Il documento resta allegato: la prenotazione si può completare a mano.
                    it.copy(
                        isReading = false,
                        message = when (result.error) {
                            DataError.NoConnection -> BookingEditorMessage.MODEL_DOWNLOADING
                            DataError.InvalidResponse -> BookingEditorMessage.NOTHING_FOUND
                            else -> BookingEditorMessage.READ_FAILED
                        },
                    )
                }
            }
        }
    }

    fun onRemoveAttachment() {
        val name = _uiState.value.attachment?.name ?: return
        _uiState.update { it.copy(attachment = null) }
        if (name in importedHere) viewModelScope.launch { documents.delete(name) }
    }

    /** Salva la prenotazione; se la lettura ne ha date altre, passa alla successiva (con lo stesso documento). */
    fun onSave() {
        val state = _uiState.value
        val attachment = state.attachment
        val booking = state.draft.toBooking(
            id = state.editingId ?: newId(),
            attachment = attachment?.name,
            attachmentType = attachment?.mimeType,
        ) ?: return
        viewModelScope.launch {
            saveBooking(booking)
            importedHere.remove(attachment?.name)
            val next = state.pending.firstOrNull()
            if (next == null) {
                _uiState.update { it.copy(finished = true) }
            } else {
                _uiState.update { it.copy(editingId = null, draft = next, pending = it.pending.drop(1), message = BookingEditorMessage.SAVED_NEXT) }
            }
        }
    }

    /** Toglie la prenotazione e il suo documento, se nessun'altra prenotazione lo usa. */
    fun onDelete() {
        val id = _uiState.value.editingId ?: return
        viewModelScope.launch {
            val removed = deleteBooking(id)
            val name = removed?.attachment
            if (name != null && observeBookings().first().none { it.attachment == name }) documents.delete(name)
            _uiState.update { it.copy(finished = true) }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private fun applyRead(drafts: List<BookingDraft>) {
        _uiState.update { state ->
            if (drafts.isEmpty()) {
                state.copy(isReading = false, message = BookingEditorMessage.NOTHING_FOUND)
            } else {
                state.copy(isReading = false, draft = drafts.first(), pending = drafts.drop(1), readCount = drafts.size, pastedText = "")
            }
        }
    }

    private fun newId(): String = UUID.randomUUID().toString()

    companion object {
        private const val MAX_FIELD = 120
        private const val MAX_NOTES = 1_000

        @Suppress("LongParameterList")
        fun factory(
            container: AppContainer,
            bookingId: String?,
            shared: SharedBookingContent?,
            defaultDate: LocalDate?,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BookingEditorViewModel(
                    observeBookings = container.observeBookings,
                    saveBooking = container.saveBooking,
                    deleteBooking = container.deleteBooking,
                    readBookingText = container.readBookingText,
                    readBookingDocument = container.readBookingDocument,
                    documents = container.bookingDocuments,
                    clock = container.clock,
                    bookingId = bookingId,
                    shared = shared,
                    defaultDate = defaultDate,
                )
            }
        }
    }
}
