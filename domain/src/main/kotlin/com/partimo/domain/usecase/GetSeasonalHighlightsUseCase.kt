package com.partimo.domain.usecase

import com.partimo.domain.common.DataResult
import com.partimo.domain.common.getOrNull
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.poi.PoiQuery
import com.partimo.domain.model.poi.PoiTag
import com.partimo.domain.model.poi.Season
import com.partimo.domain.model.poi.SeasonalHighlights
import com.partimo.domain.repository.PoiRepository
import com.partimo.domain.repository.WeatherRepository
import com.partimo.domain.service.PhotoSpotTagger
import com.partimo.domain.service.SeasonalCalendar
import com.partimo.domain.service.SeasonalPoiFilter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Clock
import java.time.LocalDate

/**
 * Esplorazione stagionale: recupera i POI (inclusi i temi del mese, es. mercatini di Natale a
 * dicembre), li etichetta come spot fotografici e li filtra per stagione e meteo attuale.
 *
 * Il meteo è un arricchimento: se non è disponibile i suggerimenti vengono comunque restituiti.
 */
class GetSeasonalHighlightsUseCase(
    private val poiRepository: PoiRepository,
    private val weatherRepository: WeatherRepository,
    private val tagger: PhotoSpotTagger = PhotoSpotTagger(),
    private val filter: SeasonalPoiFilter = SeasonalPoiFilter(),
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    /**
     * @param requiredTags se non vuoto, restituisce solo i luoghi con tutte le etichette indicate
     * (es. solo gli spot [PoiTag.INSTAGRAMMABLE]).
     * @param areaName nome della città, per ricerche testuali più precise presso il provider.
     */
    suspend operator fun invoke(
        location: GeoPoint,
        travelDate: LocalDate,
        requiredTags: Set<PoiTag> = emptySet(),
        areaName: String? = null,
        radiusMeters: Int = DEFAULT_RADIUS_METERS,
        forceRefresh: Boolean = false,
    ): DataResult<SeasonalHighlights> = coroutineScope {
        val hemisphere = location.hemisphere
        val query = PoiQuery(
            location = location,
            travelMonth = travelDate.month,
            radiusMeters = radiusMeters,
            seasonalThemes = SeasonalCalendar.themesFor(travelDate.month, hemisphere),
            areaName = areaName,
        )
        // Meteo e POI vengono richiesti in parallelo.
        val weatherRequest = async { weatherRepository.getCurrentWeather(location) }
        val poiResult = poiRepository.getPointsOfInterest(query, forceRefresh)
        val weather = weatherRequest.await().getOrNull()

        when (poiResult) {
            is DataResult.Failure -> poiResult
            is DataResult.Success -> {
                val today = LocalDate.now(clock)
                val recommendations = filter
                    .recommend(poiResult.data.map(tagger::tag), travelDate, today, hemisphere, weather)
                    .filter { recommendation -> recommendation.poi.tags.containsAll(requiredTags) }
                DataResult.Success(
                    data = SeasonalHighlights(
                        season = Season.of(travelDate.month, hemisphere),
                        travelMonth = travelDate.month,
                        currentWeather = weather,
                        weatherConsidered = weather != null && filter.isWeatherRelevant(travelDate, today),
                        recommendations = recommendations,
                    ),
                    origin = poiResult.origin,
                )
            }
        }
    }

    private companion object {
        const val DEFAULT_RADIUS_METERS = 5_000
    }
}
