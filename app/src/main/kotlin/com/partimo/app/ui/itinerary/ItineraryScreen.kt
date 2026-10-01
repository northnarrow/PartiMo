package com.partimo.app.ui.itinerary

import android.content.ActivityNotFoundException
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.partimo.app.R
import com.partimo.app.ui.common.Formatters
import com.partimo.app.ui.common.SectionError
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.dashboard.PreviewData
import com.partimo.app.ui.dashboard.components.toPointOfInterest
import com.partimo.app.ui.place.ExternalLinks
import com.partimo.app.ui.place.RouteStop
import com.partimo.app.ui.place.googleMapsSearchUrl
import com.partimo.app.ui.place.googleMapsWalkingRouteUrl
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.model.plan.DayPlan
import com.partimo.domain.model.plan.PackingGroup
import com.partimo.domain.model.plan.PlanStop
import com.partimo.domain.model.plan.StopTarget
import com.partimo.domain.model.plan.TripInterest
import com.partimo.domain.model.plan.TripPace
import com.partimo.domain.model.plan.TripPlan
import com.partimo.domain.model.plan.TripPreferences
import com.partimo.domain.model.poi.PointOfInterest
import java.time.Duration

/** Tag della lista dell'itinerario, usato dai test UI per lo scroll. */
const val ITINERARY_LIST_TAG = "itinerary_list"

/** Tag della riga degli interessi, scorrevole in orizzontale. */
const val ITINERARY_INTERESTS_TAG = "itinerary_interests"

/** Azioni dell'itinerario (predefinite vuote per anteprime e test). */
data class ItineraryActions(
    val onBack: () -> Unit = {},
    val onPaceSelected: (TripPace) -> Unit = {},
    val onInterestToggled: (TripInterest) -> Unit = {},
    val onApplyPreferences: () -> Unit = {},
    val onRegenerate: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onTabSelected: (ItineraryTab) -> Unit = {},
    val onPackedToggled: (String) -> Unit = {},
    /** Tappa con un luogo o un evento dell'app: si apre la sua scheda. */
    val onOpenPlace: (PointOfInterest) -> Unit = {},
    /** Google Maps (giro a piedi, ricerca dei locali consigliati dall'IA). */
    val onOpenLink: (String) -> Unit = {},
    val onAddToCalendar: (DayPlan, Int) -> Unit = { _, _ -> },
    val onShare: (TripPlan) -> Unit = {},
    val onAskAssistant: () -> Unit = {},
)

@Composable
fun ItineraryRoute(
    viewModel: ItineraryViewModel,
    onBack: () -> Unit,
    onOpenPlace: (PointOfInterest) -> Unit,
    onAskAssistant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()
    val noBrowser = stringResource(R.string.place_no_browser)
    val noCalendar = stringResource(R.string.itinerary_no_calendar)
    ItineraryScreen(
        state = state,
        actions = ItineraryActions(
            onBack = onBack,
            onPaceSelected = viewModel::onPaceSelected,
            onInterestToggled = viewModel::onInterestToggled,
            onApplyPreferences = viewModel::applyPreferences,
            onRegenerate = viewModel::regenerate,
            onRetry = viewModel::retry,
            onTabSelected = viewModel::onTabSelected,
            onPackedToggled = viewModel::onPackedToggled,
            onOpenPlace = onOpenPlace,
            onOpenLink = { url ->
                if (!ExternalLinks.openLink(context, url, toolbarColor)) Toast.makeText(context, noBrowser, Toast.LENGTH_LONG).show()
            },
            onAddToCalendar = { day, number ->
                try {
                    context.startActivity(ItineraryIntents.calendar(context, day, number, state.destination.name))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, noCalendar, Toast.LENGTH_LONG).show()
                }
            },
            onShare = { plan -> context.startActivity(ItineraryIntents.share(context, plan, state.destination.name, state.from, state.to)) },
            onAskAssistant = onAskAssistant,
        ),
        modifier = modifier,
    )
}

