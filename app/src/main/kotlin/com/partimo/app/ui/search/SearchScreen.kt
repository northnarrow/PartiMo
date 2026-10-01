package com.partimo.app.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.partimo.app.R
import com.partimo.app.ui.common.CityResultItem
import com.partimo.app.ui.common.CitySearchField
import com.partimo.app.ui.common.Formatters
import com.partimo.app.ui.common.PeriodChips
import com.partimo.app.ui.common.SectionError
import com.partimo.app.ui.common.SectionLoading
import com.partimo.app.ui.common.SectionMessage
import com.partimo.app.ui.common.TravellersRow
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.common.emoji
import com.partimo.app.ui.common.errorMessage
import com.partimo.app.ui.common.flagEmoji
import com.partimo.app.ui.common.labelRes
import com.partimo.app.ui.common.phrase
import com.partimo.app.ui.dashboard.PreviewData
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.model.Destination
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.Travellers
import com.partimo.domain.model.place.CityPlace
import com.partimo.domain.model.place.DeparturePoint
import com.partimo.domain.model.place.DestinationSuggestion
import com.partimo.domain.model.saved.SavedTrip
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Tag della lista della schermata di ricerca, usato dai test UI per lo scroll. */
const val SEARCH_LIST_TAG = "search_list"

private const val MAX_SUGGESTION_CHIPS = 4

/** Azioni della schermata di ricerca (predefinite vuote per anteprime e test). */
data class SearchActions(
    val onQueryChange: (String) -> Unit = {},
    val onClearQuery: () -> Unit = {},
    val onRetrySearch: () -> Unit = {},
    val onPeriodSelected: (TravelPeriod) -> Unit = {},
    /** Date esatte scelte nelle celle «Andata» e «Ritorno». */
    val onDatesSelected: (LocalDate, LocalDate) -> Unit = { _, _ -> },
    /** Adulti e bambini scelti nella cella «Chi parte». */
    val onTravellersSelected: (Travellers) -> Unit = {},
    val onChooseDeparture: () -> Unit = {},
    val onCitySelected: (CityPlace) -> Unit = {},
    val onRecommend: () -> Unit = {},
    val onMoreRecommendations: () -> Unit = {},
    val onSuggestionSelected: (DestinationSuggestion) -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onSavedTripSelected: (SavedTrip) -> Unit = {},
    val onRemoveSavedTrip: (SavedTrip) -> Unit = {},
    /** Fonti, licenze e privacy. */
    val onOpenAbout: () -> Unit = {},
)

/** Collega il ViewModel alla schermata e apre la dashboard quando la meta è pronta. */
@Composable
fun SearchRoute(
    viewModel: SearchViewModel,
    onOpenDestination: (Destination, TravelPeriod) -> Unit,
    onChooseDeparture: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAbout: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val openDestination by rememberUpdatedState(onOpenDestination)
    LaunchedEffect(state.pendingNavigation) {
        state.pendingNavigation?.let { navigation ->
            openDestination(navigation.destination, navigation.period)
            viewModel.onNavigationHandled()
        }
    }
    SearchScreen(
        state = state,
        query = viewModel.query,
        actions = SearchActions(
            onQueryChange = viewModel::onQueryChange,
            onClearQuery = viewModel::onClearQuery,
            onRetrySearch = viewModel::onRetrySearch,
            onPeriodSelected = viewModel::onPeriodSelected,
            onDatesSelected = viewModel::onDatesSelected,
            onTravellersSelected = viewModel::onTravellersSelected,
            onChooseDeparture = onChooseDeparture,
            onCitySelected = viewModel::onCitySelected,
            onRecommend = viewModel::onRecommend,
            onMoreRecommendations = viewModel::onMoreRecommendations,
            onSuggestionSelected = viewModel::onSuggestionSelected,
            onDismissError = viewModel::onPreparationErrorDismissed,
            onSavedTripSelected = viewModel::onSavedTripSelected,
            onRemoveSavedTrip = viewModel::onRemoveSavedTrip,
            onOpenAbout = onOpenAbout,
        ),
        modifier = modifier,
    )
}

