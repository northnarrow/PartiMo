package com.partimo.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.partimo.domain.model.deal.PriceWatch
import com.partimo.domain.model.place.DeparturePoint
import com.partimo.domain.model.saved.SavedTrip
import com.partimo.domain.repository.ChecklistRepository
import com.partimo.domain.repository.PriceWatchRepository
import com.partimo.domain.repository.SavedTripRepository
import com.partimo.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

// Preferenze dell'utente persistite con Preferences DataStore: scritture transazionali e lettura
// reattiva (Flow). A differenza della cache Room, questi dati non vanno mai persi.

private val DEPARTURE_KEY = stringPreferencesKey("departure")
private val PRICE_WATCHES_KEY = stringPreferencesKey("price_watches")
private val SAVED_TRIPS_KEY = stringPreferencesKey("saved_trips")
private const val USER_DATA_STORE_NAME = "partimo_user"

/** Crea il DataStore dell'utente: va creato una sola volta per processo (lo garantisce DataModule). */
internal fun createUserDataStore(context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile(USER_DATA_STORE_NAME) })

/** Un file illeggibile non deve bloccare l'app: si riparte da preferenze vuote. */
private fun DataStore<Preferences>.safeData(): Flow<Preferences> =
    data.catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

class DataStoreUserPreferencesRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {

    override val departure: Flow<DeparturePoint?> = dataStore.safeData()
        .map { preferences -> preferences[DEPARTURE_KEY]?.let(StoredJson::decodeDeparture) }
        .distinctUntilChanged()

    override suspend fun setDeparture(departure: DeparturePoint) {
        dataStore.edit { preferences -> preferences[DEPARTURE_KEY] = StoredJson.encodeDeparture(departure) }
    }
}

class DataStorePriceWatchRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) : PriceWatchRepository {

    override val watches: Flow<List<PriceWatch>> = dataStore.safeData()
        .map { preferences -> StoredJson.decodeWatches(preferences[PRICE_WATCHES_KEY]) }
        .distinctUntilChanged()

    override suspend fun add(watch: PriceWatch) = editWatches { watches ->
        if (watches.any { it.id == watch.id }) watches else watches + watch
    }

    override suspend fun update(watch: PriceWatch) = editWatches { watches ->
        watches.map { if (it.id == watch.id) watch else it }
    }

    override suspend fun remove(id: String) = editWatches { watches -> watches.filterNot { it.id == id } }

    /** Lettura e scrittura nella stessa transazione: nessun aggiornamento concorrente va perso. */
    private suspend fun editWatches(transform: (List<PriceWatch>) -> List<PriceWatch>) {
        dataStore.edit { preferences ->
            val current = StoredJson.decodeWatches(preferences[PRICE_WATCHES_KEY])
            preferences[PRICE_WATCHES_KEY] = StoredJson.encodeWatches(transform(current))
        }
    }
}

/** Liste di controllo (es. la valigia): per ogni lista l'insieme delle voci spuntate. */
class DataStoreChecklistRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) : ChecklistRepository {

    override fun checkedItems(listId: String): Flow<Set<String>> = dataStore.safeData()
        .map { preferences -> preferences[keyOf(listId)].orEmpty() }
        .distinctUntilChanged()

    override suspend fun setChecked(listId: String, item: String, checked: Boolean) {
        dataStore.edit { preferences ->
            val key = keyOf(listId)
            val items = preferences[key].orEmpty()
            val updated = if (checked) items + item else items - item
            if (updated.isEmpty()) preferences.remove(key) else preferences[key] = updated
        }
    }

    private fun keyOf(listId: String) = stringSetPreferencesKey("checklist:$listId")
}

/** Viaggi salvati con i preferiti, in JSON versionabile come gli avvisi. */
class DataStoreSavedTripRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) : SavedTripRepository {

    override val trips: Flow<List<SavedTrip>> = dataStore.safeData()
        .map { preferences -> StoredJson.decodeSavedTrips(preferences[SAVED_TRIPS_KEY]) }
        .distinctUntilChanged()

    override suspend fun update(transform: (List<SavedTrip>) -> List<SavedTrip>) {
        dataStore.edit { preferences ->
            val current = StoredJson.decodeSavedTrips(preferences[SAVED_TRIPS_KEY])
            preferences[SAVED_TRIPS_KEY] = StoredJson.encodeSavedTrips(transform(current))
        }
    }
}
