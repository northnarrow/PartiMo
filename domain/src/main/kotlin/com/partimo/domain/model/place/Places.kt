package com.partimo.domain.model.place

import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.weather.WeatherSnapshot
import java.time.Month
import java.time.ZoneId

/** Città trovata dalla ricerca (geocoding), in qualunque parte del mondo. */
data class CityPlace(
    val id: String,
    val name: String,
    /** Codice paese ISO 3166-1 alpha-2 (es. "IT"). */
    val countryCode: String,
    val location: GeoPoint,
    val timeZone: ZoneId,
    val country: String? = null,
    /** Regione o stato (es. "Île-de-France"), se diverso dal nome della città. */
    val region: String? = null,
    val population: Int? = null,
)

enum class AirportSize {
    /** Hub internazionale principale di un'area metropolitana (es. Fiumicino per Roma). */
    HUB,
    LARGE,
    MEDIUM,
}

/** Aeroporto con voli di linea, identificato dal codice IATA. */
data class Airport(
    val iata: String,
    val name: String,
    val city: String?,
    val countryCode: String,
    val location: GeoPoint,
    val size: AirportSize,
) {
    /** Nome senza suffissi generici: "Milan Malpensa International Airport" → "Milan Malpensa". */
    val shortName: String
        get() = GENERIC_SUFFIXES.firstOrNull { name.endsWith(it) }
            ?.let { name.removeSuffix(it).trim() }
            ?.takeIf { it.isNotEmpty() }
            ?: name

    private companion object {
        val GENERIC_SUFFIXES = listOf(" International Airport", " Intercontinental Airport", " Regional Airport", " Airport")
    }
}

/** Punto di partenza scelto dall'utente: la città come l'ha cercata e l'aeroporto da cui volare. */
data class DeparturePoint(
    val cityName: String,
    val airport: Airport,
)

/** Aeroporto proposto come partenza per una città, con la distanza dal centro. */
data class AirportOption(
    val airport: Airport,
    val distanceKm: Int,
    /** L'aeroporto con più collegamenti a una distanza ragionevole (lo stesso scelto per le mete). */
    val recommended: Boolean,
)

/** Temi di viaggio usati dal motore "Consigliami". */
enum class TravelTheme {
    CHRISTMAS_MARKETS,
    NORTHERN_LIGHTS,
    WINTER_SPORTS,
    BEACH,
    FOLIAGE,
    BLOSSOM,
    FESTIVAL,
    WARM_ESCAPE,
    CULTURE,
    FOOD,
    NATURE,
    NIGHTLIFE,
}

/** Esperienza offerta da una meta in certi mesi (nessun mese = tutto l'anno). */
data class TravelExperience(
    val theme: TravelTheme,
    val months: Set<Month> = emptySet(),
) {
    val isYearRound: Boolean get() = months.isEmpty()

    fun isAvailableIn(month: Month): Boolean = isYearRound || month in months
}

/** Meta del catalogo di ispirazione. */
data class CatalogDestination(
    val city: CityPlace,
    /** Mesi in cui il clima è piacevole per visitarla. */
    val pleasantMonths: Set<Month>,
    val experiences: List<TravelExperience>,
    /** Breve descrizione mostrata nella scheda del consiglio. */
    val tagline: String,
)

/** Meta consigliata per il periodo del viaggio, con i motivi del consiglio. */
data class DestinationSuggestion(
    val destination: CatalogDestination,
    /** Rilevanza in [0, 1]. */
    val score: Double,
    /** Esperienze stagionali disponibili nel mese del viaggio. */
    val seasonalHighlights: List<TravelTheme>,
    /** Esperienze disponibili tutto l'anno (es. cultura, cucina). */
    val yearRoundHighlights: List<TravelTheme>,
    val pleasantClimate: Boolean,
    /** Meteo attuale della meta, se disponibile (solo informativo). */
    val currentWeather: WeatherSnapshot? = null,
)
