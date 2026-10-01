package com.partimo.app.ui.dashboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.partimo.app.R
import com.partimo.app.navigation.TripArgs
import com.partimo.app.ui.common.Formatters
import com.partimo.app.ui.common.PeriodChips
import com.partimo.app.ui.common.flagEmoji
import com.partimo.app.ui.common.toFavorite
import com.partimo.app.ui.dashboard.components.FlightsSection
import com.partimo.app.ui.dashboard.components.HighlightsSection
import com.partimo.app.ui.dashboard.components.LodgingsSection
import com.partimo.app.ui.dashboard.components.RestaurantsSection
import com.partimo.app.ui.dashboard.components.StaysSection
import com.partimo.app.ui.dashboard.components.TransitSection
import com.partimo.app.ui.dashboard.components.TripEventsSection
import com.partimo.app.ui.dashboard.components.lodgingMapsUrl
import com.partimo.app.ui.place.ExternalLinks
import com.partimo.app.ui.place.googleMapsTransitUrl
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.TripContext
import com.partimo.domain.model.deal.PriceChange
import com.partimo.domain.model.poi.PointOfInterest
import com.partimo.domain.model.saved.Favorite
import java.time.Instant
import java.time.ZoneId

/** Tag della lista della sezione mostrata, usato dai test UI per lo scroll. */
const val DASHBOARD_LIST_TAG = "dashboard_list"

/** Azioni della dashboard (predefinite vuote per anteprime e test). */
data class DashboardActions(
    val onBack: () -> Unit = {},
    val onPeriodSelected: (TravelPeriod) -> Unit = {},
    val onSectionSelected: (DashboardSection) -> Unit = {},
    val onPhotoSpotsOnlyChanged: (Boolean) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: (DashboardSection) -> Unit = {},
    val onToggleAlert: () -> Unit = {},
    val onChooseDeparture: () -> Unit = {},
    val onRefreshSummaryShown: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onOpenNotificationSettings: () -> Unit = {},
    /** Tocco su un luogo o un evento da vedere: apre la sua scheda (descrizione, storia, "Naviga"). */
    val onOpenPlace: (PointOfInterest) -> Unit = {},
    /** "Riprova" della sezione eventi. */
    val onRetryEvents: () -> Unit = {},
    /** Collegamento: siti di voli e alloggi con le date del viaggio (nel browser interno), Google Maps, fonti dei dati. */
    val onOpenLink: (String) -> Unit = {},
    /** Itinerario giorno per giorno proposto dall'IA per il viaggio mostrato. */
    val onOpenItinerary: () -> Unit = {},
    /** "Chiedi a PartiMo": domande all'assistente sul viaggio mostrato. */
    val onOpenAssistant: () -> Unit = {},
    /** Guida del viaggio: meteo per le date, paese, valuta, emergenze, Wikivoyage. */
    val onOpenGuide: () -> Unit = {},
    /** Segnalibro: salva il viaggio o lo toglie dai salvati. */
    val onToggleTripSaved: () -> Unit = {},
    /** Stella su un luogo, evento, ristorante o alloggio. */
    val onToggleFavorite: (Favorite) -> Unit = {},
    /** Elenco dei preferiti del viaggio. */
    val onOpenFavorites: () -> Unit = {},
    /** Mappa del viaggio: luoghi, eventi, ristoranti e alloggi. */
    val onOpenMap: () -> Unit = {},
    /** Traduttore con la lingua del posto, anche offline. */
    val onOpenTranslator: () -> Unit = {},
)

/**
 * Collega il ViewModel alla UI. Prima di attivare un avviso chiede, se serve (Android 13+),
 * il permesso di mostrare notifiche.
 */
