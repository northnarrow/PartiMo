package com.partimo.domain.usecase

import com.partimo.domain.common.DataResult
import com.partimo.domain.common.getOrNull
import com.partimo.domain.model.Destination
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate

/** Esito dello scaricamento di un viaggio per l'uso offline. */
data class PrefetchResult(
    val succeeded: Int,
    val failed: Int,
    /** Foto dei luoghi e degli eventi, da scaricare nella cache delle immagini. */
    val photoUrls: List<String>,
)

/**
 * Prepara un viaggio per l'uso senza rete: carica nelle cache luoghi da vedere, eventi, ristoranti,
 * alloggi, guida di Wikivoyage, meteo e cambio, con le stesse ricerche della dashboard. Le fonti
 * sono indipendenti: una che non risponde non ferma le altre.
 */
class PrefetchTripUseCase(
    private val getSeasonalHighlights: GetSeasonalHighlightsUseCase,
    private val getTripEvents: GetTripEventsUseCase,
    private val findBudgetRestaurants: FindBudgetRestaurantsUseCase,
    private val findLodgings: FindLodgingsUseCase,
    private val getTravelGuide: GetTravelGuideUseCase,
    private val getTripWeather: GetTripWeatherUseCase,
    private val getCountryInfo: GetCountryInfoUseCase,
    private val getExchangeRate: GetExchangeRateUseCase,
) {

    suspend operator fun invoke(destination: Destination, from: LocalDate, to: LocalDate): PrefetchResult = coroutineScope {
        val highlights = async { getSeasonalHighlights(location = destination.center, travelDate = from, areaName = destination.name) }
        val events = async { getTripEvents(destination, from, to) }
        val others = listOf(
            async { findBudgetRestaurants(location = destination.center, areaName = destination.name) },
            async { findLodgings(destination.center) },
            async { getTravelGuide(destination) },
            async { getTripWeather(destination.center, from, to) },
            async {
                val currency = getCountryInfo(destination.countryCode).getOrNull()?.takeUnless { it.usesEuro }?.currencyCode
                currency?.let { getExchangeRate(to = it) }
            },
        )
        val highlightsResult = highlights.await()
        val eventsResult = events.await()
        val results: List<DataResult<*>> = listOf(highlightsResult, eventsResult) + others.awaitAll().filterNotNull()
        val photos = highlightsResult.getOrNull()?.recommendations.orEmpty().mapNotNull { it.poi.photoUrl } +
            eventsResult.getOrNull()?.events.orEmpty().mapNotNull { it.photoUrl }
        PrefetchResult(
            succeeded = results.count { it is DataResult.Success },
            failed = results.count { it is DataResult.Failure },
            photoUrls = photos.distinct().take(MAX_PHOTOS),
        )
    }

    companion object {
        /** Abbastanza per le schede della dashboard senza riempire la memoria del telefono. */
        const val MAX_PHOTOS = 30
    }
}
