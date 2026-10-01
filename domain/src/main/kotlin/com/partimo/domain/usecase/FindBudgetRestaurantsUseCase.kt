package com.partimo.domain.usecase

import com.partimo.domain.common.DataResult
import com.partimo.domain.common.map
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.dining.BudgetDiningCriteria
import com.partimo.domain.model.dining.PriceLevel
import com.partimo.domain.model.dining.Restaurant
import com.partimo.domain.model.dining.RestaurantSearchQuery
import com.partimo.domain.repository.RestaurantRepository

/**
 * Ristorazione economica di qualità: fascia di prezzo 1–2 e valutazione ≥ 4,3 (configurabili).
 *
 * I criteri vengono passati al provider come pre-filtro per ridurre i dati scaricati, ma sono
 * sempre riapplicati qui: il vincolo resta garantito anche se il provider lo ignora o lo approssima.
 * Con un provider senza valutazioni (OpenStreetMap) i criteri non sono verificabili: i locali reali
 * restano nell'ordine del provider (vicinanza al centro e completezza dei dati).
 */
class FindBudgetRestaurantsUseCase(private val repository: RestaurantRepository) {

    /** `true` se i locali hanno valutazioni e fasce di prezzo, quindi rispettano i criteri di qualità. */
    val ratingsAvailable: Boolean
        get() = repository.providesRatings

    suspend operator fun invoke(
        location: GeoPoint,
        criteria: BudgetDiningCriteria = BudgetDiningCriteria(),
        keyword: String? = null,
        areaName: String? = null,
        forceRefresh: Boolean = false,
    ): DataResult<List<Restaurant>> {
        val query = RestaurantSearchQuery(
            location = location,
            keyword = keyword,
            priceLevels = criteria.priceLevels.mapNotNull { PriceLevel.fromLevel(it) }.toSet(),
            minRating = criteria.minRating,
            openNow = criteria.openNowOnly,
            areaName = areaName,
        )
        return repository.searchRestaurants(query, forceRefresh).map { restaurants ->
            if (!ratingsAvailable) return@map restaurants
            restaurants
                .filter(criteria::matches)
                .sortedWith(
                    compareByDescending<Restaurant> { weightedRating(it) }
                        .thenBy { it.priceLevel?.level ?: Int.MAX_VALUE }
                        .thenBy { it.name },
                )
        }
    }

    /**
     * Media bayesiana della valutazione: a parità di voto, premia i locali con più recensioni
     * (un 4,8 con 5 recensioni è meno affidabile di un 4,6 con 2.000).
     */
    internal fun weightedRating(restaurant: Restaurant): Double {
        val rating = restaurant.rating ?: return 0.0
        val votes = (restaurant.reviewCount ?: 0).toDouble()
        return (votes / (votes + CONFIDENCE_REVIEWS)) * rating + (CONFIDENCE_REVIEWS / (votes + CONFIDENCE_REVIEWS)) * PRIOR_RATING
    }

    private companion object {
        const val CONFIDENCE_REVIEWS = 50.0
        const val PRIOR_RATING = 4.0
    }
}
