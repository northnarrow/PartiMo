package com.partimo.domain.usecase

import com.partimo.domain.common.DataResult
import com.partimo.domain.common.getOrNull
import com.partimo.domain.model.place.CatalogDestination
import com.partimo.domain.model.place.DestinationSuggestion
import com.partimo.domain.repository.DestinationCatalogRepository
import com.partimo.domain.repository.WeatherRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import java.time.Month

/**
 * Motore "Consigliami": propone le mete più adatte al mese del viaggio.
 *
 * Una meta è consigliata se in quel mese offre esperienze stagionali (mercatini di Natale, aurora
 * boreale, fioriture, foliage, mare...) oppure ha un clima piacevole; le altre sono scartate.
 * Punteggio: esperienze stagionali (fino a 0,6) + clima piacevole (0,3) + esperienze di tutto l'anno
 * (fino a 0,1). Le pagine successive ruotano sull'intera classifica ("Altre idee").
 */
class RecommendDestinationsUseCase(
    private val catalogRepository: DestinationCatalogRepository,
    private val weatherRepository: WeatherRepository,
) {

    suspend operator fun invoke(
        travelDate: LocalDate,
        count: Int = DEFAULT_COUNT,
        page: Int = 0,
    ): DataResult<List<DestinationSuggestion>> = coroutineScope {
        when (val catalog = catalogRepository.destinations()) {
            is DataResult.Failure -> catalog
            is DataResult.Success -> {
                val ranked = rank(catalog.data, travelDate.month)
                val pageItems = pageOf(ranked, page, count)
                // Meteo attuale come informazione aggiuntiva: se non disponibile la scheda resta valida.
                val enriched = pageItems
                    .map { suggestion ->
                        async {
                            val weather = weatherRepository.getCurrentWeather(suggestion.destination.city.location).getOrNull()
                            suggestion.copy(currentWeather = weather)
                        }
                    }
                    .awaitAll()
                DataResult.Success(enriched, catalog.origin)
            }
        }
    }

    internal fun rank(destinations: List<CatalogDestination>, month: Month): List<DestinationSuggestion> =
        destinations
            .mapNotNull { score(it, month) }
            .sortedWith(compareByDescending<DestinationSuggestion> { it.score }.thenBy { it.destination.city.name })

    private fun score(destination: CatalogDestination, month: Month): DestinationSuggestion? {
        val seasonal = destination.experiences.filter { !it.isYearRound && it.isAvailableIn(month) }.map { it.theme }
        val yearRound = destination.experiences.filter { it.isYearRound }.map { it.theme }
        val pleasant = month in destination.pleasantMonths
        if (seasonal.isEmpty() && !pleasant) return null

        val score = SEASONAL_WEIGHT * minOf(seasonal.size, MAX_COUNTED_EXPERIENCES) +
            (if (pleasant) CLIMATE_WEIGHT else 0.0) +
            YEAR_ROUND_WEIGHT * minOf(yearRound.size, MAX_COUNTED_EXPERIENCES)
        return DestinationSuggestion(
            destination = destination,
            score = score.coerceAtMost(1.0),
            seasonalHighlights = seasonal.distinct(),
            yearRoundHighlights = yearRound.distinct(),
            pleasantClimate = pleasant,
        )
    }

    /** Pagina circolare della classifica: "Altre idee" continua a proporre mete diverse. */
    private fun pageOf(ranked: List<DestinationSuggestion>, page: Int, count: Int): List<DestinationSuggestion> {
        if (ranked.isEmpty() || count <= 0) return emptyList()
        val size = minOf(count, ranked.size)
        val start = Math.floorMod(page * count, ranked.size)
        return List(size) { index -> ranked[(start + index) % ranked.size] }
    }

    private companion object {
        const val DEFAULT_COUNT = 6
        const val MAX_COUNTED_EXPERIENCES = 2
        const val SEASONAL_WEIGHT = 0.3
        const val CLIMATE_WEIGHT = 0.3
        const val YEAR_ROUND_WEIGHT = 0.05
    }
}
