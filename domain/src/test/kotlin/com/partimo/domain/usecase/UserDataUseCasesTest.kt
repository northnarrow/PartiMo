package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.Travellers
import com.partimo.domain.model.backup.UserData
import com.partimo.domain.model.backup.UserDataSummary
import com.partimo.domain.model.budget.Expense
import com.partimo.domain.model.budget.ExpenseCategory
import com.partimo.domain.model.budget.TripBudget
import com.partimo.domain.model.saved.Favorite
import com.partimo.domain.model.saved.FavoriteKind
import com.partimo.domain.model.saved.SavedTrip
import com.partimo.domain.testing.FakeUserDataRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.testing.failureError
import com.partimo.domain.testing.successData
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.math.BigDecimal
import java.time.Month
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UserDataUseCasesTest {

    private val vienna = TestData.destination()
    private val lisbon = TestData.destination(name = "Lisbona", airportIata = "LIS")
    private val november = TravelPeriod.InMonth(YearMonth.of(2026, Month.NOVEMBER))
    private val duomo = Favorite("wikipedia:it:1", FavoriteKind.PLACE, "Duomo di Santo Stefano")
    private val figlmueller = Favorite("osm:node/11", FavoriteKind.RESTAURANT, "Figlmüller")
    private val viennaTrip = SavedTrip(vienna, TestData.DECEMBER_2026, TestData.NOW, listOf(duomo))
    private val viennaTripId = viennaTrip.id

    private fun expense(id: String, amount: String = "12.50") =
        Expense(id, BigDecimal(amount), "EUR", ExpenseCategory.FOOD, TestData.TODAY)

    @Test
    fun `il riepilogo conta viaggi, preferiti, avvisi, spese e voci spuntate`() {
        val data = UserData(
            departure = TestData.departure(),
            savedTrips = listOf(viennaTrip.copy(favorites = listOf(duomo, figlmueller)), SavedTrip(lisbon, november, TestData.NOW)),
            priceWatches = listOf(TestData.priceWatch()),
            budgets = listOf(TripBudget(viennaTripId, expenses = listOf(expense("a"), expense("b")))),
            checklists = mapOf("packing:$viennaTripId" to setOf("Passaporto", "Caricabatterie")),
        )

        assertEquals(UserDataSummary(trips = 2, favorites = 2, alerts = 1, expenses = 2, checkedItems = 2, hasDeparture = true), data.summary)
        assertFalse(data.summary.isEmpty)
        assertTrue(UserData().summary.isEmpty)
    }

    @Test
    fun `l'unione non perde nulla, il file vince sugli stessi elementi e preferiti, spese e voci si sommano`() {
        val phone = UserData(
            departure = TestData.departure(),
            savedTrips = listOf(viennaTrip),
            budgets = listOf(TripBudget(viennaTripId, limit = BigDecimal("800"), expenses = listOf(expense("a")))),
            checklists = mapOf("packing" to setOf("Passaporto")),
        )
        val file = UserData(
            departure = TestData.departure(cityName = "Bergamo", iata = "BGY"),
            savedTrips = listOf(viennaTrip.copy(favorites = listOf(figlmueller)), SavedTrip(lisbon, november, TestData.NOW)),
            priceWatches = listOf(TestData.priceWatch(flightPrices = listOf("120"))),
            budgets = listOf(TripBudget(viennaTripId, expenses = listOf(expense("a", amount = "15"), expense("b")))),
            checklists = mapOf("packing" to setOf("Ombrello"), "altra" to setOf("Biglietti")),
        )

        val merged = phone.mergedWith(file)

        assertEquals("BGY", merged.departure?.airport?.iata)
        assertEquals(listOf("Vienna", "Lisbona"), merged.savedTrips.map { it.destination.name })
        assertEquals(listOf("Figlmüller", "Duomo di Santo Stefano"), merged.savedTrips.first().favorites.map { it.name })
        assertEquals(1, merged.priceWatches.size)
        val budget = merged.budgets.single()
        assertEquals(BigDecimal("800"), budget.limit, "Il file non ha un tetto: resta quello del telefono")
        assertEquals(listOf(BigDecimal("15"), BigDecimal("12.50")), budget.expenses.map { it.amount })
        assertEquals(mapOf("packing" to setOf("Passaporto", "Ombrello"), "altra" to setOf("Biglietti")), merged.checklists)
    }

    @Test
    fun `un file senza punto di partenza né viaggiatori conserva quelli del telefono`() {
        val family = Travellers(adults = 2, childAges = listOf(5))
        val merged = UserData(departure = TestData.departure(), travellers = family).mergedWith(UserData(savedTrips = listOf(viennaTrip)))
        assertEquals("MXP", merged.departure?.airport?.iata)
        assertEquals(family, merged.travellers)
        assertEquals(Travellers(adults = 3), merged.mergedWith(UserData(travellers = Travellers(adults = 3))).travellers)
    }

    @Test
    fun `esporta tutti i dati in un file con la data nel nome`() = runTest {
        val repository = FakeUserDataRepository(UserData(savedTrips = listOf(viennaTrip)))

        val export = ExportUserDataUseCase(repository, TestData.FIXED_CLOCK)()

        assertEquals("PartiMo-backup-2026-09-30.json", export.fileName)
        assertEquals(1, export.summary.trips)
        assertEquals(repository.data, repository.decode(export.content))
    }

    @Test
    fun `importa un backup unendolo ai dati del telefono`() = runTest {
        val source = FakeUserDataRepository(UserData(savedTrips = listOf(SavedTrip(lisbon, november, TestData.NOW)), priceWatches = listOf(TestData.priceWatch())))
        val content = ExportUserDataUseCase(source, TestData.FIXED_CLOCK)().content
        val phone = FakeUserDataRepository(UserData(savedTrips = listOf(viennaTrip)))
        // Il «file» del finto repository vale solo per l'istanza che l'ha creato: lo si copia.
        val target = object : com.partimo.domain.repository.UserDataRepository by phone {
            override fun decode(content: String): UserData? = source.decode(content)
        }

        val summary = ImportUserDataUseCase(target)(content).successData()

        assertEquals(1, summary.trips)
        assertEquals(1, summary.alerts)
        assertEquals(listOf("Vienna", "Lisbona"), phone.data.savedTrips.map { it.destination.name })
        assertEquals(1, phone.data.priceWatches.size)
    }

    @Test
    fun `un file che non è un backup non cambia nulla`() = runTest {
        val repository = FakeUserDataRepository(UserData(savedTrips = listOf(viennaTrip)))

        val error = ImportUserDataUseCase(repository)("{\"ciao\": 1}").failureError()

        assertEquals(DataError.InvalidResponse, error)
        assertEquals(listOf(viennaTrip), repository.data.savedTrips)
    }

    @Test
    fun `un errore di scrittura diventa un errore, non un crash`() = runTest {
        val repository = FakeUserDataRepository()
        val content = repository.encode(UserData(savedTrips = listOf(viennaTrip)), TestData.NOW)
        repository.updateFailure = IOException("Spazio esaurito")

        assertIs<DataError.Unknown>(ImportUserDataUseCase(repository)(content).failureError())
    }
}
