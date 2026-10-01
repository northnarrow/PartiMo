package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.backup.UserDataExport
import com.partimo.domain.model.backup.UserDataSummary
import com.partimo.domain.repository.UserDataRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

/** Tutti i dati dell'utente in un file di backup, da salvare dove si vuole (es. Google Drive, Download). */
class ExportUserDataUseCase(
    private val repository: UserDataRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend operator fun invoke(): UserDataExport {
        val data = repository.read()
        return UserDataExport(
            fileName = UserDataExport.fileName(LocalDate.now(clock)),
            content = repository.encode(data, Instant.now(clock)),
            summary = data.summary,
        )
    }
}

/** Quanti dati ci sono sul telefono (viaggi, preferiti, avvisi, spese, voci della valigia). */
class SummarizeUserDataUseCase(private val repository: UserDataRepository) {
    suspend operator fun invoke(): UserDataSummary = repository.read().summary
}

/**
 * Reimporta un file di backup unendolo ai dati del telefono (vedi `UserData.mergedWith`): nulla di ciò che
 * c'è già va perso. Restituisce cosa conteneva il file; un file che non è un backup di PartiMo è
 * [DataError.InvalidResponse].
 */
class ImportUserDataUseCase(private val repository: UserDataRepository) {
    suspend operator fun invoke(content: String): DataResult<UserDataSummary> {
        val imported = repository.decode(content) ?: return DataResult.Failure(DataError.InvalidResponse)
        return try {
            repository.update { current -> current.mergedWith(imported) }
            DataResult.Success(imported.summary, DataOrigin.LOCAL)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DataResult.Failure(DataError.Unknown(e.message))
        }
    }
}
