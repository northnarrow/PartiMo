package com.partimo.domain.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Coordinata geografica WGS84. */
data class GeoPoint(val latitude: Double, val longitude: Double) {

    init {
        require(latitude in -90.0..90.0) { "Latitudine fuori intervallo: $latitude" }
        require(longitude in -180.0..180.0) { "Longitudine fuori intervallo: $longitude" }
    }

    /** Emisfero del punto: determina l'inversione delle stagioni. */
    val hemisphere: Hemisphere
        get() = if (latitude < 0) Hemisphere.SOUTHERN else Hemisphere.NORTHERN

    /** Distanza in metri calcolata con la formula dell'haversine. */
    fun distanceTo(other: GeoPoint): Double {
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(latitude)) * cos(Math.toRadians(other.latitude)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}

enum class Hemisphere { NORTHERN, SOUTHERN }