@Composable
fun TripDashboardRoute(
    viewModel: TripDashboardViewModel,
    onBack: () -> Unit,
    onChooseDeparture: () -> Unit,
    onOpenPlace: (PointOfInterest, TripArgs) -> Unit,
    modifier: Modifier = Modifier,
    onOpenItinerary: (TripArgs) -> Unit = {},
    onOpenAssistant: (TripArgs) -> Unit = {},
    onOpenGuide: (TripArgs) -> Unit = {},
    onOpenFavorites: (TripArgs) -> Unit = {},
    onOpenMap: (TripArgs) -> Unit = {},
    onOpenTranslator: (TripArgs) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val noAppForLink = stringResource(R.string.place_no_browser)
    val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onAlertToggled(notificationsAllowed = granted)
    }
    TripDashboardScreen(
        state = state,
        actions = DashboardActions(
            onBack = onBack,
            onPeriodSelected = viewModel::onPeriodSelected,
            onSectionSelected = viewModel::onSectionSelected,
            onPhotoSpotsOnlyChanged = viewModel::onPhotoSpotsOnlyChanged,
            onRefresh = viewModel::refresh,
            onRetry = viewModel::retry,
            onRetryEvents = viewModel::retryEvents,
            onToggleAlert = {
                val enabling = !state.alertEnabled && state.trip.departure != null
                if (enabling && needsNotificationPermission(context)) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.onAlertToggled(notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled())
                }
            },
            onChooseDeparture = onChooseDeparture,
            onRefreshSummaryShown = viewModel::onRefreshSummaryShown,
            onMessageShown = viewModel::onMessageShown,
            onOpenNotificationSettings = { context.startActivity(notificationSettingsIntent(context)) },
            onOpenPlace = { poi -> onOpenPlace(poi, TripArgs.from(state.trip, state.period)) },
            onOpenLink = { url ->
                if (!ExternalLinks.openLink(context, url, toolbarColor)) Toast.makeText(context, noAppForLink, Toast.LENGTH_LONG).show()
            },
            onOpenItinerary = { onOpenItinerary(TripArgs.from(state.trip, state.period)) },
            onOpenAssistant = { onOpenAssistant(TripArgs.from(state.trip, state.period)) },
            onOpenGuide = { onOpenGuide(TripArgs.from(state.trip, state.period)) },
            onToggleTripSaved = viewModel::onToggleTripSaved,
            onToggleFavorite = viewModel::onToggleFavorite,
            onOpenFavorites = { onOpenFavorites(TripArgs.from(state.trip, state.period)) },
            onOpenMap = { onOpenMap(TripArgs.from(state.trip, state.period)) },
            onOpenTranslator = { onOpenTranslator(TripArgs.from(state.trip, state.period)) },
        ),
        modifier = modifier,
    )
}

/** Impostazioni delle notifiche di PartiMo, per riattivarle dopo averle negate. */
private fun notificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

