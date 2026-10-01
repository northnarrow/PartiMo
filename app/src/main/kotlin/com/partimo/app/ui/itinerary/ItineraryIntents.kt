package com.partimo.app.ui.itinerary

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.annotation.StringRes
import com.partimo.app.R
import com.partimo.app.ui.common.Formatters
import com.partimo.domain.model.plan.DayPart
import com.partimo.domain.model.plan.DayPlan
import com.partimo.domain.model.plan.TripPlan
import java.time.LocalDate
import java.time.ZoneOffset

/** Intent per portare l'itinerario fuori da PartiMo: calendario del telefono e condivisione come testo. */
object ItineraryIntents {

    /**
     * Evento di tutto il giorno nel calendario con il programma della giornata, da confermare
     * nell'app del calendario (nessun permesso richiesto).
     */
    fun calendar(context: Context, day: DayPlan, dayNumber: Int, cityName: String): Intent {
        // Gli eventi di tutto il giorno vanno espressi a mezzanotte UTC.
        val start = day.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val end = day.date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, context.getString(R.string.itinerary_calendar_title, cityName, dayNumber, day.title))
            .putExtra(CalendarContract.Events.DESCRIPTION, dayText(context, day))
            .putExtra(CalendarContract.Events.EVENT_LOCATION, cityName)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
    }

    /** Itinerario completo come testo semplice, da inviare con qualunque app. */
    fun share(context: Context, plan: TripPlan, cityName: String, from: LocalDate, to: LocalDate): Intent {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.itinerary_title, cityName))
            .putExtra(Intent.EXTRA_TEXT, planText(context, plan, cityName, from, to))
        return Intent.createChooser(send, context.getString(R.string.itinerary_share_chooser))
    }

    fun planText(context: Context, plan: TripPlan, cityName: String, from: LocalDate, to: LocalDate): String = buildString {
        appendLine("✨ " + context.getString(R.string.itinerary_title, cityName) + " · " + Formatters.dateRange(from, to))
        plan.days.forEachIndexed { index, day ->
            appendLine()
            appendLine(context.getString(R.string.itinerary_day, index + 1, Formatters.weekdayDayMonth(day.date)) + " — " + day.title)
            append(dayText(context, day))
            appendLine()
        }
        if (plan.packing.isNotEmpty()) {
            appendLine()
            appendLine("🧳 " + context.getString(R.string.itinerary_tab_packing))
            plan.packing.forEach { group -> appendLine("${group.category}: ${group.items.joinToString(", ")}") }
        }
        if (plan.tips.isNotEmpty()) {
            appendLine()
            appendLine("💡 " + context.getString(R.string.itinerary_tab_tips))
            plan.tips.forEach { tip -> appendLine("• $tip") }
        }
        appendLine()
        append(context.getString(R.string.itinerary_share_footer))
    }

    /** Tappe della giornata, una per riga, e il consiglio del giorno. */
    private fun dayText(context: Context, day: DayPlan): String = buildString {
        day.stops.forEach { stop ->
            val activity = stop.activity.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
            appendLine("• ${context.getString(stop.dayPart.labelRes())} · ${stop.name}$activity")
        }
        day.tip?.let { append("💡 $it") }
    }.trimEnd()
}

@StringRes
fun DayPart.labelRes(): Int = when (this) {
    DayPart.MORNING -> R.string.itinerary_morning
    DayPart.AFTERNOON -> R.string.itinerary_afternoon
    DayPart.EVENING -> R.string.itinerary_evening
}

fun DayPart.emoji(): String = when (this) {
    DayPart.MORNING -> "🌅"
    DayPart.AFTERNOON -> "☀️"
    DayPart.EVENING -> "🌙"
}
