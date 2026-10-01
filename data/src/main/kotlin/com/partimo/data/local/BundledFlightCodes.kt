package com.partimo.data.local

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.time.ZoneId
import java.util.Locale

/** Codice della città per la ricerca dei voli (FCO e CIA → ROM) e fuso orario di un aeroporto. */
internal data class AirportCodes(val cityCode: String, val timeZone: ZoneId)

/**
 * Dati per la ricerca dei voli inclusi negli asset, dai dati per gli sviluppatori di Travelpayouts: codice
 * della città e fuso orario degli aeroporti di `airports.csv` e nomi delle compagnie aeree. I file vengono
 * letti alla prima richiesta e poi tenuti in memoria.
 */
internal class BundledFlightCodes(
    private val openAirports: () -> InputStream,
    private val openAirlines: () -> InputStream,
) {
    private val mutex = Mutex()

    @Volatile
    private var airports: Map<String, AirportCodes>? = null

    @Volatile
    private var airlines: Map<String, String>? = null

    suspend fun airport(iata: String): AirportCodes? = loadAirports()[iata.uppercase(Locale.ROOT)]

    suspend fun airlineName(iata: String): String? = loadAirlines()[iata.uppercase(Locale.ROOT)]

    private suspend fun loadAirports(): Map<String, AirportCodes> {
        airports?.let { return it }
        return mutex.withLock {
            airports ?: openAirports().bufferedReader(Charsets.UTF_8).useLines { parseAirportCodes(it) }.also { airports = it }
        }
    }

    private suspend fun loadAirlines(): Map<String, String> {
        airlines?.let { return it }
        return mutex.withLock {
            airlines ?: openAirlines().bufferedReader(Charsets.UTF_8).useLines { parseAirlines(it) }.also { airlines = it }
        }
    }
}

/** Formato: `iata;città;fuso`, con la città vuota se coincide con l'aeroporto. Le righe con `#` sono commenti. */
internal fun parseAirportCodes(lines: Sequence<String>): Map<String, AirportCodes> = lines
    .filter { it.isNotBlank() && !it.startsWith("#") }
    .mapNotNull { line ->
        val fields = line.split(';')
        if (fields.size < 3) return@mapNotNull null
        val iata = fields[0].trim().uppercase(Locale.ROOT)
        val zone = runCatching { ZoneId.of(fields[2].trim()) }.getOrNull() ?: return@mapNotNull null
        iata to AirportCodes(cityCode = fields[1].trim().uppercase(Locale.ROOT).ifEmpty { iata }, timeZone = zone)
    }
    .toMap()

/** Formato: `iata;nome`. Le righe con `#` sono commenti. */
internal fun parseAirlines(lines: Sequence<String>): Map<String, String> = lines
    .filter { it.isNotBlank() && !it.startsWith("#") }
    .mapNotNull { line ->
        val code = line.substringBefore(';').trim().uppercase(Locale.ROOT)
        val name = line.substringAfter(';', missingDelimiterValue = "").trim()
        if (code.length == 2 && name.isNotEmpty()) code to name else null
    }
    .toMap()
