package com.partimo.domain.model

import com.partimo.domain.model.transit.TransitMode
import com.partimo.domain.model.transit.TransitRoute
import com.partimo.domain.testing.TestData
import com.partimo.domain.testing.TestData.ride
import com.partimo.domain.testing.TestData.walk
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransitRouteTest {

    @Test
    fun `calcola cambi, tempo a piedi e coincidenze`() {
        val firstWalk = walk(TestData.NOW, 5)
        val metro = ride(TransitMode.METRO, firstWalk.arrivalTime, 10, line = "U1", from = "Hauptbahnhof", to = "Karlsplatz")
        val tram = ride(TransitMode.TRAM, metro.arrivalTime.plus(Duration.ofMinutes(4)), 6, line = "D", from = "Karlsplatz", to = "Oper")
        val lastWalk = walk(tram.arrivalTime, 3)

        val route = TransitRoute(listOf(firstWalk, metro, tram, lastWalk))

        assertEquals(1, route.transfers)
        assertEquals(setOf(TransitMode.METRO, TransitMode.TRAM), route.modes)
        assertEquals(Duration.ofMinutes(8), route.walkingDuration)
        assertEquals(Duration.ofMinutes(28), route.totalDuration)

        val connection = route.connections.single()
        assertEquals("Karlsplatz", connection.stopName)
        assertEquals(Duration.ofMinutes(4), connection.transferTime)
        assertFalse(connection.isMissed)
        assertFalse(connection.isTight())
    }

    @Test
    fun `una coincidenza che parte prima dell'arrivo è persa`() {
        val metro = ride(TransitMode.METRO, TestData.NOW, 10)
        val bus = ride(TransitMode.BUS, metro.arrivalTime.minus(Duration.ofMinutes(1)), 8)

        val connection = TransitRoute(listOf(metro, bus)).connections.single()

        assertTrue(connection.isMissed)
        assertTrue(connection.isTight())
    }

    @Test
    fun `un percorso a piedi non ha cambi`() {
        val route = TransitRoute(listOf(walk(TestData.NOW, 12)))
        assertEquals(0, route.transfers)
        assertTrue(route.connections.isEmpty())
        assertTrue(route.modes.isEmpty())
    }
}
