package com.partimo.data.remote.places

import com.partimo.data.cache.CacheKey
import com.partimo.data.cache.CachePolicy
import com.partimo.data.cache.ResponseCache
import com.partimo.data.network.Fetched
import com.partimo.data.network.NetworkJson
import com.partimo.data.network.bestEffort
import com.partimo.data.network.mapNotNullSafely
import com.partimo.data.source.PoiDataSource
import com.partimo.data.source.RestaurantDataSource
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.model.dining.Restaurant
import com.partimo.domain.model.dining.RestaurantSearchQuery
import com.partimo.domain.model.poi.PoiQuery
import com.partimo.domain.model.poi.PointOfInterest
import com.partimo.domain.model.poi.SeasonalTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Ristoranti da Google Places Text Search, con pre-filtro lato API su fascia di prezzo e valutazione. */
class GooglePlacesRestaurantDataSource internal constructor(
    private val api: GooglePlacesApi,
    private val cache: ResponseCache,
    private val languageCode: String,
) : RestaurantDataSource {

    override suspend fun searchRestaurants(query: RestaurantSearchQuery, forceRefresh: Boolean): Fetched<List<Restaurant>> {
        val request = PlacesTextSearchRequest(
            textQuery = withArea(query.keyword ?: DEFAULT_TEXT_QUERY, query.areaName),
            includedType = RESTAURANT_TYPE,
            strictTypeFiltering = true,
            locationBias = query.location.toLocationBias(query.radiusMeters),
            minRating = query.minRating?.let(::placesMinRating),
            priceLevels = query.priceLevels.mapNotNull { it.toPlacesFilterValue() }.sorted().ifEmpty { null },
            openNow = query.openNow.takeIf { it },
            pageSize = PAGE_SIZE,
            languageCode = languageCode,
        )
        return cache.getOrFetch(
            key = CacheKey.of("restaurants", NetworkJson.encodeToString(PlacesTextSearchRequest.serializer(), request)),
            ttl = CachePolicy.RESTAURANTS,
            forceRefresh = forceRefresh,
            fetch = { api.searchText(request, FIELD_MASK) },
            parse = { body ->
                NetworkJson.decodeFromString(PlacesSearchResponse.serializer(), body)
                    .places
                    .mapNotNullSafely { place -> place.toRestaurant { photo -> api.photoUrl(photo) } }
            },
        )
    }

    internal companion object {
        const val DEFAULT_TEXT_QUERY = "ristoranti"
        const val RESTAURANT_TYPE = "restaurant"
        const val PAGE_SIZE = 20
        val FIELD_MASK = listOf(
            "places.id",
            "places.displayName",
            "places.formattedAddress",
            "places.location",
            "places.rating",
            "places.userRatingCount",
            "places.priceLevel",
            "places.primaryTypeDisplayName",
            "places.photos",
            "places.currentOpeningHours.openNow",
            "places.googleMapsUri",
        ).joinToString(",")
    }
}

/**
 * Punti di interesse da Google Places: una ricerca generica delle attrazioni più una ricerca per
 * ciascun tema stagionale del mese (es. "mercatini di Natale" a dicembre).
 * Le ricerche tematiche sono un arricchimento: se falliscono restano le attrazioni generiche.
 */
class GooglePlacesPoiDataSource internal constructor(
    private val api: GooglePlacesApi,
    private val cache: ResponseCache,
    private val languageCode: String,
) : PoiDataSource {

    override suspend fun pointsOfInterest(query: PoiQuery, forceRefresh: Boolean): Fetched<List<PointOfInterest>> =
        coroutineScope {
            val themed = query.seasonalThemes.map { theme ->
                async { bestEffort { search(withArea(theme.searchQuery, query.areaName), theme, query, forceRefresh) } }
            }
            val general = search(withArea(DEFAULT_TEXT_QUERY, query.areaName), theme = null, query = query, forceRefresh = forceRefresh)
            val results = themed.awaitAll().filterNotNull() + general

            // I risultati tematici vengono per primi: a parità di id conservano categoria e mesi del tema.
            Fetched(
                data = results.flatMap { it.data }.distinctBy { it.id },
                origin = combinedOrigin(results.map { it.origin }),
            )
        }

    private suspend fun search(
        textQuery: String,
        theme: SeasonalTheme?,
        query: PoiQuery,
        forceRefresh: Boolean,
    ): Fetched<List<PointOfInterest>> {
        val request = PlacesTextSearchRequest(
            textQuery = textQuery,
            locationBias = query.location.toLocationBias(query.radiusMeters),
            pageSize = PAGE_SIZE,
            languageCode = languageCode,
        )
        return cache.getOrFetch(
            key = CacheKey.of("poi", NetworkJson.encodeToString(PlacesTextSearchRequest.serializer(), request), theme?.category),
            ttl = CachePolicy.POIS,
            forceRefresh = forceRefresh,
            fetch = { api.searchText(request, FIELD_MASK) },
            parse = { body ->
                NetworkJson.decodeFromString(PlacesSearchResponse.serializer(), body)
                    .places
                    .mapNotNullSafely { place -> place.toPointOfInterest(theme) { photo -> api.photoUrl(photo) } }
            },
        )
    }

    internal companion object {
        const val DEFAULT_TEXT_QUERY = "attrazioni turistiche"
        const val PAGE_SIZE = 20
        val FIELD_MASK = listOf(
            "places.id",
            "places.displayName",
            "places.formattedAddress",
            "places.location",
            "places.rating",
            "places.userRatingCount",
            "places.types",
            "places.primaryType",
            "places.photos",
            "places.editorialSummary",
            "places.googleMapsUri",
        ).joinToString(",")
    }
}

/** Aggiunge il nome della zona alla ricerca testuale (es. "ristoranti a Lisbona"). */
internal fun withArea(textQuery: String, areaName: String?): String =
    if (areaName.isNullOrBlank()) textQuery else "$textQuery a ${areaName.trim()}"

/** Provenienza complessiva di più risposte: basta un dato "stale" perché la UI lo segnali. */
internal fun combinedOrigin(origins: List<DataOrigin>): DataOrigin = when {
    origins.isEmpty() -> DataOrigin.REMOTE
    DataOrigin.STALE_CACHE in origins -> DataOrigin.STALE_CACHE
    origins.all { it == DataOrigin.CACHE } -> DataOrigin.CACHE
    origins.all { it == DataOrigin.DEMO } -> DataOrigin.DEMO
    else -> DataOrigin.REMOTE
}