/**
 * Dashboard di un viaggio: in alto il periodo, in basso un pulsante per ogni sezione (voli, alloggi,
 * cosa vedere, trasporti, ristoranti) per passare dall'una all'altra con un tocco. "Aggiorna" cerca
 * le offerte last minute e la campanella attiva gli avvisi sui prezzi. È stateless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDashboardScreen(
    state: TripDashboardUiState,
    actions: DashboardActions,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val currentActions by rememberUpdatedState(actions)
    val summaryText = state.refreshSummary?.let { refreshSummaryText(it) }
    val messageText = state.message?.let { messageText(it, state.trip.destination.name) }
    val settingsLabel = stringResource(R.string.alert_open_settings)
    LaunchedEffect(state.refreshSummary) {
        if (summaryText != null) {
            snackbarHostState.showSnackbar(summaryText)
            currentActions.onRefreshSummaryShown()
        }
    }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            // Con le notifiche disattivate la snackbar offre la scorciatoia alle impostazioni.
            val needsSettings = state.message == DashboardMessage.ALERT_ENABLED_WITHOUT_NOTIFICATIONS
            val result = snackbarHostState.showSnackbar(
                message = messageText,
                actionLabel = settingsLabel.takeIf { needsSettings },
                duration = if (needsSettings) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) currentActions.onOpenNotificationSettings()
            currentActions.onMessageShown()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { DashboardTopBar(state = state, actions = actions) },
        bottomBar = { SectionNavigationBar(selected = state.selectedSection, onSelected = actions.onSectionSelected) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            PeriodChips(
                periods = state.periods,
                selected = state.period,
                today = state.today,
                onSelected = actions.onPeriodSelected,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            TripToolChips(
                assistantAvailable = state.assistantAvailable,
                favoriteCount = state.savedTrip?.favorites?.size ?: 0,
                onOpenGuide = actions.onOpenGuide,
                onOpenItinerary = actions.onOpenItinerary,
                onOpenAssistant = actions.onOpenAssistant,
                onOpenFavorites = actions.onOpenFavorites,
                onOpenMap = actions.onOpenMap,
                onOpenTranslator = actions.onOpenTranslator,
            )
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = actions.onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                // Ogni sezione ha la sua lista (e la sua posizione di scroll); il cambio è animato.
                Crossfade(targetState = state.selectedSection, label = "section") { section ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag(DASHBOARD_LIST_TAG),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        sectionContent(section, state, actions)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.sectionContent(section: DashboardSection, state: TripDashboardUiState, actions: DashboardActions) {
    when (section) {
        DashboardSection.FLIGHTS -> {
            if (state.isDemoMode) item(key = "demo") { DemoBanner() }
            item(key = "flights") {
                if (state.trip.departure == null) {
                    DeparturePromptCard(onChoose = actions.onChooseDeparture)
                } else {
                    FlightsSection(
                        state = state.flights,
                        trip = state.trip,
                        onRetry = { actions.onRetry(DashboardSection.FLIGHTS) },
                        onChangeDeparture = actions.onChooseDeparture,
                        onOpenLink = actions.onOpenLink,
                    )
                }
            }
            state.pricesUpdatedAt?.let { updatedAt -> item(key = "updated") { PricesUpdatedNote(updatedAt) } }
        }
        DashboardSection.STAYS -> if (state.stayOffersAvailable) {
            item(key = "stays") {
                StaysSection(
                    state = state.stays,
                    trip = state.trip,
                    onRetry = { actions.onRetry(DashboardSection.STAYS) },
                    onOpenLink = actions.onOpenLink,
                )
            }
            state.pricesUpdatedAt?.let { updatedAt -> item(key = "updated") { PricesUpdatedNote(updatedAt) } }
        } else {
            item(key = "lodgings") {
                LodgingsSection(
                    state = state.lodgings,
                    trip = state.trip,
                    onRetry = { actions.onRetry(DashboardSection.STAYS) },
                    onOpenLink = actions.onOpenLink,
                    favoriteKeys = state.favoriteKeys,
                    onToggleFavorite = { lodging -> actions.onToggleFavorite(lodging.toFavorite(lodgingMapsUrl(lodging, state.trip.destination.name))) },
                )
            }
        }
        DashboardSection.HIGHLIGHTS -> {
            item(key = "events") {
                TripEventsSection(
                    state = state.events,
                    trip = state.trip,
                    onRetry = actions.onRetryEvents,
                    onOpenLink = actions.onOpenLink,
                    onEventClick = actions.onOpenPlace,
                    favoriteKeys = state.favoriteKeys,
                    onToggleFavorite = { event -> actions.onToggleFavorite(event.toFavorite()) },
                )
            }
            item(key = "highlights") {
                HighlightsSection(
                    state = state.highlights,
                    destinationName = state.trip.destination.name,
                    photoSpotsOnly = state.photoSpotsOnly,
                    onPhotoSpotsOnlyChanged = actions.onPhotoSpotsOnlyChanged,
                    onRetry = { actions.onRetry(DashboardSection.HIGHLIGHTS) },
                    onPlaceClick = actions.onOpenPlace,
                    favoriteKeys = state.favoriteKeys,
                    onToggleFavorite = { poi -> actions.onToggleFavorite(poi.toFavorite()) },
                )
            }
        }
        DashboardSection.TRANSIT -> item(key = "transit") {
            val destination = state.trip.destination
            TransitSection(
                state = state.transit,
                hubName = destination.arrivalHubName,
                timeZone = destination.timeZone,
                onRetry = { actions.onRetry(DashboardSection.TRANSIT) },
                mapsUrl = googleMapsTransitUrl(origin = destination.arrivalHub, destination = destination.center),
                onOpenLink = actions.onOpenLink,
            )
        }
        DashboardSection.RESTAURANTS -> item(key = "restaurants") {
            RestaurantsSection(
                state = state.restaurants,
                onRetry = { actions.onRetry(DashboardSection.RESTAURANTS) },
                ratingsAvailable = state.restaurantRatingsAvailable,
                center = state.trip.destination.center,
                onOpenLink = actions.onOpenLink,
                hoursWeekOf = state.trip.departureDate,
                nowAtDestination = state.nowAtDestination,
                favoriteKeys = state.favoriteKeys,
                onToggleFavorite = { restaurant -> actions.onToggleFavorite(restaurant.toFavorite()) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBar(state: TripDashboardUiState, actions: DashboardActions) {
    val trip = state.trip
    val dates = Formatters.dateRange(trip.departureDate, trip.returnDate)
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = actions.onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        },
        title = {
            Column {
                Text(
                    text = flagEmoji(trip.destination.countryCode) + " " + trip.destination.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = trip.originIata
                        ?.let { stringResource(R.string.trip_summary, it, trip.destination.airportIata, dates) }
                        ?: stringResource(R.string.trip_summary_no_departure, trip.destination.airportIata, dates),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = {
            if (state.favoritesEnabled) {
                val saved = state.savedTrip != null
                IconButton(onClick = actions.onToggleTripSaved) {
                    Icon(
                        imageVector = if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(if (saved) R.string.trip_unsave else R.string.trip_save),
                        tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = actions.onToggleAlert) {
                Icon(
                    imageVector = if (state.alertEnabled) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                    contentDescription = stringResource(if (state.alertEnabled) R.string.action_alert_disable else R.string.action_alert_enable),
                    tint = if (state.alertEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RefreshButton(isRefreshing = state.isRefreshing, onRefresh = actions.onRefresh)
            Spacer(Modifier.width(8.dp))
        },
    )
}

/** Pulsante "Aggiorna" ben visibile: ricarica le offerte ignorando la cache (offerte last minute). */
@Composable
private fun RefreshButton(isRefreshing: Boolean, onRefresh: () -> Unit) {
    FilledTonalButton(
        onClick = onRefresh,
        enabled = !isRefreshing,
        contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.action_refresh))
    }
}

