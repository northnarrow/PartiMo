package com.partimo.app.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.partimo.app.R
import com.partimo.app.ui.common.BestValueBadge
import com.partimo.app.ui.common.DashboardSection
import com.partimo.app.ui.common.FavoriteButton
import com.partimo.app.ui.common.Formatters
import com.partimo.app.ui.common.TravelLinks
import com.partimo.app.ui.common.TravellersDialog
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.common.ValueScoreBar
import com.partimo.app.ui.common.label
import com.partimo.app.ui.common.labelRes
import com.partimo.app.ui.common.toFavorite
import com.partimo.app.ui.place.googleMapsSearchUrl
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.model.ScoredOffer
import com.partimo.domain.model.Travellers
import com.partimo.domain.model.TripContext
import com.partimo.domain.model.flight.FlightOffer
import com.partimo.domain.model.flight.FlightPriceSource
import com.partimo.domain.model.flight.FlightSlice
import com.partimo.domain.model.stay.AccommodationOffer
import com.partimo.domain.model.stay.Lodging
import com.partimo.domain.model.stay.LodgingType
import java.net.URI

/** Nella sezione dedicata si mostrano quasi tutte le offerte; le altre sono solo contate. */
private const val MAX_VISIBLE_FLIGHTS = 10
private const val MAX_VISIBLE_LODGINGS = 20

/**
 * Voli dal punto di partenza scelto dall'utente (va mostrata solo quando la partenza è nota). In alto
 * i collegamenti a Google Voli e Skyscanner con tratta e date già compilate: con le tariffe stimate
 * (senza chiave API) portano ai prezzi reali e alla prenotazione. Con i prezzi trovati di recente su
 * Aviasales ([FlightPriceSource.RECENT_SEARCHES]) ogni volo ha le sue date nel periodo scelto e si apre
 * su Aviasales per verificarne il prezzo.
 */
