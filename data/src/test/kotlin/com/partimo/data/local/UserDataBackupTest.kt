package com.partimo.data.local

import com.partimo.data.local.preferences.DataStoreBudgetRepository
import com.partimo.data.local.preferences.DataStoreChecklistRepository
import com.partimo.data.local.preferences.DataStorePriceWatchRepository
import com.partimo.data.local.preferences.DataStoreReminderLogRepository
import com.partimo.data.local.preferences.DataStoreSavedTripRepository
import com.partimo.data.local.preferences.DataStoreUserDataRepository
import com.partimo.data.local.preferences.DataStoreUserPreferencesRepository
import com.partimo.domain.common.DataError
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.Money
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.budget.Expense
import com.partimo.domain.model.budget.ExpenseCategory
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.poi.WikipediaPage
import com.partimo.domain.model.saved.Favorite
import com.partimo.domain.model.saved.FavoriteKind
import com.partimo.domain.model.saved.SavedTrip
import com.partimo.domain.testing.TestData
import com.partimo.domain.testing.failureError
import com.partimo.domain.testing.successData
import com.partimo.domain.usecase.ExportUserDataUseCase
import com.partimo.domain.usecase.ImportUserDataUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** File di backup: esportazione e importazione di tutti i dati salvati con DataStore. */
class UserDataBackupTest {

