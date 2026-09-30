package com.partimo.app.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.partimo.app.R
import com.partimo.app.ui.common.BestValueBadge
import com.partimo.app.ui.common.DashboardSection
import com.partimo.app.ui.common.Formatters
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.common.ValueScoreBar
import com.partimo.domain.model.ScoredOffer
import com.partimo.domain.model.TripContext
import com.partimo.domain.model.flight.FlightOffer
import com.partimo.domain.model.flight.FlightSlice
import com.partimo.domain.model.stay.AccommodationOffer

/** Nella sezione dedicata si mostrano quasi tutte le offerte; le altre sono solo contate. */
private const val MAX_VISIBLE_FLIGHTS = 10

/** Voli dal punto di partenza scelto dall'utente (va mostrata solo quando la partenza è nota). */
@Composable
fun FlightsSection(
    state: UiState<List<ScoredOffer<FlightOffer>>>,
    trip: TripContext,
    onRetry: () -> Unit,
    onChangeDeparture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val departure = trip.departure
    DashboardSection(
        title = stringResource(R.string.section_flights),
        subtitle = departure?.let {
            stringResource(R.string.section_flights_subtitle, it.airport.iata, trip.destination.airportIata)
        },
        state = state,
        emptyMessage = stringResource(R.string.empty_flights),
        onRetry = onRetry,
        modifier = modifier,
        headerContent = departure?.let {
            {
                TextButton(onClick = onChangeDeparture, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text("📍 " + it.cityName + " · " + stringResource(R.string.flights_change_departure))
                }
            }
        },
    ) { offers ->
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            offers.take(MAX_VISIBLE_FLIGHTS).forEachIndexed { index, scored ->
                FlightCard(scored = scored, isBestValue = index == 0)
            }
            val hiddenOffers = offers.size - MAX_VISIBLE_FLIGHTS
            if (hiddenOffers > 0) {
                Text(
                    text = pluralStringResource(R.plurals.more_offers, hiddenOffers, hiddenOffers),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun FlightCard(scored: ScoredOffer<FlightOffer>, isBestValue: Boolean, modifier: Modifier = Modifier) {
    val offer = scored.offer
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isBestValue) BestValueBadge()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = offer.carrierName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Formatters.money(offer.totalPrice),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            offer.slices.forEach { slice -> FlightSliceRow(slice) }
            ValueScoreBar(score = scored.valueScore)
            if (offer.refundable == true) {
                Text(
                    text = stringResource(R.string.flight_refundable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun FlightSliceRow(slice: FlightSlice) {
    val stops = if (slice.isDirect) {
        stringResource(R.string.flight_direct)
    } else {
        pluralStringResource(R.plurals.flight_stops, slice.stops, slice.stops)
    }
    Text(
        text = stringResource(
            R.string.flight_leg,
            Formatters.time(slice.departureTime),
            slice.originIata,
            Formatters.time(slice.arrivalTime),
            slice.destinationIata,
            Formatters.duration(slice.duration),
            stops,
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
fun StaysSection(
    state: UiState<List<ScoredOffer<AccommodationOffer>>>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DashboardSection(
        title = stringResource(R.string.section_stays),
        subtitle = stringResource(R.string.section_stays_subtitle),
        state = state,
        emptyMessage = stringResource(R.string.empty_stays),
        onRetry = onRetry,
        modifier = modifier,
    ) { stays ->
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            stays.forEachIndexed { index, scored ->
                StayCard(scored = scored, isBestValue = index == 0, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun StayCard(scored: ScoredOffer<AccommodationOffer>, isBestValue: Boolean, modifier: Modifier = Modifier) {
    val stay = scored.offer
    val reviews = stay.reviewScore?.let { score ->
        val scoreText = stringResource(R.string.review_score, Formatters.rating(score))
        val countText = stay.reviewCount?.let { count ->
            pluralStringResource(R.plurals.review_count, count, Formatters.count(count))
        }
        listOfNotNull(scoreText, countText).joinToString(" · ")
    }
    ElevatedCard(modifier = modifier) {
        AsyncImage(
            model = stay.photoUrl,
            contentDescription = stay.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isBestValue) BestValueBadge()
            Text(
                text = stay.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            stay.starRating?.let { stars ->
                Text(text = "★".repeat(stars), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            }
            reviews?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
            Text(
                text = stringResource(R.string.price_per_night, Formatters.money(stay.pricePerNight)),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.price_total, Formatters.money(stay.totalPrice)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (stay.freeCancellation == true) {
                Text(
                    text = stringResource(R.string.free_cancellation),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            ValueScoreBar(score = scored.valueScore)
        }
    }
}
