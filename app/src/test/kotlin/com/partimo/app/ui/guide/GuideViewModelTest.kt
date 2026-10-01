package com.partimo.app.ui.guide

import com.partimo.app.testing.MainDispatcherRule
import com.partimo.app.testing.successData
import com.partimo.app.ui.common.UiState
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.guide.CountryInfo
import com.partimo.domain.model.guide.ExchangeRates
import com.partimo.domain.model.guide.GuideSection
import com.partimo.domain.model.guide.TravelGuide
import com.partimo.domain.model.weather.DailyObservation
import com.partimo.domain.model.weather.TripWeather
import com.partimo.domain.testing.FakeCountryInfoRepository
import com.partimo.domain.testing.FakeExchangeRateRepository
import com.partimo.domain.testing.FakeTravelGuideRepository
import com.partimo.domain.testing.FakeTripWeatherRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.usecase.GetCountryInfoUseCase
import com.partimo.domain.usecase.GetExchangeRateUseCase
import com.partimo.domain.usecase.GetTravelGuideUseCase
import com.partimo.domain.usecase.GetTripWeatherUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GuideViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val from = LocalDate.of(2026, Month.DECEMBER, 10)
    private val to = LocalDate.of(2026, Month.DECEMBER, 14)
    private val austria = CountryInfo("AT", "AUT", "Austria", "EUR", "euro", "€", listOf("tedesco"))
    private val czechia = CountryInfo("CZ", "CZE", "Cechia", "CZK", "corona ceca", "Kč", listOf("ceco"))
    private val countries = FakeCountryInfoRepository(DataResult.Success(austria))
    private val rates = FakeExchangeRateRepository(DataResult.Success(ExchangeRates("EUR", mapOf("CZK" to BigDecimal("24.44")))))
    private val guides = FakeTravelGuideRepository(
        DataResult.Success(
            TravelGuide(
                title = "Vienna",
                language = "it",
                url = "https://it.wikivoyage.org/wiki/Vienna",
                introduction = listOf("Vienna è la capitale dell'Austria."),
                sections = listOf(GuideSection("Cosa vedere", listOf("Musei")), GuideSection("Come spostarsi", listOf("Metro"))),
            ),
        ),
    )
    private val weather = FakeTripWeatherRepository(
        historyResult = DataResult.Success(
            (2016..2025).flatMap { year -> (5..20).map { day -> DailyObservation(LocalDate.of(year, 12, day), 4.0, -1.0, 0.0) } },
        ),
    )

    private fun createViewModel() = GuideViewModel(
        getCountryInfo = GetCountryInfoUseCase(countries),
        getExchangeRate = GetExchangeRateUseCase(rates),
        getTravelGuide = GetTravelGuideUseCase(guides),
        getTripWeather = GetTripWeatherUseCase(weather, TestData.FIXED_CLOCK),
        destination = TestData.destination(),
        from = from,
        to = to,
        deviceZone = ZoneId.of("Europe/Rome"),
    )

    @Test
    fun `in Austria si paga in euro e la guida mostra clima, luce del giorno e capitoli utili`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals(austria, state.country.successData())
        assertEquals(UiState.Empty, state.exchangeRate, "Nessun cambio da mostrare")
        assertTrue(rates.requests.isEmpty())
        val climate = assertIs<TripWeather.Climate>(state.weather.successData())
        assertEquals(4.0, climate.normals.averageMaxCelsius, 0.001)
        assertEquals(listOf("Come spostarsi"), state.guide.successData().sections.map { it.title })
        assertNotNull(state.sunTimes?.sunset)
        assertEquals(0, state.timeDifferenceMinutes, "Vienna e Roma hanno lo stesso fuso")
    }

    @Test
    fun `in Cechia il convertitore cambia euro e corone nei due sensi`() = runTest {
        countries.result = DataResult.Success(czechia)
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf("EUR" to false), rates.requests)
        assertEquals(0, BigDecimal("2444").compareTo(viewModel.uiState.value.convertedAmount))

        viewModel.onAmountChanged("12,5")
        assertEquals(0, BigDecimal("305.5").compareTo(viewModel.uiState.value.convertedAmount))
        viewModel.onAmountChanged("12a")
        assertEquals("12,5", viewModel.uiState.value.amountText, "Solo cifre e separatore decimale")

        viewModel.onSwapCurrencies()
        viewModel.onAmountChanged("244.4")
        assertEquals(0, BigDecimal.TEN.compareTo(viewModel.uiState.value.convertedAmount!!.setScale(2, java.math.RoundingMode.HALF_UP)))
    }

    @Test
    fun `i capitoli della guida si aprono e chiudono, e la guida si può ricaricare`() = runTest {
        guides.result = DataResult.Failure(DataError.RateLimited)
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(UiState.Error(DataError.RateLimited), viewModel.uiState.value.guide)

        guides.result = DataResult.Success(null)
        viewModel.retryGuide()
        advanceUntilIdle()
        assertEquals(UiState.Empty, viewModel.uiState.value.guide, "Città senza guida")

        viewModel.onSectionToggled("Come spostarsi")
        assertEquals(setOf("Come spostarsi"), viewModel.uiState.value.expandedSections)
        viewModel.onSectionToggled("Come spostarsi")
        assertTrue(viewModel.uiState.value.expandedSections.isEmpty())
    }

    @Test
    fun `la differenza di fuso tiene conto dell'ora legale`() {
        val newYork = ZoneId.of("America/New_York")
        val rome = ZoneId.of("Europe/Rome")

        assertEquals(-6 * 60, GuideViewModel.timeDifferenceMinutes(LocalDate.of(2026, 12, 10), newYork, rome))
        // A fine marzo l'Europa è già passata all'ora legale... e gli Stati Uniti anche: sempre 6 ore.
        assertEquals(-6 * 60, GuideViewModel.timeDifferenceMinutes(LocalDate.of(2026, 7, 10), newYork, rome))
        assertEquals(7 * 60, GuideViewModel.timeDifferenceMinutes(LocalDate.of(2026, 7, 10), ZoneId.of("Asia/Tokyo"), rome))
        assertEquals(330 - 60, GuideViewModel.timeDifferenceMinutes(LocalDate.of(2026, 12, 10), ZoneId.of("Asia/Kolkata"), rome))
    }
}
