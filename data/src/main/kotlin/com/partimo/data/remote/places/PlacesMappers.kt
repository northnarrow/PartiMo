package com.partimo.data.remote.places

import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.dining.PriceLevel
import com.partimo.domain.model.dining.Restaurant
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.poi.PointOfInterest
import com.partimo.domain.model.poi.SeasonalTheme
import kotlin.math.floor

internal fun PlaceDto.toRestaurant(photoUrl: (String) -> String): Restaurant? {
    val name = displayName?.text ?: return null
    return Restaurant(
        id = id,
        name = name,
        priceLevel = priceLevel?.let(::placesPriceLevelToDomain),
        rating = rating,
        reviewCount = userRatingCount,
        cuisine = primaryTypeDisplayName?.text,
        address = formattedAddress,
        location = location?.toGeoPointOrNull(),
        photoUrl = photos.firstOrNull()?.let { photoUrl(it.name) },
        isOpenNow = currentOpeningHours?.openNow,
        mapsUrl = googleMapsUri,
    )
}

/**
 * @param theme tema stagionale che ha prodotto il risultato: ne eredita categoria e mesi di attività
 * (es. un risultato di "mercatini di Natale" è attivo novembre–gennaio).
 */
internal fun PlaceDto.toPointOfInterest(theme: SeasonalTheme?, photoUrl: (String) -> String): PointOfInterest? {
    val name = displayName?.text ?: return null
    val geoPoint = location?.toGeoPointOrNull() ?: return null
    val category = theme?.category ?: categoryFromPlaceTypes(listOfNotNull(primaryType) + types)
    return PointOfInterest(
        id = id,
        name = name,
        category = category,
        location = geoPoint,
        rating = rating,
        reviewCount = userRatingCount,
        description = editorialSummary?.text,
        photoUrl = photos.firstOrNull()?.let { photoUrl(it.name) },
        activeMonths = theme?.activeMonths.orEmpty(),
        mapsUrl = googleMapsUri,
    )
}

/** Tipi di Places (Table A) → categorie di dominio: vince il primo tipo riconosciuto. */
internal fun categoryFromPlaceTypes(types: List<String>): PoiCategory =
    types.firstNotNullOfOrNull { PLACE_TYPE_CATEGORIES[it] } ?: PoiCategory.ATTRACTION

private val PLACE_TYPE_CATEGORIES: Map<String, PoiCategory> = mapOf(
    "museum" to PoiCategory.MUSEUM,
    "art_gallery" to PoiCategory.MUSEUM,
    "observation_deck" to PoiCategory.VIEWPOINT,
    "park" to PoiCategory.PARK,
    "national_park" to PoiCategory.PARK,
    "state_park" to PoiCategory.PARK,
    "garden" to PoiCategory.PARK,
    "botanical_garden" to PoiCategory.PARK,
    "hiking_area" to PoiCategory.PARK,
    "beach" to PoiCategory.BEACH,
    "church" to PoiCategory.RELIGIOUS_SITE,
    "place_of_worship" to PoiCategory.RELIGIOUS_SITE,
    "mosque" to PoiCategory.RELIGIOUS_SITE,
    "synagogue" to PoiCategory.RELIGIOUS_SITE,
    "hindu_temple" to PoiCategory.RELIGIOUS_SITE,
    "historical_landmark" to PoiCategory.MONUMENT,
    "historical_place" to PoiCategory.MONUMENT,
    "cultural_landmark" to PoiCategory.MONUMENT,
    "monument" to PoiCategory.MONUMENT,
    "sculpture" to PoiCategory.MONUMENT,
    "market" to PoiCategory.MARKET,
    "farmers_market" to PoiCategory.MARKET,
    "ski_resort" to PoiCategory.SKI_AREA,
    "shopping_mall" to PoiCategory.SHOPPING,
    "department_store" to PoiCategory.SHOPPING,
    "plaza" to PoiCategory.NEIGHBORHOOD,
    "tourist_attraction" to PoiCategory.ATTRACTION,
    "amusement_park" to PoiCategory.ATTRACTION,
    "zoo" to PoiCategory.ATTRACTION,
)

internal fun placesPriceLevelToDomain(value: String): PriceLevel? = when (value) {
    "PRICE_LEVEL_FREE" -> PriceLevel.FREE
    "PRICE_LEVEL_INEXPENSIVE" -> PriceLevel.INEXPENSIVE
    "PRICE_LEVEL_MODERATE" -> PriceLevel.MODERATE
    "PRICE_LEVEL_EXPENSIVE" -> PriceLevel.EXPENSIVE
    "PRICE_LEVEL_VERY_EXPENSIVE" -> PriceLevel.VERY_EXPENSIVE
    else -> null
}

/** Il filtro `priceLevels` di Places non accetta il livello "gratuito". */
internal fun PriceLevel.toPlacesFilterValue(): String? = when (this) {
    PriceLevel.FREE -> null
    PriceLevel.INEXPENSIVE -> "PRICE_LEVEL_INEXPENSIVE"
    PriceLevel.MODERATE -> "PRICE_LEVEL_MODERATE"
    PriceLevel.EXPENSIVE -> "PRICE_LEVEL_EXPENSIVE"
    PriceLevel.VERY_EXPENSIVE -> "PRICE_LEVEL_VERY_EXPENSIVE"
}

/**
 * Places arrotonda `minRating` per eccesso a multipli di 0,5 (4,3 diventerebbe 4,5 escludendo
 * locali validi): si invia il multiplo inferiore e la soglia esatta viene applicata dal dominio.
 */
internal fun placesMinRating(minRating: Double): Double = floor(minRating * 2) / 2

internal fun GeoPoint.toLocationBias(radiusMeters: Int): PlacesLocationBias = PlacesLocationBias(
    PlacesCircle(
        center = PlacesLatLng(latitude, longitude),
        radius = radiusMeters.toDouble().coerceIn(1.0, MAX_RADIUS_METERS),
    ),
)

private const val MAX_RADIUS_METERS = 50_000.0

private fun PlacesLatLng.toGeoPointOrNull(): GeoPoint? = runCatching { GeoPoint(latitude, longitude) }.getOrNull()
