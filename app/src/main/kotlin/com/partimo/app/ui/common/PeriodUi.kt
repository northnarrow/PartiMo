package com.partimo.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.partimo.app.R
import com.partimo.domain.model.Money
import com.partimo.domain.model.TravelPeriod
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.util.Locale

/** Tag della riga dei periodi, usato dai test UI per lo scroll orizzontale. */
const val PERIOD_CHIPS_TAG = "period_chips"

/**
 * Etichetta per i chip: "Prossimi giorni", "Dicembre", "Gennaio 2027" (anno solo se diverso da quello
 * corrente), "10–14 dic" per le date scelte.
 */
@Composable
fun TravelPeriod.label(today: LocalDate): String = when (this) {
    TravelPeriod.NextDays -> stringResource(R.string.period_next_days)
    is TravelPeriod.InMonth -> Formatters.monthYear(month, today.year).replaceFirstChar { it.titlecase(Locale.ITALY) }
    is TravelPeriod.Dates -> Formatters.dateRange(departure, returning)
}

/** Periodo dentro una frase: "Idee per i prossimi giorni", "Idee per gennaio 2027", "Idee per le tue date (10–14 dic)". */
@Composable
fun TravelPeriod.phrase(today: LocalDate): String = when (this) {
    TravelPeriod.NextDays -> stringResource(R.string.period_phrase_next_days)
    is TravelPeriod.InMonth -> Formatters.monthYear(month, today.year)
    is TravelPeriod.Dates -> stringResource(R.string.period_phrase_dates, Formatters.dateRange(departure, returning))
}

fun TravelPeriod.emoji(): String = when (this) {
    TravelPeriod.NextDays -> "🧳"
    is TravelPeriod.Dates -> "📅"
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
    /** Prezzo più basso a persona di ogni mese («da 72 €»), se noto. */
    monthPrices: Map<YearMonth, Money> = emptyMap(),
    /** Mese più conveniente: il suo prezzo è evidenziato. */
    cheapestMonth: YearMonth? = null,
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
                label = {
                    val month = (period as? TravelPeriod.InMonth)?.month
                    val price = month?.let { monthPrices[it] }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(period.emoji() + " " + period.label(today))
                        if (price != null) {
                            val cheapest = month == cheapestMonth
                            Text(
                                text = " · " + stringResource(R.string.month_price_from, Formatters.money(price)),
                                color = if (cheapest) CheapestPriceColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (cheapest) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                },
            )
        }
    }
}

/** Verde del mese più conveniente, leggibile nei temi chiaro e scuro. */
private val CheapestPriceColor = Color(0xFF2E9D4A)
