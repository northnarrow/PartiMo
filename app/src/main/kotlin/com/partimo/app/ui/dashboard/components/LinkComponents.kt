package com.partimo.app.ui.dashboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.partimo.app.R
import com.partimo.app.ui.common.TravelLinks
import com.partimo.domain.model.TripContext

/** Pagina del copyright di OpenStreetMap, citata insieme ai dati come chiede la licenza ODbL. */
const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

/** Pulsante verso un sito esterno che si apre con la ricerca già compilata. */
data class ExternalLink(val label: String, val url: String)

/**
 * Card con i siti che mostrano prezzi e orari reali per il viaggio: si aprono con tratta, date e
 * viaggiatori già compilati.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExternalLinksCard(
    title: String,
    lines: List<String>,
    links: List<ExternalLink>,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            lines.forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                links.forEach { link -> ExternalLinkButton(link = link, onOpenLink = onOpenLink) }
            }
        }
    }
}

@Composable
fun ExternalLinkButton(link: ExternalLink, onOpenLink: (String) -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(onClick = { onOpenLink(link.url) }, modifier = modifier) {
        Text(link.label)
        // La freccia indica che si apre un'altra app: decorativa, i lettori di schermo la ignorano.
        Text(text = " ↗", modifier = Modifier.clearAndSetSemantics {})
    }
}

/** Attribuzione dei dati di OpenStreetMap (il margine orizzontale lo decide chi la usa); il tocco apre la pagina del copyright. */
@Composable
fun OsmAttribution(onOpenLink: (String) -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.osm_attribution),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clickable(role = Role.Button) { onOpenLink(OSM_COPYRIGHT_URL) }
            .padding(vertical = 8.dp),
    )
}

/** Voli andata e ritorno con le date del viaggio; `null` finché non c'è un punto di partenza. */
@Composable
fun flightLinks(trip: TripContext): List<ExternalLink>? {
    val origin = trip.originIata ?: return null
    val destination = trip.destination.airportIata
    return listOf(
        ExternalLink(
            label = stringResource(R.string.link_google_flights),
            url = TravelLinks.googleFlights(origin, destination, trip.departureDate, trip.returnDate),
        ),
        ExternalLink(
            label = stringResource(R.string.link_skyscanner),
            url = TravelLinks.skyscanner(origin, destination, trip.departureDate, trip.returnDate, trip.travellers),
        ),
    )
}

/** Tutte le strutture della città sui siti di prenotazione, per le date del viaggio. */
@Composable
fun stayLinks(trip: TripContext): List<ExternalLink> {
    val city = trip.destination.name
    return listOf(
        ExternalLink(
            label = stringResource(R.string.link_booking),
            url = TravelLinks.booking(city, trip.departureDate, trip.returnDate, trip.travellers),
        ),
        ExternalLink(
            label = stringResource(R.string.link_airbnb),
            url = TravelLinks.airbnb(city, trip.departureDate, trip.returnDate, trip.travellers),
        ),
    )
}
