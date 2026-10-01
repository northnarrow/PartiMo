package com.partimo.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.partimo.app.R
import com.partimo.app.ui.common.Formatters
import com.partimo.domain.model.TravelPeriod
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Tag delle celle delle date, usati dai test UI. */
const val DEPARTURE_DATE_TAG = "departure_date"
const val RETURN_DATE_TAG = "return_date"

/** Fin dove si possono scegliere le date: un anno, come i mesi proposti. */
private const val MAX_DAYS_AHEAD = 365L
private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Celle «Andata» e «Ritorno» sotto i mesi: date precise del viaggio, scelte sul calendario. Toccando una
 * delle due si apre il calendario con l'intervallo da scegliere; le date confermate diventano il periodo
 * del viaggio (voli di quei giorni, alloggi, meteo, eventi).
 */
@Composable
fun TripDatesRow(
    period: TravelPeriod,
    today: LocalDate,
    onDatesSelected: (LocalDate, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val dates = period as? TravelPeriod.Dates
    Column(modifier = modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.search_dates_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateCell(
                label = stringResource(R.string.search_departure_date),
                date = dates?.departure,
                onClick = { picking = true },
                modifier = Modifier.weight(1f).testTag(DEPARTURE_DATE_TAG),
            )
            DateCell(
                label = stringResource(R.string.search_return_date),
                date = dates?.returning,
                onClick = { picking = true },
                modifier = Modifier.weight(1f).testTag(RETURN_DATE_TAG),
            )
        }
    }
    if (picking) {
        TripDatesDialog(
            initial = dates,
            today = today,
            onDismiss = { picking = false },
            onConfirm = { departure, returning ->
                picking = false
                onDatesSelected(departure, returning)
            },
        )
    }
}

@Composable
private fun DateCell(label: String, date: LocalDate?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(onClick = onClick, modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = date?.let(Formatters::weekdayDayMonth) ?: stringResource(R.string.search_pick_date),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (date != null) FontWeight.SemiBold else FontWeight.Normal,
                color = if (date != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Calendario per andata e ritorno: da oggi a un anno, al massimo [TravelPeriod.MAX_NIGHTS] notti. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripDatesDialog(
    initial: TravelPeriod.Dates?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    val lastDay = today.plusDays(MAX_DAYS_AHEAD)
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial?.departure?.toUtcMillis(),
        initialSelectedEndDateMillis = initial?.returning?.toUtcMillis(),
        yearRange = today.year..lastDay.year,
        initialDisplayMode = DisplayMode.Picker,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis.toLocalDate() in today..lastDay

            override fun isSelectableYear(year: Int): Boolean = year in today.year..lastDay.year
        },
    )
    val departure = state.selectedStartDateMillis?.toLocalDate()
    val returning = state.selectedEndDateMillis?.toLocalDate()
    val tooLong = departure != null && returning != null && ChronoUnit.DAYS.between(departure, returning) > TravelPeriod.MAX_NIGHTS
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (departure != null && returning != null) onConfirm(departure, returning) },
                enabled = departure != null && returning != null && !tooLong,
            ) { Text(stringResource(R.string.search_dates_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.search_dates_cancel)) } },
    ) {
        DateRangePicker(
            state = state,
            title = {
                Text(
                    text = if (tooLong) stringResource(R.string.search_dates_too_long, TravelPeriod.MAX_NIGHTS) else stringResource(R.string.search_dates_title),
                    color = if (tooLong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
            // Solo il calendario: l'inserimento a mano non serve e aprirebbe la tastiera.
            showModeToggle = false,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Il calendario lavora con la mezzanotte UTC di ogni giorno. */
private fun LocalDate.toUtcMillis(): Long = toEpochDay() * MILLIS_PER_DAY

private fun Long.toLocalDate(): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(this, MILLIS_PER_DAY))
