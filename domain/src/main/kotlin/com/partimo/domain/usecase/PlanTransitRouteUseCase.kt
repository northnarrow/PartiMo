package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.common.QueryIssue
import com.partimo.domain.common.map
import com.partimo.domain.model.transit.TransitPreference
import com.partimo.domain.model.transit.TransitRoute
import com.partimo.domain.model.transit.TransitRouteQuery
import com.partimo.domain.repository.TransitRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Percorsi multimodali (metro, bus, tram, treni) con orari aggiornati.
 *
 * Scarta i percorsi con mezzi non ammessi, con coincidenze impossibili o con troppo cammino,
 * e li ordina secondo la preferenza dell'utente.
 */
class PlanTransitRouteUseCase(
    private val repository: TransitRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    suspend operator fun invoke(query: TransitRouteQuery, forceRefresh: Boolean = false): DataResult<List<TransitRoute>> {
        validate(query)?.let { issue -> return DataResult.Failure(DataError.InvalidQuery(issue)) }

        return repository.getRoutes(query, forceRefresh).map { routes ->
            routes
                .filter { route -> query.allowedModes.containsAll(route.modes) }
                .filter { route -> route.connections.none { it.isMissed } }
                .filter { route -> query.maxWalking == null || route.walkingDuration <= query.maxWalking }
                .sortedWith(comparatorFor(query.preference))
        }
    }

    private fun validate(query: TransitRouteQuery): QueryIssue? = when {
        query.origin.distanceTo(query.destination) < MIN_DISTANCE_METERS -> QueryIssue.SAME_ORIGIN_AND_DESTINATION
        query.departureTime.isBefore(Instant.now(clock).minus(MAX_PAST_DEPARTURE)) -> QueryIssue.DATE_IN_THE_PAST
        else -> null
    }

    private fun comparatorFor(preference: TransitPreference): Comparator<TransitRoute> = when (preference) {
        TransitPreference.FASTEST -> compareBy<TransitRoute> { it.arrivalTime }.thenBy { it.totalDuration }
        TransitPreference.FEWER_TRANSFERS -> compareBy<TransitRoute> { it.transfers }.thenBy { it.arrivalTime }
        TransitPreference.LESS_WALKING -> compareBy<TransitRoute> { it.walkingDuration }.thenBy { it.arrivalTime }
    }

    private companion object {
        const val MIN_DISTANCE_METERS = 50.0

        /** Tolleranza per richieste appena scadute (es. schermata rimasta aperta). */
        val MAX_PAST_DEPARTURE: Duration = Duration.ofMinutes(30)
    }
}
