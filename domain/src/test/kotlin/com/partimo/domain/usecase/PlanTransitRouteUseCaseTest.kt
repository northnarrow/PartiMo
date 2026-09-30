package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.common.QueryIssue
import com.partimo.domain.model.transit.TransitMode
import com.partimo.domain.model.transit.TransitPreference
import com.partimo.domain.model.transit.TransitRoute
import com.partimo.domain.model.transit.TransitRouteQuery
import com.partimo.domain.testing.FakeTransitRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.testing.TestData.ride
import com.partimo.domain.testing.TestData.walk
import com.partimo.domain.testing.failureError
import com.partimo.domain.testing.successData
import kotlinx.coroutines.test.runTest
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanTransitRouteUseCaseTest {

    private val repository = FakeTransitRepository()
    private val useCase = PlanTransitRouteUseCase(repository, clock = TestData.FIXED_CLOCK)
    private val now = TestData.NOW
    private val query = TransitRouteQuery(
        origin = TestData.VIENNA_HUB,
        destination = TestData.VIENNA_CENTER,
        departureTime = now,
    )

    /** Metro + tram con un cambio, arriva prima. */
    private val fastWithTransfer: TransitRoute = run {
        val metro = ride(TransitMode.METRO, now, 8)
        val tram = ride(TransitMode.TRAM, metro.arrivalTime.plus(Duration.ofMinutes(3)), 5)
        TransitRoute(listOf(metro, tram))
    }

    /** Un solo bus diretto, arriva più tardi. */
    private val directBus: TransitRoute = TransitRoute(listOf(ride(TransitMode.BUS, now.plus(Duration.ofMinutes(4)), 22)))

    /** Coincidenza impossibile: il tram parte prima dell'arrivo della metro. */
    private val missedConnection: TransitRoute = run {
        val metro = ride(TransitMode.METRO, now, 10)
        TransitRoute(listOf(metro, ride(TransitMode.TRAM, metro.arrivalTime.minus(Duration.ofMinutes(2)), 5)))
    }

    @Test
    fun `scarta percorsi con mezzi non ammessi o coincidenze impossibili`() = runTest {
        repository.result = DataResult.Success(listOf(directBus, missedConnection, fastWithTransfer))

        val routes = useCase(query.copy(allowedModes = setOf(TransitMode.METRO, TransitMode.TRAM))).successData()

        assertEquals(listOf(fastWithTransfer), routes)
    }

    @Test
    fun `ordina secondo la preferenza dell'utente`() = runTest {
        repository.result = DataResult.Success(listOf(directBus, fastWithTransfer))

        assertEquals(fastWithTransfer, useCase(query).successData().first())
        assertEquals(directBus, useCase(query.copy(preference = TransitPreference.FEWER_TRANSFERS)).successData().first())
    }

    @Test
    fun `limita il tempo massimo a piedi`() = runTest {
        val walkingRoute = TestData.simpleRoute(now) // 7 minuti a piedi in totale
        repository.result = DataResult.Success(listOf(walkingRoute, directBus))

        val routes = useCase(query.copy(maxWalking = Duration.ofMinutes(5))).successData()

        assertEquals(listOf(directBus), routes)
    }

    @Test
    fun `origine e destinazione coincidenti non sono valide`() = runTest {
        val error = useCase(query.copy(destination = query.origin)).failureError()

        assertEquals(DataError.InvalidQuery(QueryIssue.SAME_ORIGIN_AND_DESTINATION), error)
        assertTrue(repository.queries.isEmpty())
    }

    @Test
    fun `una partenza troppo nel passato non è valida`() = runTest {
        val error = useCase(query.copy(departureTime = now.minus(Duration.ofHours(2)))).failureError()

        assertEquals(DataError.InvalidQuery(QueryIssue.DATE_IN_THE_PAST), error)
    }

    @Test
    fun `nessun percorso produce una lista vuota`() = runTest {
        repository.result = DataResult.Success(listOf(TransitRoute(listOf(walk(now, 30)))))

        val routes = useCase(query.copy(maxWalking = Duration.ofMinutes(10))).successData()

        assertTrue(routes.isEmpty())
    }
}
