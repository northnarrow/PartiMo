package com.partimo.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.partimo.app.R
import com.partimo.domain.model.WheelchairAccess
import com.partimo.domain.model.hours.OpenState
import com.partimo.domain.model.hours.OpeningHours
import com.partimo.domain.model.hours.TimeSpan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Testi degli orari di apertura in italiano. */
object OpeningHoursText {

    private val locale = Locale.ITALY

    /**
     * Orari della settimana che contiene [weekOf], con i giorni uguali raggruppati:
     * "lun–ven 10:00–23:00 · sab–dom 09:00–23:00", "mar–dom 10:00–18:00 · lun chiuso".
     */
    fun weekly(hours: OpeningHours, weekOf: LocalDate, closedLabel: String): String {
        val monday = weekOf.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val days = (0L..6L).map { offset -> monday.plusDays(offset) }.map { date -> date.dayOfWeek to spansText(hours.spansOn(date), closedLabel) }
        val groups = mutableListOf<Triple<DayOfWeek, DayOfWeek, String>>()
        days.forEach { (day, text) ->
            val last = groups.lastOrNull()
            if (last != null && last.third == text) groups[groups.lastIndex] = last.copy(second = day) else groups += Triple(day, day, text)
        }
        // Prima i giorni di apertura, poi quelli di chiusura.
        return groups.sortedBy { it.third == closedLabel }.joinToString(" · ") { (first, last, text) ->
            val name = if (first == last) dayName(first) else dayName(first) + "–" + dayName(last)
            "$name $text"
        }
    }

    private fun spansText(spans: List<TimeSpan>, closedLabel: String): String = when {
        spans.isEmpty() -> closedLabel
        spans.singleOrNull()?.isWholeDay == true -> "24 h"
        else -> spans.joinToString(", ") { span ->
            when {
                span.openEnd && span.endMinute == TimeSpan.MINUTES_PER_DAY -> "dalle ${Formatters.time(span.start)}"
                // La chiusura a mezzanotte si scrive 24:00, come sulle insegne.
                span.endMinute == TimeSpan.MINUTES_PER_DAY -> "${Formatters.time(span.start)}–24:00"
                else -> "${Formatters.time(span.start)}–${Formatters.time(span.end)}"
            }
        }
    }

    private fun dayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, locale).lowercase(locale).replace(".", "")
}

/** "🟢 Aperto ora · chiude alle 23:00" oppure "🔴 Chiuso ora · apre domani alle 10:00". */
@Composable
fun openStateText(state: OpenState, now: LocalDateTime): String = when (state) {
    is OpenState.Open -> state.closesAt?.let { stringResource(R.string.hours_open_until, Formatters.time(it)) } ?: stringResource(R.string.hours_open_now)
    is OpenState.Closed -> when (val opensAt = state.opensAt) {
        null -> stringResource(R.string.hours_closed_now)
        else -> when (opensAt.toLocalDate()) {
            now.toLocalDate() -> stringResource(R.string.hours_closed_opens, Formatters.time(opensAt))
            now.toLocalDate().plusDays(1) -> stringResource(R.string.hours_closed_opens_day, stringResource(R.string.hours_tomorrow), Formatters.time(opensAt))
            else -> stringResource(
                R.string.hours_closed_opens_day,
                opensAt.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ITALY).lowercase(Locale.ITALY),
                Formatters.time(opensAt),
            )
        }
    }
}

@StringRes
fun WheelchairAccess.labelRes(): Int = when (this) {
    WheelchairAccess.YES -> R.string.wheelchair_yes
    WheelchairAccess.LIMITED -> R.string.wheelchair_limited
    WheelchairAccess.NO -> R.string.wheelchair_no
}
