package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.common.map
import com.partimo.domain.model.ScoredOffer
import com.partimo.domain.model.flight.FlightFilter
import com.partimo.domain.model.flight.FlightOffer
import com.partimo.domain.model.flight.FlightSearchQuery
import com.partimo.domain.model.flight.FlightSortOption
import com.partimo.domain.repository.FlightRepository
import com.partimo.domain.service.ValueForMoneyScorer
import java.time.Clock
import java.time.LocalDate

/**
 * Cerca voli, applica i filtri e ordina le offerte per rapporto qualità/prezzo (o altro criterio).
 * La query viene validata prima di consumare una chiamata API.
 */
class SearchFlightsUseCase(
    private val repository: FlightRepository,
    private val scorer: ValueForMoneyScorer = ValueForMoneyScorer(),
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    suspend operator fun invoke(
        query: FlightSearchQuery,
        filter: FlightFilter = FlightFilter(),
        sortBy: FlightSortOption = FlightSortOption.BEST_VALUE,
        forceRefresh: Boolean = false,
    ): DataResult<List<ScoredOffer<FlightOffer>>> {
        query.validate(LocalDate.now(clock))?.let { issue ->
            return DataResult.Failure(DataError.InvalidQuery(issue))
        }
        return repository.searchFlights(query, forceRefresh).map { offers ->
            val comparable = offers.inSingleCurrency(query.currencyCode) { it.totalPrice.currencyCode }
            scorer.scoreFlights(comparable)
                .filter { filter.matches(it.offer) }
                .sortedWith(comparatorFor(sortBy))
        }
    }

    private fun comparatorFor(sortBy: FlightSortOption): Comparator<ScoredOffer<FlightOffer>> = when (sortBy) {
        FlightSortOption.BEST_VALUE -> compareByDescending<ScoredOffer<FlightOffer>> { it.valueScore }
            .thenBy { it.offer.totalPrice.amount }
        FlightSortOption.CHEAPEST -> compareBy<ScoredOffer<FlightOffer>> { it.offer.totalPrice.amount }
            .thenBy { it.offer.totalDuration }
        FlightSortOption.FASTEST -> compareBy<ScoredOffer<FlightOffer>> { it.offer.totalDuration }
            .thenBy { it.offer.totalPrice.amount }
    }
}
