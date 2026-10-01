package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.common.DataResult
import com.partimo.domain.common.QueryIssue
import com.partimo.domain.common.map
import com.partimo.domain.model.ocr.DocumentSource
import com.partimo.domain.model.ocr.PhotoTranslation
import com.partimo.domain.model.ocr.RecognizedBlock
import com.partimo.domain.model.ocr.TextScript
import com.partimo.domain.model.ocr.TranslatedBlock
import com.partimo.domain.repository.TextRecognitionRepository
import com.partimo.domain.repository.TranslatorRepository

/**
 * Lingue del traduttore: codici ISO 639-1 normalizzati come li usa il traduttore (es. il norvegese
 * "nb" diventa "no", il vecchio codice dell'ebraico "iw" diventa "he").
 */
object TranslatorLanguages {

    /** Lingua ponte: il traduttore la conosce sempre, senza pacchetti da scaricare. */
    const val ENGLISH = "en"

    private val ALIASES = mapOf("nb" to "no", "nn" to "no", "iw" to "he", "in" to "id", "ji" to "yi", "fil" to "tl")

    fun normalize(code: String): String = code.trim().lowercase().substringBefore('-').let { ALIASES[it] ?: it }

    /**
     * Lingua straniera proposta per un paese: la prima lingua del paese che il traduttore conosce e
     * che non è quella dell'utente; altrimenti l'inglese (o, per chi parla inglese, la lingua del paese).
     */
    fun defaultForeignLanguage(countryLanguages: List<String>, userLanguage: String, supported: Set<String>): String {
        val user = normalize(userLanguage)
        val local = countryLanguages.map(::normalize).filter { it in supported }
        return local.firstOrNull { it != user } ?: if (user != ENGLISH) ENGLISH else local.firstOrNull() ?: "fr"
    }
}

/** Traduce un testo sul telefono; il testo vuoto o nella stessa lingua non richiede il traduttore. */
class TranslateTextUseCase(private val repository: TranslatorRepository) {

    val supportedLanguages: Set<String> get() = repository.supportedLanguages

    suspend operator fun invoke(text: String, from: String, to: String): DataResult<String> {
        val source = TranslatorLanguages.normalize(from)
        val target = TranslatorLanguages.normalize(to)
        val trimmed = text.trim()
        return when {
            trimmed.length > MAX_LENGTH -> DataResult.Failure(DataError.InvalidQuery(QueryIssue.TEXT_TOO_LONG))
            trimmed.isEmpty() -> DataResult.Success("", DataOrigin.LOCAL)
            source == target -> DataResult.Success(trimmed, DataOrigin.LOCAL)
            else -> repository.translate(trimmed, source, target).map { it.trim() }
        }
    }

    companion object {
        /** Oltre questa lunghezza conviene dividere il testo: il traduttore è pensato per frasi e brevi testi. */
        const val MAX_LENGTH = 2_000
    }
}

/** Pacchetti lingua del traduttore: quali mancano per una coppia di lingue e il loro download. */
class LanguagePacksUseCase(private val repository: TranslatorRepository) {

    /** Lingue della coppia ancora da scaricare (l'inglese c'è sempre). */
    suspend fun missing(from: String, to: String): DataResult<Set<String>> =
        repository.downloadedLanguages().map { downloaded ->
            setOf(TranslatorLanguages.normalize(from), TranslatorLanguages.normalize(to)) - downloaded - TranslatorLanguages.ENGLISH
        }

    suspend fun download(from: String, to: String): DataResult<Unit> =
        repository.download(TranslatorLanguages.normalize(from), TranslatorLanguages.normalize(to))
}

/**
 * Traduce il testo di una foto (menù, cartelli, orari): lo riconosce sul telefono con il modello della scrittura
 * della lingua [from] e traduce ogni blocco tenendone la posizione, per mostrare la traduzione sopra la foto.
 * Gli elenchi (righe che finiscono con un prezzo o un numero, come nei menù) si traducono riga per riga, gli altri
 * blocchi come un'unica frase. Una foto senza testo è [DataError.InvalidResponse].
 */
class TranslatePhotoUseCase(
    private val textRecognition: TextRecognitionRepository,
    private val translateText: TranslateTextUseCase,
) {
    suspend operator fun invoke(source: DocumentSource, from: String, to: String): DataResult<PhotoTranslation> {
        val script = TextScript.of(from) ?: return DataResult.Failure(DataError.InvalidQuery(QueryIssue.UNSUPPORTED_SCRIPT))
        val recognized = when (val result = textRecognition.recognize(source, script)) {
            is DataResult.Failure -> return result
            is DataResult.Success -> result.data
        }
        if (recognized.isEmpty) return DataResult.Failure(DataError.InvalidResponse)
        // Senza posizioni (es. un PDF) tutto il testo è un blocco solo.
        val blocks = recognized.blocks.ifEmpty { listOf(RecognizedBlock(recognized.text, 0, 0, 0, 0)) }
            .filter { it.text.isNotBlank() }
            .sortedWith(compareBy({ it.top }, { it.left }))
        val cache = mutableMapOf<String, String>()
        val translated = blocks.map { block ->
            val translation = linesToTranslate(block.text).map { line ->
                cache[line] ?: when (val result = translateText(line.take(TranslateTextUseCase.MAX_LENGTH), from, to)) {
                    is DataResult.Failure -> return result
                    is DataResult.Success -> result.data.also { cache[line] = it }
                }
            }
            TranslatedBlock(block, translation.joinToString("\n"))
        }
        return DataResult.Success(PhotoTranslation(recognized.width, recognized.height, translated), DataOrigin.LOCAL)
    }

    /** Righe di un elenco una per una; altrimenti il blocco come un'unica frase, ricomponendo le parole divise. */
    private fun linesToTranslate(text: String): List<String> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val listLike = lines.size > 1 && lines.count { line -> line.last().isDigit() || line.last() in CURRENCY_SIGNS } * 2 >= lines.size
        if (listLike) return lines
        return listOf(lines.reduceOrNull { joined, line -> if (joined.endsWith('-')) joined.dropLast(1) + line else "$joined $line" }.orEmpty())
    }

    private companion object {
        val CURRENCY_SIGNS = setOf('€', '$', '£', '¥', '₩', '₹')
    }
}
