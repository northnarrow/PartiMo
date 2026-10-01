package com.partimo.app.navigation

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import com.partimo.app.notifications.BookingReminderNotifier
import com.partimo.app.notifications.DealNotifier
import com.partimo.app.ui.bookings.SharedBookingContent
import com.partimo.domain.usecase.ReadBookingTextUseCase
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Le mie prenotazioni. Dalla dashboard di un viaggio solo quelle delle sue date ([from]–[to], ISO), con il nome
 * della meta; altrimenti tutte.
 */
@Serializable
data class BookingsDestination(val from: String? = null, val to: String? = null, val tripName: String? = null) {
    fun tripDates(): ClosedRange<LocalDate>? {
        val start = from?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val end = to?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return if (end.isBefore(start)) null else start..end
    }

    companion object {
        fun forTrip(trip: TripArgs) = BookingsDestination(from = trip.from, to = trip.to, tripName = trip.cityName)
    }
}

/**
 * Modulo di una prenotazione: [bookingId] per modificarne una, altrimenti è nuova. Con il contenuto condiviso da
 * un'altra app (testo di una mail, PDF, screenshot) lo legge subito; [defaultDate] (ISO) è il giorno proposto.
 */
@Serializable
data class BookingEditorDestination(
    val bookingId: String? = null,
    val sharedText: String? = null,
    val sharedUri: String? = null,
    val sharedMimeType: String? = null,
    val defaultDate: String? = null,
) {
    /** Aperto da una condivisione: salvando si va alle prenotazioni invece di tornare indietro. */
    val isShared: Boolean get() = sharedText != null || sharedUri != null

    fun shared(): SharedBookingContent? = if (isShared) SharedBookingContent(sharedText, sharedUri, sharedMimeType) else null

    fun defaultLocalDate(): LocalDate? = defaultDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

/**
 * Schermata da aprire per un intent arrivato da fuori: notifica di un'offerta o di un promemoria, widget, oppure
 * un contenuto condiviso con PartiMo (mail di conferma, PDF o foto del biglietto) da cui creare una prenotazione.
 */
object IncomingRoutes {

    fun from(intent: Intent?): Any? {
        intent ?: return null
        DealNotifier.dashboardRouteFrom(intent)?.let { return it }
        if (BookingReminderNotifier.opensBookings(intent)) return BookingsDestination()
        return if (intent.action == Intent.ACTION_SEND) sharedBooking(intent) else null
    }

    private fun sharedBooking(intent: Intent): BookingEditorDestination? {
        val type = intent.type
        val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        if (stream != null && (type == PDF || type?.startsWith("image/") == true)) {
            return BookingEditorDestination(sharedUri = stream.toString(), sharedMimeType = type)
        }
        val text = listOfNotNull(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString())
            .joinToString("\n")
            .trim()
            .take(ReadBookingTextUseCase.MAX_TEXT_LENGTH)
        return if (text.isEmpty()) null else BookingEditorDestination(sharedText = text)
    }

    private const val PDF = "application/pdf"
}
