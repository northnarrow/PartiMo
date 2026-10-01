package com.partimo.app.files

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * File scelti dall'utente con il selettore di Android (Storage Access Framework): il backup dei dati,
 * i documenti delle prenotazioni. Il file resta dove l'ha scelto l'utente: nessun permesso di archiviazione.
 */
interface UserDocuments {
    /** Scrive [content] in UTF-8 nel documento [uri], sostituendone il contenuto. */
    suspend fun writeText(uri: String, content: String)

    /** Contenuto del documento [uri]; un file più grande di [maxBytes] è un [FileTooLargeException]. */
    suspend fun readBytes(uri: String, maxBytes: Int): ByteArray

    /** Testo UTF-8 del documento [uri] (vedi [readBytes]). */
    suspend fun readText(uri: String, maxBytes: Int): String = readBytes(uri, maxBytes).toString(Charsets.UTF_8)
}

/** Il documento scelto supera la dimensione accettata. */
class FileTooLargeException(maxBytes: Int) : IOException("File più grande di $maxBytes byte")

/** [UserDocuments] con il ContentResolver di Android, sul dispatcher di I/O. */
class ContentResolverDocuments(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : UserDocuments {

    private val resolver: ContentResolver = context.applicationContext.contentResolver

    override suspend fun writeText(uri: String, content: String) = withContext(ioDispatcher) {
        val target = Uri.parse(uri)
        // "wt" svuota il file prima di scriverlo; alcuni fornitori (es. Google Drive) accettano solo "w".
        val stream = try {
            resolver.openOutputStream(target, "wt")
        } catch (e: FileNotFoundException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } ?: resolver.openOutputStream(target, "w") ?: throw IOException("Documento non scrivibile: $uri")
        stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
    }

    override suspend fun readBytes(uri: String, maxBytes: Int): ByteArray = withContext(ioDispatcher) {
        val stream = resolver.openInputStream(Uri.parse(uri)) ?: throw IOException("Documento non leggibile: $uri")
        stream.use { it.readAtMost(maxBytes) }
    }
}

/** Legge tutto lo stream, ma non oltre [maxBytes]: un file enorme non deve esaurire la memoria. */
internal fun InputStream.readAtMost(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_SIZE)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        output.write(buffer, 0, read)
        if (output.size() > maxBytes) throw FileTooLargeException(maxBytes)
    }
    return output.toByteArray()
}

private const val BUFFER_SIZE = 8 * 1024
