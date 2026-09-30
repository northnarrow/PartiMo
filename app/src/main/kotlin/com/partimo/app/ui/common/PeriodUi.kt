package com.partimo.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.partimo.app.R
import com.partimo.domain.model.TravelPeriod
import java.time.LocalDate
import java.time.Month
import java.util.Locale

/** Tag della riga dei periodi, usato dai test UI per lo scroll orizzontale. */
const val PERIOD_CHIPS_TAG = "period_chips"

/** Etichetta per i chip: "Prossimi giorni", "Dicembre", "Gennaio 2027" (anno solo se diverso da quello corrente). */
@Composable
fun TravelPeriod.label(today: LocalDate): String = when (this) {
    TravelPeriod.NextDays -> stringResource(R.string.period_next_days)
    is TravelPeriod.InMonth -> Formatters.monthYear(month, today.year).replaceFirstChar { it.titlecase(Locale.ITALY) }
}

/** Periodo dentro una frase: "Idee per i prossimi giorni", "Idee per gennaio 2027". */
@Composable
fun TravelPeriod.phrase(today: LocalDate): String = when (this) {
    TravelPeriod.NextDays -> stringResource(R.string.period_phrase_next_days)
    is TravelPeriod.InMonth -> Formatters.monthYear(month, today.year)
}

fun TravelPeriod.emoji(): String = when (this) {
    TravelPeriod.NextDays -> "🧳"
    is TravelPeriod.InMonth -> when (month.month) {
        Month.JANUARY, Month.FEBRUARY -> "❄️"
        Month.MARCH -> "🌷"
        Month.APRIL -> "🌸"
        Month.MAY -> "🌼"
        Month.JUNE -> "☀️"
        Month.JULY, Month.AUGUST -> "🏖️"
        Month.SEPTEMBER -> "🍇"
        Month.OCTOBER -> "🍂"
        Month.NOVEMBER -> "🍁"
        Month.DECEMBER -> "🎄"
    }
}

/**
 * Riga scorrevole con "Prossimi giorni" e i dodici mesi successivi. All'apertura porta in vista il
 * periodo selezionato, anche se è in fondo alla lista (es. un viaggio tra dieci mesi).
 */
@Composable
fun PeriodChips(
    periods: List<TravelPeriod>,
    selected: TravelPeriod,
    today: LocalDate,
    onSelected: (TravelPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val index = periods.indexOf(selected)
        if (index > 0) listState.scrollToItem(index - 1)
    }
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth().testTag(PERIOD_CHIPS_TAG),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(periods, key = { it.key }) { period ->
            FilterChip(
                selected = period == selected,
                onClick = { onSelected(period) },
                label = { Text(period.emoji() + " " + period.label(today)) },
            )
        }
    }
}
