package com.partimo.app.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import coil3.compose.AsyncImage
import com.partimo.app.R
import com.partimo.app.ui.common.DashboardSection
import com.partimo.app.ui.common.Formatters
import com.partimo.app.ui.common.OpeningHoursText
import com.partimo.app.ui.common.labelRes
import com.partimo.app.ui.common.openStateText
import com.partimo.domain.service.OpeningHoursParser
import java.time.LocalDate
import java.time.LocalDateTime
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.common.emoji
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.dining.BudgetDiningCriteria
import com.partimo.domain.model.dining.Restaurant
import com.partimo.domain.model.transit.TransferConnection
import com.partimo.domain.model.transit.TransitLeg
import com.partimo.domain.model.transit.TransitRoute
import java.time.ZoneId

// Ogni sezione ha una schermata dedicata: si mostrano più risultati che nella vecchia pagina unica.
private const val MAX_VISIBLE_ROUTES = 5
private const val MAX_VISIBLE_RESTAURANTS = 20

// ---- Trasporti pubblici ----------------------------------------------------------------------

/**
 * Percorsi con i mezzi dal nodo di arrivo al centro. In alto il collegamento allo stesso percorso su
 * Google Maps: linee e orari reali anche senza chiave API (in quel caso i percorsi mostrati sono stime).
 */
