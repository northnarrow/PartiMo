package com.partimo.app.ui.map

import com.partimo.app.testing.MainDispatcherRule
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.dashboard.SampleDestinations
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.dining.PriceLevel
import com.partimo.domain.model.event.EventKind
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.saved.Favorite
import com.partimo.domain.model.saved.FavoriteKind
import com.partimo.domain.model.saved.SavedTrip
import com.partimo.domain.service.SeasonalCalendar
import com.partimo.domain.testing.FakeEventRepository
import com.partimo.domain.testing.FakeHolidayRepository
import com.partimo.domain.testing.FakeLodgingRepository
import com.partimo.domain.testing.FakePoiRepository
import com.partimo.domain.testing.FakeRestaurantRepository
import com.partimo.domain.testing.FakeSavedTripRepository
import com.partimo.domain.testing.FakeWeatherRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.usecase.FindBudgetRestaurantsUseCase
import com.partimo.domain.usecase.FindLodgingsUseCase
import com.partimo.domain.usecase.GetSeasonalHighlightsUseCase
import com.partimo.domain.usecase.GetTripEventsUseCase
import com.partimo.domain.usecase.ObserveSavedTripUseCase
import com.partimo.domain.usecase.ToggleFavoriteUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val december = TravelPeriod.InMonth(YearMonth.of(2026, Month.DECEMBER))
    private val from = LocalDate.of(2026, Month.DECEMBER, 10)
    private val to = LocalDate.of(2026, Month.DECEMBER, 14)
    private val pois = FakePoiRepository(
        DataResult.Success(
            listOf(
                TestData.poi("view", "Kahlenberg", category = PoiCategory.VIEWPOINT, location = GeoPoint(48.2767, 16.3339)),
                TestData.poi("museum", "Kunsthistorisches Museum", category = PoiCategory.MUSEUM),
            ),
        ),
    )
    private val events = FakeEventRepository(
        DataResult.Success(listOf(TestData.event("mercatino", SeasonalCalendar.CHRISTMAS_MARKET_SEASON, kind = EventKind.CHRISTMAS_MARKET))),
    )

    /** Le festività valgono in tutto il paese: non hanno un punto sulla mappa. */
    private val holidays = FakeHolidayRepository(default = DataResult.Success(listOf(TestData.holiday("festa", LocalDate.of(2026, Month.DECEMBER, 12)))))
    private val restaurants = FakeRestaurantRepository(
        DataResult.Success(
            listOf(
                TestData.restaurant("budget", PriceLevel.INEXPENSIVE, rating = 4.6).copy(location = GeoPoint(48.2091, 16.3747)),
                TestData.restaurant("senza-posizione", PriceLevel.INEXPENSIVE, rating = 4.5),
            ),
        ),
    )
    private val lodgings = FakeLodgingRepository(DataResult.Success(listOf(TestData.lodging("sacher", location = GeoPoint(48.2039, 16.3694)))))
    private val savedTrips = FakeSavedTripRepository()

    private fun createViewModel(period: TravelPeriod? = december, favoritesOnly: Boolean = false) = MapViewModel(
        getSeasonalHighlights = GetSeasonalHighlightsUseCase(pois, FakeWeatherRepository(), clock = TestData.FIXED_CLOCK),
        getTripEvents = GetTripEventsUseCase(events, holidays),
        findBudgetRestaurants = FindBudgetRestaurantsUseCase(restaurants),
        findLodgings = FindLodgingsUseCase(lodgings),
        destination = SampleDestinations.VIENNA,
        from = from,
        to = to,
        period = period,
        favoritesOnly = favoritesOnly,
        observeSavedTrip = ObserveSavedTripUseCase(savedTrips),
        toggleFavorite = ToggleFavoriteUseCase(savedTrips, TestData.FIXED_CLOCK),
    )

    private val stephansdom = Favorite(
        id = "stephansdom",
        kind = FavoriteKind.PLACE,
        name = "Duomo di Vienna",
        location = GeoPoint(48.2085, 16.3731),
        category = PoiCategory.RELIGIOUS_SITE,
    )

    @Test
    fun `luoghi, eventi, ristoranti e alloggi diventano punti, tranne quelli senza posizione`() = runTest {
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.isLoading)

        advanceUntilIdle()
        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertEquals(
            setOf("PLACE:view", "PLACE:museum", "EVENT:mercatino", "RESTAURANT:budget", "LODGING:sacher"),
            state.points.map { it.key }.toSet(),
        )
        assertEquals(state.points, state.visiblePoints)
        assertEquals(1, state.countOf(FavoriteKind.EVENT))
        assertEquals(1, state.fitRequest, "A fine caricamento la mappa inquadra tutti i punti")
        // Luoghi ed eventi riaprono la loro scheda; ristoranti e alloggi la pagina su Google Maps.
        assertEquals("Kahlenberg", state.points.first { it.key == "PLACE:view" }.place?.name)
        assertNotNull(state.points.first { it.key == "EVENT:mercatino" }.place)
        val lodging = state.points.first { it.key == "LODGING:sacher" }
        assertNull(lodging.place)
        assertTrue(lodging.item.url.orEmpty().startsWith("https://www.google.com/maps/search/"))
    }

    @Test
    fun `i filtri nascondono i tipi e chiudono la scheda del punto nascosto`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onPointSelected("RESTAURANT:budget")
        assertEquals("Ristorante budget", viewModel.uiState.value.selected?.item?.name)

        viewModel.onKindToggled(FavoriteKind.RESTAURANT)
        val filtered = viewModel.uiState.value
        assertTrue(filtered.visiblePoints.none { it.kind == FavoriteKind.RESTAURANT })
        assertNull(filtered.selectedKey, "La scheda di un punto nascosto si chiude")
        assertEquals(1, filtered.countOf(FavoriteKind.RESTAURANT), "Il conteggio del filtro resta visibile")

        viewModel.onKindToggled(FavoriteKind.RESTAURANT)
        assertTrue(viewModel.uiState.value.visiblePoints.any { it.kind == FavoriteKind.RESTAURANT })

        viewModel.onFitAll()
        assertEquals(2, viewModel.uiState.value.fitRequest)
        viewModel.onPointSelected(null)
        assertNull(viewModel.uiState.value.selected)
    }

    @Test
    fun `solo preferiti mostra i preferiti salvati, anche quelli che non sono tra i risultati`() = runTest {
        val restaurantFavorite = Favorite(id = "budget", kind = FavoriteKind.RESTAURANT, name = "Ristorante budget", location = GeoPoint(48.2091, 16.3747))
        savedTrips.update { listOf(SavedTrip(SampleDestinations.VIENNA, december, Instant.parse("2026-09-30T08:00:00Z"), listOf(stephansdom, restaurantFavorite))) }
        val viewModel = createViewModel(favoritesOnly = true)
        advanceUntilIdle()
        val state = viewModel.uiState.value

        assertTrue(state.favoritesOnly)
        assertEquals(setOf("PLACE:stephansdom", "RESTAURANT:budget"), state.visiblePoints.map { it.key }.toSet())
        assertEquals("Duomo di Vienna", state.visiblePoints.first { it.key == "PLACE:stephansdom" }.place?.name, "Il preferito riapre la sua scheda")

        viewModel.onFavoritesOnlyChanged(false)
        assertEquals(6, viewModel.uiState.value.visiblePoints.size)
    }

    @Test
    fun `la stella salva il punto tra i preferiti del viaggio`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val lodging = viewModel.uiState.value.points.first { it.key == "LODGING:sacher" }

        viewModel.onToggleFavorite(lodging)
        advanceUntilIdle()

        val trip = savedTrips.current.single()
        assertEquals(december, trip.period)
        assertEquals(listOf("LODGING:sacher"), trip.favorites.map { it.key })
        assertEquals(setOf("LODGING:sacher"), viewModel.uiState.value.favoriteKeys)
    }

    @Test
    fun `senza periodo i preferiti non sono attivi e la mappa mostra tutto`() = runTest {
        val viewModel = createViewModel(period = null, favoritesOnly = true)
        advanceUntilIdle()
        val state = viewModel.uiState.value

        assertFalse(state.favoritesEnabled)
        assertFalse(state.favoritesOnly)
        assertEquals(5, state.visiblePoints.size)

        viewModel.onToggleFavorite(state.points.first())
        advanceUntilIdle()
        assertTrue(savedTrips.current.isEmpty())
    }

    @Test
    fun `una fonte non riuscita non blocca le altre e si può ricaricare`() = runTest {
        restaurants.result = DataResult.Failure(DataError.NoConnection)
        val viewModel = createViewModel()
        advanceUntilIdle()
        val failed = viewModel.uiState.value

        assertTrue(failed.hasErrors)
        assertIs<UiState.Error>(failed.sources.getValue(MapSource.RESTAURANTS))
        assertEquals(4, failed.points.size)

        restaurants.result = DataResult.Success(listOf(TestData.restaurant("budget", PriceLevel.INEXPENSIVE, rating = 4.6).copy(location = GeoPoint(48.2091, 16.3747))))
        viewModel.retry()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasErrors)
        assertEquals(5, viewModel.uiState.value.points.size)
    }

    @Test
    fun `dove sono mostra la posizione, centra la mappa e dice la distanza dal punto scelto`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onPointSelected("LODGING:sacher")
        assertNull(viewModel.uiState.value.selectedDistanceMeters)

        val stephansplatz = GeoPoint(48.2085, 16.3731)
        viewModel.onMyLocation(stephansplatz)

        val state = viewModel.uiState.value
        assertEquals(stephansplatz, state.myLocation)
        assertEquals(1, state.locateRequest)
        assertEquals(GeoPoint(48.2039, 16.3694).distanceTo(stephansplatz), state.selectedDistanceMeters!!, 0.001)
    }
}

