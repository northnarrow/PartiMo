package com.partimo.data.remote.places

import kotlinx.serialization.Serializable

// DTO di Google Places API (New) – Text Search:
// https://developers.google.com/maps/documentation/places/web-service/text-search

@Serializable
internal data class PlacesTextSearchRequest(
    val textQuery: String,
    val includedType: String? = null,
    val strictTypeFiltering: Boolean? = null,
    val locationBias: PlacesLocationBias? = null,
    val minRating: Double? = null,
    val priceLevels: List<String>? = null,
    val openNow: Boolean? = null,
    val pageSize: Int? = null,
    val languageCode: String? = null,
)

@Serializable
internal data class PlacesLocationBias(val circle: PlacesCircle)

@Serializable
internal data class PlacesCircle(val center: PlacesLatLng, val radius: Double)

@Serializable
internal data class PlacesLatLng(val latitude: Double, val longitude: Double)

@Serializable
internal data class PlacesSearchResponse(val places: List<PlaceDto> = emptyList())

@Serializable
internal data class PlaceDto(
    val id: String,
    val displayName: PlacesLocalizedText? = null,
    val formattedAddress: String? = null,
    val location: PlacesLatLng? = null,
    val rating: Double? = null,
    val userRatingCount: Int? = null,
    val priceLevel: String? = null,
    val types: List<String> = emptyList(),
    val primaryType: String? = null,
    val primaryTypeDisplayName: PlacesLocalizedText? = null,
    val photos: List<PlacePhotoDto> = emptyList(),
    val currentOpeningHours: PlacesOpeningHours? = null,
    val editorialSummary: PlacesLocalizedText? = null,
    val googleMapsUri: String? = null,
)

@Serializable
internal data class PlacesLocalizedText(val text: String? = null, val languageCode: String? = null)

@Serializable
internal data class PlacePhotoDto(val name: String, val widthPx: Int? = null, val heightPx: Int? = null)

@Serializable
internal data class PlacesOpeningHours(val openNow: Boolean? = null)
