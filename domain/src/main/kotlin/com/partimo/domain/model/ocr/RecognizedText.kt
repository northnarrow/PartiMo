package com.partimo.domain.model.ocr

import java.util.Locale

/** Scrittura del testo da riconoscere: ogni modello sul telefono ne conosce una. */
enum class TextScript {
    LATIN,
    CHINESE,
    DEVANAGARI,
    JAPANESE,
    KOREAN,
    ;

    companion object {
        /** Lingue con una scrittura che il riconoscimento sul telefono non legge (cirillico, greco, arabo, thai...). */
        private val UNSUPPORTED = setOf(
            "am", "ar", "be", "bg", "bn", "el", "fa", "gu", "he", "hy", "iw", "ka", "kk", "km", "kn", "ky", "lo",
            "mk", "ml", "mn", "my", "pa", "ru", "si", "ta", "te", "tg", "th", "uk", "ur", "yi",
        )

        /** Scrittura della lingua [language] (codice ISO 639-1); `null` se il telefono non sa leggerla. */
        fun of(language: String): TextScript? = when (val code = language.trim().lowercase(Locale.ROOT).substringBefore('-')) {
            "zh" -> CHINESE
            "ja" -> JAPANESE
            "ko" -> KOREAN
            "hi", "mr", "ne", "sa" -> DEVANAGARI
            else -> if (code in UNSUPPORTED) null else LATIN
        }
    }
}

/** Documento scelto o condiviso dall'utente: un'immagine (foto, screenshot) o un PDF. */
data class DocumentSource(val uri: String, val mimeType: String?) {
    val isPdf: Boolean get() = mimeType == PDF_MIME_TYPE || uri.lowercase().endsWith(".pdf")

    companion object {
        const val PDF_MIME_TYPE = "application/pdf"
    }
}

/** Blocco di testo riconosciuto (un paragrafo, un cartello) con la sua posizione nell'immagine, in pixel. */
data class RecognizedBlock(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/**
 * Testo riconosciuto in un documento: tutto il testo (le pagine di un PDF una dopo l'altra) e, per un'immagine,
 * i blocchi con la loro posizione, per mostrare la traduzione sopra la foto.
 */
data class RecognizedText(
    val text: String,
    val blocks: List<RecognizedBlock> = emptyList(),
    /** Dimensioni dell'immagine analizzata (della prima pagina per un PDF). */
    val width: Int = 0,
    val height: Int = 0,
) {
    val isEmpty: Boolean get() = text.isBlank()
}

/** Blocco di testo di una foto con la sua traduzione, nella stessa posizione. */
data class TranslatedBlock(val original: RecognizedBlock, val translation: String)

/** Foto tradotta: dimensioni dell'immagine analizzata e blocchi tradotti, dall'alto in basso. */
data class PhotoTranslation(val width: Int, val height: Int, val blocks: List<TranslatedBlock>) {
    /** `true` se le posizioni dei blocchi si possono riportare sulla foto. */
    val hasLayout: Boolean get() = width > 0 && height > 0 && blocks.any { it.original.right > it.original.left && it.original.bottom > it.original.top }
}
