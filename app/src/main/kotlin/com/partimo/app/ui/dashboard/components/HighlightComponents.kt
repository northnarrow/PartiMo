package com.partimo.app.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.partimo.app.R
import com.partimo.app.ui.common.DashboardSection
import com.partimo.app.ui.common.Formatters
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.common.emoji
import com.partimo.app.ui.common.labelRes
import com.partimo.domain.model.poi.PoiTag
import com.partimo.domain.model.poi.SeasonalHighlights
import com.partimo.domain.model.poi.SeasonalRecommendation

/** Ordine di visualizzazione delle etichette fotografiche (le più rilevanti per prime). */
private val TAG_DISPLAY_ORDER = listOf(
    PoiTag.SEASONAL_HIGHLIGHT,
    PoiTag.INSTAGRAMMABLE,
    PoiTag.PANORAMIC,
    PoiTag.SUNSET_SPOT,
    PoiTag.HIDDEN_GEM,
)
private const val MAX_VISIBLE_TAGS = 3

@Composable
fun HighlightsSection(
    state: UiState<SeasonalHighlights>,
    destinationName: String,
    photoSpotsOnly: Boolean,
    onPhotoSpotsOnlyChanged: (Boolean) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DashboardSection(
        title = stringResource(R.string.section_highlights),
        state = state,
        emptyMessage = stringResource(if (photoSpotsOnly) R.string.empty_highlights_photo else R.string.empty_highlights),
        onRetry = onRetry,
        modifier = modifier,
        headerContent = {
            FilterChip(
                selected = photoSpotsOnly,
                onClick = { onPhotoSpotsOnlyChanged(!photoSpotsOnly) },
                label = { Text("📸 " + stringResource(R.string.photo_spots_only)) },
                leadingIcon = if (photoSpotsOnly) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
        },
    ) { highlights ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SeasonWeatherCard(highlights = highlights, destinationName = destinationName)
            highlights.recommendations.forEach { recommendation ->
                PoiCard(recommendation, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun SeasonWeatherCard(highlights: SeasonalHighlights, destinationName: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = highlights.season.emoji() + " " + stringResource(
                    R.string.season_title,
                    stringResource(highlights.season.labelRes()),
                    Formatters.monthName(highlights.travelMonth),
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            val weather = highlights.currentWeather
            if (weather == null) {
                Text(text = stringResource(R.string.weather_unavailable), style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    text = stringResource(
                        R.string.weather_now,
                        destinationName,
                        Formatters.temperature(weather.temperatureCelsius),
                        stringResource(weather.condition.labelRes()),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(
                        if (highlights.weatherConsidered) R.string.weather_considered else R.string.weather_not_considered,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
fun PoiCard(recommendation: SeasonalRecommendation, modifier: Modifier = Modifier) {
    val poi = recommendation.poi
    val ratingText = poi.rating?.let { rating ->
        val reviews = poi.reviewCount?.let { count -> pluralStringResource(R.plurals.review_count, count, Formatters.count(count)) }
        listOfNotNull("★ " + Formatters.rating(rating), reviews).joinToString(" · ")
    }
    ElevatedCard(modifier = modifier) {
        AsyncImage(
            model = poi.photoUrl,
            contentDescription = poi.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = poi.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            ratingText?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PoiTags(poi.tags)
            recommendation.reasons.firstOrNull()?.let { reason ->
                Text(
                    text = stringResource(reason.labelRes()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PoiTags(tags: Set<PoiTag>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TAG_DISPLAY_ORDER.filter { it in tags }.take(MAX_VISIBLE_TAGS).forEach { tag ->
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(
                    text = tag.emoji() + " " + stringResource(tag.labelRes()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}
