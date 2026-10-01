package com.partimo.app.ui.place

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.partimo.app.R
import com.partimo.app.ui.common.FavoriteButton
import com.partimo.app.ui.common.SectionError
import com.partimo.app.ui.common.SectionLoading
import com.partimo.app.ui.common.TravelLinks
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.common.emoji
import com.partimo.app.ui.common.labelRes
import com.partimo.app.ui.dashboard.PreviewData
import com.partimo.app.ui.dashboard.components.PoiTags
import com.partimo.app.ui.dashboard.components.poiRatingText
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.model.poi.HistoryChapter
import com.partimo.domain.model.poi.ImageCredit
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.poi.PoiDetails
import com.partimo.domain.model.poi.PointOfInterest
import java.util.Locale

/** Tag della lista della scheda, usato dai test UI per lo scroll. */
const val PLACE_DETAIL_LIST_TAG = "place_detail_list"

/** Lingua dei testi dell'app: se la voce è in un'altra lingua la scheda lo segnala. */
private const val APP_LANGUAGE = "it"

/** Azioni della scheda di un luogo (predefinite vuote per anteprime e test). */
data class PlaceDetailActions(
    val onBack: () -> Unit = {},
    /** "Naviga": indicazioni di Google Maps fino al luogo. */
    val onNavigate: () -> Unit = {},
    val onOpenLink: (String) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onToggleFavorite: () -> Unit = {},
)

/** Collega il ViewModel alla scheda e apre Google Maps o il browser interno per le azioni esterne. */
@Composable
fun PlaceDetailRoute(viewModel: PlaceDetailViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()
    val noMapsApp = stringResource(R.string.place_no_maps_app)
    val noBrowser = stringResource(R.string.place_no_browser)
    PlaceDetailScreen(
        state = state,
        actions = PlaceDetailActions(
            onBack = onBack,
            onNavigate = {
                if (!ExternalLinks.openNavigation(context, state.poi.location, toolbarColor)) {
                    Toast.makeText(context, noMapsApp, Toast.LENGTH_LONG).show()
                }
            },
            onOpenLink = { url ->
                if (!ExternalLinks.openLink(context, url, toolbarColor)) Toast.makeText(context, noBrowser, Toast.LENGTH_LONG).show()
            },
            onRetry = viewModel::retry,
            onToggleFavorite = viewModel::onToggleFavorite,
        ),
        modifier = modifier,
    )
}

/**
 * Scheda di un luogo da vedere: foto reale, breve descrizione e storia (da Wikipedia) e, sempre
 * visibile in basso, il pulsante "Naviga" che apre Google Maps con il percorso fino al luogo.
 * È stateless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailScreen(state: PlaceDetailUiState, actions: PlaceDetailActions, modifier: Modifier = Modifier) {
    val poi = state.poi
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(poi.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    state.isFavorite?.let { favorite -> FavoriteButton(isFavorite = favorite, name = poi.name, onToggle = actions.onToggleFavorite) }
                },
            )
        },
        bottomBar = { NavigateBar(placeName = poi.name, onNavigate = actions.onNavigate) },
    ) { innerPadding ->
        val details = (state.details as? UiState.Success)?.data
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag(PLACE_DETAIL_LIST_TAG),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "photo") { PlacePhoto(imageUrl = state.imageUrl, poi = poi) }
            item(key = "header") { PlaceHeader(poi) }
            if (poi.category.sellsTickets()) {
                item(key = "tickets") {
                    // Tiqets apre i biglietti del luogo se li vende, altrimenti la ricerca.
                    FilledTonalButton(
                        onClick = { actions.onOpenLink(TravelLinks.tiqets(poi.name)) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text("🎟️ " + stringResource(R.string.place_tickets))
                        Text(text = " ↗", modifier = Modifier.clearAndSetSemantics {})
                    }
                }
            }
            item(key = "description") { DescriptionSection(state, onRetry = actions.onRetry) }
            if (details != null) {
                item(key = "history") { HistorySection(details.history) }
                item(key = "sources") { SourcesSection(details, onOpenLink = actions.onOpenLink) }
            }
        }
    }
}

@Composable
private fun PlacePhoto(imageUrl: String?, poi: PointOfInterest) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl == null) {
            Text(text = poi.category.emoji(), style = MaterialTheme.typography.displayMedium)
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = poi.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PlaceHeader(poi: PointOfInterest) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = poi.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        val category = poi.category.emoji() + " " + stringResource(poi.category.labelRes())
        Text(
            text = listOfNotNull(category, poiRatingText(poi)).joinToString("  ·  "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PoiTags(poi.tags)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun BodyText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun MutedText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp),
    )
}

/** Breve descrizione: durante il caricamento un segnaposto, in caso di errore la descrizione del provider e "Riprova". */
@Composable
private fun DescriptionSection(state: PlaceDetailUiState, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.place_description))
        when (val details = state.details) {
            UiState.Loading -> SectionLoading()
            UiState.Empty -> BodyText(state.poi.description ?: stringResource(R.string.place_no_description))
            is UiState.Error -> {
                state.poi.description?.let { BodyText(it) }
                SectionError(error = details.error, onRetry = onRetry)
            }
            is UiState.Success -> {
                BodyText(details.data.summary ?: stringResource(R.string.place_no_description))
                details.data.language?.takeIf { it != APP_LANGUAGE }?.let { language ->
                    MutedText(stringResource(R.string.place_text_language, languageName(language)))
                }
            }
        }
    }
}

