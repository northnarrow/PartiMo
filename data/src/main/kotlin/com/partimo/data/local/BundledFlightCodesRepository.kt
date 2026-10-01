package com.partimo.data.local

import com.partimo.domain.repository.FlightCodesRepository
import java.time.ZoneId

/** Codici dei voli inclusi nell'app (aeroporti, compagnie, fusi orari), per leggere le conferme dei voli. */
internal class BundledFlightCodesRepository(private val codes: BundledFlightCodes) : FlightCodesRepository {
    override suspend fun airportCodes(): Set<String> = codes.airportCodes()

    override suspend fun airlines(): Map<String, String> = codes.airlines()

    override suspend fun airportTimeZone(iata: String): ZoneId? = codes.airport(iata)?.timeZone
}
