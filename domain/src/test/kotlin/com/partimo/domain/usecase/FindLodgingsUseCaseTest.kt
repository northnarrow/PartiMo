package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.stay.LodgingType
import com.partimo.domain.testing.FakeLodgingRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.testing.TestData.lodging
import com.partimo.domain.testing.failureError
import com.partimo.domain.testing.successData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FindLodgingsUseCaseTest {

    private val center = TestData.VIENNA_CENTER
    private val repository = FakeLodgingRepository()

    @Test
    fun `le strutture reali arrivano dalla più vicina al centro`() = runTest {
        repository.result = DataResult.Success(
            listOf(
                lodging("lontano", location = GeoPoint(48.2200, 16.3738)),
                lodging("vicino", location = GeoPoint(48.2085, 16.3740)),
                lodging("medio", type = LodgingType.HOSTEL, location = GeoPoint(48.2120, 16.3738)),
            ),
        )

        val lodgings = FindLodgingsUseCase(repository)(center).successData()

        assertEquals(listOf("vicino", "medio", "lontano"), lodgings.map { it.id })
        assertEquals(center, repository.queries.single().location)
    }

    @Test
    fun `a parità di distanza vengono prima le strutture con più stelle e i doppioni spariscono`() = runTest {
        val spot = GeoPoint(48.2090, 16.3740)
        repository.result = DataResult.Success(
            listOf(lodging("b&b", location = spot), lodging("hotel", location = spot, stars = 4), lodging("hotel", location = spot, stars = 4)),
        )

        val lodgings = FindLodgingsUseCase(repository, maxResults = 5)(center).successData()

        assertEquals(listOf("hotel", "b&b"), lodgings.map { it.id })
    }

    @Test
    fun `limita il numero di strutture e propaga gli errori`() = runTest {
        repository.result = DataResult.Success((1..40).map { lodging("s$it", location = GeoPoint(48.2082 + it / 10_000.0, 16.3738)) })

        assertEquals(25, FindLodgingsUseCase(repository)(center).successData().size)

        repository.result = DataResult.Failure(DataError.NoConnection)
        assertEquals(DataError.NoConnection, FindLodgingsUseCase(repository)(center).failureError())
    }

    @Test
    fun `le stelle devono essere tra 1 e 5`() {
        assertFailsWith<IllegalArgumentException> { lodging("x", stars = 7) }
    }
}