/** Barra dei pulsanti delle sezioni: un tocco per passare da voli ad alloggi, luoghi, trasporti e ristoranti. */
@Composable
private fun SectionNavigationBar(selected: DashboardSection, onSelected: (DashboardSection) -> Unit) {
    NavigationBar {
        DashboardSection.entries.forEach { section ->
            NavigationBarItem(
                selected = section == selected,
                onClick = { onSelected(section) },
                icon = { Text(section.emoji(), style = MaterialTheme.typography.titleLarge) },
                label = { Text(stringResource(section.labelRes()), maxLines = 1) },
            )
        }
    }
}

fun DashboardSection.labelRes(): Int = when (this) {
    DashboardSection.FLIGHTS -> R.string.nav_flights
    DashboardSection.STAYS -> R.string.nav_stays
    DashboardSection.HIGHLIGHTS -> R.string.nav_highlights
    DashboardSection.TRANSIT -> R.string.nav_transit
    DashboardSection.RESTAURANTS -> R.string.nav_restaurants
}

fun DashboardSection.emoji(): String = when (this) {
    DashboardSection.FLIGHTS -> "✈️"
    DashboardSection.STAYS -> "🏨"
    DashboardSection.HIGHLIGHTS -> "📸"
    DashboardSection.TRANSIT -> "🚇"
    DashboardSection.RESTAURANTS -> "🍝"
}

/** Tag della riga degli strumenti del viaggio, scorrevole in orizzontale. */
const val TRIP_TOOLS_TAG = "trip_tools"

/**
 * Strumenti del viaggio mostrato: i preferiti (se ce ne sono), la mappa, la guida e il traduttore
 * (sempre) e, con la chiave Gemini, l'itinerario e le domande all'assistente con l'IA.
 */
@Composable
private fun TripToolChips(
    assistantAvailable: Boolean,
    favoriteCount: Int,
    onOpenGuide: () -> Unit,
    onOpenItinerary: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenTranslator: () -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp).testTag(TRIP_TOOLS_TAG),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (favoriteCount > 0) {
            item(key = "favorites") {
                AssistChip(onClick = onOpenFavorites, label = { Text(stringResource(R.string.favorites_chip, favoriteCount)) }, leadingIcon = { Text("⭐") })
            }
        }
        // Prima gli strumenti con l'IA, i più utili per organizzare il viaggio; la riga scorre.
        if (assistantAvailable) {
            item(key = "itinerary") {
                AssistChip(onClick = onOpenItinerary, label = { Text(stringResource(R.string.assistant_itinerary_chip)) }, leadingIcon = { Text("✨") })
            }
            item(key = "assistant") {
                AssistChip(onClick = onOpenAssistant, label = { Text(stringResource(R.string.assistant_chat_chip)) }, leadingIcon = { Text("💬") })
            }
        }
        item(key = "map") {
            AssistChip(onClick = onOpenMap, label = { Text(stringResource(R.string.map_chip)) }, leadingIcon = { Text("🗺️") })
        }
        item(key = "guide") {
            AssistChip(onClick = onOpenGuide, label = { Text(stringResource(R.string.guide_chip)) }, leadingIcon = { Text("📖") })
        }
        item(key = "translator") {
            AssistChip(onClick = onOpenTranslator, label = { Text(stringResource(R.string.translator_chip)) }, leadingIcon = { Text("🗣️") })
        }
    }
}

