package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.booking.Booking
import com.partimo.domain.model.booking.BookingDraft
import com.partimo.domain.model.booking.BookingKind
import com.partimo.domain.model.booking.BookingReminder
import com.partimo.domain.model.ocr.DocumentSource
import com.partimo.domain.repository.BookingRepository
import com.partimo.domain.repository.FlightCodesRepository
import com.partimo.domain.repository.ReminderLogRepository
import com.partimo.domain.repository.TextRecognitionRepository
import com.partimo.domain.service.BookingReminders
import com.partimo.domain.service.BookingTextReader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/** Prenotazioni nell'ordine della linea del tempo (vedi [TIMELINE]). */
class ObserveBookingsUseCase(private val repository: BookingRepository) {
    operator fun invoke(): Flow<List<Booking>> = repository.bookings.map { bookings -> bookings.sortedWith(TIMELINE) }

    companion object {
        /**
         * Per giorno; nello stesso giorno prima quelle senza ora, poi per ora e gli alloggi per ultimi: al check-in
         * si arriva dopo il volo o il treno, qualunque sia l'ora da cui è possibile.
         */
        val TIMELINE: Comparator<Booking> = compareBy({ it.startDate }, { it.kind == BookingKind.LODGING }, { it.startTime ?: LocalTime.MIN }, { it.title })
    }
}

/**
 * Salva una prenotazione nuova o modificata (stesso id). Per un volo con l'aeroporto di partenza conosciuto
 * il fuso orario è quello dell'aeroporto: i promemoria arrivano all'ora giusta anche al ritorno.
 */
class SaveBookingUseCase(
    private val repository: BookingRepository,
    private val flightCodes: FlightCodesRepository? = null,
) {
    suspend operator fun invoke(booking: Booking): Booking {
        val zone = booking.timeZone ?: departureZone(booking)
        val saved = booking.copy(timeZone = zone)
        repository.update { bookings ->
            if (bookings.any { it.id == saved.id }) bookings.map { if (it.id == saved.id) saved else it } else bookings + saved
        }
        return saved
    }

    private suspend fun departureZone(booking: Booking): ZoneId? {
        if (booking.kind != BookingKind.FLIGHT) return null
        val code = booking.origin?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.length == IATA_LENGTH && it.all(Char::isLetter) } ?: return null
        return flightCodes?.airportTimeZone(code)
    }

    private companion object {
        const val IATA_LENGTH = 3
    }
}

/** Toglie una prenotazione; restituisce quella tolta (per cancellarne il documento), `null` se non c'era. */
class DeleteBookingUseCase(private val repository: BookingRepository) {
    suspend operator fun invoke(id: String): Booking? {
        var removed: Booking? = null
        repository.update { bookings ->
            removed = bookings.firstOrNull { it.id == id }
            bookings.filterNot { it.id == id }
        }
        return removed
    }
}

/**
 * Legge le prenotazioni dal testo di una conferma (email, messaggio): vedi [BookingTextReader]. Aeroporti e
 * compagnie si caricano una volta sola.
 */
class ReadBookingTextUseCase(
    private val flightCodes: FlightCodesRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val mutex = Mutex()
    private var reader: BookingTextReader? = null

    suspend operator fun invoke(text: String): List<BookingDraft> = reader().read(text.take(MAX_TEXT_LENGTH), LocalDate.now(clock))

    private suspend fun reader(): BookingTextReader = mutex.withLock {
        reader ?: BookingTextReader(flightCodes.airportCodes(), flightCodes.airlines()).also { reader = it }
    }

    companion object {
        /** Una conferma sta in poche pagine: un testo molto più lungo si legge solo all'inizio. */
        const val MAX_TEXT_LENGTH = 20_000
    }
}

/** Esito della lettura di un documento: il testo riconosciuto e le prenotazioni trovate. */
data class BookingDocumentImport(val text: String, val drafts: List<BookingDraft>)

/**
 * Legge le prenotazioni da un PDF, una foto o uno screenshot: riconosce il testo sul telefono e lo legge come
 * quello di una mail. Un documento senza testo è [DataError.InvalidResponse].
 */
class ReadBookingDocumentUseCase(
    private val textRecognition: TextRecognitionRepository,
    private val readText: ReadBookingTextUseCase,
) {
    suspend operator fun invoke(source: DocumentSource): DataResult<BookingDocumentImport> =
        when (val recognized = textRecognition.recognize(source)) {
            is DataResult.Failure -> recognized
            is DataResult.Success -> if (recognized.data.isEmpty) {
                DataResult.Failure(DataError.InvalidResponse)
            } else {
                DataResult.Success(BookingDocumentImport(recognized.data.text, readText(recognized.data.text)), DataOrigin.LOCAL)
            }
        }
}

/**
 * Promemoria delle prenotazioni (check-in, partenza per l'aeroporto...): quelli futuri da pianificare e quelli
 * da mostrare ora. Quelli mostrati si segnano, così non si ripetono.
 */
class BookingRemindersUseCase(
    private val repository: BookingRepository,
    private val log: ReminderLogRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    /** Promemoria futuri, dal più vicino. */
    suspend fun upcoming(): List<BookingReminder> = BookingReminders.plan(repository.bookings.first(), Instant.now(clock), clock.zone)

    /** Quando controllare di nuovo: subito se c'è un promemoria in sospeso, altrimenti all'ora del prossimo. */
    suspend fun nextCheck(): Instant? {
        val now = Instant.now(clock)
        val bookings = repository.bookings.first()
        val shown = log.sentReminders()
        if (BookingReminders.due(bookings, now, clock.zone, shown.bookingKeys()).isNotEmpty()) return now
        return BookingReminders.plan(bookings, now, clock.zone).firstOrNull()?.at
    }

    /**
     * Promemoria da mostrare ora, segnati come mostrati (anche con le notifiche spente: in ritardo non servono).
     * Se per una prenotazione ne sono scattati più d'uno (telefono spento) si mostra solo l'ultimo.
     */
    suspend fun takeDue(): List<BookingReminder> {
        val now = Instant.now(clock)
        val bookings = repository.bookings.first()
        val logged = log.sentReminders()
        val due = BookingReminders.due(bookings, now, clock.zone, logged.bookingKeys())
        // Le prenotazioni passate o tolte non hanno più promemoria: le loro chiavi si dimenticano.
        val active = bookings.filterNot { it.isPast(LocalDate.now(clock)) }.map { it.id }.toSet()
        val stale = logged.filter { it.startsWith(LOG_PREFIX) && it.removePrefix(LOG_PREFIX).substringBeforeLast(':') !in active }
        if (stale.isNotEmpty()) log.forget(stale)
        if (due.isNotEmpty()) log.markSent(due.map { LOG_PREFIX + it.key })
        return due.groupBy { it.booking.id }.map { (_, reminders) -> reminders.maxBy { it.at } }.sortedBy { it.at }
    }

    private fun Set<String>.bookingKeys(): Set<String> = filter { it.startsWith(LOG_PREFIX) }.map { it.removePrefix(LOG_PREFIX) }.toSet()

    private companion object {
        /** Prefisso delle chiavi delle prenotazioni nel registro dei promemoria, condiviso con i viaggi salvati. */
        const val LOG_PREFIX = "booking:"
    }
}
