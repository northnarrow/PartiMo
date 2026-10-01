package com.partimo.data.local

import com.partimo.domain.model.GeoPoint
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.time.ZoneId
import java.util.Locale

/** Codice della città per la ricerca dei voli (FCO e CIA → ROM) e fuso orario di un aeroporto. */
internal data class AirportCodes(val cityCode: String, val timeZone: ZoneId)

/** Città di un codice dei voli (es. LON → Londra), con il nome in italiano. */
internal data class FlightCity(
    val code: String,
    val name: String,
    val countryCode: String,
    val location: GeoPoint,
    val timeZone: ZoneId,
)

/**
 * Dati per la ricerca dei voli inclusi negli asset, dai dati per gli sviluppatori di Travelpayouts: codice
 * della città e fuso orario degli aeroporti di `airports.csv`, nomi delle compagnie aeree e delle città (in
 * italiano). I file vengono letti alla prima richiesta e poi tenuti in memoria.
 */
internal class BundledFlightCodes(
    private val openAirports: () -> InputStream,
    private val openAirlines: () -> InputStream,
    private val openCities: () -> InputStream = { "".byteInputStream() },
) {
    private val mutex = Mutex()

    @Volatile
    private var airports: Map<String, AirportCodes>? = null

    @Volatile
    private var airlines: Map<String, String>? = null

    @Volatile
    private var cities: Map<String, FlightCity>? = null

    suspend fun airport(iata: String): AirportCodes? = loadAirports()[iata.uppercase(Locale.ROOT)]

    suspend fun airlineName(iata: String): String? = loadAirlines()[iata.uppercase(Locale.ROOT)]

    /** Città con il codice dei voli [code] (es. "BCN" → Barcellona); `null` se non è tra quelle incluse. */
    suspend fun city(code: String): FlightCity? = loadCities()[code.uppercase(Locale.ROOT)]

    private suspend fun loadCities(): Map<String, FlightCity> {
        cities?.let { return it }
        return mutex.withLock {
            cities ?: openCities().bufferedReader(Charsets.UTF_8).useLines { parseFlightCities(it) }.also { cities = it }
        }
    }

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

/** Formato: `codice;nome;paese;lat;lon;fuso`. Le righe con `#` sono commenti, quelle non valide si scartano. */
internal fun parseFlightCities(lines: Sequence<String>): Map<String, FlightCity> = lines
    .filter { it.isNotBlank() && !it.startsWith("#") }
    .mapNotNull { line ->
        val fields = line.split(';')
        if (fields.size < 6) return@mapNotNull null
        runCatching {
            val code = fields[0].trim().uppercase(Locale.ROOT)
            require(code.length == 3 && fields[1].isNotBlank())
            code to FlightCity(
                code = code,
                name = fields[1].trim(),
                countryCode = fields[2].trim().uppercase(Locale.ROOT),
                location = GeoPoint(fields[3].trim().toDouble(), fields[4].trim().toDouble()),
                timeZone = ZoneId.of(fields[5].trim()),
            )
        }.getOrNull()
    }
    .toMap()
