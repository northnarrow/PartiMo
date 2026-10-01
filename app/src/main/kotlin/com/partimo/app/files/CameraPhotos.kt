package com.partimo.app.files

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Foto scattate per la traduzione (menù, cartelli): una alla volta, nella cache dell'app, condivisa con l'app
 * della fotocamera tramite il FileProvider. Non servono permessi: la foto la scatta l'app della fotocamera.
 */
object CameraPhotos {

    /** Cartella delle foto nella cache (vedi `res/xml/file_paths.xml`). */
    const val DIRECTORY = "camera"

    /** Indirizzo dove la fotocamera salverà la nuova foto; le foto precedenti si cancellano. */
    fun newPhotoUri(context: Context): Uri {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        directory.listFiles().orEmpty().forEach { it.delete() }
        val photo = File(directory, "photo-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, BookingFiles.authority(context), photo)
    }
}