/** Storia del luogo: con più capitoli una linea del tempo, altrimenti un unico racconto. */
@Composable
private fun HistorySection(chapters: List<HistoryChapter>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.place_history))
        when {
            chapters.isEmpty() -> MutedText(stringResource(R.string.place_no_history))
            chapters.size == 1 && chapters.single().title == null -> BodyText(chapters.single().text)
            else -> chapters.forEachIndexed { index, chapter -> TimelineChapter(chapter, isLast = index == chapters.lastIndex) }
        }
    }
}

@Composable
private fun TimelineChapter(chapter: HistoryChapter, isLast: Boolean) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 16.dp)) {
        Box(modifier = Modifier.width(16.dp).fillMaxHeight()) {
            if (!isLast) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(lineColor),
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            chapter.title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(text = chapter.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Fonti: collegamento alla voce completa e crediti di testi e foto (licenze Creative Commons). */
@Composable
private fun SourcesSection(details: PoiDetails, onOpenLink: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        details.sourceUrl?.let { url ->
            OutlinedButton(onClick = { onOpenLink(url) }, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.place_read_more))
            }
            MutedText(stringResource(R.string.place_text_credit))
        }
        details.imageCredit?.let { credit -> PhotoCredit(credit, onOpenLink) }
    }
}

@Composable
private fun PhotoCredit(credit: ImageCredit, onOpenLink: (String) -> Unit) {
    val label = listOfNotNull(credit.author, credit.license).joinToString(" · ")
    if (label.isEmpty()) return
    val source = credit.sourceUrl
    MutedText(
        text = stringResource(R.string.place_photo_credit, label),
        modifier = if (source != null) Modifier.clickable { onOpenLink(source) } else Modifier,
    )
}

/** Barra in basso sempre visibile: "Naviga" apre Google Maps con il percorso fino al luogo. */
@Composable
private fun NavigateBar(placeName: String, onNavigate: () -> Unit) {
    val description = stringResource(R.string.place_navigate_description, placeName)
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = onNavigate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .semantics { contentDescription = description },
            ) {
                Icon(Icons.Default.Place, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.place_navigate), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = stringResource(R.string.place_navigate_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Nome della lingua in italiano, es. "en" → "inglese". */
private fun languageName(language: String): String =
    Locale.forLanguageTag(language).getDisplayLanguage(Locale.ITALIAN).ifBlank { language }

// ---- Anteprime ---------------------------------------------------------------------------------

@Preview(name = "Scheda del luogo", showBackground = true, heightDp = 1100)
@Composable
private fun PlaceDetailPreview() {
    PartiMoTheme { PlaceDetailScreen(PreviewData.placeDetailState(), PlaceDetailActions()) }
}

@Preview(name = "Scheda del luogo · caricamento, tema scuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlaceDetailLoadingPreview() {
    PartiMoTheme(darkTheme = true) {
        PlaceDetailScreen(PreviewData.placeDetailState().copy(details = UiState.Loading), PlaceDetailActions())
    }
}

/** Luoghi che di solito hanno un biglietto d'ingresso (musei, monumenti, attrazioni, chiese visitabili). */
private fun PoiCategory.sellsTickets(): Boolean =
    this == PoiCategory.MUSEUM || this == PoiCategory.MONUMENT || this == PoiCategory.ATTRACTION || this == PoiCategory.RELIGIOUS_SITE
