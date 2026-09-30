package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.common.QueryIssue
import com.partimo.domain.model.place.AirportOption
import com.partimo.domain.model.place.CityPlace
import com.partimo.domain.model.place.DeparturePoint
import com.partimo.domain.repository.AirportRepository
import com.partimo.domain.repository.UserPreferencesRepository
import com.partimo.domain.service.AirportSelector
import kotlinx.coroutines.flow.Flow

/**
 * Aeroporti da cui partire per la città indicata dall'utente: il consigliato per primo, poi gli altri
 * per distanza (es. Milano → Malpensa, Linate, Bergamo). La scelta finale resta all'utente.
 */
class FindDepartureAirportsUseCase(
    private val airportRepository: AirportRepository,
    private val airportSelector: AirportSelector = AirportSelector(),
) {

    suspend operator fun invoke(city: CityPlace): DataResult<List<AirportOption>> =
        when (val result = airportRepository.airportsNear(city.location, SEARCH_RADIUS_KM)) {
            is DataResult.Failure -> result
            is DataResult.Success -> {
                val options = airportSelector.options(city.location, result.data)
                if (options.isEmpty()) {
                    DataResult.Failure(DataError.InvalidQuery(QueryIssue.NO_AIRPORT_NEARBY))
                } else {
                    DataResult.Success(options, result.origin)
                }
            }
        }

    private companion object {
        const val SEARCH_RADIUS_KM = 300.0
    }
}

/** Punto di partenza salvato: è un Flow, così ogni schermata si aggiorna appena l'utente lo cambia. */
class ObserveDepartureUseCase(private val preferences: UserPreferencesRepository) {
    operator fun invoke(): Flow<DeparturePoint?> = preferences.departure
}

class SaveDepartureUseCase(private val preferences: UserPreferencesRepository) {
    suspend operator fun invoke(departure: DeparturePoint) = preferences.setDeparture(departure)
}
