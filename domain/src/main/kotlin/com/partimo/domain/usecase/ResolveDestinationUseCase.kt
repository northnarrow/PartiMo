package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.common.QueryIssue
import com.partimo.domain.model.Destination
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.place.Airport
import com.partimo.domain.model.place.CityPlace
import com.partimo.domain.repository.AirportRepository
import com.partimo.domain.service.AirportSelector

/**
 * Trasforma una città cercata in una [Destination] completa, individuando l'aeroporto di arrivo:
 * serve ai voli (codice IATA) e ai trasporti pubblici (percorso aeroporto → centro).
 */
class ResolveDestinationUseCase(
    private val airportRepository: AirportRepository,
    private val airportSelector: AirportSelector = AirportSelector(),
) {

    suspend operator fun invoke(city: CityPlace): DataResult<Destination> =
        when (val result = airportRepository.airportsNear(city.location, SEARCH_RADIUS_KM)) {
            is DataResult.Failure -> result
            is DataResult.Success -> {
                val airport = selectAirport(city.location, result.data)
                if (airport == null) {
                    DataResult.Failure(DataError.InvalidQuery(QueryIssue.NO_AIRPORT_NEARBY))
                } else {
                    DataResult.Success(
                        data = Destination(
                            name = city.name,
                            countryCode = city.countryCode,
                            airportIata = airport.iata,
                            center = city.location,
                            arrivalHub = airport.location,
                            arrivalHubName = airport.name,
                            timeZone = city.timeZone,
                        ),
                        origin = result.origin,
                    )
                }
            }
        }

    /** Aeroporto di arrivo per una città (vedi [AirportSelector]). */
    internal fun selectAirport(center: GeoPoint, candidates: List<Airport>): Airport? = airportSelector.select(center, candidates)

    private companion object {
        const val SEARCH_RADIUS_KM = 300.0
    }
}
