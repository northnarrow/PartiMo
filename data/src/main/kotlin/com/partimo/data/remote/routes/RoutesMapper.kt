package com.partimo.data.remote.routes

import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.transit.TransitLeg
import com.partimo.domain.model.transit.TransitLine
import com.partimo.domain.model.transit.TransitMode
import com.partimo.domain.model.transit.TransitPreference
import com.partimo.domain.model.transit.TransitRoute
import com.partimo.domain.model.transit.TransitStop
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Converte una route di Google Routes in un [TransitRoute] di dominio.
 *
 * I passi a piedi arrivano "svolta per svolta" e senza orari: vengono accorpati in un unico tratto
 * e collocati nel tempo rispetto ai mezzi adiacenti, che invece hanno orari assoluti (in tempo reale
 * quando disponibili).
 */
internal object RoutesMapper {

    private const val TRANSIT_TRAVEL_MODE = "TRANSIT"

    fun toDomain(route: RouteDto, requestedDeparture: Instant): TransitRoute? {
        val segments = segmentsOf(route.legs.flatMap { it.steps })
        if (segments.isEmpty()) return null

        // Chi deve prendere il primo mezzo parte prima, di quanto serve per raggiungere la fermata.
        val firstRideIndex = segments.indexOfFirst { it is Segment.Ride }
        var cursor = if (firstRideIndex >= 0) {
            val firstRide = segments[firstRideIndex] as Segment.Ride
            val walkBefore = segments.take(firstRideIndex)
                .filterIsInstance<Segment.Walk>()
                .fold(Duration.ZERO) { total, walk -> total + walk.duration }
            (firstRide.departure ?: requestedDeparture).minus(walkBefore)
        } else {
            requestedDeparture
        }

        val legs = segments.mapIndexed { index, segment ->
            when (segment) {
                is Segment.Walk -> TransitLeg(
                    mode = TransitMode.WALK,
                    departureTime = cursor,
                    arrivalTime = cursor.plus(segment.duration),
                    departureStop = (segments.getOrNull(index - 1) as? Segment.Ride)?.arrivalStop,
                    arrivalStop = (segments.getOrNull(index + 1) as? Segment.Ride)?.departureStop,
                    distanceMeters = segment.distanceMeters,
                )

                is Segment.Ride -> {
                    val departure = segment.departure ?: cursor
                    TransitLeg(
                        mode = vehicleTypeToMode(segment.details.transitLine?.vehicle?.type),
                        departureTime = departure,
                        arrivalTime = segment.arrival ?: departure.plus(segment.staticDuration),
                        departureStop = segment.departureStop,
                        arrivalStop = segment.arrivalStop,
                        line = segment.details.transitLine?.toDomain(),
                        headsign = segment.details.headsign,
                        stopCount = segment.details.stopCount,
                        distanceMeters = segment.step.distanceMeters,
                    )
                }
            }.also { leg -> cursor = leg.arrivalTime }
        }
        return TransitRoute(legs)
    }

    private fun segmentsOf(steps: List<RouteStepDto>): List<Segment> {
        val segments = mutableListOf<Segment>()
        for (step in steps) {
            val details = step.transitDetails
            if (step.travelMode == TRANSIT_TRAVEL_MODE && details != null) {
                segments += Segment.Ride(step, details)
                continue
            }
            val walk = Segment.Walk(parseDuration(step.staticDuration), step.distanceMeters ?: 0)
            if (walk.duration.isZero && walk.distanceMeters == 0) continue
            val previous = segments.lastOrNull()
            if (previous is Segment.Walk) {
                segments[segments.lastIndex] = Segment.Walk(
                    duration = previous.duration + walk.duration,
                    distanceMeters = previous.distanceMeters + walk.distanceMeters,
                )
            } else {
                segments += walk
            }
        }
        return segments
    }

    private sealed interface Segment {
        data class Walk(val duration: Duration, val distanceMeters: Int) : Segment

        data class Ride(val step: RouteStepDto, val details: TransitDetailsDto) : Segment {
            val departure: Instant? get() = details.stopDetails?.departureTime?.let(::parseInstant)
            val arrival: Instant? get() = details.stopDetails?.arrivalTime?.let(::parseInstant)
            val departureStop: TransitStop? get() = details.stopDetails?.departureStop?.toDomain()
            val arrivalStop: TransitStop? get() = details.stopDetails?.arrivalStop?.toDomain()
            val staticDuration: Duration get() = parseDuration(step.staticDuration)
        }
    }
}

/** Tipi di veicolo di Routes API → modalità di dominio. */
internal fun vehicleTypeToMode(type: String?): TransitMode = when (type) {
    "BUS", "INTERCITY_BUS", "TROLLEYBUS", "SHARE_TAXI" -> TransitMode.BUS
    "TRAM" -> TransitMode.TRAM
    "SUBWAY", "METRO_RAIL" -> TransitMode.METRO
    "RAIL", "HEAVY_RAIL", "COMMUTER_TRAIN", "HIGH_SPEED_TRAIN", "LONG_DISTANCE_TRAIN", "MONORAIL" -> TransitMode.TRAIN
    "FERRY" -> TransitMode.FERRY
    "CABLE_CAR", "GONDOLA_LIFT", "FUNICULAR" -> TransitMode.CABLE_CAR
    else -> TransitMode.OTHER
}

/** Mezzi ammessi → filtro `allowedTravelModes` (null = nessuna restrizione). */
internal fun Set<TransitMode>.toAllowedTravelModes(): List<String>? {
    if (containsAll(TransitMode.PUBLIC_MODES)) return null
    return mapNotNull { mode ->
        when (mode) {
            TransitMode.BUS -> "BUS"
            TransitMode.METRO -> "SUBWAY"
            TransitMode.TRAIN -> "TRAIN"
            TransitMode.TRAM -> "LIGHT_RAIL"
            else -> null
        }
    }.distinct().sorted().ifEmpty { null }
}

internal fun TransitPreference.toRoutingPreference(): String? = when (this) {
    TransitPreference.FASTEST -> null
    TransitPreference.FEWER_TRANSFERS -> "FEWER_TRANSFERS"
    TransitPreference.LESS_WALKING -> "LESS_WALKING"
}

internal fun GeoPoint.toWaypoint(): RoutesWaypoint = RoutesWaypoint(RoutesLocation(RoutesLatLng(latitude, longitude)))

/** Durate di Routes API nel formato "123s" (anche con decimali). */
internal fun parseDuration(value: String?): Duration {
    val seconds = value?.removeSuffix("s")?.toDoubleOrNull() ?: return Duration.ZERO
    return Duration.ofMillis((seconds * 1_000).toLong())
}

internal fun parseInstant(value: String): Instant? =
    runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()

private fun TransitStopDto.toDomain(): TransitStop? {
    val stopName = name ?: return null
    val point = location?.latLng?.let { runCatching { GeoPoint(it.latitude, it.longitude) }.getOrNull() }
    return TransitStop(stopName, point)
}

private fun TransitLineDto.toDomain(): TransitLine? {
    val lineName = nameShort ?: name ?: return null
    return TransitLine(
        name = name ?: lineName,
        shortName = nameShort,
        colorHex = color,
        textColorHex = textColor,
        agencyName = agencies.firstOrNull()?.name,
    )
}
