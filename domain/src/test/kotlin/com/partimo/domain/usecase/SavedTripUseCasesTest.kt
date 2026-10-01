package com.partimo.domain.usecase

import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.saved.Favorite
import com.partimo.domain.model.saved.FavoriteKind
import com.partimo.domain.testing.FakeSavedTripRepository
import com.partimo.domain.testing.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.Month
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavedTripUseCasesTest {

    private val repository = FakeSavedTripRepository()
    private val clock = TestData.FIXED_CLOCK
    private val vienna = TestData.destination()
    private val lisbon = TestData.destination(name = "Lisbona", airportIata = "LIS")
    private val december = TravelPeriod.InMonth(YearMonth.of(2026, Month.DECEMBER))
    private val november = TravelPeriod.InMonth(YearMonth.of(2026, Month.NOVEMBER))
    private val toggleFavorite = ToggleFavoriteUseCase(repository, clock)
    private val setTripSaved = SetTripSavedUseCase(repository, clock)

    private val duomo = Favorite("wikipedia:it:1", FavoriteKind.PLACE, "Duomo di Santo Stefano")
    private val figlmueller = Favorite("osm:node/11", FavoriteKind.RESTAURANT, "Figlmüller")

    @Test
    fun `il primo preferito salva il viaggio, un secondo tocco lo toglie`() = runTest {
        assertTrue(toggleFavorite(vienna, december, duomo))
        assertTrue(toggleFavorite(vienna, december, figlmueller))

        val trip = ObserveSavedTripUseCase(repository)(vienna, december).first()!!
        assertEquals(listOf("Duomo di Santo Stefano", "Figlmüller"), trip.favorites.map { it.name })
        assertEquals(TestData.NOW, trip.savedAt)

        assertFalse(toggleFavorite(vienna, december, duomo))
        assertEquals(listOf("Figlmüller"), ObserveSavedTripUseCase(repository)(vienna, december).first()!!.favorites.map { it.name })
        assertNull(ObserveSavedTripUseCase(repository)(vienna, november).first(), "Stessa meta, altro periodo: altro viaggio")
    }

    @Test
    fun `salvare un viaggio già salvato conserva i preferiti, toglierlo li elimina`() = runTest {
        toggleFavorite(vienna, december, duomo)
        setTripSaved(vienna, december, saved = true)
        assertEquals(1, repository.current.single().favorites.size)

        setTripSaved(vienna, december, saved = false)
        assertTrue(repository.current.isEmpty())
    }

    @Test
    fun `prima i viaggi in arrivo, dal più vicino, poi quelli passati`() = runTest {
        val past = TravelPeriod.InMonth(YearMonth.of(2026, Month.AUGUST))
        setTripSaved(lisbon, december, saved = true)
        setTripSaved(vienna, past, saved = true)
        setTripSaved(vienna, november, saved = true)
        setTripSaved(lisbon, TravelPeriod.NextDays, saved = true)

        val ordered = ObserveSavedTripsUseCase(repository, clock)().first().map { it.destination.name to it.period.key }

        assertEquals(listOf("Lisbona" to "NEXT_DAYS", "Vienna" to "2026-11", "Lisbona" to "2026-12", "Vienna" to "2026-08"), ordered)
    }
}

class RouteOrderingTest {

    @Test
    fun `il giro va sempre al posto più vicino non ancora visitato`() {
        val duomo = "Duomo" to com.partimo.domain.model.GeoPoint(48.2085, 16.3731)
        val schoenbrunn = "Schönbrunn" to com.partimo.domain.model.GeoPoint(48.1845, 16.3122)
        val hofburg = "Hofburg" to com.partimo.domain.model.GeoPoint(48.2066, 16.3654)
        val prater = "Prater" to com.partimo.domain.model.GeoPoint(48.2161, 16.3958)

        val ordered = com.partimo.domain.service.RouteOrdering.nearestNeighbor(listOf(duomo, schoenbrunn, hofburg, prater)) { it.second }

        assertEquals(listOf("Duomo", "Hofburg", "Prater", "Schönbrunn"), ordered.map { it.first })
    }
}