    private val vienna = TestData.destination()
    private val lisbon = TestData.destination(name = "Lisbona", airportIata = "LIS")
    private val december = TestData.DECEMBER_2026
    private val dates = TravelPeriod.Dates(LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 14))
    private val stephansdom = Favorite(
        id = "wikipedia:it:1",
        kind = FavoriteKind.PLACE,
        name = "Duomo di Santo Stefano",
        subtitle = "Chiesa",
        location = GeoPoint(48.2085, 16.3731),
        photoUrl = "https://upload.wikimedia.org/stephansdom.jpg",
        wikipediaPage = WikipediaPage("it", "Duomo di Santo Stefano (Vienna)"),
        category = PoiCategory.RELIGIOUS_SITE,
        description = "Cattedrale gotica",
    )
    private val figlmueller = Favorite("osm:node/11", FavoriteKind.RESTAURANT, "Figlmüller", url = "https://www.figlmueller.at")
    private val dinner = Expense("1", BigDecimal("250.5"), "CZK", ExpenseCategory.FOOD, LocalDate.of(2026, 12, 11), "Cena")
    private val viennaId = SavedTrip.idOf(vienna, december)

    /** Telefono con un po' di tutto, scritto con i repository che usa l'app. */
    private suspend fun filledPhone(): InMemoryPreferencesDataStore {
        val store = InMemoryPreferencesDataStore()
        DataStoreUserPreferencesRepository(store).setDeparture(TestData.departure("Milano", "MXP"))
        DataStoreSavedTripRepository(store).update {
            listOf(SavedTrip(vienna, december, TestData.NOW, listOf(stephansdom, figlmueller)), SavedTrip(lisbon, dates, TestData.NOW))
        }
        DataStorePriceWatchRepository(store).add(TestData.priceWatch(flightPrices = listOf("180", "150")).copy(lastNotifiedFlight = Money.of(150, "EUR")))
        DataStoreBudgetRepository(store).update(viennaId) { it.copy(limit = BigDecimal("900"), expenses = listOf(dinner)) }
        DataStoreChecklistRepository(store).setChecked("packing:$viennaId", "Documenti › Passaporto", checked = true)
        DataStoreReminderLogRepository(store).markSent(listOf("$viennaId:WEEK_BEFORE"))
        return store
    }

    @Test
    fun `un backup esportato e reimportato su un telefono nuovo riporta tutti i dati`() = runTest {
        val export = ExportUserDataUseCase(DataStoreUserDataRepository(filledPhone()), TestData.FIXED_CLOCK)()
        val newPhone = InMemoryPreferencesDataStore()

        val summary = ImportUserDataUseCase(DataStoreUserDataRepository(newPhone))(export.content).successData()

        assertEquals(export.summary, summary)
        assertEquals(2, summary.trips)
        assertEquals(2, summary.favorites)
        assertEquals(TestData.departure("Milano", "MXP"), DataStoreUserPreferencesRepository(newPhone).departure.first())
        val trips = DataStoreSavedTripRepository(newPhone).trips.first()
        assertEquals(listOf(SavedTrip(vienna, december, TestData.NOW, listOf(stephansdom, figlmueller)), SavedTrip(lisbon, dates, TestData.NOW)), trips)
        val watch = DataStorePriceWatchRepository(newPhone).watches.first().single()
        assertEquals(listOf(Money.of("180", "EUR"), Money.of("150", "EUR")), watch.flightPrices.map { it.price })
        assertEquals(Money.of(150, "EUR"), watch.lastNotifiedFlight)
        val budget = DataStoreBudgetRepository(newPhone).budget(viennaId).first()
        assertEquals(BigDecimal("900"), budget.limit)
        assertEquals(listOf(dinner), budget.expenses)
        assertEquals(setOf("Documenti › Passaporto"), DataStoreChecklistRepository(newPhone).checkedItems("packing:$viennaId").first())
        assertTrue(DataStoreReminderLogRepository(newPhone).sentReminders().isEmpty(), "I promemoria già mostrati non fanno parte del backup")
    }

    @Test
    fun `il file è un JSON leggibile con formato, versione e data`() = runTest {
        val export = ExportUserDataUseCase(DataStoreUserDataRepository(filledPhone()), TestData.FIXED_CLOCK)()

        val root = Json.parseToJsonElement(export.content).jsonObject
        assertEquals("partimo-backup", root.getValue("format").jsonPrimitive.content)
        assertEquals("1", root.getValue("version").jsonPrimitive.content)
        assertEquals(TestData.NOW.toString(), root.getValue("exportedAt").jsonPrimitive.content)
        assertTrue(export.content.lines().size > 20, "JSON indentato, apribile anche da una persona")
        assertTrue("WEEK_BEFORE" !in export.content)
    }

    @Test
    fun `importare sul telefono che ha già dei dati li unisce senza perderne`() = runTest {
        val export = ExportUserDataUseCase(DataStoreUserDataRepository(filledPhone()), TestData.FIXED_CLOCK)()
        val phone = InMemoryPreferencesDataStore()
        val prague = TestData.destination(name = "Praga", airportIata = "PRG")
        val sachertorte = Favorite("osm:node/99", FavoriteKind.RESTAURANT, "Café Sacher")
        DataStoreSavedTripRepository(phone).update {
            listOf(SavedTrip(vienna, december, TestData.NOW, listOf(sachertorte)), SavedTrip(prague, december, TestData.NOW))
        }
        DataStoreBudgetRepository(phone).update(viennaId) { it.copy(expenses = listOf(dinner.copy(id = "telefono"))) }
        DataStoreReminderLogRepository(phone).markSent(listOf("già mostrato"))

        ImportUserDataUseCase(DataStoreUserDataRepository(phone))(export.content).successData()

        val trips = DataStoreSavedTripRepository(phone).trips.first()
        assertEquals(listOf("Vienna", "Praga", "Lisbona"), trips.map { it.destination.name })
        assertEquals(listOf("Duomo di Santo Stefano", "Figlmüller", "Café Sacher"), trips.first().favorites.map { it.name })
        val budget = DataStoreBudgetRepository(phone).budget(viennaId).first()
        assertEquals(BigDecimal("900"), budget.limit)
        assertEquals(listOf("1", "telefono"), budget.expenses.map { it.id })
        assertEquals(setOf("già mostrato"), DataStoreReminderLogRepository(phone).sentReminders())
    }

    @Test
    fun `un file che non è un backup di PartiMo viene rifiutato senza toccare i dati`() = runTest {
        val phone = filledPhone()
        val repository = DataStoreUserDataRepository(phone)
        val before = repository.read()

        for (content in listOf("", "ciao", "{}", "[1, 2]", """{"format": "altra-app", "version": 1}""")) {
            assertEquals(DataError.InvalidResponse, ImportUserDataUseCase(repository)(content).failureError(), content)
        }
        assertEquals(before, repository.read())
    }

    @Test
    fun `gli elementi illeggibili di un backup si scartano uno a uno`() {
        val content = """
            {
              "format": "partimo-backup",
              "version": 2,
              "campoFuturo": true,
              "savedTrips": [
                {"destination": {"name": "Vienna", "countryCode": "AT", "airportIata": "VIE", "center": {"latitude": 48.2, "longitude": 16.37},
                  "arrivalHub": {"latitude": 48.11, "longitude": 16.57}, "arrivalHubName": "Aeroporto VIE", "timeZone": "Europe/Vienna"},
                 "period": "2026-12", "savedAtMillis": 0},
                {"destination": {"name": "Atlantide", "countryCode": "XX", "airportIata": "ATL", "center": {"latitude": 0, "longitude": 0},
                  "arrivalHub": {"latitude": 0, "longitude": 0}, "arrivalHubName": "?", "timeZone": "UTC"},
                 "period": "quando capita", "savedAtMillis": 0}
              ],
              "budgets": [
                {"tripId": "AT:Vienna:2026-12", "limit": "-5", "expenses": [{"id": "x", "amount": "abc", "currency": "EUR", "category": "FOOD", "date": "2026-12-11"}]},
                {"tripId": "AT:Vienna:2026-11", "limit": "300"}
              ],
              "checklists": {"packing": ["", "Passaporto"], "vuota": []}
            }
        """.trimIndent()

        val data = DataStoreUserDataRepository(InMemoryPreferencesDataStore()).decode(content)!!

        assertEquals(listOf("Vienna"), data.savedTrips.map { it.destination.name })
        assertEquals(TravelPeriod.InMonth(YearMonth.of(2026, Month.DECEMBER)), data.savedTrips.single().period)
        assertEquals(listOf("AT:Vienna:2026-11"), data.budgets.map { it.tripId }, "Budget senza tetto valido né spese: scartato")
        assertEquals(mapOf("packing" to setOf("Passaporto")), data.checklists)
        assertNull(data.departure)
    }
}
