package com.partimo.data.local

import com.partimo.data.network.Fetched
import com.partimo.data.source.AirportDataSource
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.place.Airport
import com.partimo.domain.model.place.AirportSize
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream

/**
 * Aeroporti con voli di linea dal dataset OurAirports (pubblico dominio) incluso negli asset, con gli
 * hub internazionali principali contrassegnati. Il file viene letto una sola volta, alla prima
 * richiesta, e poi mantenuto in memoria (~3.200 voci).
 */
class BundledAirportsDataSource(
    private val openCsv: () -> InputStream,
) : AirportDataSource {

    private val mutex = Mutex()

    @Volatile
    private var airports: List<Airport>? = null

    override suspend fun airportsNear(location: GeoPoint, radiusKm: Double): Fetched<List<Airport>> {
        val radiusMeters = radiusKm * METERS_PER_KM
        val nearby = loadAirports()
            .map { airport -> airport to airport.location.distanceTo(location) }
            .filter { (_, meters) -> meters <= radiusMeters }
            .sortedBy { (_, meters) -> meters }
            .map { (airport, _) -> airport }
        return Fetched(nearby, DataOrigin.LOCAL)
    }

    override suspend fun findByIata(iata: String): Airport? =
        loadAirports().firstOrNull { it.iata.equals(iata, ignoreCase = true) }

    private suspend fun loadAirports(): List<Airport> {
        airports?.let { return it }
        return mutex.withLock {
            airports ?: openCsv().bufferedReader(Charsets.UTF_8).useLines { parseAirportsCsv(it) }.also { airports = it }
        }
    }

    private companion object {
        const val METERS_PER_KM = 1_000.0
    }
}

/** Formato: `iata;nome;città;paese;lat;lon;H|L|M`. Le righe che iniziano con `#` sono commenti. */
internal fun parseAirportsCsv(lines: Sequence<String>): List<Airport> = lines
    .filter { it.isNotBlank() && !it.startsWith("#") }
    .mapNotNull { line ->
        val fields = line.split(';')
        if (fields.size < 7) return@mapNotNull null
        runCatching {
            Airport(
                iata = fields[0].trim().uppercase(),
                name = fields[1].trim(),
                city = fields[2].trim().ifBlank { null },
                countryCode = fields[3].trim(),
                location = GeoPoint(fields[4].toDouble(), fields[5].toDouble()),
                size = when (fields[6].trim()) {
                    "H" -> AirportSize.HUB
                    "L" -> AirportSize.LARGE
                    else -> AirportSize.MEDIUM
                },
            )
        }.getOrNull()
    }
    .toList()
