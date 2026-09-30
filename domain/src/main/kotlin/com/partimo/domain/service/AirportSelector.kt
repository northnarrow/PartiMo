package com.partimo.domain.service

import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.place.Airport
import com.partimo.domain.model.place.AirportOption
import com.partimo.domain.model.place.AirportSize
import kotlin.math.roundToInt

/**
 * Sceglie l'aeroporto più adatto a una città: quello con più voli e collegamenti, purché non sia
 * molto più lontano del più vicino. Prima gli hub internazionali, poi i grandi aeroporti, altrimenti
 * il più vicino. Esempi: per Roma Fiumicino (hub, 23 km) batte Ciampino (14 km); per Pisa vince il suo
 * aeroporto (4 km) e non Bologna (115 km).
 */
class AirportSelector {

    fun select(center: GeoPoint, candidates: List<Airport>): Airport? {
        val byDistance = byDistance(center, candidates)
        val nearest = byDistance.firstOrNull() ?: return null

        fun preferred(size: AirportSize) = byDistance.firstOrNull { (airport, meters) ->
            airport.size == size &&
                meters <= MAX_PREFERRED_AIRPORT_METERS &&
                meters - nearest.second <= MAX_EXTRA_METERS_FOR_PREFERRED
        }
        return (preferred(AirportSize.HUB) ?: preferred(AirportSize.LARGE) ?: nearest).first
    }

    /** Opzioni per la scelta della partenza: prima l'aeroporto consigliato, poi gli altri per distanza. */
    fun options(center: GeoPoint, candidates: List<Airport>, limit: Int = MAX_OPTIONS): List<AirportOption> {
        val recommended = select(center, candidates)
        return byDistance(center, candidates)
            .sortedByDescending { (airport, _) -> airport == recommended } // ordinamento stabile: poi per distanza
            .take(limit)
            .map { (airport, meters) ->
                AirportOption(airport, distanceKm = (meters / METERS_PER_KM).roundToInt(), recommended = airport == recommended)
            }
    }

    private fun byDistance(center: GeoPoint, candidates: List<Airport>): List<Pair<Airport, Double>> =
        candidates.map { it to it.location.distanceTo(center) }.sortedBy { (_, meters) -> meters }

    private companion object {
        const val MAX_PREFERRED_AIRPORT_METERS = 150_000.0
        const val MAX_EXTRA_METERS_FOR_PREFERRED = 40_000.0
        const val METERS_PER_KM = 1_000.0
        const val MAX_OPTIONS = 5
    }
}
