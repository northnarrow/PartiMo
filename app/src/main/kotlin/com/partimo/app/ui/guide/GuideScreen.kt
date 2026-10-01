package com.partimo.app.ui.guide

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.partimo.app.R
import com.partimo.app.ui.common.Formatters
import com.partimo.app.ui.common.SectionError
import com.partimo.app.ui.common.SectionLoading
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.common.emoji
import com.partimo.app.ui.common.flagEmoji
import com.partimo.app.ui.common.labelRes
import com.partimo.app.ui.dashboard.PreviewData
import com.partimo.app.ui.place.ExternalLinks
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.model.guide.CountryInfo
import com.partimo.domain.model.guide.DrivingSide
import com.partimo.domain.model.guide.EmergencyNumbers
import com.partimo.domain.model.guide.ExchangeRate
import com.partimo.domain.model.guide.GuideSection
import com.partimo.domain.model.guide.TravelGuide
import com.partimo.domain.model.weather.SunTimes
import com.partimo.domain.model.weather.TripWeather
import java.math.BigDecimal
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** Tag della lista della guida, usato dai test UI per lo scroll. */
const val GUIDE_LIST_TAG = "guide_list"

/** Azioni della guida (predefinite vuote per anteprime e test). */
data class GuideActions(
    val onBack: () -> Unit = {},
    val onAmountChanged: (String) -> Unit = {},
    val onSwapCurrencies: () -> Unit = {},
    val onSectionToggled: (String) -> Unit = {},
    val onRetryWeather: () -> Unit = {},
    val onRetryGuide: () -> Unit = {},
    val onRetryExchangeRate: () -> Unit = {},
    val onOpenLink: (String) -> Unit = {},
    /** Compone un numero (di emergenza) nell'app del telefono, senza chiamare. */
    val onDial: (String) -> Unit = {},
)

/** Scheda del paese su Viaggiare Sicuri, il sito della Farnesina (codice ISO a tre lettere). */
fun viaggiareSicuriUrl(countryCode3: String): String = "https://www.viaggiaresicuri.it/find-country/country/$countryCode3"

@Composable
fun GuideRoute(viewModel: GuideViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()
    val noBrowser = stringResource(R.string.place_no_browser)
    val noDialer = stringResource(R.string.guide_no_dialer)
    GuideScreen(
        state = state,
        actions = GuideActions(
            onBack = onBack,
            onAmountChanged = viewModel::onAmountChanged,
            onSwapCurrencies = viewModel::onSwapCurrencies,
            onSectionToggled = viewModel::onSectionToggled,
            onRetryWeather = viewModel::retryWeather,
            onRetryGuide = viewModel::retryGuide,
            onRetryExchangeRate = viewModel::retryExchangeRate,
            onOpenLink = { url ->
                if (!ExternalLinks.openLink(context, url, toolbarColor)) Toast.makeText(context, noBrowser, Toast.LENGTH_LONG).show()
            },
            onDial = { number ->
                try {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, noDialer, Toast.LENGTH_LONG).show()
                }
            },
        ),
        modifier = modifier,
    )
}

