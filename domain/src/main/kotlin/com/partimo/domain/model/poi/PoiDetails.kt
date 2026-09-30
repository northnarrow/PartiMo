package com.partimo.domain.model.poi

/** Autore e licenza di una foto (es. Wikimedia Commons), da citare accanto all'immagine. */
data class ImageCredit(
    val author: String?,
    val license: String?,
    /** Pagina della foto con i dettagli completi sulla licenza. */
    val sourceUrl: String?,
)

/** Sezione di una voce enciclopedica: titolo (assente per il testo introduttivo) e paragrafi. */
data class ArticleSection(val title: String?, val paragraphs: List<String>)

/**
 * Voce enciclopedica completa su un luogo (es. Wikipedia), così come fornita dalla sorgente:
 * il caso d'uso ne ricava la scheda breve mostrata all'utente.
 */
data class PoiArticle(
    val title: String,
    /** Lingua della voce (ISO 639-1). */
    val language: String,
    /** Pagina della voce, da aprire per leggere il testo completo. */
    val url: String,
    /** Descrizione brevissima, es. "antico anfiteatro romano a Roma". */
    val shortDescription: String? = null,
    /** Paragrafi dell'introduzione. */
    val introduction: List<String> = emptyList(),
    /**
     * Sezione sulla storia del luogo: prima il testo che la apre (senza titolo), poi le sottosezioni
     * in ordine (es. "Costruzione", "Dal Medioevo all'epoca moderna"). Vuota se la voce non la prevede.
     */
    val history: List<ArticleSection> = emptyList(),
    val imageUrl: String? = null,
    val imageCredit: ImageCredit? = null,
)

/** Capitolo della storia di un luogo; il titolo manca quando la storia è un racconto unico. */
data class HistoryChapter(val title: String?, val text: String)

/** Scheda di un luogo da visitare: breve descrizione, cenni storici, foto e fonti da citare. */
data class PoiDetails(
    val summary: String?,
    val history: List<HistoryChapter> = emptyList(),
    val imageUrl: String? = null,
    /** Crediti della foto; `null` se la foto non richiede attribuzione (es. arriva dal provider dei luoghi). */
    val imageCredit: ImageCredit? = null,
    /** Voce da cui provengono i testi, per leggerla per intero; `null` se non ne è stata trovata una. */
    val sourceUrl: String? = null,
    /** Lingua dei testi: può essere l'inglese quando la voce manca nella lingua dell'app. */
    val language: String? = null,
)