@Composable
private fun DeparturePromptCard(onChoose: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "✈️ " + stringResource(R.string.departure_missing_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(R.string.flights_need_departure),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Button(onClick = onChoose) { Text(stringResource(R.string.flights_choose_departure)) }
        }
    }
}

@Composable
private fun PricesUpdatedNote(updatedAt: Instant) {
    Text(
        text = stringResource(R.string.prices_updated_at, Formatters.time(updatedAt, ZoneId.systemDefault())),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun DemoBanner() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.demo_banner),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

/** Testo della snackbar dopo "Aggiorna": ribassi evidenziati, altrimenti conferma dell'aggiornamento. */
@Composable
private fun refreshSummaryText(summary: RefreshSummary): String {
    val parts = listOfNotNull(
        summary.flight?.takeIf { it.isDrop || it.isRise }?.let { priceChangeText(it, R.string.refresh_flight_drop, R.string.refresh_flight_rise) },
        summary.stay?.takeIf { it.isDrop || it.isRise }?.let { priceChangeText(it, R.string.refresh_stay_drop, R.string.refresh_stay_rise) },
    )
    return when {
        parts.isNotEmpty() -> parts.joinToString(" · ")
        summary.flight != null || summary.stay != null -> stringResource(R.string.refresh_no_changes)
        else -> stringResource(R.string.refresh_done)
    }
}

@Composable
private fun priceChangeText(change: PriceChange, dropRes: Int, riseRes: Int): String = stringResource(
    if (change.isDrop) dropRes else riseRes,
    Formatters.money(change.current),
    Formatters.moneyAmount(change.difference, change.current.currencyCode),
)

@Composable
private fun messageText(message: DashboardMessage, destinationName: String): String = when (message) {
    DashboardMessage.ALERT_ENABLED -> stringResource(R.string.alert_enabled, destinationName)
    DashboardMessage.ALERT_ENABLED_WITHOUT_NOTIFICATIONS -> stringResource(R.string.alert_enabled_without_notifications)
    DashboardMessage.ALERT_DISABLED -> stringResource(R.string.alert_disabled)
    DashboardMessage.ALERT_NEEDS_DEPARTURE -> stringResource(R.string.alert_needs_departure)
    DashboardMessage.TRIP_SAVED -> stringResource(R.string.trip_saved_message)
    DashboardMessage.TRIP_REMOVED -> stringResource(R.string.trip_removed_message)
}

// ---- Anteprime ---------------------------------------------------------------------------------

@Preview(name = "Dashboard · voli", showBackground = true, heightDp = 900)
@Composable
private fun TripDashboardFlightsPreview() {
    PartiMoTheme { TripDashboardScreen(PreviewData.loadedState(), DashboardActions()) }
}

@Preview(name = "Dashboard · da vedere, tema scuro", showBackground = true, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TripDashboardDarkPreview() {
    PartiMoTheme(darkTheme = true) {
        TripDashboardScreen(PreviewData.loadedState().copy(selectedSection = DashboardSection.HIGHLIGHTS), DashboardActions())
    }
}

@Preview(name = "Dashboard · alloggi reali senza chiavi", showBackground = true, heightDp = 900)
@Composable
private fun TripDashboardOpenDataPreview() {
    PartiMoTheme { TripDashboardScreen(PreviewData.openDataState().copy(selectedSection = DashboardSection.STAYS), DashboardActions()) }
}

@Preview(name = "Dashboard · senza partenza", showBackground = true, heightDp = 900)
@Composable
private fun TripDashboardNoDeparturePreview() {
    PartiMoTheme { TripDashboardScreen(PreviewData.noDepartureState(), DashboardActions()) }
}
