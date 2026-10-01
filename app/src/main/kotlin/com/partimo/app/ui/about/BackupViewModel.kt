package com.partimo.app.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.partimo.app.di.AppContainer
import com.partimo.app.files.UserDocuments
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.backup.UserDataExport
import com.partimo.domain.model.backup.UserDataSummary
import com.partimo.domain.usecase.ExportUserDataUseCase
import com.partimo.domain.usecase.ImportUserDataUseCase
import com.partimo.domain.usecase.SummarizeUserDataUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

/** Stato della sezione «I tuoi dati»: cosa c'è sul telefono ed esito dell'ultima esportazione o importazione. */
data class BackupUiState(
    /** Nome proposto per il file di backup. */
    val suggestedFileName: String,
    /** `null` finché i dati non sono stati letti. */
    val summary: UserDataSummary? = null,
    val isWorking: Boolean = false,
    /** Esito da mostrare una volta; la UI lo consuma e poi lo notifica. */
    val message: BackupMessage? = null,
)

sealed interface BackupMessage {
    data class Exported(val summary: UserDataSummary) : BackupMessage

    data class Imported(val summary: UserDataSummary) : BackupMessage

    data object ExportFailed : BackupMessage

    data object ImportFailed : BackupMessage

    /** Il file scelto non è un backup di PartiMo. */
    data object NotABackup : BackupMessage
}

/**
 * Backup dei dati dell'utente in un file scelto da lui (Download, Google Drive...) e importazione da un
 * file, unito ai dati già presenti.
 */
class BackupViewModel(
    private val exportUserData: ExportUserDataUseCase,
    private val importUserData: ImportUserDataUseCase,
    private val summarizeUserData: SummarizeUserDataUseCase,
    private val documents: UserDocuments,
    clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState(suggestedFileName = UserDataExport.fileName(LocalDate.now(clock))))
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    init {
        refreshSummary()
    }

    /** Salva il backup nel documento [uri] appena creato dall'utente. */
    fun onExport(uri: String) = runTask {
        val message = try {
            val export = exportUserData()
            documents.writeText(uri, export.content)
            BackupMessage.Exported(export.summary)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BackupMessage.ExportFailed
        }
        _uiState.update { it.copy(message = message) }
    }

    /** Importa il backup contenuto nel documento [uri] scelto dall'utente. */
    fun onImport(uri: String) = runTask {
        val content = try {
            documents.readText(uri, MAX_BACKUP_BYTES)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val message = when (val result = content?.let { importUserData(it) }) {
            null -> BackupMessage.ImportFailed
            is DataResult.Success -> BackupMessage.Imported(result.data)
            is DataResult.Failure -> if (result.error == DataError.InvalidResponse) BackupMessage.NotABackup else BackupMessage.ImportFailed
        }
        _uiState.update { it.copy(message = message, summary = summarizeUserData()) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private fun refreshSummary() {
        viewModelScope.launch { _uiState.update { it.copy(summary = summarizeUserData()) } }
    }

    private fun runTask(task: suspend () -> Unit) {
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            try {
                task()
            } finally {
                _uiState.update { it.copy(isWorking = false) }
            }
        }
    }

    companion object {
        /** Un backup con centinaia di viaggi pesa pochi megabyte: un file più grande non è un backup. */
        const val MAX_BACKUP_BYTES = 10 * 1024 * 1024

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BackupViewModel(
                    exportUserData = container.exportUserData,
                    importUserData = container.importUserData,
                    summarizeUserData = container.summarizeUserData,
                    documents = container.documents,
                    clock = container.clock,
                )
            }
        }
    }
}