@Composable
fun FlightsSection(
    state: UiState<List<ScoredOffer<FlightOffer>>>,
    trip: TripContext,
    onRetry: () -> Unit,
    onChangeDeparture: () -> Unit,
    modifier: Modifier = Modifier,
    priceSource: FlightPriceSource = FlightPriceSource.LIVE_OFFERS,
    /** Date di andata e ritorno scelte dall'utente: i voli di Aviasales proprio in quei giorni sono segnalati. */
    exactDates: Boolean = false,
    onOpenLink: (String) -> Unit = {},
    /** Nuovi viaggiatori scelti dall'intestazione; `null` = nessun pulsante per cambiarli. */
    onTravellersSelected: ((Travellers) -> Unit)? = null,
) {
    val departure = trip.departure
    val links = flightLinks(trip)
    var pickingTravellers by rememberSaveable { mutableStateOf(false) }
    val estimated = priceSource == FlightPriceSource.ESTIMATES || (state as? UiState.Success)?.origin == DataOrigin.DEMO
    val recentPrices = priceSource == FlightPriceSource.RECENT_SEARCHES
    val note = when {
        estimated -> R.string.flights_links_estimates
        recentPrices && exactDates -> R.string.flights_links_recent_dates
        recentPrices -> R.string.flights_links_recent
        else -> R.string.flights_links_compare
    }
    DashboardSection(
        title = stringResource(R.string.section_flights),
        subtitle = departure?.let {
            // Aviasales cerca per città: compaiono anche gli altri aeroporti (Bergamo per Milano).
            if (priceSource == FlightPriceSource.RECENT_SEARCHES) {
                stringResource(R.string.section_flights_subtitle_cities, it.cityName, trip.destination.name)
            } else {
                stringResource(R.string.section_flights_subtitle, it.airport.iata, trip.destination.airportIata)
            }
        },
        state = state,
        emptyMessage = stringResource(if (priceSource == FlightPriceSource.RECENT_SEARCHES) R.string.empty_flights_recent else R.string.empty_flights),
        onRetry = onRetry,
        modifier = modifier,
        headerContent = departure?.let {
            {
                Column {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(onClick = onChangeDeparture, contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Text("📍 " + it.cityName + " · " + stringResource(R.string.flights_change_departure))
                        }
                        if (onTravellersSelected != null) {
                            TextButton(onClick = { pickingTravellers = true }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                                Text("👥 " + trip.travellers.label() + " · " + stringResource(R.string.flights_change_travellers))
                            }
                        }
                    }
                    links?.let { flightLinks ->
                        ExternalLinksCard(
                            title = stringResource(R.string.links_title),
                            lines = listOf(stringResource(note)),
                            links = flightLinks,
                            onOpenLink = onOpenLink,
                        )
                    }
                }
            }
        },
    ) { offers ->
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val visible = offers.take(MAX_VISIBLE_FLIGHTS)
            // Con le date scelte i voli di quei giorni vengono prima: il migliore può essere uno dei giorni vicini.
            val bestValue = visible.maxByOrNull { it.valueScore }
            visible.forEach { scored ->
                val onTripDates = exactDates && recentPrices &&
                    scored.offer.outbound.departureTime.toLocalDate() == trip.departureDate &&
                    scored.offer.inbound?.departureTime?.toLocalDate() == trip.returnDate
                FlightCard(scored = scored, isBestValue = scored === bestValue, onTripDates = onTripDates, onOpenLink = onOpenLink)
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
    if (pickingTravellers && onTravellersSelected != null) {
        TravellersDialog(
            initial = trip.travellers,
            onDismiss = { pickingTravellers = false },
            onConfirm = { travellers ->
                pickingTravellers = false
                onTravellersSelected(travellers)
            },
        )
    }
}

@Composable
fun FlightCard(
    scored: ScoredOffer<FlightOffer>,
    isBestValue: Boolean,
    modifier: Modifier = Modifier,
    /** `true` se il volo parte e torna proprio nelle date scelte dall'utente (le altre sono date vicine). */
    onTripDates: Boolean = false,
    onOpenLink: (String) -> Unit = {},
) {
    val offer = scored.offer
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isBestValue || onTripDates) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (onTripDates) TripDatesBadge()
                    if (isBestValue) BestValueBadge()
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                offer.carrierLogoUrl?.let { logo ->
                    AsyncImage(
                        model = logo,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.width(8.dp))
                }
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
            if (offer.passengers > 1) {
                Text(
                    text = stringResource(
                        R.string.flight_price_per_person,
                        Formatters.money(offer.pricePerPassenger),
                        pluralStringResource(R.plurals.flight_seats, offer.passengers, offer.passengers),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End),
                )
            }
            offer.slices.forEachIndexed { index, slice -> FlightSliceRow(slice, isReturn = index > 0) }
            val details = listOfNotNull(
                offer.stayNights?.let { nights -> pluralStringResource(R.plurals.flight_stay_nights, nights, nights) },
                offer.priceFoundOn?.let { day -> stringResource(R.string.flight_price_found_on, Formatters.dayMonth(day)) },
            )
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ValueScoreBar(score = scored.valueScore)
            if (offer.refundable == true) {
                Text(
                    text = stringResource(R.string.flight_refundable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            offer.bookingUrl?.let { url ->
                TextButton(onClick = { onOpenLink(url) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text(stringResource(R.string.flight_open_booking, siteName(url)))
                    // La freccia indica che si apre un'altra pagina: decorativa, i lettori di schermo la ignorano.
                    Text(text = " ↗", modifier = Modifier.clearAndSetSemantics {})
                }
            }
        }
    }
}

/** Volo proprio nelle date scelte dall'utente, tra quelli dei giorni vicini. */
@Composable
private fun TripDatesBadge() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            text = "📅 " + stringResource(R.string.flight_on_trip_dates),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Nome del sito da mostrare per un collegamento: "aviasales.com". */
private fun siteName(url: String): String = runCatching { URI(url).host }.getOrNull()?.removePrefix("www.") ?: url

@Composable
private fun FlightSliceRow(slice: FlightSlice, isReturn: Boolean) {
    val stops = if (slice.isDirect) {
        stringResource(R.string.flight_direct)
    } else {
        pluralStringResource(R.plurals.flight_stops, slice.stops, slice.stops)
    }
    Column {
        Text(
            text = stringResource(
                if (isReturn) R.string.flight_inbound_day else R.string.flight_outbound_day,
                Formatters.weekdayDayMonth(slice.departureTime.toLocalDate()),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
}

/** Offerte con prezzo del provider di prenotazione, con il confronto su Booking.com e Airbnb. */
@Composable
fun StaysSection(
    state: UiState<List<ScoredOffer<AccommodationOffer>>>,
    trip: TripContext,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLink: (String) -> Unit = {},
) {
    DashboardSection(
        title = stringResource(R.string.section_stays),
        subtitle = stringResource(R.string.section_stays_subtitle),
        state = state,
        emptyMessage = stringResource(R.string.empty_stays),
        onRetry = onRetry,
        modifier = modifier,
        headerContent = { StayLinksCard(trip = trip, onOpenLink = onOpenLink) },
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

/** Booking.com e Airbnb con città e date del viaggio. */
@Composable
private fun StayLinksCard(trip: TripContext, onOpenLink: (String) -> Unit) {
    ExternalLinksCard(
        title = stringResource(R.string.links_title),
        lines = listOf(stringResource(R.string.stays_links_text, trip.destination.name)),
        links = stayLinks(trip),
        onOpenLink = onOpenLink,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * Alloggi senza provider di prenotazione: strutture reali attorno al centro (OpenStreetMap), ognuna
 * collegata a Booking.com con nome e date già compilati, alla mappa e al sito ufficiale.
 */
@Composable
fun LodgingsSection(
    state: UiState<List<Lodging>>,
    trip: TripContext,
    onRetry: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    favoriteKeys: Set<String>? = null,
    onToggleFavorite: (Lodging) -> Unit = {},
) {
    DashboardSection(
        title = stringResource(R.string.section_lodgings),
        subtitle = stringResource(R.string.section_lodgings_subtitle),
        state = state,
        emptyMessage = stringResource(R.string.empty_lodgings),
        onRetry = onRetry,
        modifier = modifier,
        headerContent = { StayLinksCard(trip = trip, onOpenLink = onOpenLink) },
    ) { lodgings ->
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            lodgings.take(MAX_VISIBLE_LODGINGS).forEach { lodging ->
                LodgingCard(
                    lodging = lodging,
                    trip = trip,
                    onOpenLink = onOpenLink,
                    isFavorite = favoriteKeys?.let { lodging.toFavorite(lodgingMapsUrl(lodging, trip.destination.name)).key in it },
                    onToggleFavorite = { onToggleFavorite(lodging) },
                )
            }
            OsmAttribution(onOpenLink = onOpenLink)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LodgingCard(
    lodging: Lodging,
    trip: TripContext,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean? = null,
    onToggleFavorite: () -> Unit = {},
) {
    val city = trip.destination.name
    val details = listOfNotNull(
        stringResource(lodging.type.labelRes()),
        lodging.starRating?.let { "★".repeat(it) },
        stringResource(R.string.distance_from_center, Formatters.distance(lodging.location.distanceTo(trip.destination.center))),
    ).joinToString(" · ")
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EmojiBox(emoji = lodging.type.emoji())
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = lodging.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                lodging.address?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                lodging.wheelchair?.let { access ->
                    Text(text = stringResource(access.labelRes()), style = MaterialTheme.typography.bodySmall)
                }
            }
            isFavorite?.let { FavoriteButton(isFavorite = it, name = lodging.name, onToggle = onToggleFavorite) }
        }
        FlowRow(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ExternalLinkButton(
                link = ExternalLink(
                    label = stringResource(R.string.lodging_prices),
                    url = TravelLinks.booking(lodging.name + ", " + city, trip.departureDate, trip.returnDate, trip.travellers),
                ),
                onOpenLink = onOpenLink,
            )
            TextButton(onClick = { onOpenLink(lodgingMapsUrl(lodging, city)) }) {
                Text(stringResource(R.string.lodging_map))
            }
            lodging.website?.let { website ->
                TextButton(onClick = { onOpenLink(website) }) { Text(stringResource(R.string.lodging_website)) }
            }
        }
    }
}

/** Riquadro con un'emoji al posto della foto (le fonti aperte non hanno immagini dei locali). */
@Composable
internal fun EmojiBox(emoji: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, style = MaterialTheme.typography.titleLarge)
    }
}

private fun LodgingType.labelRes(): Int = when (this) {
    LodgingType.HOTEL -> R.string.lodging_type_hotel
    LodgingType.HOSTEL -> R.string.lodging_type_hostel
    LodgingType.GUEST_HOUSE -> R.string.lodging_type_guest_house
    LodgingType.APARTMENT -> R.string.lodging_type_apartment
    LodgingType.MOTEL -> R.string.lodging_type_motel
}

private fun LodgingType.emoji(): String = when (this) {
    LodgingType.HOTEL -> "🏨"
    LodgingType.HOSTEL -> "🛏️"
    LodgingType.GUEST_HOUSE -> "🏡"
    LodgingType.APARTMENT -> "🏢"
    LodgingType.MOTEL -> "🚗"
}

/** Pagina della struttura su Google Maps (foto, recensioni, indicazioni). */
fun lodgingMapsUrl(lodging: Lodging, city: String): String =
    googleMapsSearchUrl(listOfNotNull(lodging.name, lodging.address, city).joinToString(", "))
