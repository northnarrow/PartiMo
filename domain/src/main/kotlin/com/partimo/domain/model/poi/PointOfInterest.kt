package com.partimo.domain.model.poi

import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.weather.WeatherSnapshot
import java.time.Month

enum class PoiCategory(val isIndoorByDefault: Boolean) {
    MUSEUM(isIndoorByDefault = true),
    MONUMENT(isIndoorByDefault = false),
    RELIGIOUS_SITE(isIndoorByDefault = true),
    VIEWPOINT(isIndoorByDefault = false),
    PARK(isIndoorByDefault = false),
    BEACH(isIndoorByDefault = false),
    MARKET(isIndoorByDefault = false),
    SEASONAL_EVENT(isIndoorByDefault = false),
    SKI_AREA(isIndoorByDefault = false),
    NEIGHBORHOOD(isIndoorByDefault = false),
    ATTRACTION(isIndoorByDefault = false),
    SHOPPING(isIndoorByDefault = true),
    OTHER(isIndoorByDefault = false),
    ;

    /** Categorie che restano piacevoli anche con la neve (mercatini, eventi invernali, piste). */
    val isWinterFriendly: Boolean
        get() = this == SEASONAL_EVENT || this == MARKET || this == SKI_AREA
}

/** Etichette assegnate dal dominio per evidenziare i luoghi più fotogenici. */
enum class PoiTag {
    INSTAGRAMMABLE,
    PANORAMIC,
    SUNSET_SPOT,
    HIDDEN_GEM,
    /** Luogo o evento attivo solo in alcuni mesi e disponibile nel periodo del viaggio. */
    SEASONAL_HIGHLIGHT,
}

/** Voce di Wikipedia che descrive un luogo: da qui arrivano descrizione, storia e foto della scheda. */
data class WikipediaPage(val language: String, val title: String)

data class PointOfInterest(
    val id: String,
    val name: String,
    val category: PoiCategory,
    val location: GeoPoint,
    val rating: Double? = null,
    val reviewCount: Int? = null,
    val description: String? = null,
    val photoUrl: String? = null,
    val isIndoor: Boolean = category.isIndoorByDefault,
    /** Mesi in cui il luogo/evento è attivo. Vuoto = tutto l'anno (o informazione non disponibile). */
    val activeMonths: Set<Month> = emptySet(),
    val tags: Set<PoiTag> = emptySet(),
    val mapsUrl: String? = null,
    /** Voce di Wikipedia sul luogo, se il provider la conosce già (evita di cercarla per nome). */
    val wikipediaPage: WikipediaPage? = null,
    /**
     * Notorietà relativa in [0, 1] stimata dal provider quando mancano valutazioni e recensioni
     * (es. l'ordine di rilevanza di Wikipedia): ordina i luoghi e riconosce gli spot più famosi.
     */
    val popularity: Double? = null,
) {
    init {
        require(popularity == null || popularity in 0.0..1.0) { "Notorietà fuori intervallo: $popularity" }
    }

    /** Testo in minuscolo su cui il dominio cerca le parole chiave (nome + descrizione). */
    val searchableText: String
        get() = listOfNotNull(name, description).joinToString(separator = " ").lowercase()
}

/**
 * Tema di ricerca stagionale (es. "mercatini di Natale" a dicembre): il data layer lo usa per
 * interrogare il provider, e i risultati ereditano categoria e mesi di attività del tema.
 */
data class SeasonalTheme(
    val searchQuery: String,
    val category: PoiCategory,
    val activeMonths: Set<Month>,
)

data class PoiQuery(
    val location: GeoPoint,
    val travelMonth: Month,
    val radiusMeters: Int = 5_000,
    val seasonalThemes: List<SeasonalTheme> = emptyList(),
    /** Nome della zona (es. la città), usato dai provider per rendere più precise le ricerche testuali. */
    val areaName: String? = null,
)

/** Motivi per cui un luogo è stato consigliato (mostrati in UI). */
enum class RecommendationReason {
    IN_SEASON,
    GREAT_WEATHER_OUTDOOR,
    INDOOR_ALTERNATIVE,
    SNOW_ATMOSPHERE,
    WEATHER_RISK,
    PHOTO_SPOT,
}

data class SeasonalRecommendation(
    val poi: PointOfInterest,
    /** Rilevanza in [0, 1] usata per l'ordinamento. */
    val score: Double,
    val reasons: Set<RecommendationReason>,
)

/** Risultato aggregato del modulo "esplorazione stagionale". */
data class SeasonalHighlights(
    val season: Season,
    val travelMonth: Month,
    /** Meteo attuale della destinazione, se disponibile. */
    val currentWeather: WeatherSnapshot?,
    /** `true` se il meteo attuale ha influito sui suggerimenti (viaggio imminente). */
    val weatherConsidered: Boolean,
    val recommendations: List<SeasonalRecommendation>,
)