/**
 * Schermata iniziale: "Dove vuoi andare?". Si cerca una città in tutto il mondo oppure si chiede
 * un consiglio in base al periodo; la meta scelta apre la dashboard con tutte le informazioni.
 * Il punto di partenza e il periodo (uno qualunque dei prossimi dodici mesi) li decide l'utente.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    query: String,
    actions: SearchActions,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val selectCity: (CityPlace) -> Unit = { city ->
        keyboard?.hide()
        actions.onCitySelected(city)
    }
    val selectSuggestion: (DestinationSuggestion) -> Unit = { suggestion ->
        keyboard?.hide()
        actions.onSuggestionSelected(suggestion)
    }

    var fieldFocused by remember { mutableStateOf(false) }
    // "Modalità ricerca": mentre si scrive l'intestazione si compatta, così campo e risultati
    // restano visibili sopra la tastiera.
    val searchMode = fieldFocused || query.isNotEmpty()

    Scaffold(modifier = modifier) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .testTag(SEARCH_LIST_TAG),
            contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 24.dp),
        ) {
            item(key = "header") {
                SearchHeader(topInset = innerPadding.calculateTopPadding(), compact = searchMode, onOpenAbout = actions.onOpenAbout)
            }
            item(key = "field") {
                CitySearchField(
                    query = query,
                    placeholder = stringResource(R.string.search_placeholder),
                    isSearching = state.results == UiState.Loading,
                    onQueryChange = actions.onQueryChange,
                    onClear = actions.onClearQuery,
                    onSearch = { keyboard?.hide() },
                    onFocusChanged = { fieldFocused = it },
                )
            }
            state.preparationError?.let { error ->
                item(key = "preparation-error") { PreparationErrorCard(error = error, onDismiss = actions.onDismissError) }
            }

            val results = state.results
            if (results == null) {
                if (state.savedTrips.isNotEmpty()) {
                    item(key = "saved-trips") {
                        SavedTripsRow(
                            trips = state.savedTrips,
                            today = state.today,
                            onSelected = actions.onSavedTripSelected,
                            onRemove = actions.onRemoveSavedTrip,
                        )
                    }
                }
                item(key = "departure") { DepartureRow(departure = state.departure, onChoose = actions.onChooseDeparture) }
                item(key = "period") {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        SectionTitle(stringResource(R.string.search_when))
                        PeriodChips(
                            periods = state.periods,
                            selected = state.period,
                            today = state.today,
                            onSelected = actions.onPeriodSelected,
                        )
                        TripDatesRow(
                            period = state.period,
                            today = state.today,
                            onDatesSelected = actions.onDatesSelected,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        TravellersRow(
                            travellers = state.travellers,
                            onTravellersSelected = actions.onTravellersSelected,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                        )
                    }
                }
                item(key = "recommend") {
                    RecommendSection(isLoading = state.recommendations == UiState.Loading, onRecommend = actions.onRecommend)
                }
                state.recommendations?.let { recommendations ->
                    recommendationItems(recommendations, state, selectSuggestion, actions)
                }
            } else {
                searchResultItems(results, query, state.preparingCityId, selectCity, actions.onRetrySearch)
            }
        }
    }
}

/** Intestazione con marchio e titolo; in modalità ricerca ([compact]) resta solo il titolo. */
@Composable
private fun SearchHeader(topInset: Dp, compact: Boolean, onOpenAbout: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.background)))
            .padding(start = 24.dp, end = 24.dp, top = topInset + 20.dp, bottom = 16.dp),
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            AnimatedVisibility(visible = !compact) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
                    Text(text = "🌍 ✈️", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.primary,
                    )
                }
            }
            Text(
                text = stringResource(R.string.search_title),
                style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onPrimaryContainer,
            )
            AnimatedVisibility(visible = !compact) {
                Text(
                    text = stringResource(R.string.search_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        IconButton(onClick = onOpenAbout, modifier = Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-8).dp)) {
            Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.about_open), tint = colors.onSurfaceVariant)
        }
    }
}

