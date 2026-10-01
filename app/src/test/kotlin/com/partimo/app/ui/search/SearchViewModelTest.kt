package com.partimo.app.ui.search

import com.partimo.app.navigation.DashboardDestination
import com.partimo.app.testing.MainDispatcherRule
import com.partimo.app.testing.successData
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.dashboard.SampleDestinations
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.common.QueryIssue
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.place.TravelExperience
import com.partimo.domain.model.place.TravelTheme
import com.partimo.domain.service.SeasonalCalendar
import com.partimo.domain.testing.FakeAirportRepository
import com.partimo.domain.testing.FakeCitySearchRepository
import com.partimo.domain.testing.FakeDestinationCatalogRepository
import com.partimo.domain.testing.FakeUserPreferencesRepository
import com.partimo.domain.testing.FakeWeatherRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.testing.TestData.catalogDestination
import com.partimo.domain.usecase.ObserveDepartureUseCase
import com.partimo.domain.usecase.RecommendDestinationsUseCase
import com.partimo.domain.usecase.ResolveDestinationUseCase
import com.partimo.domain.usecase.SearchCitiesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val cities = FakeCitySearchRepository()
    private val airports = FakeAirportRepository()
    private val catalog = FakeDestinationCatalogRepository(
        DataResult.Success(
            listOf(
                catalogDestination(
                    "Rovaniemi",
                    experiences = listOf(
                        TravelExperience(TravelTheme.CHRISTMAS_MARKETS, setOf(Month.DECEMBER)),
                        TravelExperience(TravelTheme.NORTHERN_LIGHTS, SeasonalCalendar.monthRange(Month.SEPTEMBER, Month.MARCH)),
                    ),
                ),
                catalogDestination(
                    "Lisbona",
                    pleasantMonths = setOf(Month.OCTOBER),
                    experiences = listOf(TravelExperience(TravelTheme.FOOD)),
                ),
                catalogDestination(
                    "Bali",
                    pleasantMonths = SeasonalCalendar.monthRange(Month.APRIL, Month.OCTOBER),
                    experiences = listOf(TravelExperience(TravelTheme.BEACH, SeasonalCalendar.monthRange(Month.APRIL, Month.OCTOBER))),
                ),
            ),
        ),
    )

    private val preferences = FakeUserPreferencesRepository()
    private val december = TravelPeriod.InMonth(YearMonth.of(2026, Month.DECEMBER))

    private fun createViewModel() = SearchViewModel(
        searchCities = SearchCitiesUseCase(cities),
        resolveDestination = ResolveDestinationUseCase(airports),
        recommendDestinations = RecommendDestinationsUseCase(catalog, FakeWeatherRepository()),
        observeDeparture = ObserveDepartureUseCase(preferences),
        clock = TestData.FIXED_CLOCK,
    )

    private fun SearchViewModel.recommendedNames(): List<String> =
        assertNotNull(uiState.value.recommendations).successData().map { it.destination.city.name }

    @Test
    fun `un testo troppo corto non avvia la ricerca`() = runTest {
        val viewModel = createViewModel()

        viewModel.onQueryChange("P")
        advanceUntilIdle()

        assertEquals("P", viewModel.query)
        assertNull(viewModel.uiState.value.results)
        assertTrue(cities.queries.isEmpty())
    }

    @Test
    fun `la digitazione veloce produce una sola ricerca`() = runTest {
        cities.result = DataResult.Success(listOf(TestData.city("Parigi")))
        val viewModel = createViewModel()

        viewModel.onQueryChange("Pa")
        viewModel.onQueryChange("Par")
        viewModel.onQueryChange("Pari")
        assertEquals(UiState.Loading, viewModel.uiState.value.results)
        advanceUntilIdle()

        assertEquals(listOf("Pari"), cities.queries)
        assertEquals(listOf("Parigi"), assertNotNull(viewModel.uiState.value.results).successData().map { it.name })
    }

    @Test
    fun `nessun risultato mostra lo stato vuoto e dopo un errore si può riprovare`() = runTest {
        val viewModel = createViewModel()

        viewModel.onQueryChange("xyzqwk")
        advanceUntilIdle()
        assertEquals(UiState.Empty, viewModel.uiState.value.results)

        cities.result = DataResult.Failure(DataError.NoConnection)
        viewModel.onRetrySearch()
        advanceUntilIdle()

        assertEquals(UiState.Error(DataError.NoConnection), viewModel.uiState.value.results)
        assertEquals(2, cities.queries.size)
    }

    @Test
    fun `cancellare il testo torna alla schermata iniziale`() = runTest {
        cities.result = DataResult.Success(listOf(TestData.city("Parigi")))
        val viewModel = createViewModel()
        viewModel.onQueryChange("Parigi")
        advanceUntilIdle()

        viewModel.onClearQuery()
        advanceUntilIdle()

        assertEquals("", viewModel.query)
        assertNull(viewModel.uiState.value.results)
    }

    @Test
    fun `scegliere una città prepara la destinazione e chiede di aprire la dashboard`() = runTest {
        val viewModel = createViewModel()
        viewModel.onPeriodSelected(december)

        viewModel.onCitySelected(TestData.city("Vienna"))
        advanceUntilIdle()

        val navigation = assertNotNull(viewModel.uiState.value.pendingNavigation)
        assertEquals("VIE", navigation.destination.airportIata)
        assertEquals("Vienna", navigation.destination.name)
        assertEquals(december, navigation.period)
        assertNull(viewModel.uiState.value.preparingCityId)

        viewModel.onNavigationHandled()
        assertNull(viewModel.uiState.value.pendingNavigation)
    }

    @Test
    fun `senza aeroporti vicini mostra un errore chiudibile`() = runTest {
        airports.result = DataResult.Success(emptyList())
        val viewModel = createViewModel()

        viewModel.onCitySelected(TestData.city("Isola remota"))
        advanceUntilIdle()

        assertEquals(
            PreparationError("Isola remota", DataError.InvalidQuery(QueryIssue.NO_AIRPORT_NEARBY)),
            viewModel.uiState.value.preparationError,
        )
        assertNull(viewModel.uiState.value.pendingNavigation)

        viewModel.onPreparationErrorDismissed()
        assertNull(viewModel.uiState.value.preparationError)
    }

    @Test
    fun `consigliami propone le mete adatte al periodo`() = runTest {
        val viewModel = createViewModel()

        viewModel.onRecommend()
        advanceUntilIdle()

        // Partenza nei prossimi giorni = ottobre: mare a Bali, clima ideale a Lisbona, aurora a Rovaniemi.
        assertEquals(listOf("Bali", "Lisbona", "Rovaniemi"), viewModel.recommendedNames())

        viewModel.onMoreRecommendations()
        advanceUntilIdle()
        assertEquals(2, catalog.requests)
    }

    @Test
    fun `cambiare periodo aggiorna i consigli già mostrati`() = runTest {
        val viewModel = createViewModel()
        viewModel.onRecommend()
        advanceUntilIdle()

        viewModel.onPeriodSelected(december)
        advanceUntilIdle()

        assertEquals(listOf("Rovaniemi"), viewModel.recommendedNames())
    }

    @Test
    fun `mostra il punto di partenza salvato e si aggiorna quando cambia`() = runTest {
        val viewModel = createViewModel()
        assertNull(viewModel.uiState.value.departure)

        preferences.setDeparture(TestData.departure("Napoli", "NAP"))

        assertEquals("NAP", viewModel.uiState.value.departure?.airport?.iata)
    }

    @Test
    fun `propone i prossimi giorni e tutti i dodici mesi`() = runTest {
        val state = createViewModel().uiState.value

        assertEquals(TravelPeriod.NextDays, state.period)
        assertEquals(13, state.periods.size)
        assertEquals(TravelPeriod.InMonth(YearMonth.of(2027, Month.SEPTEMBER)), state.periods.last())
    }

    @Test
    fun `le date di andata e ritorno diventano il periodo del viaggio`() = runTest {
        val viewModel = createViewModel()
        val dates = TravelPeriod.Dates(LocalDate.of(2026, Month.DECEMBER, 10), LocalDate.of(2026, Month.DECEMBER, 14))

        viewModel.onDatesSelected(dates.departure, dates.returning)
        assertEquals(dates, viewModel.uiState.value.period)

        viewModel.onDatesSelected(LocalDate.of(2026, Month.DECEMBER, 20), LocalDate.of(2026, Month.DECEMBER, 18))
        assertEquals(dates, viewModel.uiState.value.period, "Un ritorno prima dell'andata si ignora")

        viewModel.onPeriodSelected(december)
        assertEquals(december, viewModel.uiState.value.period, "Un mese sostituisce le date")
    }

    @Test
    fun `scegliere un consiglio apre la sua città`() = runTest {
        val viewModel = createViewModel()
        viewModel.onRecommend()
        advanceUntilIdle()
        val bali = assertNotNull(viewModel.uiState.value.recommendations).successData().first()

        viewModel.onSuggestionSelected(bali)
        advanceUntilIdle()

        assertEquals("Bali", viewModel.uiState.value.pendingNavigation?.destination?.name)
    }
}

