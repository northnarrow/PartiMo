package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.common.QueryIssue
import com.partimo.domain.model.stay.AccommodationFilter
import com.partimo.domain.model.stay.AccommodationSearchQuery
import com.partimo.domain.model.stay.AccommodationSortOption
import com.partimo.domain.testing.FakeAccommodationRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.testing.TestData.stayOffer
import com.partimo.domain.testing.failureError
import com.partimo.domain.testing.successData
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchAccommodationsUseCaseTest {

    private val repository = FakeAccommodationRepository(
        result = DataResult.Success(
            listOf(
                stayOffer("luxury", "1200", reviewScore = 9.2, stars = 5),
                stayOffer("smart", "420", reviewScore = 8.9, stars = 3),
                stayOffer("cheap-poor", "380", reviewScore = 6.1, stars = 2),
            ),
        ),
    )
    private val useCase = SearchAccommodationsUseCase(repository, clock = TestData.FIXED_CLOCK)
    private val query = AccommodationSearchQuery(
        location = TestData.VIENNA_CENTER,
        checkIn = LocalDate.of(2026, Month.DECEMBER, 12),
        checkOut = LocalDate.of(2026, Month.DECEMBER, 16),
    )

    @Test
    fun `ordina gli alloggi per rapporto qualità prezzo`() = runTest {
        assertEquals("smart", useCase(query).successData().first().offer.id)
    }

    @Test
    fun `supporta ordinamento per prezzo e per valutazione`() = runTest {
        assertEquals("cheap-poor", useCase(query, sortBy = AccommodationSortOption.CHEAPEST).successData().first().offer.id)
        assertEquals("luxury", useCase(query, sortBy = AccommodationSortOption.TOP_RATED).successData().first().offer.id)
    }

    @Test
    fun `applica il filtro sulla valutazione minima`() = runTest {
        val filtered = useCase(query, filter = AccommodationFilter(minReviewScore = 8.0)).successData()

        assertEquals(setOf("luxury", "smart"), filtered.map { it.offer.id }.toSet())
    }

    @Test
    fun `date di soggiorno incoerenti vengono rifiutate senza chiamare il provider`() = runTest {
        val error = useCase(query.copy(checkOut = query.checkIn)).failureError()

        assertEquals(DataError.InvalidQuery(QueryIssue.INVALID_STAY_DATES), error)
        assertTrue(repository.queries.isEmpty())
    }
}
