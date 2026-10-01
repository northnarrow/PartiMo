package com.partimo.app.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.partimo.app.R
import com.partimo.app.ui.common.DashboardSection
import com.partimo.app.ui.common.FavoriteButton
import com.partimo.app.ui.common.Formatters
import com.partimo.app.ui.common.TravelLinks
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.common.toFavorite
import com.partimo.app.ui.place.googleMapsSearchUrl
import com.partimo.domain.model.TripContext
import com.partimo.domain.model.event.EventKind
import com.partimo.domain.model.event.EventTiming
import com.partimo.domain.model.event.TripEvent
import com.partimo.domain.model.event.TripEvents
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.poi.PoiTag
import com.partimo.domain.model.poi.PointOfInterest
import com.partimo.domain.service.SeasonalCalendar

/**
 * Eventi durante il soggiorno, in cima a "Da vedere": mercatini di Natale, festival e ricorrenze
 * della città e festività nazionali. Gli eventi con un luogo si aprono come i luoghi da vedere
 * (descrizione, storia, "Naviga"). In alto i collegamenti per cercarne altri: i mercatini di Natale
 * su Google Maps (nel periodo giusto) e tutti gli eventi di quei giorni su Google.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripEventsSection(
    state: UiState<TripEvents>,
    trip: TripContext,
    onRetry: () -> Unit,
    onOpenLink: (String) -> Unit,
    onEventClick: (PointOfInterest) -> Unit,
    modifier: Modifier = Modifier,
    favoriteKeys: Set<String>? = null,
    onToggleFavorite: (TripEvent) -> Unit = {},
) {
    val city = trip.destination.name
    // Dalle date del viaggio, non dagli eventi: il pulsante resta anche se la fonte degli eventi non risponde.
    val christmasMarketSeason = SeasonalCalendar.isChristmasMarketSeason(trip.departureDate, trip.returnDate)
    DashboardSection(
        title = "🎉 " + stringResource(R.string.section_events),
        subtitle = stringResource(R.string.section_events_subtitle, Formatters.dateRange(trip.departureDate, trip.returnDate)),
        state = state,
        emptyMessage = stringResource(R.string.events_none),
        onRetry = onRetry,
        modifier = modifier,
        headerContent = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (christmasMarketSeason) {
                    ExternalLinkButton(
                        link = ExternalLink(
                            label = stringResource(R.string.events_christmas_markets_link, city),
                            url = googleMapsSearchUrl("mercatini di Natale $city"),
                        ),
                        onOpenLink = onOpenLink,
                    )
                }
                ExternalLinkButton(
                    link = ExternalLink(
                        label = stringResource(R.string.events_search_link),
                        url = TravelLinks.googleEvents(city, trip.departureDate, trip.returnDate),
                    ),
                    onOpenLink = onOpenLink,
                )
            }
        },
    ) { tripEvents ->
        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            if (tripEvents.events.isEmpty()) {
                Text(
                    text = stringResource(R.string.events_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            tripEvents.events.forEachIndexed { index, event ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                val place = event.toPointOfInterest()
                TripEventRow(
                    event = event,
                    onClick = place?.let { { onEventClick(it) } },
                    // Le festività valgono in tutto il paese: non sono un posto da salvare.
                    isFavorite = favoriteKeys?.takeIf { event.kind != EventKind.PUBLIC_HOLIDAY }?.let { event.toFavorite().key in it },
                    onToggleFavorite = { onToggleFavorite(event) },
                )
            }
            Text(
                text = stringResource(R.string.events_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun TripEventRow(
    event: TripEvent,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    isFavorite: Boolean? = null,
    onToggleFavorite: () -> Unit = {},
) {
    val whenText = eventTimingText(event)
    val details = when (event.kind) {
        EventKind.PUBLIC_HOLIDAY -> listOfNotNull(event.localName, stringResource(R.string.event_holiday_note)).joinToString(" · ")
        else -> event.description
    }
    ListItem(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        headlineContent = { Text(event.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(
                    text = listOfNotNull(event.venueName, whenText).joinToString(" · "),
                    color = MaterialTheme.colorScheme.primary,
                )
                details?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        },
        leadingContent = {
            if (event.photoUrl == null) {
                EmojiBox(emoji = event.kind.emoji(), size = 56.dp)
            } else {
                AsyncImage(
                    model = event.photoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        },
        trailingContent = if (onClick == null && isFavorite == null) {
            null
        } else {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    isFavorite?.let { FavoriteButton(isFavorite = it, name = event.name, onToggle = onToggleFavorite) }
                    if (onClick != null) Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** Quando si tiene l'evento, in parole: "8 dic · festa nazionale", "Ogni anno a ottobre", "Di solito dal 15 nov al 24 dic". */
@Composable
fun eventTimingText(event: TripEvent): String = when (val timing = event.timing) {
    is EventTiming.OnDates -> {
        val dates = if (timing.start == timing.end) Formatters.dayMonth(timing.start) else Formatters.dateRange(timing.start, timing.end)
        if (event.kind == EventKind.PUBLIC_HOLIDAY) stringResource(R.string.event_holiday, dates) else dates
    }
    is EventTiming.Yearly -> stringResource(
        if (event.approximateTiming) R.string.event_usually_between else R.string.event_yearly_between,
        Formatters.dayMonth(timing.start),
        Formatters.dayMonth(timing.end),
    )
    is EventTiming.YearlyDays -> stringResource(R.string.event_every_year_on, joinWithAnd(timing.days.sorted().map(Formatters::dayMonth)))
    is EventTiming.InMonths -> stringResource(R.string.event_every_year_in, joinWithAnd(timing.months.sorted().map(Formatters::monthName)))
}

/** "a, b e c". */
@Composable
private fun joinWithAnd(items: List<String>): String =
    if (items.size <= 1) items.joinToString() else stringResource(R.string.list_and, items.dropLast(1).joinToString(", "), items.last())

private fun EventKind.emoji(): String = when (this) {
    EventKind.CHRISTMAS_MARKET -> "🎄"
    EventKind.RECURRING_EVENT -> "🎉"
    EventKind.PUBLIC_HOLIDAY -> "📅"
}

/** Un evento con un luogo si apre nella scheda dei luoghi (descrizione e storia da Wikipedia, "Naviga"). */
fun TripEvent.toPointOfInterest(): PointOfInterest? = location?.let { place ->
    PointOfInterest(
        id = id,
        name = name,
        category = PoiCategory.SEASONAL_EVENT,
        location = place,
        description = description ?: venueName,
        photoUrl = photoUrl,
        tags = setOf(PoiTag.SEASONAL_HIGHLIGHT),
        wikipediaPage = wikipediaPage,
    )
}