/**
 * Guida del viaggio: meteo per le date (con alba, tramonto e ora d'oro), informazioni pratiche sul
 * paese, numeri di emergenza, cambio della valuta e guida della città di Wikivoyage. È stateless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(state: GuideUiState, actions: GuideActions, modifier: Modifier = Modifier) {
    val country = (state.country as? UiState.Success)?.data
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
                            text = "📖 " + stringResource(R.string.guide_title, state.destination.name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = flagEmoji(state.destination.countryCode) + " " + (country?.name ?: state.destination.countryCode) +
                                " · " + Formatters.dateRange(state.from, state.to),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag(GUIDE_LIST_TAG),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "weather") { WeatherCard(state, actions) }
            country?.let { info ->
                item(key = "basics") { BasicsCard(info, state.timeDifferenceMinutes) }
                info.emergency?.takeUnless { it.isEmpty }?.let { numbers ->
                    item(key = "emergency") { EmergencyCard(numbers, actions.onDial) }
                }
                item(key = "currency") { CurrencyCard(state, actions) }
                info.countryCode3?.let { code3 ->
                    item(key = "advice") {
                        OutlinedButton(
                            onClick = { actions.onOpenLink(viaggiareSicuriUrl(code3)) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        ) { Text("🛡️ " + stringResource(R.string.guide_travel_advice) + " ↗") }
                    }
                }
            }
            guideItems(state, actions)
        }
    }
}

@Composable
private fun GuideCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    ElevatedCard(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

// ---- Meteo e luce --------------------------------------------------------------------------------

@Composable
private fun WeatherCard(state: GuideUiState, actions: GuideActions) {
    GuideCard(title = "🌤️ " + stringResource(R.string.guide_weather_title)) {
        when (val weather = state.weather) {
            UiState.Loading, UiState.Empty -> SectionLoading(modifier = Modifier.padding(horizontal = 0.dp))
            is UiState.Error -> SectionError(error = weather.error, onRetry = actions.onRetryWeather, modifier = Modifier.padding(horizontal = 0.dp))
            is UiState.Success -> when (val data = weather.data) {
                is TripWeather.Forecast -> ForecastRow(data)
                is TripWeather.Climate -> ClimateText(data)
            }
        }
        state.sunTimes?.let { SunTimesText(it) }
    }
}

@Composable
private fun ForecastRow(forecast: TripWeather.Forecast) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(forecast.days, key = { it.date.toString() }) { day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Formatters.weekdayDay(day.date), style = MaterialTheme.typography.labelMedium)
                val condition = stringResource(day.condition.labelRes())
                Text(
                    text = day.condition.emoji(),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { contentDescription = condition },
                )
                Text(
                    text = Formatters.temperature(day.maxCelsius) + " / " + Formatters.temperature(day.minCelsius),
                    style = MaterialTheme.typography.bodySmall,
                )
                day.precipitationProbability?.takeIf { it >= MIN_RAIN_PROBABILITY }?.let {
                    Text(stringResource(R.string.guide_rain_probability, it), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    Text(
        text = stringResource(R.string.guide_weather_forecast_source),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ClimateText(climate: TripWeather.Climate) {
    val normals = climate.normals
    Text(
        text = stringResource(
            R.string.guide_weather_climate,
            Formatters.temperature(normals.averageMaxCelsius),
            Formatters.temperature(normals.averageMinCelsius),
            (normals.wetDaysShare * 10).roundToInt(),
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = stringResource(R.string.guide_weather_climate_source, normals.years),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SunTimesText(sun: SunTimes) {
    val text = when {
        sun.polarNight -> "🌌 " + stringResource(R.string.guide_polar_night)
        sun.polarDay -> "☀️ " + stringResource(R.string.guide_polar_day)
        else -> listOfNotNull(
            sun.sunrise?.let { "🌅 " + stringResource(R.string.guide_sunrise, Formatters.time(it)) },
            sun.sunset?.let { "🌇 " + stringResource(R.string.guide_sunset, Formatters.time(it)) },
            sun.eveningGoldenHourStart?.let { "📸 " + stringResource(R.string.guide_golden_hour, Formatters.time(it)) },
        ).joinToString("  ·  ")
    }
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
    Text(
        text = stringResource(R.string.guide_sun_note, Formatters.dayMonth(sun.date)),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---- In breve ------------------------------------------------------------------------------------

@Composable
private fun BasicsCard(country: CountryInfo, timeDifferenceMinutes: Int) {
    GuideCard(title = "🧭 " + stringResource(R.string.guide_basics_title)) {
        if (country.languages.isNotEmpty()) FactRow(stringResource(R.string.guide_language), country.languages.joinToString(", "))
        country.currencyCode?.let { code ->
            val name = country.currencyName?.let { "$it ($code)" } ?: code
            FactRow(stringResource(R.string.guide_currency), listOfNotNull(name, country.currencySymbol?.takeIf { it != code }).joinToString(" · "))
        }
        country.callingCode?.let { FactRow(stringResource(R.string.guide_calling_code), it) }
        country.drivingSide?.let { side ->
            FactRow(
                stringResource(R.string.guide_driving),
                stringResource(if (side == DrivingSide.LEFT) R.string.guide_driving_left else R.string.guide_driving_right) +
                    if (side == DrivingSide.LEFT) " ⚠️" else "",
            )
        }
        FactRow(stringResource(R.string.guide_time_zone), timeDifferenceText(timeDifferenceMinutes))
        country.power?.let { power ->
            FactRow(stringResource(R.string.guide_plugs), stringResource(R.string.guide_plugs_value, power.plugTypes.joinToString("/"), power.voltage, power.frequency))
            Text(
                text = if (power.fitsItalianPlugs) {
                    "✅ " + stringResource(R.string.guide_plugs_ok)
                } else {
                    "🔌 " + stringResource(R.string.guide_plugs_adapter, power.plugTypes.joinToString("/"))
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (power.lowVoltage) Text("⚡ " + stringResource(R.string.guide_low_voltage), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun timeDifferenceText(minutes: Int): String = when {
    minutes == 0 -> stringResource(R.string.guide_time_same)
    minutes > 0 -> stringResource(R.string.guide_time_ahead, Formatters.hoursAndMinutes(minutes))
    else -> stringResource(R.string.guide_time_behind, Formatters.hoursAndMinutes(abs(minutes)))
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

// ---- Emergenze -----------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmergencyCard(numbers: EmergencyNumbers, onDial: (String) -> Unit) {
    val entries = listOfNotNull(
        numbers.general?.let { it to stringResource(R.string.guide_emergency_general) },
        numbers.police?.let { it to stringResource(R.string.guide_emergency_police) },
        numbers.ambulance?.let { it to stringResource(R.string.guide_emergency_ambulance) },
        numbers.fire?.let { it to stringResource(R.string.guide_emergency_fire) },
    )
        // Lo stesso numero per più servizi (es. 119 ambulanza e vigili del fuoco) compare una volta sola.
        .groupBy({ it.first }, { it.second })
        .map { (number, services) -> number to services.joinToString(" e ") }
    GuideCard(title = "🆘 " + stringResource(R.string.guide_emergency_title)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.forEach { (number, service) ->
                val description = stringResource(R.string.guide_emergency_call, number, service)
                FilledTonalButton(onClick = { onDial(number) }, modifier = Modifier.semantics { contentDescription = description }) {
                    Text("$number · $service")
                }
            }
        }
        Text(
            text = stringResource(R.string.guide_emergency_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---- Valuta --------------------------------------------------------------------------------------

@Composable
private fun CurrencyCard(state: GuideUiState, actions: GuideActions) {
    GuideCard(title = "💱 " + stringResource(R.string.guide_currency_title)) {
        when (val rate = state.exchangeRate) {
            UiState.Empty -> Text("💶 " + stringResource(R.string.guide_currency_euro), style = MaterialTheme.typography.bodyMedium)
            UiState.Loading -> SectionLoading(modifier = Modifier.padding(horizontal = 0.dp))
            is UiState.Error -> SectionError(error = rate.error, onRetry = actions.onRetryExchangeRate, modifier = Modifier.padding(horizontal = 0.dp))
            is UiState.Success -> Converter(rate.data, state, actions)
        }
    }
}

@Composable
private fun Converter(rate: ExchangeRate, state: GuideUiState, actions: GuideActions) {
    val localCode = rate.to
    val inputCode = if (state.fromLocalCurrency) localCode else rate.from
    val outputCode = if (state.fromLocalCurrency) rate.from else localCode
    Text(
        text = stringResource(R.string.guide_currency_rate, Formatters.currencyAmount(rate.rate, localCode)),
        style = MaterialTheme.typography.titleSmall,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.amountText,
            onValueChange = actions.onAmountChanged,
            label = { Text(stringResource(R.string.guide_currency_amount, inputCode)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        val swapDescription = stringResource(R.string.guide_currency_swap)
        IconButton(onClick = actions.onSwapCurrencies, modifier = Modifier.semantics { contentDescription = swapDescription }) {
            Text("⇄", style = MaterialTheme.typography.titleLarge)
        }
    }
    state.convertedAmount?.let { converted ->
        Text(
            text = "= " + Formatters.currencyAmount(converted, outputCode),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    // Promemoria rapido per i prezzi che si incontrano più spesso.
    val quick = listOf(1, 10, 50, 100).map { euros -> BigDecimal(euros) }
    Text(
        text = quick.joinToString("  ·  ") { euros -> Formatters.currencyAmount(euros, rate.from) + " = " + Formatters.currencyAmount(rate.convert(euros), localCode) },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    rate.updatedAt?.let { updated ->
        Text(
            text = stringResource(R.string.guide_currency_source, Formatters.shortDate(updated.atZone(ZoneId.systemDefault()).toLocalDate())),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---- Wikivoyage ----------------------------------------------------------------------------------

private fun LazyListScope.guideItems(state: GuideUiState, actions: GuideActions) {
    item(key = "guide-title") {
        Text(
            text = "📖 " + stringResource(R.string.guide_wikivoyage_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
    when (val guide = state.guide) {
        UiState.Loading -> item(key = "guide-loading") { SectionLoading() }
        UiState.Empty -> item(key = "guide-empty") {
            Text(
                text = stringResource(R.string.guide_wikivoyage_empty, state.destination.name),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        is UiState.Error -> item(key = "guide-error") { SectionError(error = guide.error, onRetry = actions.onRetryGuide) }
        is UiState.Success -> {
            val data = guide.data
            item(key = "guide-intro") { GuideIntroduction(data) }
            items(data.sections, key = { "guide-${it.title}" }) { section ->
                GuideSectionCard(section, expanded = section.title in state.expandedSections, onToggle = { actions.onSectionToggled(section.title) })
            }
            item(key = "guide-footer") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedButton(onClick = { actions.onOpenLink(data.url) }) { Text(stringResource(R.string.guide_wikivoyage_read) + " ↗") }
                    Text(
                        text = stringResource(R.string.guide_wikivoyage_license),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideIntroduction(guide: TravelGuide) {
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (guide.language != APP_LANGUAGE) {
            Text(
                text = stringResource(R.string.guide_wikivoyage_english),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        guide.introduction.forEach { paragraph -> Text(text = paragraph, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun GuideSectionCard(section: GuideSection, expanded: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = sectionEmoji(section.title) + " " + section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            val paragraphs = section.paragraphs
            val preview = paragraphs.firstOrNull() ?: section.subsections.firstOrNull()?.let { it.title + ": " + it.paragraphs.firstOrNull().orEmpty() }
            if (!expanded) {
                preview?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium, maxLines = PREVIEW_LINES, overflow = TextOverflow.Ellipsis) }
            } else {
                paragraphs.forEach { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
                section.subsections.forEach { subsection ->
                    Text(text = subsection.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    subsection.paragraphs.forEach { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            Text(
                text = stringResource(if (expanded) R.string.guide_wikivoyage_less else R.string.guide_wikivoyage_more),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Icona del capitolo dal suo titolo (italiano o inglese). */
