package com.partimo.app.ui.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.partimo.app.di.AppContainer
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.common.toUiState
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.Destination
import com.partimo.domain.model.guide.CountryInfo
import com.partimo.domain.model.guide.ExchangeRate
import com.partimo.domain.model.guide.TravelGuide
import com.partimo.domain.model.weather.SunTimes
import com.partimo.domain.model.weather.TripWeather
import com.partimo.domain.service.SunCalculator
import com.partimo.domain.usecase.GetCountryInfoUseCase
import com.partimo.domain.usecase.GetExchangeRateUseCase
import com.partimo.domain.usecase.GetTravelGuideUseCase
import com.partimo.domain.usecase.GetTripWeatherUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

data class GuideUiState(
    val destination: Destination,
    val from: LocalDate,
    val to: LocalDate,
    val country: UiState<CountryInfo> = UiState.Loading,
    /** Cambio dall'euro alla valuta del paese; [UiState.Empty] se nel paese si paga in euro. */
    val exchangeRate: UiState<ExchangeRate> = UiState.Loading,
    /** Importo scritto nel convertitore. */
    val amountText: String = DEFAULT_AMOUNT,
    /** `true` per convertire dalla valuta locale all'euro. */
    val fromLocalCurrency: Boolean = false,
    val weather: UiState<TripWeather> = UiState.Loading,
    /** Alba, tramonto e ora d'oro del giorno di arrivo, calcolati sul telefono. */
    val sunTimes: SunTimes? = null,
    /** Differenza tra l'ora della meta e quella del telefono, in minuti (positiva se la meta è avanti). */
    val timeDifferenceMinutes: Int = 0,
    /** Guida della città; [UiState.Empty] se Wikivoyage non ne ha una. */
    val guide: UiState<TravelGuide> = UiState.Loading,
    /** Capitoli della guida aperti per intero. */
    val expandedSections: Set<String> = emptySet(),
) {
    /** Importo scritto, accettando sia la virgola sia il punto come separatore decimale. */
    val amount: BigDecimal? get() = amountText.trim().replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }

    /** Risultato della conversione nella direzione scelta. */
    val convertedAmount: BigDecimal?
        get() {
            val rate = (exchangeRate as? UiState.Success)?.data ?: return null
            val value = amount ?: return null
            return if (fromLocalCurrency) rate.inverse().convert(value) else rate.convert(value)
        }

    companion object {
        const val DEFAULT_AMOUNT = "100"
    }
}

/**
 * ViewModel della guida del viaggio: informazioni sul paese (subito, sono nell'app), cambio della
 * valuta, meteo per le date e guida di Wikivoyage, caricati in parallelo e ognuno per conto suo.
 */
class GuideViewModel(
    private val getCountryInfo: GetCountryInfoUseCase,
    private val getExchangeRate: GetExchangeRateUseCase,
    private val getTravelGuide: GetTravelGuideUseCase,
    private val getTripWeather: GetTripWeatherUseCase,
    destination: Destination,
    from: LocalDate,
    to: LocalDate,
    deviceZone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        GuideUiState(
            destination = destination,
            from = from,
            to = to,
            sunTimes = SunCalculator.sunTimes(from, destination.center, destination.timeZone),
            timeDifferenceMinutes = timeDifferenceMinutes(from, destination.timeZone, deviceZone),
        ),
    )
    val uiState: StateFlow<GuideUiState> = _uiState.asStateFlow()

    init {
        loadCountry()
        loadWeather()
        loadGuide(forceRefresh = false)
    }

    fun onAmountChanged(text: String) {
        // Solo cifre e un separatore decimale: è un campo numerico.
        if (text.length <= MAX_AMOUNT_LENGTH && text.all { it.isDigit() || it == ',' || it == '.' }) {
            _uiState.update { it.copy(amountText = text) }
        }
    }

    fun onSwapCurrencies() {
        _uiState.update { it.copy(fromLocalCurrency = !it.fromLocalCurrency) }
    }

    fun onSectionToggled(title: String) {
        _uiState.update { state ->
            val expanded = state.expandedSections
            state.copy(expandedSections = if (title in expanded) expanded - title else expanded + title)
        }
    }

    fun retryWeather() = loadWeather()

    fun retryGuide() = loadGuide(forceRefresh = true)

    fun retryExchangeRate() {
        val currency = (_uiState.value.country as? UiState.Success)?.data?.currencyCode ?: return
        loadExchangeRate(currency, forceRefresh = true)
    }

    private fun loadCountry() {
        viewModelScope.launch {
            val result = getCountryInfo(_uiState.value.destination.countryCode)
            _uiState.update { it.copy(country = result.toUiState()) }
            val currency = (result as? DataResult.Success)?.data?.takeUnless { it.usesEuro }?.currencyCode
            if (currency != null) loadExchangeRate(currency, forceRefresh = false) else _uiState.update { it.copy(exchangeRate = UiState.Empty) }
        }
    }

    private fun loadExchangeRate(currency: String, forceRefresh: Boolean) {
        _uiState.update { it.copy(exchangeRate = UiState.Loading) }
        viewModelScope.launch {
            val rate = getExchangeRate(to = currency, forceRefresh = forceRefresh).toUiState()
            _uiState.update { it.copy(exchangeRate = rate) }
        }
    }

    private fun loadWeather() {
        _uiState.update { it.copy(weather = UiState.Loading) }
        viewModelScope.launch {
            val state = _uiState.value
            val weather = getTripWeather(state.destination.center, state.from, state.to).toUiState()
            _uiState.update { it.copy(weather = weather) }
        }
    }

    private fun loadGuide(forceRefresh: Boolean) {
        _uiState.update { it.copy(guide = UiState.Loading) }
        viewModelScope.launch {
            val result = getTravelGuide(_uiState.value.destination, forceRefresh)
            val guide = when (result) {
                is DataResult.Success -> result.data?.let { UiState.Success(it, result.origin) } ?: UiState.Empty
                is DataResult.Failure -> UiState.Error(result.error)
            }
            _uiState.update { it.copy(guide = guide) }
        }
    }

    companion object {
        private const val MAX_AMOUNT_LENGTH = 12

        /** Differenza di fuso a mezzogiorno del giorno di arrivo (tiene conto dell'ora legale). */
        fun timeDifferenceMinutes(date: LocalDate, destinationZone: ZoneId, deviceZone: ZoneId): Int {
            val instant = date.atTime(12, 0).atZone(destinationZone).toInstant()
            val destinationOffset = destinationZone.rules.getOffset(instant).totalSeconds
            val deviceOffset = deviceZone.rules.getOffset(instant).totalSeconds
            return (destinationOffset - deviceOffset) / 60
        }

        fun factory(container: AppContainer, destination: Destination, from: LocalDate, to: LocalDate): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    GuideViewModel(
                        getCountryInfo = container.getCountryInfo,
                        getExchangeRate = container.getExchangeRate,
                        getTravelGuide = container.getTravelGuide,
                        getTripWeather = container.getTripWeather,
                        destination = destination,
                        from = from,
                        to = to,
                    )
                }
            }
    }
}
