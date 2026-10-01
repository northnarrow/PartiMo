package com.partimo.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.partimo.domain.model.backup.UserData
import com.partimo.domain.model.budget.TripBudget
import com.partimo.domain.model.deal.PriceWatch
import com.partimo.domain.model.place.DeparturePoint
import com.partimo.domain.model.saved.SavedTrip
import com.partimo.domain.repository.BudgetRepository
import com.partimo.domain.repository.ChecklistRepository
import com.partimo.domain.repository.PriceWatchRepository
import com.partimo.domain.repository.ReminderLogRepository
import com.partimo.domain.repository.SavedTripRepository
import com.partimo.domain.repository.UserDataRepository
import com.partimo.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException
import java.time.Instant

// Preferenze dell'utente persistite con Preferences DataStore: scritture transazionali e lettura
// reattiva (Flow). A differenza della cache Room, questi dati non vanno mai persi.

private val DEPARTURE_KEY = stringPreferencesKey("departure")
private val PRICE_WATCHES_KEY = stringPreferencesKey("price_watches")
private val SAVED_TRIPS_KEY = stringPreferencesKey("saved_trips")
private const val BUDGET_KEY_PREFIX = "budget:"
private const val CHECKLIST_KEY_PREFIX = "checklist:"
private const val USER_DATA_STORE_NAME = "partimo_user"

/** Budget di un viaggio: una preferenza per viaggio. */
private fun budgetKey(tripId: String) = stringPreferencesKey(BUDGET_KEY_PREFIX + tripId)

/** Voci spuntate di una lista di controllo: una preferenza per lista. */
private fun checklistKey(listId: String) = stringSetPreferencesKey(CHECKLIST_KEY_PREFIX + listId)

/** Crea il DataStore dell'utente: va creato una sola volta per processo (lo garantisce DataModule). */
internal fun createUserDataStore(context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(produceFile = { userDataStoreFile(context) })

/**
 * File con tutti i dati dell'utente: è l'unico incluso nel backup di Android e nel passaggio a un telefono
 * nuovo (vedi `res/xml/data_extraction_rules.xml` e `backup_rules.xml` dell'app).
 */
fun userDataStoreFile(context: Context): File = context.preferencesDataStoreFile(USER_DATA_STORE_NAME)

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

    private fun keyOf(listId: String) = checklistKey(listId)
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

/** Budget dei viaggi, uno per viaggio (meta e periodo), in JSON versionabile. */
class DataStoreBudgetRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) : BudgetRepository {

    override fun budget(tripId: String): Flow<TripBudget> = dataStore.safeData()
        .map { preferences -> StoredJson.decodeBudget(tripId, preferences[keyOf(tripId)]) }
        .distinctUntilChanged()

    override suspend fun update(tripId: String, transform: (TripBudget) -> TripBudget) {
        dataStore.edit { preferences ->
            val key = keyOf(tripId)
            val updated = transform(StoredJson.decodeBudget(tripId, preferences[key]))
            if (updated.hasData) preferences[key] = StoredJson.encodeBudget(updated) else preferences.remove(key)
        }
    }

    private fun keyOf(tripId: String) = budgetKey(tripId)
}

/** Promemoria dei viaggi già mostrati: chiavi in un insieme, una per viaggio e tipo di promemoria. */
class DataStoreReminderLogRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) : ReminderLogRepository {

    override suspend fun sentReminders(): Set<String> = dataStore.safeData().first()[SENT_REMINDERS_KEY].orEmpty()

    override suspend fun markSent(keys: Collection<String>) {
        dataStore.edit { preferences -> preferences[SENT_REMINDERS_KEY] = preferences[SENT_REMINDERS_KEY].orEmpty() + keys }
    }

    private companion object {
        val SENT_REMINDERS_KEY = stringSetPreferencesKey("sent_reminders")
    }
}


/**
 * Tutti i dati dell'utente insieme, per il file di backup: le stesse preferenze dei repository qui sopra,
 * lette e scritte nella stessa transazione.
 */
class DataStoreUserDataRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) : UserDataRepository {

    override suspend fun read(): UserData = dataStore.safeData().first().toUserData()

    override suspend fun update(transform: (UserData) -> UserData) {
        dataStore.edit { preferences -> preferences.write(transform(preferences.toUserData())) }
    }

    override fun encode(data: UserData, exportedAt: Instant): String = StoredJson.encodeBackup(data, exportedAt)

    override fun decode(content: String): UserData? = StoredJson.decodeBackup(content)

    private fun Preferences.toUserData(): UserData {
        val entries = asMap()
        return UserData(
            departure = this[DEPARTURE_KEY]?.let(StoredJson::decodeDeparture),
            savedTrips = StoredJson.decodeSavedTrips(this[SAVED_TRIPS_KEY]),
            priceWatches = StoredJson.decodeWatches(this[PRICE_WATCHES_KEY]),
            budgets = entries.mapNotNull { (key, value) ->
                val tripId = key.name.removePrefix(BUDGET_KEY_PREFIX).takeIf { key.name.startsWith(BUDGET_KEY_PREFIX) }
                tripId?.let { StoredJson.decodeBudget(it, value as? String) }?.takeIf { it.hasData }
            },
            checklists = entries.mapNotNull { (key, value) ->
                val listId = key.name.removePrefix(CHECKLIST_KEY_PREFIX).takeIf { key.name.startsWith(CHECKLIST_KEY_PREFIX) }
                val items = (value as? Set<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
                if (listId != null && items.isNotEmpty()) listId to items else null
            }.toMap(),
        )
    }

    /** Sostituisce tutti i dati dell'utente con [data]; le altre preferenze (es. i promemoria già mostrati) restano. */
    private fun MutablePreferences.write(data: UserData) {
        val departure = data.departure
        if (departure != null) this[DEPARTURE_KEY] = StoredJson.encodeDeparture(departure) else remove(DEPARTURE_KEY)
        this[SAVED_TRIPS_KEY] = StoredJson.encodeSavedTrips(data.savedTrips)
        this[PRICE_WATCHES_KEY] = StoredJson.encodeWatches(data.priceWatches)
        asMap().keys
            .filter { it.name.startsWith(BUDGET_KEY_PREFIX) || it.name.startsWith(CHECKLIST_KEY_PREFIX) }
            .forEach { key -> remove(key) }
        data.budgets.filter { it.hasData }.forEach { budget -> this[budgetKey(budget.tripId)] = StoredJson.encodeBudget(budget) }
        data.checklists.filterValues { it.isNotEmpty() }.forEach { (listId, items) -> this[checklistKey(listId)] = items }
    }
}
