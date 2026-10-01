package com.partimo.data.local

import com.partimo.data.network.Fetched
import com.partimo.data.source.CountryInfoDataSource
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.model.guide.CountryInfo
import java.util.Currency
import java.util.Locale
import java.util.MissingResourceException

/**
 * Informazioni pratiche sui paesi senza rete: nome, valuta e lingue arrivano dai dati internazionali
 * del sistema (CLDR), prefisso, guida, prese e numeri di emergenza dal catalogo curato dell'app.
 */
internal class BundledCountryInfoDataSource(private val languageCode: String = "it") : CountryInfoDataSource {

    private val displayLocale: Locale = Locale.forLanguageTag(languageCode)

    override suspend fun countryInfo(countryCode: String): Fetched<CountryInfo> {
        val code = countryCode.uppercase(Locale.ROOT)
        require(code.length == 2 && code.all { it in 'A'..'Z' }) { "Codice paese non valido: $countryCode" }
        val region = Locale("", code)
        val facts = CountryFactsCatalog.factsFor(code)
        val currency = runCatching { Currency.getInstance(region) }.getOrNull()
        val languageCodes = facts?.languages ?: systemLanguageCodes(code)
        val info = CountryInfo(
            countryCode = code,
            countryCode3 = runCatching { region.isO3Country }.getOrNull()?.takeIf { it.isNotBlank() },
            name = region.getDisplayCountry(displayLocale).takeIf { it.isNotBlank() && it != code } ?: code,
            currencyCode = currency?.currencyCode,
            currencyName = currency?.getDisplayName(displayLocale),
            currencySymbol = currency?.let { symbolOf(it, facts?.languages?.firstOrNull(), code) },
            languages = languageCodes.map(::languageName).distinct(),
            languageCodes = languageCodes,
            callingCode = facts?.callingCode,
            drivingSide = facts?.drivingSide,
            power = facts?.power,
            emergency = facts?.emergency,
        )
        return Fetched(info, DataOrigin.LOCAL)
    }

    private fun languageName(language: String): String =
        Locale.forLanguageTag(language).getDisplayLanguage(displayLocale).ifBlank { language }

    /**
     * Per i paesi fuori dal catalogo: le lingue per cui il sistema ha dati regionali (al massimo due, in
     * ordine alfabetico), come codici ISO 639-1 aggiornati (es. "he", non il vecchio "iw").
     */
    private fun systemLanguageCodes(countryCode: String): List<String> = Locale.getAvailableLocales()
        .filter { it.country == countryCode && it.variant.isEmpty() && it.script.isEmpty() && it.language.isNotEmpty() }
        .map { Locale(it.language).toLanguageTag() }
        .distinct()
        .sortedBy(::languageName)
        .take(MAX_SYSTEM_LANGUAGES)

    /** Simbolo come lo scrivono nel paese ("€", "Kč", "¥"), non il codice ISO. */
    private fun symbolOf(currency: Currency, language: String?, countryCode: String): String? = try {
        val local = Locale(language ?: "", countryCode)
        currency.getSymbol(local).takeIf { it != currency.currencyCode } ?: currency.getSymbol(displayLocale)
    } catch (e: MissingResourceException) {
        null
    }

    private companion object {
        const val MAX_SYSTEM_LANGUAGES = 2
    }
}