private fun sectionEmoji(title: String): String {
    val normalized = title.lowercase()
    return when {
        normalized.startsWith("da sapere") || normalized.startsWith("understand") -> "ℹ️"
        normalized.startsWith("come arrivare") || normalized.startsWith("get in") -> "✈️"
        normalized.startsWith("come spostarsi") || normalized.startsWith("get around") -> "🚇"
        normalized.startsWith("eventi") || normalized.startsWith("events") -> "🎉"
        normalized.startsWith("dove mangiare") || normalized.startsWith("eat") -> "🍽️"
        normalized.startsWith("come divertirsi") || normalized.startsWith("drink") -> "🍻"
        normalized.startsWith("acquisti") || normalized.startsWith("buy") -> "🛍️"
        normalized.startsWith("sicurezza") || normalized.startsWith("stay safe") -> "🛡️"
        normalized.startsWith("come restare in contatto") || normalized.startsWith("connect") -> "📶"
        else -> "📌"
    }
}

private const val APP_LANGUAGE = "it"
private const val PREVIEW_LINES = 3

/** Sotto questa probabilità la pioggia non si segnala. */
private const val MIN_RAIN_PROBABILITY = 20

// ---- Anteprime -----------------------------------------------------------------------------------

@Preview(name = "Guida", showBackground = true, heightDp = 1400)
@Composable
private fun GuidePreview() {
    PartiMoTheme { GuideScreen(PreviewData.guideState(), GuideActions()) }
}

@Preview(name = "Guida · tema scuro", showBackground = true, heightDp = 1400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GuideDarkPreview() {
    PartiMoTheme(darkTheme = true) { GuideScreen(PreviewData.guideState(), GuideActions()) }
}
