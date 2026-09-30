package com.partimo.domain.service

import com.partimo.domain.model.Hemisphere
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.poi.PoiTag
import com.partimo.domain.model.poi.PointOfInterest
import com.partimo.domain.model.poi.RecommendationReason
import com.partimo.domain.model.poi.SeasonalRecommendation
import com.partimo.domain.model.weather.WeatherCondition
import com.partimo.domain.model.weather.WeatherSeverity
import com.partimo.domain.model.weather.WeatherSnapshot
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit

/**
 * Filtra e ordina i POI in base al mese del viaggio e, se il viaggio è imminente, al meteo attuale.
 *
 * Regole:
 * 1. Stagionalità: un luogo con mesi di attività (espliciti o dedotti, es. "mercatino di Natale"
 *    → novembre–gennaio, spiagge → estate dell'emisfero) viene escluso fuori stagione e premiato in stagione.
 * 2. Meteo: il meteo *attuale* conta solo se la partenza è entro [weatherRelevanceDays] giorni.
 *    Con allerta meteo (temporale, pioggia forte, vento) i luoghi all'aperto vengono esclusi;
 *    con pioggia o neve si privilegiano quelli al chiuso; con bel tempo quelli all'aperto.
 * 3. I luoghi fotogenici (panoramici/instagrammabili) ricevono un piccolo bonus.
 */
class SeasonalPoiFilter(private val weatherRelevanceDays: Long = DEFAULT_WEATHER_RELEVANCE_DAYS) {

    fun recommend(
        pois: List<PointOfInterest>,
        travelDate: LocalDate,
        today: LocalDate,
        hemisphere: Hemisphere,
        currentWeather: WeatherSnapshot?,
    ): List<SeasonalRecommendation> {
        val weather = currentWeather?.takeIf { isWeatherRelevant(travelDate, today) }
        return pois
            .mapNotNull { evaluate(it, travelDate.month, hemisphere, weather) }
            .sortedWith(compareByDescending<SeasonalRecommendation> { it.score }.thenBy { it.poi.name })
    }

    /** Il meteo attuale è significativo solo per partenze nei prossimi giorni. */
    fun isWeatherRelevant(travelDate: LocalDate, today: LocalDate): Boolean =
        ChronoUnit.DAYS.between(today, travelDate) in 0..weatherRelevanceDays

    /** Mesi di attività espliciti oppure dedotti da categoria e parole chiave (vuoto = tutto l'anno). */
    fun effectiveActiveMonths(poi: PointOfInterest, hemisphere: Hemisphere): Set<Month> {
        if (poi.activeMonths.isNotEmpty()) return poi.activeMonths
        val text = poi.searchableText
        return when {
            text.containsAnyWordPrefix(SeasonalCalendar.CHRISTMAS_KEYWORDS) -> SeasonalCalendar.CHRISTMAS_MONTHS
            poi.category == PoiCategory.SKI_AREA || text.containsAnyWordPrefix(SeasonalCalendar.SKI_KEYWORDS) ->
                SeasonalCalendar.skiMonths(hemisphere)
            poi.category == PoiCategory.BEACH || text.containsAnyWordPrefix(SeasonalCalendar.BEACH_KEYWORDS) ->
                SeasonalCalendar.beachMonths(hemisphere)
            else -> emptySet()
        }
    }

    private fun evaluate(
        poi: PointOfInterest,
        month: Month,
        hemisphere: Hemisphere,
        weather: WeatherSnapshot?,
    ): SeasonalRecommendation? {
        val activeMonths = effectiveActiveMonths(poi, hemisphere)
        if (activeMonths.isNotEmpty() && month !in activeMonths) return null
        if (weather?.severity == WeatherSeverity.SEVERE && !poi.isIndoor) return null

        val reasons = mutableSetOf<RecommendationReason>()
        var tags = poi.tags
        var score = qualityScore(poi)

        if (activeMonths.isNotEmpty()) {
            score += SEASONAL_BONUS
            reasons += RecommendationReason.IN_SEASON
            tags = tags + PoiTag.SEASONAL_HIGHLIGHT
        }

        if (weather != null) {
            score += weatherAdjustment(poi, weather, reasons)
        }

        if (PoiTag.PANORAMIC in tags || PoiTag.INSTAGRAMMABLE in tags) {
            score += PHOTO_SPOT_BONUS
            reasons += RecommendationReason.PHOTO_SPOT
        }

        return SeasonalRecommendation(
            poi = poi.copy(tags = tags),
            score = score.coerceIn(0.0, 1.0),
            reasons = reasons,
        )
    }

    private fun weatherAdjustment(
        poi: PointOfInterest,
        weather: WeatherSnapshot,
        reasons: MutableSet<RecommendationReason>,
    ): Double = when (weather.severity) {
        WeatherSeverity.GOOD -> if (!poi.isIndoor) {
            reasons += RecommendationReason.GREAT_WEATHER_OUTDOOR
            GOOD_WEATHER_OUTDOOR_BONUS
        } else {
            0.0
        }

        WeatherSeverity.MODERATE -> when {
            poi.isIndoor -> {
                reasons += RecommendationReason.INDOOR_ALTERNATIVE
                INDOOR_BONUS
            }
            weather.condition == WeatherCondition.SNOW && poi.category.isWinterFriendly -> {
                reasons += RecommendationReason.SNOW_ATMOSPHERE
                SNOW_ATMOSPHERE_BONUS
            }
            else -> {
                reasons += RecommendationReason.WEATHER_RISK
                -BAD_WEATHER_OUTDOOR_PENALTY
            }
        }

        // Con allerta meteo arrivano qui solo i luoghi al chiuso (gli altri sono già esclusi).
        WeatherSeverity.SEVERE -> {
            reasons += RecommendationReason.INDOOR_ALTERNATIVE
            INDOOR_BONUS
        }
    }

    /** Qualità di base in [0, 0.5] ricavata dalla valutazione media (neutra se assente). */
    private fun qualityScore(poi: PointOfInterest): Double = ((poi.rating ?: NEUTRAL_RATING) / MAX_RATING) * 0.5

    private companion object {
        const val DEFAULT_WEATHER_RELEVANCE_DAYS = 2L
        const val MAX_RATING = 5.0
        const val NEUTRAL_RATING = 2.5
        const val SEASONAL_BONUS = 0.30
        const val GOOD_WEATHER_OUTDOOR_BONUS = 0.15
        const val INDOOR_BONUS = 0.20
        const val SNOW_ATMOSPHERE_BONUS = 0.10
        const val BAD_WEATHER_OUTDOOR_PENALTY = 0.20
        const val PHOTO_SPOT_BONUS = 0.10
    }
}
