package com.partimo.data.remote.osm

import com.partimo.domain.model.GeoPoint
import kotlinx.serialization.Serializable

// DTO della risposta JSON di Overpass API: https://wiki.openstreetmap.org/wiki/Overpass_API/Overpass_QL

@Serializable
internal data class OverpassResponse(
    val elements: List<OsmElement> = emptyList(),
    /** Avvisi del server, es. "runtime error: Query timed out" (risposta incompleta). */
    val remark: String? = null,
)

@Serializable
internal data class OsmElement(
    /** "node", "way" o "relation". */
    val type: String,
    val id: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    /** Centro delle aree (way e relation), presente con `out center`. */
    val center: OsmCenter? = null,
    val tags: Map<String, String> = emptyMap(),
) {
    val location: GeoPoint?
        get() {
            val latitude = lat ?: center?.lat ?: return null
            val longitude = lon ?: center?.lon ?: return null
            return runCatching { GeoPoint(latitude, longitude) }.getOrNull()
        }
}

@Serializable
internal data class OsmCenter(val lat: Double, val lon: Double)
