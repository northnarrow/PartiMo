package com.partimo.app.ui.about

import com.partimo.app.files.FileTooLargeException
import com.partimo.app.files.UserDocuments
import com.partimo.app.testing.MainDispatcherRule
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.backup.UserData
import com.partimo.domain.model.backup.UserDataSummary
import com.partimo.domain.model.saved.SavedTrip
import com.partimo.domain.testing.FakeUserDataRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.usecase.ExportUserDataUseCase
import com.partimo.domain.usecase.ImportUserDataUseCase
import com.partimo.domain.usecase.SummarizeUserDataUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import java.io.IOException
import java.time.Month
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Documenti in memoria: il contenuto scritto si ritrova allo stesso indirizzo. */
private class FakeDocuments : UserDocuments {
    val files = mutableMapOf<String, String>()
    var failure: IOException? = null

    override suspend fun writeText(uri: String, content: String) {
        failure?.let { throw it }
        files[uri] = content
    }

    override suspend fun readBytes(uri: String, maxBytes: Int): ByteArray {
        failure?.let { throw it }
        val content = files[uri] ?: throw IOException("Nessun file $uri")
        if (content.length > maxBytes) throw FileTooLargeException(maxBytes)
        return content.toByteArray()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val vienna = SavedTrip(TestData.destination(), TestData.DECEMBER_2026, TestData.NOW)
    private val lisbon = SavedTrip(TestData.destination(name = "Lisbona", airportIata = "LIS"), TravelPeriod.InMonth(YearMonth.of(2026, Month.NOVEMBER)), TestData.NOW)
    private val documents = FakeDocuments()

    private fun createViewModel(repository: FakeUserDataRepository) = BackupViewModel(
        exportUserData = ExportUserDataUseCase(repository, TestData.FIXED_CLOCK),
        importUserData = ImportUserDataUseCase(repository),
        summarizeUserData = SummarizeUserDataUseCase(repository),
        documents = documents,
        clock = TestData.FIXED_CLOCK,
    )

    @Test
    fun `mostra cosa c'è sul telefono e propone un nome per il file`() = runTest {
        val viewModel = createViewModel(FakeUserDataRepository(UserData(savedTrips = listOf(vienna), departure = TestData.departure())))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("PartiMo-backup-2026-09-30.json", state.suggestedFileName)
        assertEquals(UserDataSummary(trips = 1, hasDeparture = true), state.summary)
    }

    @Test
    fun `esporta nel file scelto e lo segnala`() = runTest {
        val repository = FakeUserDataRepository(UserData(savedTrips = listOf(vienna)))
        val viewModel = createViewModel(repository)

        viewModel.onExport("content://drive/backup.json")
        advanceUntilIdle()

        assertEquals(UserData(savedTrips = listOf(vienna)), repository.decode(documents.files.getValue("content://drive/backup.json")))
        assertEquals(BackupMessage.Exported(UserDataSummary(trips = 1)), viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isWorking)

        viewModel.onMessageShown()
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `se il file non si può scrivere lo dice`() = runTest {
        val viewModel = createViewModel(FakeUserDataRepository())
        documents.failure = IOException("Spazio esaurito")

        viewModel.onExport("content://drive/backup.json")
        advanceUntilIdle()

        assertEquals(BackupMessage.ExportFailed, viewModel.uiState.value.message)
    }

    @Test
    fun `importa un backup, unisce i dati e aggiorna il riepilogo`() = runTest {
        val repository = FakeUserDataRepository(UserData(savedTrips = listOf(vienna)))
        documents.files["content://download/backup.json"] = repository.encode(UserData(savedTrips = listOf(lisbon)), TestData.NOW)
        val viewModel = createViewModel(repository)

        viewModel.onImport("content://download/backup.json")
        advanceUntilIdle()

        assertEquals(listOf("Vienna", "Lisbona"), repository.data.savedTrips.map { it.destination.name })
        assertEquals(BackupMessage.Imported(UserDataSummary(trips = 1)), viewModel.uiState.value.message)
        assertEquals(UserDataSummary(trips = 2), viewModel.uiState.value.summary)
    }

    @Test
    fun `un file qualunque non è un backup e un file illeggibile è un errore`() = runTest {
        val repository = FakeUserDataRepository(UserData(savedTrips = listOf(vienna)))
        documents.files["content://download/foto.json"] = "{\"foto\": true}"
        val viewModel = createViewModel(repository)

        viewModel.onImport("content://download/foto.json")
        advanceUntilIdle()
        assertEquals(BackupMessage.NotABackup, viewModel.uiState.value.message)

        viewModel.onImport("content://download/sparito.json")
        advanceUntilIdle()
        assertEquals(BackupMessage.ImportFailed, viewModel.uiState.value.message)
        assertEquals(listOf(vienna), repository.data.savedTrips)
    }
}
