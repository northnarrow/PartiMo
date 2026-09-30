package com.partimo.domain.model.transit

import com.partimo.domain.model.GeoPoint
import java.time.Duration
import java.time.Instant

enum class TransitMode {
    WALK, BUS, TRAM, METRO, TRAIN, FERRY, CABLE_CAR, OTHER;

    companion object {
        /** Tutti i mezzi pubblici (esclusa la camminata). */
        val PUBLIC_MODES: Set<TransitMode> = entries.toSet() - WALK
    }
}

data class TransitStop(val name: String, val location: GeoPoint? = null)

data class TransitLine(
    val name: String,
    val shortName: String? = null,
    /** Colore della linea in formato esadecimale (#RRGGBB), se fornito dall'operatore. */
    val colorHex: String? = null,
    val textColorHex: String? = null,
    val agencyName: String? = null,
)

/**
 * Tratto di un percorso: a piedi oppure a bordo di un mezzo.
 * Per i tratti a piedi le fermate possono mancare (partenza o arrivo nel punto richiesto).
 */
data class TransitLeg(
    val mode: TransitMode,
    val departureTime: Instant,
    val arrivalTime: Instant,
    val departureStop: TransitStop? = null,
    val arrivalStop: TransitStop? = null,
    val line: TransitLine? = null,
    val headsign: String? = null,
    val stopCount: Int? = null,
    val distanceMeters: Int? = null,
) {
    val duration: Duration get() = Duration.between(departureTime, arrivalTime)
    val isWalking: Boolean get() = mode == TransitMode.WALK
}

/** Coincidenza tra due mezzi consecutivi dello stesso percorso. */
data class TransferConnection(
    val arrivingLeg: TransitLeg,
    val departingLeg: TransitLeg,
) {
    /** Tempo disponibile per il cambio (incluso l'eventuale tratto a piedi). */
    val transferTime: Duration get() = Duration.between(arrivingLeg.arrivalTime, departingLeg.departureTime)
    val stopName: String? get() = departingLeg.departureStop?.name

    /** Coincidenza impossibile: il secondo mezzo parte prima dell'arrivo del primo. */
    val isMissed: Boolean get() = transferTime.isNegative

    fun isTight(minimum: Duration = MIN_COMFORTABLE_TRANSFER): Boolean = transferTime < minimum

    companion object {
        val MIN_COMFORTABLE_TRANSFER: Duration = Duration.ofMinutes(3)
    }
}

/** Percorso multimodale (es. a piedi → metro → tram → a piedi). */
data class TransitRoute(val legs: List<TransitLeg>) {

    init {
        require(legs.isNotEmpty()) { "Un percorso contiene almeno un tratto" }
    }

    val departureTime: Instant get() = legs.first().departureTime
    val arrivalTime: Instant get() = legs.last().arrivalTime
    val totalDuration: Duration get() = Duration.between(departureTime, arrivalTime)
    val transitLegs: List<TransitLeg> get() = legs.filterNot { it.isWalking }
    val transfers: Int get() = (transitLegs.size - 1).coerceAtLeast(0)
    val modes: Set<TransitMode> get() = transitLegs.mapTo(mutableSetOf()) { it.mode }

    val walkingDuration: Duration
        get() = legs.filter { it.isWalking }.fold(Duration.ZERO) { total, leg -> total + leg.duration }

    /** Coincidenze tra mezzi consecutivi, con il relativo tempo di cambio. */
    val connections: List<TransferConnection>
        get() = transitLegs.zipWithNext { arriving, departing -> TransferConnection(arriving, departing) }
}

enum class TransitPreference { FASTEST, FEWER_TRANSFERS, LESS_WALKING }

data class TransitRouteQuery(
    val origin: GeoPoint,
    val destination: GeoPoint,
    val departureTime: Instant,
    val allowedModes: Set<TransitMode> = TransitMode.PUBLIC_MODES,
    val preference: TransitPreference = TransitPreference.FASTEST,
    val maxWalking: Duration? = null,
)
