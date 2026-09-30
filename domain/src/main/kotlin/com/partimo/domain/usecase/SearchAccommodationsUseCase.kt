package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.common.map
import com.partimo.domain.model.ScoredOffer
import com.partimo.domain.model.stay.AccommodationFilter
import com.partimo.domain.model.stay.AccommodationOffer
import com.partimo.domain.model.stay.AccommodationSearchQuery
import com.partimo.domain.model.stay.AccommodationSortOption
import com.partimo.domain.repository.AccommodationRepository
import com.partimo.domain.service.ValueForMoneyScorer
import java.time.Clock
import java.time.LocalDate

/** Cerca alloggi e li ordina per rapporto qualità/prezzo, prezzo o valutazione. */
class SearchAccommodationsUseCase(
    private val repository: AccommodationRepository,
    private val scorer: ValueForMoneyScorer = ValueForMoneyScorer(),
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    suspend operator fun invoke(
        query: AccommodationSearchQuery,
        filter: AccommodationFilter = AccommodationFilter(),
        sortBy: AccommodationSortOption = AccommodationSortOption.BEST_VALUE,
        forceRefresh: Boolean = false,
    ): DataResult<List<ScoredOffer<AccommodationOffer>>> {
        query.validate(LocalDate.now(clock))?.let { issue ->
            return DataResult.Failure(DataError.InvalidQuery(issue))
        }
        return repository.searchAccommodations(query, forceRefresh).map { offers ->
            val comparable = offers.inSingleCurrency(query.currencyCode) { it.totalPrice.currencyCode }
            scorer.scoreStays(comparable)
                .filter { filter.matches(it.offer) }
                .sortedWith(comparatorFor(sortBy))
        }
    }

    private fun comparatorFor(sortBy: AccommodationSortOption): Comparator<ScoredOffer<AccommodationOffer>> =
        when (sortBy) {
            AccommodationSortOption.BEST_VALUE -> compareByDescending<ScoredOffer<AccommodationOffer>> { it.valueScore }
                .thenBy { it.offer.pricePerNight.amount }
            AccommodationSortOption.CHEAPEST -> compareBy<ScoredOffer<AccommodationOffer>> { it.offer.pricePerNight.amount }
                .thenByDescending { it.offer.reviewScore ?: 0.0 }
            AccommodationSortOption.TOP_RATED -> compareByDescending<ScoredOffer<AccommodationOffer>> { it.offer.reviewScore ?: 0.0 }
                .thenBy { it.offer.pricePerNight.amount }
        }
}
