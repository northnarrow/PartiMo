package com.partimo.app.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.partimo.app.R
import com.partimo.app.ui.common.TravelLinks
import com.partimo.app.ui.place.googleMapsTransitUrl
import com.partimo.domain.service.ModeFootprint
import com.partimo.domain.service.TravelMode
import java.util.Locale
import kotlin.math.roundToInt

/** Tag della scheda "Come arrivare", usato dai test UI. */
const val GETTING_THERE_TAG = "getting_there"

/**
 * Come arrivare dalla città di partenza: treni, pullman e voli a confronto su Google Maps e Rome2rio
 * e, se la distanza lo permette, le emissioni di CO₂ di ogni mezzo, a persona.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GettingThereCard(
    fromCity: String,
    toCity: String,
    footprint: List<ModeFootprint>,
    travellers: Int,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "🧭 " + stringResource(R.string.getting_there_title, fromCity),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.getting_there_text, toCity),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (footprint.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.footprint_title),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
                val max = footprint.maxOf { it.kgCo2e }.coerceAtLeast(1.0)
                footprint.forEach { mode -> FootprintRow(mode, travellers, fraction = (mode.kgCo2e / max).toFloat()) }
                Text(
                    text = stringResource(R.string.footprint_source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExternalLinkButton(
                    link = ExternalLink("🚆 " + stringResource(R.string.getting_there_google), googleMapsTransitUrl(fromCity, toCity)),
                    onOpenLink = onOpenLink,
                )
                ExternalLinkButton(link = ExternalLink(stringResource(R.string.getting_there_rome2rio), TravelLinks.rome2rio(fromCity, toCity)), onOpenLink = onOpenLink)
            }
        }
    }
}

@Composable
private fun FootprintRow(footprint: ModeFootprint, travellers: Int, fraction: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = footprint.mode.emoji() + " " + footprint.mode.label(travellers),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(128.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .height(8.dp)
                    .background(footprint.mode.barColor(), RoundedCornerShape(4.dp)),
            )
        }
        Text(
            text = stringResource(R.string.footprint_kg, kilograms(footprint.kgCo2e)),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(64.dp),
        )
    }
}

/** Chilogrammi arrotondati con il separatore delle migliaia (es. "1.234"). */
fun kilograms(kg: Double): String = String.format(Locale.ITALY, "%,d", kg.roundToInt())

private fun TravelMode.emoji(): String = when (this) {
    TravelMode.FLIGHT -> "✈️"
    TravelMode.TRAIN -> "🚆"
    TravelMode.COACH -> "🚌"
    TravelMode.CAR -> "🚗"
}

@Composable
private fun TravelMode.label(travellers: Int): String = when (this) {
    TravelMode.FLIGHT -> stringResource(R.string.footprint_flight)
    TravelMode.TRAIN -> stringResource(R.string.footprint_train)
    TravelMode.COACH -> stringResource(R.string.footprint_coach)
    TravelMode.CAR -> if (travellers > 1) stringResource(R.string.footprint_car_shared, travellers) else stringResource(R.string.footprint_car_alone)
}

/** Treno e pullman in verde, aereo e auto in arancio: si vede subito chi inquina meno. */
@Composable
private fun TravelMode.barColor() = when (this) {
    TravelMode.TRAIN, TravelMode.COACH -> MaterialTheme.colorScheme.primary
    TravelMode.FLIGHT, TravelMode.CAR -> MaterialTheme.colorScheme.tertiary
}
