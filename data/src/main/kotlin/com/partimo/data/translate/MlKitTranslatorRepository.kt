package com.partimo.data.translate

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.common.DataResult
import com.partimo.domain.repository.TranslatorRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Traduttore sul telefono con ML Kit di Google: gratuito, senza chiave. Ogni lingua ha un pacchetto
 * di circa 30 MB scaricato una volta dai server di Google; poi le traduzioni non usano la rete.
 * L'inglese, lingua ponte, è sempre disponibile.
 */
internal class MlKitTranslatorRepository : TranslatorRepository {

    override val supportedLanguages: Set<String> by lazy { TranslateLanguage.getAllLanguages().toSet() }

    private val modelManager: RemoteModelManager by lazy { RemoteModelManager.getInstance() }

    /** Resta aperto un solo traduttore (l'ultima coppia usata): il frasario lo riusa per tutte le frasi. */
    private val mutex = Mutex()
    private var current: Pair<Pair<String, String>, Translator>? = null

    override suspend fun downloadedLanguages(): DataResult<Set<String>> = mlKit {
        modelManager.getDownloadedModels(TranslateRemoteModel::class.java).await().mapTo(HashSet()) { it.language }
    }

    override suspend fun download(from: String, to: String): DataResult<Unit> = mlKit {
        translator(from, to).downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        Unit
    }

    override suspend fun translate(text: String, from: String, to: String): DataResult<String> = mlKit {
        translator(from, to).translate(text).await()
    }

    private suspend fun translator(from: String, to: String): Translator = mutex.withLock {
        val pair = from to to
        current?.takeIf { it.first == pair }?.let { return@withLock it.second }
        val source = requireNotNull(TranslateLanguage.fromLanguageTag(from)) { "Lingua non supportata: $from" }
        val target = requireNotNull(TranslateLanguage.fromLanguageTag(to)) { "Lingua non supportata: $to" }
        current?.second?.close()
        Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
            .also { current = pair to it }
    }

    private inline fun <T> mlKit(block: () -> T): DataResult<T> = try {
        DataResult.Success(block(), DataOrigin.LOCAL)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Traduttore non riuscito", e)
        DataResult.Failure(e.toDataError())
    } catch (e: LinkageError) {
        // Processore senza la libreria nativa del traduttore (es. vecchi emulatori x86).
        Log.w(TAG, "Traduttore non disponibile su questo dispositivo", e)
        DataResult.Failure(DataError.Unknown(e.message))
    }

    private fun Exception.toDataError(): DataError = when {
        this is IOException || cause is IOException -> DataError.NoConnection
        this is MlKitException && errorCode == MlKitException.UNAVAILABLE -> DataError.NoConnection
        else -> DataError.Unknown(message)
    }

    private companion object {
        const val TAG = "MlKitTranslator"
    }
}

/** Attende un [Task] di Google Play services senza bloccare il thread. */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}
