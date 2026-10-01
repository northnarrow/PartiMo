package com.partimo.app.ui.favorites

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.partimo.app.ui.common.toPointOfInterest
import com.partimo.app.ui.dashboard.PreviewData
import com.partimo.app.ui.dashboard.components.EmojiBox
import com.partimo.app.ui.place.ExternalLinks
import com.partimo.app.ui.place.RouteStop
import com.partimo.app.ui.place.googleMapsWalkingRouteUrl
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.model.poi.PointOfInterest
import com.partimo.domain.model.saved.Favorite
import com.partimo.domain.model.saved.FavoriteKind
import com.partimo.domain.service.RouteOrdering

/** Tag della lista dei preferiti, usato dai test UI per lo scroll. */
const val FAVORITES_LIST_TAG = "favorites_list"

data class FavoritesActions(
    val onBack: () -> Unit = {},
    val onOpenPlace: (PointOfInterest) -> Unit = {},
    val onOpenLink: (String) -> Unit = {},
    val onRemove: (Favorite) -> Unit = {},
    val onShare: (List<Favorite>) -> Unit = {},
    /** Mappa del viaggio con i soli preferiti. */
    val onOpenMap: () -> Unit = {},
)

@Composable
fun FavoritesRoute(
    viewModel: FavoritesViewModel,
    onBack: () -> Unit,
    onOpenPlace: (PointOfInterest) -> Unit,
    modifier: Modifier = Modifier,
    onOpenMap: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()
    val noBrowser = stringResource(R.string.place_no_browser)
    FavoritesScreen(
        state = state,
        actions = FavoritesActions(
            onBack = onBack,
            onOpenPlace = onOpenPlace,
            onOpenLink = { url ->
                if (!ExternalLinks.openLink(context, url, toolbarColor)) Toast.makeText(context, noBrowser, Toast.LENGTH_LONG).show()
            },
            onRemove = viewModel::onRemove,
            onShare = { favorites -> context.startActivity(shareIntent(context, state.destination.name, favorites)) },
            onOpenMap = onOpenMap,
        ),
        modifier = modifier,
    )
}

/**
 * Preferiti di un viaggio, divisi per tipo: ogni elemento riapre la sua scheda o Google Maps, la
 * stella lo toglie. In fondo la mappa dei preferiti, il giro a piedi tra loro e la condivisione dell'elenco.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FavoritesScreen(state: FavoritesUiState, actions: FavoritesActions, modifier: Modifier = Modifier) {
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
                            text = "⭐ " + stringResource(R.string.favorites_title, state.destination.name),
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
                actions = {
                    if (state.favorites.isNotEmpty()) {
                        IconButton(onClick = { actions.onShare(state.favorites) }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.favorites_share))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag(FAVORITES_LIST_TAG),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (state.loaded && state.favorites.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.favorites_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            FavoriteKind.entries.forEach { kind ->
                val group = state.favorites.filter { it.kind == kind }
                if (group.isNotEmpty()) {
                    item(key = "title-$kind") {
                        Text(
                            text = kind.emoji() + " " + stringResource(kind.titleRes()),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(group, key = { it.key }) { favorite -> FavoriteRow(favorite, actions) }
                }
            }
            val walkUrl = favoritesWalkingRouteUrl(state.favorites)
            if (state.favorites.any { it.location != null }) {
                item(key = "routes") {
                    FlowRow(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(onClick = actions.onOpenMap) { Text("🗺️ " + stringResource(R.string.favorites_map)) }
                        walkUrl?.let { url ->
                            FilledTonalButton(onClick = { actions.onOpenLink(url) }) { Text("🚶 " + stringResource(R.string.favorites_walk)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteRow(favorite: Favorite, actions: FavoritesActions) {
    val place = favorite.toPointOfInterest()
    val open: (() -> Unit)? = when {
        place != null -> { { actions.onOpenPlace(place) } }
        favorite.url != null -> { { actions.onOpenLink(favorite.url.orEmpty()) } }
        else -> null
    }
    ListItem(
        modifier = if (open != null) Modifier.clickable(onClick = open) else Modifier,
        headlineContent = { Text(favorite.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = (favorite.subtitle ?: favorite.description)?.let { text -> { Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        leadingContent = {
            if (favorite.photoUrl == null) {
                EmojiBox(emoji = favorite.kind.emoji(), size = 48.dp)
            } else {
                AsyncImage(
                    model = favorite.photoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FavoriteButton(isFavorite = true, name = favorite.name, onToggle = { actions.onRemove(favorite) })
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** Giro a piedi tra i preferiti con una posizione, dal primo salvato e poi sempre al più vicino. */
fun favoritesWalkingRouteUrl(favorites: List<Favorite>): String? {
    val located = favorites.mapNotNull { favorite -> favorite.location?.let { favorite to it } }
    if (located.size < 2) return null
    val ordered = RouteOrdering.nearestNeighbor(located) { it.second }
    return googleMapsWalkingRouteUrl(ordered.map { RouteStop.At(it.second) })
}

private fun shareIntent(context: Context, cityName: String, favorites: List<Favorite>): Intent {
    val text = buildString {
        appendLine("⭐ " + context.getString(R.string.favorites_share_title, cityName))
        FavoriteKind.entries.forEach { kind ->
            val group = favorites.filter { it.kind == kind }
            if (group.isNotEmpty()) {
                appendLine()
                appendLine(kind.emoji() + " " + context.getString(kind.titleRes()))
                group.forEach { favorite -> appendLine("• " + favorite.name + (favorite.url?.let { " — $it" } ?: "")) }
            }
        }
    }.trim()
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    return Intent.createChooser(send, context.getString(R.string.favorites_share))
}

fun FavoriteKind.emoji(): String = when (this) {
    FavoriteKind.PLACE -> "📸"
    FavoriteKind.EVENT -> "🎉"
    FavoriteKind.RESTAURANT -> "🍝"
    FavoriteKind.LODGING -> "🏨"
}

fun FavoriteKind.titleRes(): Int = when (this) {
    FavoriteKind.PLACE -> R.string.favorites_places
    FavoriteKind.EVENT -> R.string.favorites_events
    FavoriteKind.RESTAURANT -> R.string.favorites_restaurants
    FavoriteKind.LODGING -> R.string.favorites_lodgings
}

// ---- Anteprime -----------------------------------------------------------------------------------

@Preview(name = "Preferiti", showBackground = true, heightDp = 800)
@Composable
private fun FavoritesPreview() {
    PartiMoTheme { FavoritesScreen(PreviewData.favoritesState(), FavoritesActions()) }
}

@Preview(name = "Preferiti · tema scuro", showBackground = true, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FavoritesDarkPreview() {
    PartiMoTheme(darkTheme = true) { FavoritesScreen(PreviewData.favoritesState(), FavoritesActions()) }
}
