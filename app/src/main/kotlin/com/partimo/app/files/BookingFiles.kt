package com.partimo.app.files

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/** Documento di una prenotazione salvato dall'app: nome del file e tipo. */
data class StoredDocument(val name: String, val mimeType: String)

/**
 * Documenti delle prenotazioni (PDF dei biglietti, carte d'imbarco, screenshot) copiati nella cartella privata
 * dell'app: si aprono anche senza rete e restano anche se l'originale viene cancellato. Passano a un telefono
 * nuovo con il trasferimento di Android, ma non sono nel backup su Google (limite di 25 MB).
 */
interface BookingDocuments {
    /** Copia il documento [uri] tra quelli delle prenotazioni. */
    suspend fun import(uri: String, mimeType: String?): StoredDocument

    /** Indirizzo `content://` del documento [name] da aprire con un'altra app; `null` se il file non c'è. */
    fun shareableUri(name: String): String?

    suspend fun delete(name: String)

    /**
     * Cancella i documenti che nessuna prenotazione usa ([used]), copiati da più di un giorno: allegati e poi
     * abbandonati senza salvare. Quelli più recenti possono essere in un modulo ancora aperto.
     */
    suspend fun deleteUnused(used: Set<String>)
}

class BookingFiles(
    private val context: Context,
    private val documents: UserDocuments,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BookingDocuments {

    private val directory: File get() = File(context.filesDir, DIRECTORY)

    override suspend fun import(uri: String, mimeType: String?): StoredDocument {
        val type = mimeType?.takeIf { it in ACCEPTED_TYPES || it.startsWith("image/") } ?: context.contentResolver.getType(Uri.parse(uri))
            ?: throw IOException("Tipo di documento sconosciuto")
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(type) ?: if (type == PDF) "pdf" else "jpg"
        val bytes = documents.readBytes(uri, MAX_BYTES)
        return withContext(ioDispatcher) {
            directory.mkdirs()
            val name = "booking-${UUID.randomUUID()}.$extension"
            File(directory, name).writeBytes(bytes)
            StoredDocument(name, type)
        }
    }

    override fun shareableUri(name: String): String? {
        val file = fileOf(name)?.takeIf { it.isFile } ?: return null
        return FileProvider.getUriForFile(context, authority(context), file).toString()
    }

    override suspend fun delete(name: String) {
        withContext(ioDispatcher) { fileOf(name)?.delete() }
    }

    override suspend fun deleteUnused(used: Set<String>) = withContext(ioDispatcher) {
        val threshold = System.currentTimeMillis() - UNUSED_GRACE_MILLIS
        directory.listFiles().orEmpty()
            .filter { it.isFile && it.name !in used && it.lastModified() < threshold }
            .forEach { it.delete() }
    }

    /** Solo nomi di file nella cartella dei documenti: mai un percorso che ne esca. */
    private fun fileOf(name: String): File? =
        name.takeIf { it.isNotBlank() && '/' !in it && '\\' !in it && it != "." && it != ".." }?.let { File(directory, it) }

    companion object {
        /** Cartella dei documenti, dentro i file dell'app (vedi anche le regole del backup di Android). */
        const val DIRECTORY = "bookings"

        /** Un biglietto o una conferma pesano pochi megabyte. */
        const val MAX_BYTES = 20 * 1024 * 1024

        const val PDF = "application/pdf"
        private const val UNUSED_GRACE_MILLIS = 24 * 60 * 60 * 1000L
        private val ACCEPTED_TYPES = setOf(PDF)

        /** Autorità del FileProvider dichiarato nel manifest. */
        fun authority(context: Context): String = "${context.packageName}.files"
    }
}
