package com.partimo.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.partimo.data.local.preferences.DataStorePriceWatchRepository
import com.partimo.data.local.preferences.DataStoreUserPreferencesRepository
import com.partimo.data.local.preferences.StoredJson
import com.partimo.domain.model.Money
import com.partimo.domain.model.TravelPeriod
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
