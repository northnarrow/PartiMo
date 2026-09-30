package com.partimo.app.ui.departure

import com.partimo.app.testing.MainDispatcherRule
import com.partimo.app.testing.successData
import com.partimo.app.ui.common.UiState
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.common.QueryIssue
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.place.AirportSize
import com.partimo.domain.testing.FakeAirportRepository
import com.partimo.domain.testing.FakeCitySearchRepository
import com.partimo.domain.testing.FakeUserPreferencesRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.usecase.FindDepartureAirportsUseCase
import com.partimo.domain.usecase.ObserveDepartureUseCase
import com.partimo.domain.usecase.SaveDepartureUseCase
import com.partimo.domain.usecase.SearchCitiesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeparturePickerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val milan = TestData.city("Milano", location = GeoPoint(45.4642, 9.19), countryCode = "IT")
    private val cities = FakeCitySearchRepository(DataResult.Success(listOf(milan)))
    private val airports = FakeAirportRepository(
        DataResult.Success(
            listOf(
                TestData.airport("LIN", GeoPoint(45.4451, 9.2767), AirportSize.LARGE),
                TestData.airport("MXP", GeoPoint(45.6306, 8.7281), AirportSize.HUB),
            ),
        ),
    )
    private val preferences = FakeUserPreferencesRepository()

    private fun createViewModel() = DeparturePickerViewModel(
        searchCities = SearchCitiesUseCase(cities),
        findDepartureAirports = FindDepartureAirportsUseCase(airports),
        observeDeparture = ObserveDepartureUseCase(preferences),
        saveDeparture = SaveDepartureUseCase(preferences),
    )

    @Test
    fun `cercare la città e scegliere l'aeroporto salva il punto di partenza`() = runTest {
        val viewModel = createViewModel()

        viewModel.onQueryChange("Mila")
        advanceUntilIdle()
        val city = assertNotNull(viewModel.uiState.value.results).successData().single()

        viewModel.onCitySelected(city)
        val options = assertNotNull(viewModel.uiState.value.airports).successData()
        assertEquals(listOf("MXP", "LIN"), options.map { it.airport.iata })
        assertTrue(options.first().recommended)

        viewModel.onAirportSelected(options[1])

        val saved = assertNotNull(preferences.departure.first())
        assertEquals("Milano", saved.cityName)
        assertEquals("LIN", saved.airport.iata)
        assertTrue(viewModel.uiState.value.saved)
        assertEquals(saved, viewModel.uiState.value.current)

        viewModel.onSavedHandled()
        assertEquals(false, viewModel.uiState.value.saved)
    }

    @Test
    fun `si può tornare alla ricerca per scegliere un'altra città`() = runTest {
        val viewModel = createViewModel()
        viewModel.onCitySelected(milan)

        viewModel.onChangeCity()

        assertNull(viewModel.uiState.value.selectedCity)
        assertNull(viewModel.uiState.value.airports)
    }

    @Test
    fun `senza aeroporti vicini mostra l'errore e permette di riprovare`() = runTest {
        airports.result = DataResult.Success(emptyList())
        val viewModel = createViewModel()

        viewModel.onCitySelected(milan)
        assertEquals(UiState.Error(DataError.InvalidQuery(QueryIssue.NO_AIRPORT_NEARBY)), viewModel.uiState.value.airports)

        airports.result = DataResult.Success(listOf(TestData.airport("MXP")))
        viewModel.onRetryAirports()

        assertEquals(listOf("MXP"), assertNotNull(viewModel.uiState.value.airports).successData().map { it.airport.iata })
        assertNull(preferences.departure.first(), "Nulla viene salvato finché l'utente non sceglie")
    }
}