@Composable
fun TransitSection(
    state: UiState<List<TransitRoute>>,
    hubName: String,
    timeZone: ZoneId,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    mapsUrl: String? = null,
    onOpenLink: (String) -> Unit = {},
) {
    val estimated = (state as? UiState.Success)?.origin == DataOrigin.DEMO
    DashboardSection(
        title = stringResource(R.string.section_transit),
        subtitle = stringResource(R.string.section_transit_subtitle, hubName),
        state = state,
        emptyMessage = stringResource(R.string.empty_transit),
        onRetry = onRetry,
        modifier = modifier,
        headerContent = mapsUrl?.let { url ->
            {
                ExternalLinksCard(
                    title = stringResource(R.string.transit_links_title),
                    lines = listOf(stringResource(if (estimated) R.string.transit_links_estimates else R.string.transit_links_text)),
                    links = listOf(ExternalLink(stringResource(R.string.transit_open_maps), url)),
                    onOpenLink = onOpenLink,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
    ) { routes ->
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            routes.take(MAX_VISIBLE_ROUTES).forEach { route -> TransitRouteCard(route, timeZone) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransitRouteCard(route: TransitRoute, timeZone: ZoneId, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        R.string.transit_times,
                        Formatters.time(route.departureTime, timeZone),
                        Formatters.time(route.arrivalTime, timeZone),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Formatters.duration(route.totalDuration),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = if (route.transfers == 0) {
                    stringResource(R.string.transit_no_transfers)
                } else {
                    pluralStringResource(R.plurals.transit_transfers, route.transfers, route.transfers)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                route.legs.forEachIndexed { index, leg ->
                    if (index > 0) {
                        Text(
                            text = "›",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                    LegChip(leg, modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
            route.connections.forEach { connection -> ConnectionInfo(connection) }
        }
    }
}

@Composable
private fun LegChip(leg: TransitLeg, modifier: Modifier = Modifier) {
    val minutes = leg.duration.toMinutes()
    if (leg.isWalking) {
        Text(
            text = "${leg.mode.emoji()} $minutes min",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    val lineColor = leg.line?.colorHex?.toComposeColor()
    val textColor = leg.line?.textColorHex?.toComposeColor()
        ?: if (lineColor != null) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
    val lineName = leg.line?.shortName ?: leg.line?.name.orEmpty()
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = lineColor ?: MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Text(
            text = "${leg.mode.emoji()} $lineName · $minutes min",
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ConnectionInfo(connection: TransferConnection) {
    val base = stringResource(
        R.string.transit_connection,
        connection.stopName ?: "—",
        connection.transferTime.toMinutes().toInt(),
    )
    val tight = connection.isTight()
    Text(
        text = if (tight) base + " · " + stringResource(R.string.transit_tight_connection) else base,
        style = MaterialTheme.typography.bodySmall,
        color = if (tight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun String.toComposeColor(): Color? = runCatching { Color(toColorInt()) }.getOrNull()

// ---- Ristoranti ------------------------------------------------------------------------------

/**
 * Ristoranti: con Google Places quelli economici e ben recensiti; senza chiave i locali reali di
 * OpenStreetMap vicino al centro, senza valutazioni. Il tocco apre il locale su Google Maps
 * (recensioni, foto, orari e indicazioni).
 */
@Composable
fun RestaurantsSection(
    state: UiState<List<Restaurant>>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    ratingsAvailable: Boolean = true,
    center: GeoPoint? = null,
    onOpenLink: (String) -> Unit = {},
    /** Settimana di cui mostrare gli orari (quella del viaggio). */
    hoursWeekOf: LocalDate? = null,
    /** Ora locale della meta, per "Aperto ora"; `null` se il viaggio non è imminente. */
    nowAtDestination: LocalDateTime? = null,
) {
    DashboardSection(
        title = stringResource(if (ratingsAvailable) R.string.section_restaurants else R.string.section_restaurants_nearby),
        subtitle = if (ratingsAvailable) {
            stringResource(R.string.section_restaurants_subtitle, Formatters.rating(BudgetDiningCriteria.DEFAULT_MIN_RATING))
        } else {
            stringResource(R.string.section_restaurants_nearby_subtitle)
        },
        state = state,
        emptyMessage = stringResource(if (ratingsAvailable) R.string.empty_restaurants else R.string.empty_restaurants_nearby),
        onRetry = onRetry,
        modifier = modifier,
    ) { restaurants ->
        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            restaurants.take(MAX_VISIBLE_RESTAURANTS).forEachIndexed { index, restaurant ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                RestaurantRow(
                    restaurant = restaurant,
                    distanceMeters = center?.let { restaurant.location?.distanceTo(it) },
                    onClick = restaurant.mapsUrl?.let { url -> { onOpenLink(url) } },
                    hoursWeekOf = hoursWeekOf,
                    nowAtDestination = nowAtDestination,
                )
            }
            if (!ratingsAvailable) OsmAttribution(onOpenLink = onOpenLink, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
fun RestaurantRow(
    restaurant: Restaurant,
    modifier: Modifier = Modifier,
    distanceMeters: Double? = null,
    onClick: (() -> Unit)? = null,
    hoursWeekOf: LocalDate? = null,
    nowAtDestination: LocalDateTime? = null,
) {
    val details = listOfNotNull(
        restaurant.cuisine,
        restaurant.priceLevel?.let(Formatters::priceLevel),
        distanceMeters?.let { stringResource(R.string.distance_from_center, Formatters.distance(it)) },
    ).joinToString(" · ")
    val hours = remember(restaurant.openingHours) { OpeningHoursParser.parse(restaurant.openingHours) }
    val closedLabel = stringResource(R.string.hours_closed)
    ListItem(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        headlineContent = { Text(restaurant.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                if (details.isNotEmpty()) Text(details)
                restaurant.address?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (hours != null && nowAtDestination != null) {
                    Text(openStateText(hours.stateAt(nowAtDestination), nowAtDestination), style = MaterialTheme.typography.labelMedium)
                }
                if (hours != null && hoursWeekOf != null) {
                    Text(
                        text = stringResource(R.string.hours_weekly, OpeningHoursText.weekly(hours, hoursWeekOf, closedLabel)),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                restaurant.wheelchair?.let { Text(stringResource(it.labelRes())) }
            }
        },
        leadingContent = {
            if (restaurant.photoUrl == null) {
                EmojiBox(emoji = "🍽️", size = 56.dp)
            } else {
                AsyncImage(
                    model = restaurant.photoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                restaurant.rating?.let {
                    Text(
                        text = "★ " + Formatters.rating(it),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                restaurant.reviewCount?.let {
                    Text(
                        text = "(" + Formatters.count(it) + ")",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (restaurant.isOpenNow == true) {
                    Text(
                        text = stringResource(R.string.restaurant_open_now),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
