package com.partimo.app.ui.bookings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.partimo.app.R
import com.partimo.app.ui.common.Formatters
import com.partimo.domain.model.booking.Booking
import com.partimo.domain.model.booking.BookingKind

fun BookingKind.emoji(): String = when (this) {
    BookingKind.FLIGHT -> "✈️"
    BookingKind.LODGING -> "🏨"
    BookingKind.TRAIN -> "🚆"
    BookingKind.BUS -> "🚌"
    BookingKind.CAR_RENTAL -> "🚗"
    BookingKind.ACTIVITY -> "🎟️"
    BookingKind.OTHER -> "📌"
}

@StringRes
fun BookingKind.labelRes(): Int = when (this) {
    BookingKind.FLIGHT -> R.string.booking_kind_flight
    BookingKind.LODGING -> R.string.booking_kind_lodging
    BookingKind.TRAIN -> R.string.booking_kind_train
    BookingKind.BUS -> R.string.booking_kind_bus
    BookingKind.CAR_RENTAL -> R.string.booking_kind_car
    BookingKind.ACTIVITY -> R.string.booking_kind_activity
    BookingKind.OTHER -> R.string.booking_kind_other
}

/** Tratte con partenza e arrivo: per questi tipi il modulo chiede da dove e per dove. */
val BookingKind.hasRoute: Boolean get() = this == BookingKind.FLIGHT || this == BookingKind.TRAIN || this == BookingKind.BUS

/**
 * Quando: "21:10 → 22:55" per un volo, "ven 11 → dom 13 dic · check-in 15:00" per un alloggio, "tutto il giorno"
 * senza orario.
 */
@Composable
fun Booking.whenText(): String {
    val end = endDate?.takeIf { it.isAfter(startDate) }
    val range = when {
        kind == BookingKind.LODGING && end != null -> Formatters.weekdayDayMonth(startDate) + " → " + Formatters.weekdayDayMonth(end)
        else -> null
    }
    val start = startTime
    val finish = endTime
    val times = when {
        kind == BookingKind.LODGING -> start?.let { stringResource(R.string.booking_check_in_at, Formatters.time(it)) }
        start != null && finish != null -> Formatters.time(start) + " → " + Formatters.time(finish) +
            (if (end != null) " " + stringResource(R.string.booking_next_day) else "")
        start != null -> Formatters.time(start)
        else -> null
    }
    return listOfNotNull(range, times).joinToString(" · ").ifEmpty { stringResource(R.string.booking_all_day) }
}

/** "BGY → VIE", "Milano Centrale → Roma Termini"; `null` senza tratta. */
val Booking.routeText: String?
    get() = when {
        origin != null && destination != null -> "$origin → $destination"
        else -> origin ?: destination
    }
