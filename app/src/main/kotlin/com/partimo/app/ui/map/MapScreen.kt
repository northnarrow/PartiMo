package com.partimo.app.ui.map

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.partimo.app.R
import com.partimo.app.ui.common.FavoriteButton
import com.partimo.app.ui.common.Formatters
import com.partimo.app.ui.dashboard.PreviewData
import com.partimo.app.ui.dashboard.components.EmojiBox
import com.partimo.app.ui.favorites.emoji
import com.partimo.app.ui.favorites.titleRes
import com.partimo.app.ui.place.ExternalLinks
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.poi.PointOfInterest
import com.partimo.domain.model.saved.FavoriteKind

/** Tag della riga dei filtri, scorrevole in orizzontale. */
const val MAP_FILTERS_TAG = "map_filters"

/** Tag della scheda del punto selezionato. */
const val MAP_SELECTED_CARD_TAG = "map_selected_card"

data class MapActions(
    val onBack: () -> Unit = {},
    val onKindToggled: (FavoriteKind) -> Unit = {},
    val onFavoritesOnlyChanged: (Boolean) -> Unit = {},
    /** Tocco su un punto della mappa; `null` chiude la scheda. */
    val onPointSelected: (String?) -> Unit = {},
    val onFitAll: () -> Unit = {},
    val onRetry: () -> Unit = {},
    /** Scheda di un luogo o di un evento (descrizione, storia, "Naviga"). */
    val onOpenPlace: (PointOfInterest) -> Unit = {},
    /** Pagina di un ristorante o di un alloggio, attribuzione dei dati. */
    val onOpenLink: (String) -> Unit = {},
    val onNavigate: (GeoPoint) -> Unit = {},
    val onToggleFavorite: (MapPoint) -> Unit = {},
)

/** Disegna la mappa: nell'app MapLibre, nei test e nelle anteprime lo schema dei punti. */
typealias MapRenderer = @Composable (content: MapContent, modifier: Modifier) -> Unit

@Composable
fun MapRoute(viewModel: MapViewModel, onBack: () -> Unit, onOpenPlace: (PointOfInterest) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()
    val noApp = stringResource(R.string.place_no_browser)
    MapScreen(
        state = state,
        actions = MapActions(
            onBack = onBack,
            onKindToggled = viewModel::onKindToggled,
            onFavoritesOnlyChanged = viewModel::onFavoritesOnlyChanged,
            onPointSelected = viewModel::onPointSelected,
            onFitAll = viewModel::onFitAll,
            onRetry = viewModel::retry,
            onOpenPlace = onOpenPlace,
            onOpenLink = { url -> if (!ExternalLinks.openLink(context, url, toolbarColor)) Toast.makeText(context, noApp, Toast.LENGTH_LONG).show() },
            onNavigate = { point ->
                if (!ExternalLinks.openNavigation(context, point, toolbarColor)) Toast.makeText(context, noApp, Toast.LENGTH_LONG).show()
            },
            onToggleFavorite = viewModel::onToggleFavorite,
        ),
        modifier = modifier,
    )
}

