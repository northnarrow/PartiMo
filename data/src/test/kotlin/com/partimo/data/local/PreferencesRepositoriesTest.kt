package com.partimo.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.partimo.data.local.preferences.DataStoreChecklistRepository
import com.partimo.data.local.preferences.DataStorePriceWatchRepository
import com.partimo.data.local.preferences.DataStoreSavedTripRepository
import com.partimo.data.local.preferences.DataStoreUserPreferencesRepository
import com.partimo.data.local.preferences.StoredJson
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.Money
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.poi.WikipediaPage
import com.partimo.domain.model.saved.Favorite
import com.partimo.domain.model.saved.FavoriteKind
import com.partimo.domain.model.saved.SavedTrip
import com.partimo.domain.testing.TestData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DataStore in memoria con la stessa semantica transazionale di quello reale. I test verificano la
 * logica dei repository; la persistenza su file è responsabilità della libreria (e il suo storage
 * basato su java.io.File non riesce a sovrascrivere i file quando i test girano su Windows).
 */
private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock { transform(state.value).also { state.value = it } }
}

class PreferencesRepositoriesTest {

    private fun dataStore(): DataStore<Preferences> = InMemoryPreferencesDataStore()

    @Test
    fun `il punto di partenza viene salvato con tutti i dati dell'aeroporto`() = runTest {
        val repository = DataStoreUserPreferencesRepository(dataStore())
        assertNull(repository.departure.first())

        val milan = TestData.departure("Milano", "MXP")
        repository.setDeparture(milan)

        assertEquals(milan, repository.departure.first())
    }

    @Test
    fun `gli avvisi si aggiungono, aggiornano e rimuovono`() = runTest {
        val repository = DataStorePriceWatchRepository(dataStore())
        val watch = TestData.priceWatch(flightPrices = listOf("200"))

        repository.add(watch)
        repository.add(watch.copy(flightPrices = emptyList())) // già presente: conserva lo storico
        assertEquals(listOf(watch), repository.watches.first())

        val updated = watch.copy(lastNotifiedFlight = Money.of(150, "EUR"))
        repository.update(updated)
        assertEquals(listOf(updated), repository.watches.first())

        repository.remove(watch.id)
        assertTrue(repository.watches.first().isEmpty())
    }

    @Test
    fun `aggiornare un avviso appena rimosso non lo ricrea`() = runTest {
        val repository = DataStorePriceWatchRepository(dataStore())

        repository.update(TestData.priceWatch())

        assertTrue(repository.watches.first().isEmpty())
    }

    @Test
    fun `le voci della valigia restano spuntate per ciascun viaggio`() = runTest {
        val repository = DataStoreChecklistRepository(dataStore())

        repository.setChecked("packing:vienna", "Abbigliamento › Cappotto", checked = true)
        repository.setChecked("packing:vienna", "Documenti › Passaporto", checked = true)
        repository.setChecked("packing:lisbona", "Mare › Costume", checked = true)
        repository.setChecked("packing:vienna", "Documenti › Passaporto", checked = false)

        assertEquals(setOf("Abbigliamento › Cappotto"), repository.checkedItems("packing:vienna").first())
        assertEquals(setOf("Mare › Costume"), repository.checkedItems("packing:lisbona").first())
        assertEquals(emptySet(), repository.checkedItems("packing:parigi").first())
    }
}

class SavedTripRepositoryTest {

    @Test
    fun `viaggi e preferiti sopravvivono alla codifica`() = runTest {
        val repository = DataStoreSavedTripRepository(InMemoryPreferencesDataStoreForTrips())
        val trip = SavedTrip(
            destination = TestData.destination(),
            period = TravelPeriod.NextDays,
            savedAt = TestData.NOW,
            favorites = listOf(
                Favorite("wikipedia:it:1", FavoriteKind.PLACE, "Duomo", "Luogo di culto", GeoPoint(48.2085, 16.3731), "https://img.test/a.jpg", null, WikipediaPage("it", "Duomo di Vienna"), PoiCategory.RELIGIOUS_SITE, "Cattedrale"),
                Favorite("osm:node/11", FavoriteKind.RESTAURANT, "Figlmüller", url = "https://www.google.com/maps/search/?api=1&query=Figlm%C3%BCller"),
            ),
        )

        repository.update { it + trip }

        assertEquals(listOf(trip), repository.trips.first())
        repository.update { trips -> trips.filterNot { it.id == trip.id } }
        assertTrue(repository.trips.first().isEmpty())
    }
}

/** Copia del DataStore in memoria di questo file per il test dei viaggi salvati. */
private class InMemoryPreferencesDataStoreForTrips : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock { transform(state.value).also { state.value = it } }
}

class StoredJsonTest {

    @Test
    fun `gli avvisi sopravvivono alla codifica con storico, periodo e fuso orario`() {
        val watches = listOf(
            TestData.priceWatch(flightPrices = listOf("199.90", "180"), stayPrices = listOf("95")),
            TestData.priceWatch(period = TravelPeriod.NextDays).copy(lastNotifiedStay = Money.of("80.50", "EUR")),
        )

        assertEquals(watches, StoredJson.decodeWatches(StoredJson.encodeWatches(watches)))
    }

    @Test
    fun `dati illeggibili vengono ignorati senza errori`() {
        assertTrue(StoredJson.decodeWatches("{non è json").isEmpty())
        assertTrue(StoredJson.decodeWatches(null).isEmpty())
        assertNull(StoredJson.decodeDeparture("[]"))

        // Un avviso con un periodo sconosciuto viene scartato, gli altri restano.
        val valid = TestData.priceWatch()
        val encoded = StoredJson.encodeWatches(listOf(valid, valid.copy(period = TravelPeriod.NextDays)))
            .replace("\"NEXT_DAYS\"", "\"SEMPRE\"")
        assertEquals(listOf(valid), StoredJson.decodeWatches(encoded))
    }
}
