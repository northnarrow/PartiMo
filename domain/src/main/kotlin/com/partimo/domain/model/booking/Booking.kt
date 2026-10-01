package com.partimo.domain.model.booking

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Tipo di prenotazione: decide l'icona, i campi del modulo e i promemoria. */
enum class BookingKind { FLIGHT, LODGING, TRAIN, BUS, CAR_RENTAL, ACTIVITY, OTHER }

/**
 * Prenotazione dell'utente (volo, alloggio, treno...), salvata sul telefono per averla sotto mano anche senza
 * rete: date e orari, luoghi, codice di prenotazione e, se c'è, il documento originale (PDF, carta d'imbarco).
 * Date e orari sono quelli locali del luogo, come sui biglietti.
 */
data class Booking(
    val id: String,
    val kind: BookingKind,
    /** Titolo breve: "Ryanair FR 7178", "Hotel Sacher Wien". */
    val title: String,
    /** Giorno di inizio: partenza, check-in, ritiro dell'auto. */
    val startDate: LocalDate,
    /** Ora di inizio, se nota: senza, i promemoria arrivano il giorno prima. */
    val startTime: LocalTime? = null,
    /** Fine: arrivo, check-out, riconsegna. */
    val endDate: LocalDate? = null,
    val endTime: LocalTime? = null,
    /** Fuso orario del luogo di partenza (es. dell'aeroporto); `null` = quello del telefono. */
    val timeZone: ZoneId? = null,
    /** Da dove e per dove: aeroporti, stazioni, fermate. */
    val origin: String? = null,
    val destination: String? = null,
    /** Codice di prenotazione (PNR, numero di conferma). */
    val reference: String? = null,
    /** Compagnia, struttura o sito della prenotazione. */
    val provider: String? = null,
    val address: String? = null,
    val notes: String = "",
    /** File del documento originale salvato dall'app (es. "booking-1a2b.pdf"); `null` senza documento. */
    val attachment: String? = null,
    /** Tipo del documento (es. "application/pdf"), per aprirlo con l'app giusta. */
    val attachmentType: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Prenotazione senza id" }
        require(title.isNotBlank()) { "Prenotazione senza titolo" }
    }

    /** Inizio con l'ora (o la mezzanotte se non è nota). */
    val start: LocalDateTime get() = startDate.atTime(startTime ?: LocalTime.MIDNIGHT)

    /** Istante d'inizio nel fuso del luogo, o in [fallbackZone] se non è noto. */
    fun startInstant(fallbackZone: ZoneId): Instant = start.atZone(timeZone ?: fallbackZone).toInstant()

    /** Ultimo giorno occupato dalla prenotazione (il check-out per un alloggio). */
    val lastDate: LocalDate get() = endDate?.takeIf { !it.isBefore(startDate) } ?: startDate

    /** `true` se la prenotazione è finita prima di [today]. */
    fun isPast(today: LocalDate): Boolean = lastDate.isBefore(today)

    /** `true` se la prenotazione cade almeno in parte tra [from] e [to] (es. le date di un viaggio). */
    fun overlaps(from: LocalDate, to: LocalDate): Boolean = !startDate.isAfter(to) && !lastDate.isBefore(from)
}

/**
 * Prenotazione in scrittura (modulo) o letta da un testo: i campi possono mancare finché l'utente non li completa.
 */
data class BookingDraft(
    val kind: BookingKind = BookingKind.OTHER,
    val title: String = "",
    val startDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    val endDate: LocalDate? = null,
    val endTime: LocalTime? = null,
    val origin: String = "",
    val destination: String = "",
    val reference: String = "",
    val provider: String = "",
    val address: String = "",
    val notes: String = "",
) {
    /** Si può salvare: almeno titolo e giorno d'inizio. */
    val isComplete: Boolean get() = title.isNotBlank() && startDate != null

    /** Prenotazione con [id], o `null` se mancano titolo o data. */
    fun toBooking(id: String, timeZone: ZoneId? = null, attachment: String? = null, attachmentType: String? = null): Booking? {
        val date = startDate ?: return null
        if (title.isBlank()) return null
        return Booking(
            id = id,
            kind = kind,
            title = title.trim(),
            startDate = date,
            startTime = startTime,
            endDate = endDate,
            endTime = endTime,
            timeZone = timeZone,
            origin = origin.trim().ifEmpty { null },
            destination = destination.trim().ifEmpty { null },
            reference = reference.trim().ifEmpty { null },
            provider = provider.trim().ifEmpty { null },
            address = address.trim().ifEmpty { null },
            notes = notes.trim(),
            attachment = attachment,
            attachmentType = attachmentType,
        )
    }

    companion object {
        /** Modulo con i dati di una prenotazione salvata, per modificarla. */
        fun of(booking: Booking): BookingDraft = BookingDraft(
            kind = booking.kind,
            title = booking.title,
            startDate = booking.startDate,
            startTime = booking.startTime,
            endDate = booking.endDate,
            endTime = booking.endTime,
            origin = booking.origin.orEmpty(),
            destination = booking.destination.orEmpty(),
            reference = booking.reference.orEmpty(),
            provider = booking.provider.orEmpty(),
            address = booking.address.orEmpty(),
            notes = booking.notes,
        )
    }
}

/** Tipo di promemoria di una prenotazione. */
enum class BookingReminderKind {
    /** 24 ore prima di un volo: il check-in online di solito è aperto. */
    CHECK_IN,

    /** 3 ore prima di un volo: è ora di andare in aeroporto. */
    LEAVE_FOR_AIRPORT,

    /** Un'ora prima di un treno, un pullman, un'attività. */
    STARTS_SOON,

    /** La mattina del check-in di un alloggio. */
    CHECK_IN_TODAY,

    /** La sera prima, quando l'ora non è nota. */
    TOMORROW,
}

/** Promemoria da mostrare all'istante [at] per una prenotazione. */
data class BookingReminder(val booking: Booking, val kind: BookingReminderKind, val at: Instant) {
    /** Identifica il promemoria: uno per prenotazione e tipo. */
    val key: String get() = "${booking.id}:${kind.name}"
}