class DashboardDestinationTest {

    private val july = TravelPeriod.InMonth(YearMonth.of(2027, Month.JULY))

    @Test
    fun `la rotta conserva tutti i dati della destinazione e il mese`() {
        val route = DashboardDestination.from(SampleDestinations.VIENNA, july)

        assertEquals(SampleDestinations.VIENNA, route.toDestination())
        assertEquals(july, route.tripPeriod())
    }

    @Test
    fun `la rotta conserva anche le date di andata e ritorno`() {
        val dates = TravelPeriod.Dates(LocalDate.of(2026, Month.DECEMBER, 10), LocalDate.of(2026, Month.DECEMBER, 14))

        assertEquals(dates, DashboardDestination.from(SampleDestinations.VIENNA, dates).tripPeriod())
    }

    @Test
    fun `valori non validi ripiegano su valori sicuri`() {
        val route = DashboardDestination.from(SampleDestinations.VIENNA, july)
            .copy(period = "SCONOSCIUTO", timeZone = "Non/Valido")

        assertEquals(TravelPeriod.NextDays, route.tripPeriod())
        assertEquals(ZoneOffset.UTC, route.toDestination().timeZone)
    }

    @Test
    fun `la rotta viaggia dentro le notifiche come JSON`() {
        val route = DashboardDestination.from(SampleDestinations.VIENNA, july)

        assertEquals(route, DashboardDestination.fromJson(route.toJson()))
        assertNull(DashboardDestination.fromJson("{\"cityName\": 42"))
    }
}
