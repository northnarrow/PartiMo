package com.partimo.app.ui.departure

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.partimo.app.R
import com.partimo.app.ui.common.CityResultItem
import com.partimo.app.ui.common.CitySearchField
import com.partimo.app.ui.common.SectionError
import com.partimo.app.ui.common.SectionLoading
import com.partimo.app.ui.common.SectionMessage
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.common.flagEmoji
import com.partimo.app.ui.dashboard.PreviewData
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.model.place.AirportOption
import com.partimo.domain.model.place.CityPlace
import com.partimo.domain.model.place.DeparturePoint

/** Tag della lista della scelta della partenza, usato dai test UI. */
const val DEPARTURE_LIST_TAG = "departure_list"

/** Azioni della scelta della partenza (predefinite vuote per anteprime e test). */
data class DeparturePickerActions(
    val onBack: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onClearQuery: () -> Unit = {},
    val onRetrySearch: () -> Unit = {},
    val onCitySelected: (CityPlace) -> Unit = {},
    val onRetryAirports: () -> Unit = {},
    val onChangeCity: () -> Unit = {},
    val onAirportSelected: (AirportOption) -> Unit = {},
)

/** Collega il ViewModel alla schermata e torna indietro appena la partenza è salvata. */
@Composable
fun DeparturePickerRoute(viewModel: DeparturePickerViewModel, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(state.saved) {
        if (state.saved) {
            viewModel.onSavedHandled()
            done()
        }
    }
    DeparturePickerScreen(
        state = state,
        query = viewModel.query,
        actions = DeparturePickerActions(
            onBack = onDone,
            onQueryChange = viewModel::onQueryChange,
            onClearQuery = viewModel::onClearQuery,
            onRetrySearch = viewModel::onRetrySearch,
            onCitySelected = viewModel::onCitySelected,
            onRetryAirports = viewModel::onRetryAirports,
            onChangeCity = viewModel::onChangeCity,
            onAirportSelected = viewModel::onAirportSelected,
        ),
        modifier = modifier,
    )
}

/**
 * "Da dove parti?": l'utente cerca la sua città e sceglie l'aeroporto da cui volare. La scelta viene
 * salvata e usata per tutti i voli (e per gli avvisi sulle offerte).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeparturePickerScreen(
    state: DeparturePickerUiState,
    query: String,
    actions: DeparturePickerActions,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val selectedCity = state.selectedCity
    LaunchedEffect(selectedCity) {
        // Si apre con la tastiera pronta per scrivere la città.
        if (autoFocus && selectedCity == null) focusRequester.requestFocus()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.departure_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .testTag(DEPARTURE_LIST_TAG),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            state.current?.let { current -> item(key = "current") { CurrentDepartureCard(current) } }
            if (selectedCity == null) {
                item(key = "field") {
                    CitySearchField(
                        query = query,
                        placeholder = stringResource(R.string.departure_placeholder),
                        isSearching = state.results == UiState.Loading,
                        onQueryChange = actions.onQueryChange,
                        onClear = actions.onClearQuery,
                        onSearch = { keyboard?.hide() },
                        modifier = Modifier.padding(top = 8.dp),
                        focusRequester = focusRequester,
                    )
                }
                state.results?.let { results ->
                    cityResultItems(results, query, onRetry = actions.onRetrySearch) { city ->
                        keyboard?.hide()
                        actions.onCitySelected(city)
                    }
                }
            } else {
                airportItems(selectedCity, state.airports, actions)
            }
        }
    }
}

private fun LazyListScope.cityResultItems(
    results: UiState<List<CityPlace>>,
    query: String,
    onRetry: () -> Unit,
    onSelected: (CityPlace) -> Unit,
) {
    when (results) {
        UiState.Loading -> Unit // La barra sotto il campo indica già il caricamento.
        UiState.Empty -> item(key = "empty") { SectionMessage(stringResource(R.string.search_no_results, query.trim())) }
        is UiState.Error -> item(key = "error") { SectionError(error = results.error, onRetry = onRetry) }
        is UiState.Success -> items(results.data, key = { it.id }) { city ->
            CityResultItem(city = city, isPreparing = false, onClick = { onSelected(city) })
            HorizontalDivider(modifier = Modifier.padding(start = 76.dp, end = 16.dp))
        }
    }
}

private fun LazyListScope.airportItems(city: CityPlace, airports: UiState<List<AirportOption>>?, actions: DeparturePickerActions) {
    item(key = "airports-title") {
        Text(
            text = flagEmoji(city.countryCode) + " " + stringResource(R.string.departure_airports_title, city.name),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )
    }
    when (airports) {
        null, UiState.Loading -> item(key = "airports-loading") { SectionLoading() }
        UiState.Empty -> item(key = "airports-empty") { SectionMessage(stringResource(R.string.error_query_no_airport)) }
        is UiState.Error -> item(key = "airports-error") { SectionError(error = airports.error, onRetry = actions.onRetryAirports) }
        is UiState.Success -> items(airports.data, key = { it.airport.iata }) { option ->
            AirportOptionItem(option = option, onClick = { actions.onAirportSelected(option) })
            HorizontalDivider(modifier = Modifier.padding(start = 76.dp, end = 16.dp))
        }
    }
    item(key = "change-city") {
        TextButton(onClick = actions.onChangeCity, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text("← " + stringResource(R.string.departure_other_city))
        }
    }
}

@Composable
private fun AirportOptionItem(option: AirportOption, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Box(contentAlignment = Alignment.Center) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = option.airport.iata,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        },
        headlineContent = { Text(option.airport.shortName, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            // La località aiuta a riconoscere gli aeroporti dal nome poco noto (es. "Il Caravaggio" = Orio al Serio).
            val distance = stringResource(R.string.departure_airport_distance, option.distanceKm)
            Text(listOfNotNull(option.airport.city, distance).joinToString(" · "))
        },
        trailingContent = {
            if (option.recommended) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(
                        text = "⭐ " + stringResource(R.string.departure_recommended),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun CurrentDepartureCard(departure: DeparturePoint) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(
            text = "📍 " + stringResource(
                R.string.departure_current,
                stringResource(R.string.departure_value, departure.cityName, departure.airport.iata),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}

// ---- Anteprime ---------------------------------------------------------------------------------

@Preview(name = "Partenza · aeroporti", showBackground = true, heightDp = 700)
@Composable
private fun DepartureAirportsPreview() {
    PartiMoTheme {
        DeparturePickerScreen(PreviewData.departureAirportsState(), query = "Milano", actions = DeparturePickerActions(), autoFocus = false)
    }
}
