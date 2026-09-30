package com.partimo.data.remote.routes

import kotlinx.serialization.Serializable

// DTO di Google Routes API – computeRoutes con travelMode TRANSIT:
// https://developers.google.com/maps/documentation/routes/transit-route

@Serializable
internal data class ComputeRoutesRequest(
    val origin: RoutesWaypoint,
    val destination: RoutesWaypoint,
    val travelMode: String = "TRANSIT",
    /** Orario RFC 3339 (es. 2026-12-12T09:30:00Z). */
    val departureTime: String? = null,
    val computeAlternativeRoutes: Boolean = true,
    val languageCode: String? = null,
    val units: String = "METRIC",
    val transitPreferences: RoutesTransitPreferences? = null,
)

@Serializable
internal data class RoutesWaypoint(val location: RoutesLocation)

@Serializable
internal data class RoutesLocation(val latLng: RoutesLatLng)

@Serializable
internal data class RoutesLatLng(val latitude: Double, val longitude: Double)

@Serializable
internal data class RoutesTransitPreferences(
    /** Valori ammessi: BUS, SUBWAY, TRAIN, LIGHT_RAIL, RAIL. */
    val allowedTravelModes: List<String>? = null,
    /** LESS_WALKING oppure FEWER_TRANSFERS. */
    val routingPreference: String? = null,
)

@Serializable
internal data class ComputeRoutesResponse(val routes: List<RouteDto> = emptyList())

@Serializable
internal data class RouteDto(
    val legs: List<RouteLegDto> = emptyList(),
    val duration: String? = null,
    val distanceMeters: Int? = null,
)

@Serializable
internal data class RouteLegDto(val steps: List<RouteStepDto> = emptyList())

@Serializable
internal data class RouteStepDto(
    val travelMode: String? = null,
    /** Durata nel formato "123s". */
    val staticDuration: String? = null,
    val distanceMeters: Int? = null,
    val transitDetails: TransitDetailsDto? = null,
)

@Serializable
internal data class TransitDetailsDto(
    val stopDetails: TransitStopDetailsDto? = null,
    val headsign: String? = null,
    val transitLine: TransitLineDto? = null,
    val stopCount: Int? = null,
)

@Serializable
internal data class TransitStopDetailsDto(
    val arrivalStop: TransitStopDto? = null,
    val arrivalTime: String? = null,
    val departureStop: TransitStopDto? = null,
    val departureTime: String? = null,
)

@Serializable
internal data class TransitStopDto(val name: String? = null, val location: RoutesLocation? = null)

@Serializable
internal data class TransitLineDto(
    val agencies: List<TransitAgencyDto> = emptyList(),
    val name: String? = null,
    val nameShort: String? = null,
    val color: String? = null,
    val textColor: String? = null,
    val vehicle: TransitVehicleDto? = null,
)

@Serializable
internal data class TransitAgencyDto(val name: String? = null)

@Serializable
internal data class TransitVehicleDto(val name: RoutesLocalizedText? = null, val type: String? = null)

@Serializable
internal data class RoutesLocalizedText(val text: String? = null)