/**
 * Mappa del viaggio: luoghi da vedere, eventi, ristoranti e alloggi come punti colorati, con i
 * filtri per tipo (che fanno anche da legenda) e "solo preferiti". Il tocco su un punto apre in
 * basso la sua scheda: dettagli, pagina, "Naviga" e stella dei preferiti.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    state: MapUiState,
    actions: MapActions,
    modifier: Modifier = Modifier,
    renderer: MapRenderer = { content, mapModifier -> TripMap(content, mapModifier) },
) {
    val selected = state.selected
    // "Indietro" chiude prima la scheda del punto, poi la mappa.
    BackHandler(enabled = selected != null) { actions.onPointSelected(null) }
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
                            text = "🗺️ " + stringResource(R.string.map_title, state.destination.name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = Formatters.dateRange(state.from, state.to),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            MapFilters(state = state, actions = actions)
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (state.hasErrors) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.map_partial_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = actions.onRetry) { Text(stringResource(R.string.action_retry)) }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val visiblePoints = state.visiblePoints
                renderer(
                    MapContent(
                        center = state.destination.center,
                        points = visiblePoints,
                        favoriteKeys = state.favoriteKeys,
                        selectedKey = selected?.key,
                        fitRequest = state.fitRequest,
                        onPointSelected = actions.onPointSelected,
                        onOpenLink = actions.onOpenLink,
                    ),
                    Modifier.fillMaxSize(),
                )
                if (!state.isLoading && visiblePoints.isEmpty()) {
                    Text(
                        text = stringResource(if (state.favoritesOnly) R.string.map_no_favorites else R.string.map_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                    )
                }
                SmallFloatingActionButton(
                    onClick = actions.onFitAll,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                ) {
                    Icon(Icons.Default.Place, contentDescription = stringResource(R.string.map_fit_all))
                }
            }
            selected?.let { point ->
                SelectedPointCard(point = point, isFavorite = point.key in state.favoriteKeys, favoritesEnabled = state.favoritesEnabled, actions = actions)
            }
        }
    }
}

@Composable
private fun MapFilters(state: MapUiState, actions: MapActions) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag(MAP_FILTERS_TAG),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.favoritesEnabled && (state.favorites.isNotEmpty() || state.favoritesOnly)) {
            item(key = "favorites") {
                FilterChip(
                    selected = state.favoritesOnly,
                    onClick = { actions.onFavoritesOnlyChanged(!state.favoritesOnly) },
                    label = { Text(stringResource(R.string.map_favorites_only)) },
                    leadingIcon = { Text("⭐") },
                )
            }
        }
        FavoriteKind.entries.forEach { kind ->
            item(key = kind.name) {
                val count = state.countOf(kind)
                FilterChip(
                    selected = kind !in state.hiddenKinds,
                    onClick = { actions.onKindToggled(kind) },
                    enabled = count > 0,
                    label = { Text(stringResource(kind.titleRes()) + " · $count") },
                    leadingIcon = { Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(kind.pinColor())) },
                )
            }
        }
    }
}

@Composable
private fun SelectedPointCard(point: MapPoint, isFavorite: Boolean, favoritesEnabled: Boolean, actions: MapActions) {
    val item = point.item
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp).testTag(MAP_SELECTED_CARD_TAG),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (item.photoUrl == null) {
                    EmojiBox(emoji = point.kind.emoji(), size = 56.dp)
                } else {
                    AsyncImage(
                        model = item.photoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = point.kind.emoji() + " " + stringResource(point.kind.titleRes()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    (item.subtitle ?: item.description)?.let { text ->
                        Text(text = text, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (favoritesEnabled) {
                    FavoriteButton(isFavorite = isFavorite, name = item.name, onToggle = { actions.onToggleFavorite(point) })
                }
                IconButton(onClick = { actions.onPointSelected(null) }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.map_close_point))
                }
            }
            Row(
                modifier = Modifier.padding(top = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val place = point.place
                val url = item.url
                if (place != null) {
                    FilledTonalButton(onClick = { actions.onOpenPlace(place) }) { Text(stringResource(R.string.map_open_details)) }
                } else if (url != null) {
                    FilledTonalButton(onClick = { actions.onOpenLink(url) }) { Text(stringResource(R.string.map_open_page)) }
                }
                Button(onClick = { actions.onNavigate(point.location) }) { Text("🧭 " + stringResource(R.string.place_navigate)) }
            }
        }
    }
}

// ---- Anteprime -----------------------------------------------------------------------------------

@Preview(name = "Mappa", showBackground = true, heightDp = 800)
@Composable
private fun MapPreview() {
    PartiMoTheme { MapScreen(PreviewData.mapState(), MapActions()) }
}

@Preview(name = "Mappa · punto selezionato · tema scuro", showBackground = true, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MapSelectedDarkPreview() {
    PartiMoTheme(darkTheme = true) { MapScreen(PreviewData.mapState(selectFirst = true), MapActions()) }
}