/** Punto di partenza scelto dall'utente, con il pulsante per cambiarlo (o sceglierlo la prima volta). */
@Composable
private fun DepartureRow(departure: DeparturePoint?, onChoose: () -> Unit) {
    OutlinedCard(
        onClick = onChoose,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "📍", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (departure == null) {
                    Text(
                        text = stringResource(R.string.departure_missing_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.departure_missing_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.departure_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.departure_value, departure.cityName, departure.airport.iata),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = departure.airport.shortName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(onClick = onChoose) {
                Text(stringResource(if (departure == null) R.string.departure_choose else R.string.departure_change))
            }
        }
    }
}

@Composable
private fun RecommendSection(isLoading: Boolean, onRecommend: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp)) {
        Button(
            onClick = onRecommend,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            Text(text = "✨  " + stringResource(R.string.search_recommend), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.search_recommend_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
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

private fun LazyListScope.searchResultItems(
    results: UiState<List<CityPlace>>,
    query: String,
    preparingCityId: String?,
    onCitySelected: (CityPlace) -> Unit,
    onRetry: () -> Unit,
) {
    item(key = "results-title") { SectionTitle(stringResource(R.string.search_results_title)) }
    when (results) {
        UiState.Loading -> Unit // La barra sotto il campo di ricerca indica già il caricamento.
        UiState.Empty -> item(key = "results-empty") { SectionMessage(stringResource(R.string.search_no_results, query.trim())) }
        is UiState.Error -> item(key = "results-error") { SectionError(error = results.error, onRetry = onRetry) }
        is UiState.Success -> items(results.data, key = { it.id }) { city ->
            CityResultItem(city = city, isPreparing = city.id == preparingCityId, onClick = { onCitySelected(city) })
            HorizontalDivider(modifier = Modifier.padding(start = 76.dp, end = 16.dp))
        }
    }
}

private fun LazyListScope.recommendationItems(
    recommendations: UiState<List<DestinationSuggestion>>,
    state: SearchUiState,
    onSuggestionSelected: (DestinationSuggestion) -> Unit,
    actions: SearchActions,
) {
    item(key = "ideas-title") {
        SectionTitle(stringResource(R.string.search_ideas_title, state.period.phrase(state.today)))
    }
    when (recommendations) {
        UiState.Loading -> item(key = "ideas-loading") { SectionLoading() }
        UiState.Empty -> item(key = "ideas-empty") { SectionMessage(stringResource(R.string.search_ideas_empty)) }
        is UiState.Error -> item(key = "ideas-error") { SectionError(error = recommendations.error, onRetry = actions.onRecommend) }
        is UiState.Success -> {
            items(recommendations.data, key = { it.destination.city.id }) { suggestion ->
                SuggestionCard(
                    suggestion = suggestion,
                    isPreparing = suggestion.destination.city.id == state.preparingCityId,
                    onClick = { onSuggestionSelected(suggestion) },
                )
            }
            item(key = "ideas-more") {
                TextButton(onClick = actions.onMoreRecommendations, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("🔄 " + stringResource(R.string.search_more_ideas))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionCard(suggestion: DestinationSuggestion, isPreparing: Boolean, onClick: () -> Unit) {
    val city = suggestion.destination.city
    val chips = buildList {
        suggestion.seasonalHighlights.forEach { add(it.emoji() + " " + stringResource(it.labelRes())) }
        if (suggestion.pleasantClimate) add("☀️ " + stringResource(R.string.search_pleasant_climate))
        suggestion.yearRoundHighlights.forEach { add(it.emoji() + " " + stringResource(it.labelRes())) }
    }.take(MAX_SUGGESTION_CHIPS)
    val weather = suggestion.currentWeather?.let {
        stringResource(R.string.search_weather_now, Formatters.temperature(it.temperatureCelsius), stringResource(it.condition.labelRes()))
    }

    ElevatedCard(
        onClick = onClick,
        enabled = !isPreparing,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = flagEmoji(city.countryCode), style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = city.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                city.country?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(text = suggestion.destination.tagline, style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    chips.forEach { chip ->
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                text = chip,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                weather?.let {
                    Text(text = it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(8.dp))
            if (isPreparing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun PreparationErrorCard(error: PreparationError, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.search_preparation_error, error.cityName, errorMessage(error.error)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.search_dismiss))
            }
        }
    }
}

// ---- Anteprime ---------------------------------------------------------------------------------

@Preview(name = "Ricerca · iniziale", showBackground = true, heightDp = 900)
@Composable
private fun SearchIdlePreview() {
    PartiMoTheme { SearchScreen(state = PreviewData.searchIdleState(), query = "", actions = SearchActions()) }
}

@Preview(name = "Ricerca · consigli", showBackground = true, heightDp = 1400)
@Composable
private fun SearchIdeasPreview() {
    PartiMoTheme { SearchScreen(state = PreviewData.searchIdeasState(), query = "", actions = SearchActions()) }
}

@Preview(name = "Ricerca · risultati", showBackground = true, heightDp = 900)
@Composable
private fun SearchResultsPreview() {
    PartiMoTheme { SearchScreen(state = PreviewData.searchResultsState(), query = "Par", actions = SearchActions()) }
}

/** Tag della riga dei viaggi salvati, scorrevole in orizzontale. */
const val SAVED_TRIPS_TAG = "saved_trips"

/** "I tuoi viaggi": i viaggi salvati, dal più vicino, da riaprire con un tocco. */
@Composable
private fun SavedTripsRow(trips: List<SavedTrip>, today: LocalDate, onSelected: (SavedTrip) -> Unit, onRemove: (SavedTrip) -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        SectionTitle(stringResource(R.string.saved_trips_title))
        LazyRow(
            modifier = Modifier.testTag(SAVED_TRIPS_TAG),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(trips, key = { it.id }) { trip -> SavedTripCard(trip, today, onClick = { onSelected(trip) }, onRemove = { onRemove(trip) }) }
        }
    }
}

@Composable
private fun SavedTripCard(trip: SavedTrip, today: LocalDate, onClick: () -> Unit, onRemove: () -> Unit) {
    val from = trip.departureDate(today)
    val past = trip.period.isOver(today)
    val days = ChronoUnit.DAYS.between(today, from)
    OutlinedCard(onClick = onClick, modifier = Modifier.width(220.dp)) {
        Row(modifier = Modifier.padding(start = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = flagEmoji(trip.destination.countryCode) + " " + trip.destination.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.saved_trip_remove, trip.destination.name))
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (past) stringResource(R.string.saved_trip_past) else Formatters.dateRange(from, trip.returnDate(today)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!past) {
                    Text(
                        text = if (days <= 1) stringResource(R.string.saved_trip_tomorrow) else stringResource(R.string.saved_trip_in_days, days.toInt()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (trip.favorites.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.saved_trip_favorites, trip.favorites.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
