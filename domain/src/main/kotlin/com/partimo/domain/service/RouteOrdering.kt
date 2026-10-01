package com.partimo.domain.service

import com.partimo.domain.model.GeoPoint

/** Ordine di visita di più posti, per un giro a piedi senza avanti e indietro. */
object RouteOrdering {

    /**
     * "Vicino per vicino": si parte dal primo elemento e ogni volta si va al più vicino non ancora
     * visitato. Non è il percorso ottimo, ma per una decina di tappe in città è più che sufficiente.
     */
    fun <T> nearestNeighbor(items: List<T>, location: (T) -> GeoPoint): List<T> {
        if (items.size <= 2) return items
        val remaining = items.drop(1).toMutableList()
        val ordered = mutableListOf(items.first())
        while (remaining.isNotEmpty()) {
            val current = location(ordered.last())
            val next = remaining.minBy { current.distanceTo(location(it)) }
            remaining.remove(next)
            ordered += next
        }
        return ordered
    }
}
