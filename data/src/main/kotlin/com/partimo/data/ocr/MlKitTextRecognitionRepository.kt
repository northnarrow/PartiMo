package com.partimo.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.partimo.data.translate.await
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.ocr.DocumentSource
import com.partimo.domain.model.ocr.RecognizedBlock
import com.partimo.domain.model.ocr.RecognizedText
import com.partimo.domain.model.ocr.TextScript
import com.partimo.domain.repository.TextRecognitionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Riconoscimento del testo con ML Kit sul telefono: foto e screenshot (con l'orientamento giusto), PDF pagina per
 * pagina disegnati con PdfRenderer. I modelli arrivano da Google Play services (l'alfabeto latino
 * all'installazione, le altre scritture al primo uso); poi tutto avviene senza Internet e il documento non lascia
 * il telefono.
 */
internal class MlKitTextRecognitionRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TextRecognitionRepository {

    private val appContext = context.applicationContext
    private val recognizers = ConcurrentHashMap<TextScript, TextRecognizer>()

    override suspend fun recognize(source: DocumentSource, script: TextScript): DataResult<RecognizedText> = withContext(ioDispatcher) {
        try {
            val recognizer = recognizers.getOrPut(script) { TextRecognition.getClient(options(script)) }
            val uri = Uri.parse(source.uri)
            val text = if (source.isPdf) recognizePdf(recognizer, uri) else recognizeImage(recognizer, uri)
            DataResult.Success(text, DataOrigin.LOCAL)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Riconoscimento del testo non riuscito", e)
            DataResult.Failure(e.toDataError())
        }
    }

    private suspend fun recognizeImage(recognizer: TextRecognizer, uri: Uri): RecognizedText {
        val bitmap = decodeUpright(uri)
        try {
            val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            return RecognizedText(text = text.text, blocks = text.blocks(), width = bitmap.width, height = bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }

    /** Pagine del PDF una dopo l'altra (al massimo [MAX_PDF_PAGES]); i blocchi sono quelli della prima pagina. */
    private suspend fun recognizePdf(recognizer: TextRecognizer, uri: Uri): RecognizedText {
        val descriptor = appContext.contentResolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException(uri.toString())
        return descriptor.use { file ->
            PdfRenderer(file).use { renderer ->
                val pages = (0 until minOf(renderer.pageCount, MAX_PDF_PAGES)).map { index -> recognizePage(recognizer, renderer, index) }
                val first = pages.firstOrNull()
                RecognizedText(
                    text = pages.joinToString("\n\n") { it.text },
                    blocks = first?.blocks.orEmpty(),
                    width = first?.width ?: 0,
                    height = first?.height ?: 0,
                )
            }
        }
    }

    private suspend fun recognizePage(recognizer: TextRecognizer, renderer: PdfRenderer, index: Int): RecognizedText {
        val page = renderer.openPage(index)
        val bitmap = page.use {
            // Pagina disegnata larga circa PDF_PAGE_WIDTH pixel: abbastanza per leggere anche i caratteri piccoli.
            val scale = PDF_PAGE_WIDTH.toFloat() / max(1, it.width)
            Bitmap.createBitmap((it.width * scale).roundToInt(), (it.height * scale).roundToInt(), Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
                it.render(this, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
        try {
            val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            return RecognizedText(text = text.text, blocks = text.blocks(), width = bitmap.width, height = bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }

    /** Immagine con l'orientamento della foto già applicato e il lato lungo al massimo [MAX_IMAGE_SIDE]. */
    private fun decodeUpright(uri: Uri): Bitmap {
        val resolver = appContext.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_IMAGE_SIDE) {
                    val ratio = MAX_IMAGE_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * ratio).roundToInt(), (info.size.height * ratio).roundToInt())
                }
                // Memoria normale invece di quella grafica: il bitmap va letto dal riconoscimento.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: throw FileNotFoundException(uri.toString())
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: throw IOException("Immagine illeggibile: $uri")
        // Android 8: BitmapFactory ignora l'orientamento salvato dalla fotocamera (EXIF), si raddrizza a mano.
        val exif = resolver.openInputStream(uri)?.use { ExifInterface(it) }
        val matrix = Matrix().apply {
            if (exif?.isFlipped == true) postScale(-1f, 1f)
            postRotate(exif?.rotationDegrees?.toFloat() ?: 0f)
        }
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() }
    }

    private fun Text.blocks(): List<RecognizedBlock> = textBlocks.mapNotNull { block ->
        block.boundingBox?.let { box -> RecognizedBlock(block.text, box.left, box.top, box.right, box.bottom) }
    }

    private fun options(script: TextScript) = when (script) {
        TextScript.LATIN -> TextRecognizerOptions.DEFAULT_OPTIONS
        TextScript.CHINESE -> ChineseTextRecognizerOptions.Builder().build()
        TextScript.DEVANAGARI -> DevanagariTextRecognizerOptions.Builder().build()
        TextScript.JAPANESE -> JapaneseTextRecognizerOptions.Builder().build()
        TextScript.KOREAN -> KoreanTextRecognizerOptions.Builder().build()
    }

    /** Modello non ancora scaricato da Google Play services: serve la rete la prima volta. */
    private fun Exception.toDataError(): DataError = when {
        this is MlKitException && errorCode == MlKitException.UNAVAILABLE -> DataError.NoConnection
        else -> DataError.Unknown(message)
    }

    private companion object {
        const val TAG = "MlKitTextRecognition"
        const val MAX_PDF_PAGES = 5
        const val PDF_PAGE_WIDTH = 1600
        const val MAX_IMAGE_SIDE = 2048
    }
}
