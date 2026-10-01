package com.partimo.app.ui.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.partimo.app.R
import com.partimo.app.ui.common.Formatters
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.common.errorMessage
import com.partimo.app.ui.dashboard.PriceCalendarState
import com.partimo.domain.model.flight.FareLevel
import com.partimo.domain.model.flight.FareSnapshot
import com.partimo.domain.model.flight.PriceCalendar
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Tag di un giorno del calendario dei prezzi ("price_day_2026-12-07"), usato dai test UI. */
fun priceDayTag(date: LocalDate): String = "price_day_$date"

/** Azioni del calendario dei prezzi. */
data class PriceCalendarActions(
    val onMonthChanged: (Int) -> Unit = {},
    val onDaySelected: (FareSnapshot) -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/**
 * Calendario dei prezzi: per ogni giorno di partenza del mese il prezzo più basso a persona (andata e ritorno),
 * colorato per fascia. Un tocco su un giorno porta i voli di quelle date.
 */
@Composable
fun PriceCalendarDialog(state: PriceCalendarState, actions: PriceCalendarActions) {
    AlertDialog(
        onDismissRequest = actions.onDismiss,
        title = { Text("📅 " + stringResource(R.string.price_calendar_title)) },
        text = { PriceCalendarContent(state, actions) },
        confirmButton = { TextButton(onClick = actions.onDismiss) { Text(stringResource(R.string.price_calendar_close)) } },
    )
}

/** Contenuto del calendario: mese, giorni della settimana, griglia dei prezzi e nota sulla loro origine. */
@Composable
fun PriceCalendarContent(state: PriceCalendarState, actions: PriceCalendarActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MonthHeader(state, actions.onMonthChanged)
        WeekdayHeader()
        when (val calendar = state.calendar) {
            UiState.Loading -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            UiState.Empty -> Text(
                text = stringResource(R.string.price_calendar_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 24.dp),
            )
            is UiState.Error -> Text(
                text = errorMessage(calendar.error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 24.dp),
            )
            is UiState.Success -> DayGrid(calendar.data, actions.onDaySelected)
        }
        (state.calendar as? UiState.Success)?.data?.let { calendar ->
            Text(
                text = stringResource(R.string.price_calendar_note, nightsText(calendar.stayNights)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MonthHeader(state: PriceCalendarState, onMonthChanged: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        val previous = stringResource(R.string.price_calendar_previous)
        val next = stringResource(R.string.price_calendar_next)
        IconButton(onClick = { onMonthChanged(-1) }, enabled = state.hasPrevious, modifier = Modifier.semantics { contentDescription = previous }) {
            Text("‹", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clearAndSetSemantics {})
        }
        Text(
            text = Formatters.monthYear(state.month, currentYear = -1).replaceFirstChar { it.titlecase(Locale.ITALY) },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onMonthChanged(1) }, enabled = state.hasNext, modifier = Modifier.semantics { contentDescription = next }) {
            Text("›", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.NARROW, Locale.ITALY).uppercase(Locale.ITALY),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clearAndSetSemantics {},
            )
        }
    }
}

/** Settimane del mese, dal lunedì: i giorni con un prezzo si possono toccare. */
@Composable
private fun DayGrid(calendar: PriceCalendar, onDaySelected: (FareSnapshot) -> Unit) {
    val first = calendar.month.atDay(1)
    val leading = first.dayOfWeek.value - 1
    val cells = List(leading) { null } + (1..calendar.month.lengthOfMonth()).map { calendar.month.atDay(it) }
    val cheapest = calendar.cheapest?.departureDate
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cells.chunked(DAYS_PER_WEEK).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until DAYS_PER_WEEK).forEach { index ->
                    val date = week.getOrNull(index)
                    Box(modifier = Modifier.weight(1f).aspectRatio(CELL_RATIO)) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                fare = calendar.fares[date],
                                level = calendar.levels[date],
                                isCheapest = date == cheapest,
                                onSelected = onDaySelected,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, fare: FareSnapshot?, level: FareLevel?, isCheapest: Boolean, onSelected: (FareSnapshot) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (background, priceColor) = when (level) {
        FareLevel.LOW -> CheapGreen.copy(alpha = 0.18f) to CheapGreen
        FareLevel.MEDIUM -> colors.surfaceVariant to colors.onSurface
        FareLevel.HIGH -> colors.errorContainer.copy(alpha = 0.6f) to colors.error
        null -> Color.Transparent to colors.onSurfaceVariant
    }
    val description = fare?.let { stringResource(R.string.price_calendar_day, Formatters.dayMonth(date), Formatters.money(it.price)) }
        ?: stringResource(R.string.price_calendar_day_empty, Formatters.dayMonth(date))
    Surface(
        onClick = { fare?.let(onSelected) },
        enabled = fare != null,
        shape = RoundedCornerShape(8.dp),
        color = background,
        border = if (isCheapest) BorderStroke(2.dp, CheapGreen) else null,
        modifier = Modifier.fillMaxSize().testTag(priceDayTag(date)).semantics { contentDescription = description },
    ) {
        // Il lettore di schermo legge la descrizione della casella ("7 dic: da 72 €"), non le singole cifre.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().clearAndSetSemantics {},
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (fare != null) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.6f),
            )
            if (fare != null) {
                Text(
                    // Solo la cifra: "72" sta nella casella, la valuta è nella nota sotto.
                    text = fare.price.amount.setScale(0, RoundingMode.HALF_UP).toPlainString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (level == FareLevel.LOW) FontWeight.Bold else FontWeight.Normal,
                    color = priceColor,
                )
            }
        }
    }
}

@Composable
private fun nightsText(nights: LongRange): String =
    if (nights.first == nights.last) {
        pluralStringResource(R.plurals.price_calendar_nights, nights.first.toInt(), nights.first.toInt())
    } else {
        stringResource(R.string.price_calendar_nights_range, nights.first, nights.last)
    }

private const val DAYS_PER_WEEK = 7
private const val CELL_RATIO = 0.8f

/** Verde dei prezzi convenienti, leggibile nei temi chiaro e scuro. */
private val CheapGreen = Color(0xFF2E9D4A)