/**
 * Itinerario giorno per giorno proposto dall'IA: in alto ritmo e interessi, poi tre schede (programma,
 * valigia, consigli). Ogni tappa apre la scheda del luogo o Google Maps; ogni giornata ha il giro a
 * piedi e l'aggiunta al calendario. È stateless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryScreen(state: ItineraryUiState, actions: ItineraryActions, modifier: Modifier = Modifier) {
    val plan = (state.plan as? UiState.Success)?.data
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "✨ " + stringResource(R.string.itinerary_title, state.destination.name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = pluralStringResource(
                                R.plurals.itinerary_subtitle,
                                state.dayCount,
                                Formatters.dateRange(state.from, state.to),
                                state.dayCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (plan != null) {
                        IconButton(onClick = { actions.onShare(plan) }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.itinerary_share))
                        }
                    }
                    if (state.isAvailable) {
                        IconButton(onClick = actions.onRegenerate, enabled = !state.isGenerating) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.itinerary_regenerate))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (!state.isAvailable) {
                AssistantUnavailable()
                return@Column
            }
            PreferencesPanel(
                preferences = state.preferences,
                showApply = state.preferencesChanged && !state.isGenerating,
                actions = actions,
            )
            when (val planState = state.plan) {
                UiState.Loading, UiState.Empty -> PlanLoading()
                is UiState.Error -> SectionError(error = planState.error, onRetry = actions.onRetry, modifier = Modifier.padding(top = 8.dp))
                is UiState.Success -> {
                    PlanTabs(selected = state.selectedTab, onSelected = actions.onTabSelected)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag(ITINERARY_LIST_TAG),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    ) {
                        when (state.selectedTab) {
                            ItineraryTab.DAYS -> dayItems(planState.data, state.destination.name, actions)
                            ItineraryTab.PACKING -> packingItems(planState.data.packing, state.packedItems, actions.onPackedToggled)
                            ItineraryTab.TIPS -> tipItems(planState.data.tips, actions.onAskAssistant)
                        }
                        item(key = "disclaimer") { AiDisclaimer() }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferencesPanel(preferences: TripPreferences, showApply: Boolean, actions: ItineraryActions) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        ChipRow(label = stringResource(R.string.itinerary_pace)) {
            items(TripPace.entries) { pace ->
                FilterChip(
                    selected = pace == preferences.pace,
                    onClick = { actions.onPaceSelected(pace) },
                    label = { Text(stringResource(pace.labelRes())) },
                )
            }
        }
        ChipRow(label = stringResource(R.string.itinerary_interests), modifier = Modifier.testTag(ITINERARY_INTERESTS_TAG)) {
            items(TripInterest.entries) { interest ->
                FilterChip(
                    selected = interest in preferences.interests,
                    onClick = { actions.onInterestToggled(interest) },
                    label = { Text(interest.emoji() + " " + stringResource(interest.labelRes())) },
                )
            }
        }
        if (showApply) {
            Button(onClick = actions.onApplyPreferences, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.itinerary_apply_preferences))
            }
        }
    }
}

@Composable
private fun ChipRow(label: String, modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp).width(72.dp),
        )
        LazyRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 16.dp),
            content = content,
        )
    }
}

@Composable
private fun PlanTabs(selected: ItineraryTab, onSelected: (ItineraryTab) -> Unit) {
    PrimaryTabRow(selectedTabIndex = selected.ordinal) {
        ItineraryTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelected(tab) },
                text = { Text(tab.emoji() + " " + stringResource(tab.labelRes()), maxLines = 1) },
            )
        }
    }
}

private fun LazyListScope.dayItems(plan: TripPlan, cityName: String, actions: ItineraryActions) {
    itemsIndexed(plan.days, key = { _, day -> "day-${day.date}" }) { index, day ->
        DayCard(day = day, number = index + 1, cityName = cityName, actions = actions)
    }
}

@Composable
private fun DayCard(day: DayPlan, number: Int, cityName: String, actions: ItineraryActions) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.itinerary_day, number, Formatters.weekdayDayMonth(day.date)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(text = day.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            day.stops.forEach { stop -> StopRow(stop = stop, onClick = { openStop(stop, cityName, actions) }) }
            day.tip?.let { tip ->
                Text(text = "💡 $tip", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val distance = day.straightLineMeters
            if (distance >= MIN_DISTANCE_TO_SHOW_METERS) {
                Text(
                    text = stringResource(R.string.itinerary_distance, Formatters.distance(distance)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                walkingRouteUrl(day, cityName)?.let { url ->
                    FilledTonalButton(onClick = { actions.onOpenLink(url) }) { Text("🚶 " + stringResource(R.string.itinerary_walk)) }
                }
                OutlinedButton(onClick = { actions.onAddToCalendar(day, number) }) {
                    Text("📅 " + stringResource(R.string.itinerary_add_to_calendar))
                }
            }
        }
    }
}

@Composable
private fun StopRow(stop: PlanStop, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(text = stop.dayPart.emoji(), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            val duration = stop.durationMinutes?.let { minutes ->
                " · " + stringResource(R.string.itinerary_duration, Formatters.duration(Duration.ofMinutes(minutes.toLong())))
            }.orEmpty()
            Text(
                text = stringResource(stop.dayPart.labelRes()) + duration,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = stop.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (stop.activity.isNotBlank()) Text(text = stop.activity, style = MaterialTheme.typography.bodyMedium)
            if (stop.target == null) {
                Text(
                    text = "✨ " + stringResource(R.string.itinerary_suggested_by_ai),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

/** Luoghi ed eventi dell'app aprono la loro scheda; gli altri, consigliati dall'IA, la ricerca su Google Maps. */
private fun openStop(stop: PlanStop, cityName: String, actions: ItineraryActions) {
    when (val target = stop.target) {
        is StopTarget.Place -> actions.onOpenPlace(target.poi)
        is StopTarget.Event -> target.event.toPointOfInterest()?.let(actions.onOpenPlace)
            ?: actions.onOpenLink(googleMapsSearchUrl("${stop.name}, $cityName"))
        null -> actions.onOpenLink(googleMapsSearchUrl("${stop.name}, $cityName"))
    }
}

