package com.partimo.domain.model.ocr

/** Scrittura del testo da riconoscere: ogni modello sul telefono ne conosce una. */
enum class TextScript { LATIN, CHINESE, DEVANAGARI, JAPANESE, KOREAN }

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
