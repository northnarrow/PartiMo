package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.dining.BudgetDiningCriteria
import com.partimo.domain.model.dining.PriceLevel
import com.partimo.domain.testing.FakeRestaurantRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.testing.TestData.restaurant
import com.partimo.domain.testing.failureError
import com.partimo.domain.testing.successData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FindBudgetRestaurantsUseCaseTest {

    private val repository = FakeRestaurantRepository(
        result = DataResult.Success(
            listOf(
                restaurant("boundary", PriceLevel.INEXPENSIVE, rating = 4.3),
                restaurant("moderate-top", PriceLevel.MODERATE, rating = 4.7),
                restaurant("expensive", PriceLevel.EXPENSIVE, rating = 4.8),
                restaurant("below-threshold", PriceLevel.INEXPENSIVE, rating = 4.29),
                restaurant("unknown-price", priceLevel = null, rating = 4.9),
                restaurant("unknown-rating", PriceLevel.MODERATE, rating = null),
                restaurant("free", PriceLevel.FREE, rating = 4.5),
            ),
        ),
    )
    private val useCase = FindBudgetRestaurantsUseCase(repository)

    @Test
    fun `mantiene solo fascia di prezzo 1-2 con valutazione minima 4_3`() = runTest {
        val restaurants = useCase(TestData.VIENNA_CENTER).successData()

        assertEquals(listOf("moderate-top", "boundary"), restaurants.map { it.id })
    }

    @Test
    fun `passa i criteri al provider come pre-filtro`() = runTest {
        useCase(TestData.VIENNA_CENTER)

        val query = repository.queries.single()
        assertEquals(setOf(PriceLevel.INEXPENSIVE, PriceLevel.MODERATE), query.priceLevels)
        assertEquals(4.3, query.minRating)
    }

    @Test
    fun `a parità di condizioni preferisce le valutazioni più affidabili`() = runTest {
        repository.result = DataResult.Success(
            listOf(
                restaurant("few-reviews", PriceLevel.INEXPENSIVE, rating = 4.8, reviewCount = 5),
                restaurant("many-reviews", PriceLevel.INEXPENSIVE, rating = 4.6, reviewCount = 2_000),
            ),
        )

        assertEquals("many-reviews", useCase(TestData.VIENNA_CENTER).successData().first().id)
    }

    @Test
    fun `supporta criteri personalizzati`() = runTest {
        val criteria = BudgetDiningCriteria(priceLevels = 2..2, minRating = 4.5, openNowOnly = true)

        val restaurants = useCase(TestData.VIENNA_CENTER, criteria).successData()

        assertEquals(listOf("moderate-top"), restaurants.map { it.id })
    }

    @Test
    fun `propaga gli errori del repository`() = runTest {
        repository.result = DataResult.Failure(DataError.Unauthorized)

        assertEquals(DataError.Unauthorized, useCase(TestData.VIENNA_CENTER).failureError())
    }

    @Test
    fun `criteri fuori scala vengono rifiutati`() {
        assertFailsWith<IllegalArgumentException> { BudgetDiningCriteria(minRating = 6.0) }
        assertFailsWith<IllegalArgumentException> { BudgetDiningCriteria(priceLevels = 0..5) }
    }
}