/** Giro a piedi della giornata su Google Maps: coordinate per i luoghi noti, nome e città per gli altri. */
fun walkingRouteUrl(day: DayPlan, cityName: String): String? =
    googleMapsWalkingRouteUrl(day.stops.map { stop -> stop.location?.let(RouteStop::At) ?: RouteStop.Named("${stop.name}, $cityName") })

private fun LazyListScope.packingItems(groups: List<PackingGroup>, packed: Set<String>, onToggle: (String) -> Unit) {
    val keys = groups.flatMap { group -> group.items.map { packingItemKey(group, it) } }
    item(key = "packed-count") {
        Text(
            text = stringResource(R.string.itinerary_packed_count, keys.count { it in packed }, keys.size),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
    items(groups, key = { "packing-${it.category}" }) { group ->
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = group.category,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                group.items.forEach { item ->
                    val key = packingItemKey(group, item)
                    val checked = key in packed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(key) })
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                        Text(text = item, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.tipItems(tips: List<String>, onAskAssistant: () -> Unit) {
    items(tips, key = { "tip-$it" }) { tip ->
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(text = "💡", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(12.dp))
            Text(text = tip, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
    item(key = "ask") {
        OutlinedButton(onClick = onAskAssistant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("💬 " + stringResource(R.string.itinerary_ask))
        }
    }
}

@Composable
private fun PlanLoading() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(40.dp))
        Text(
            text = stringResource(R.string.itinerary_loading),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.itinerary_loading_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AiDisclaimer(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.assistant_ai_disclaimer),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
fun AssistantUnavailable(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Text(
            text = stringResource(R.string.assistant_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}

private fun TripPace.labelRes(): Int = when (this) {
    TripPace.RELAXED -> R.string.pace_relaxed
    TripPace.BALANCED -> R.string.pace_balanced
    TripPace.INTENSE -> R.string.pace_intense
}

private fun TripInterest.labelRes(): Int = when (this) {
    TripInterest.ART -> R.string.interest_art
    TripInterest.HISTORY -> R.string.interest_history
    TripInterest.FOOD -> R.string.interest_food
    TripInterest.NATURE -> R.string.interest_nature
    TripInterest.SHOPPING -> R.string.interest_shopping
    TripInterest.NIGHTLIFE -> R.string.interest_nightlife
    TripInterest.FAMILY -> R.string.interest_family
}

private fun TripInterest.emoji(): String = when (this) {
    TripInterest.ART -> "🖼️"
    TripInterest.HISTORY -> "🏛️"
    TripInterest.FOOD -> "🍝"
    TripInterest.NATURE -> "🌳"
    TripInterest.SHOPPING -> "🛍️"
    TripInterest.NIGHTLIFE -> "🌃"
    TripInterest.FAMILY -> "👨‍👩‍👧"
}

private fun ItineraryTab.labelRes(): Int = when (this) {
    ItineraryTab.DAYS -> R.string.itinerary_tab_days
    ItineraryTab.PACKING -> R.string.itinerary_tab_packing
    ItineraryTab.TIPS -> R.string.itinerary_tab_tips
}

private fun ItineraryTab.emoji(): String = when (this) {
    ItineraryTab.DAYS -> "📅"
    ItineraryTab.PACKING -> "🧳"
    ItineraryTab.TIPS -> "💡"
}

/** Sotto i 100 m la distanza non dice nulla di utile. */
private const val MIN_DISTANCE_TO_SHOW_METERS = 100.0

// ---- Anteprime ---------------------------------------------------------------------------------

@Preview(name = "Itinerario", showBackground = true, heightDp = 900)
@Composable
private fun ItineraryPreview() {
    PartiMoTheme { ItineraryScreen(PreviewData.itineraryState(), ItineraryActions()) }
}

@Preview(name = "Itinerario · valigia, tema scuro", showBackground = true, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ItineraryPackingPreview() {
    PartiMoTheme(darkTheme = true) {
        ItineraryScreen(PreviewData.itineraryState().copy(selectedTab = ItineraryTab.PACKING), ItineraryActions())
    }
}
