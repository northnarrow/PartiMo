package com.partimo.domain.service

/**
 * Estratti brevi di testi lunghi (voci enciclopediche): si tengono i paragrafi iniziali entro un
 * numero massimo di caratteri e il testo si taglia alla fine di una frase, mai a metà parola.
 */
internal object Excerpt {

    private const val PARAGRAPH_SEPARATOR = "\n\n"
    private const val ELLIPSIS = "…"
    private const val MIN_WORDS = 4

    /** Una frase completa sostituisce il taglio con "…" solo se conserva almeno un quarto del testo. */
    private const val MIN_SENTENCE_FRACTION = 4

    private val SENTENCE_TERMINATORS = setOf('.', '!', '?', '…')
    private val CLOSING_MARKS = setOf('»', '"', '”', '’', ')')
    private val OPENING_MARKS = setOf('«', '"', '“', '(', '\'')
    private val EMPTY_PARENTHESES = Regex("""\(\s*[,;:]?\s*\)""")
    private val SPACE_BEFORE_PUNCTUATION = Regex("""\s+([,.;:!?])""")
    private val WHITESPACE = Regex("""\s+""")

    /**
     * Paragrafi iniziali di [paragraphs] entro [maxChars] caratteri, separati da una riga vuota;
     * `null` se non c'è testo leggibile. Il primo paragrafo c'è sempre (accorciato se serve),
     * i successivi solo se entrano per intero.
     */
    fun of(paragraphs: List<String>, maxChars: Int, maxParagraphs: Int = Int.MAX_VALUE): String? {
        require(maxChars > 0 && maxParagraphs > 0) { "Limiti non validi: $maxChars caratteri, $maxParagraphs paragrafi" }
        val selected = mutableListOf<String>()
        var length = 0
        for (paragraph in readableParagraphs(paragraphs)) {
            if (selected.size == maxParagraphs) break
            if (selected.isEmpty()) {
                selected += shorten(paragraph, maxChars)
                length = selected.single().length
                continue
            }
            val extended = length + PARAGRAPH_SEPARATOR.length + paragraph.length
            if (extended > maxChars) break
            selected += paragraph
            length = extended
        }
        return selected.takeIf { it.isNotEmpty() }?.joinToString(PARAGRAPH_SEPARATOR)
    }

    /** `true` se almeno un paragrafo è leggibile da solo in un estratto. */
    fun hasReadableText(paragraphs: List<String>): Boolean = readableParagraphs(paragraphs).any()

    /**
     * Accorcia [text] alla fine dell'ultima frase che entra in [maxChars] caratteri; se non ce n'è una
     * abbastanza lunga taglia all'ultima parola intera e aggiunge "…".
     */
    fun shorten(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val sentenceEnd = (maxChars - 1 downTo 0).firstNotNullOfOrNull { index ->
            sentenceEndAt(text, index)?.takeIf { it < maxChars }
        }
        if (sentenceEnd != null && sentenceEnd + 1 >= maxChars / MIN_SENTENCE_FRACTION) {
            return text.substring(0, sentenceEnd + 1)
        }
        val cut = text.lastIndexOf(' ', startIndex = maxChars - 1).takeIf { it > 0 } ?: (maxChars - 1)
        return text.substring(0, cut).trimEnd(',', ';', ':', ' ', '(') + ELLIPSIS
    }

    /**
     * Indice dell'ultimo carattere della frase che termina in [index] (compresi virgolette o parentesi
     * di chiusura), oppure `null` se lì non finisce una frase. Le iniziali puntate ("G. Verdi")
     * e i punti seguiti da una minuscola ("d.C. fu") non chiudono la frase.
     */
    private fun sentenceEndAt(text: String, index: Int): Int? {
        if (text[index] !in SENTENCE_TERMINATORS) return null
        var end = index
        while (end + 1 < text.length && text[end + 1] in CLOSING_MARKS) end++
        if (end + 1 == text.length) return end
        if (!text[end + 1].isWhitespace()) return null
        val next = text.substring(end + 1).trimStart().firstOrNull() ?: return end
        if (!next.isUpperCase() && !next.isDigit() && next !in OPENING_MARKS) return null
        val wordStart = text.lastIndexOf(' ', startIndex = index) + 1
        val isInitial = text[index] == '.' && index - wordStart == 1 && text[wordStart].isUpperCase()
        return end.takeUnless { isInitial }
    }

    private fun readableParagraphs(paragraphs: List<String>): Sequence<String> =
        paragraphs.asSequence().map(::normalize).filter(::isReadable)

    /**
     * Esclude i paragrafi troppo brevi (voci di elenco, didascalie) e quelli che introducono un
     * elenco o una citazione, che nel testo semplice della voce non compaiono.
     */
    private fun isReadable(paragraph: String): Boolean =
        paragraph.split(' ').size >= MIN_WORDS && !paragraph.endsWith(':')

    /** Spazi e punteggiatura lasciati dai modelli rimossi nel testo semplice (es. "( )", "Roma ,"). */
    private fun normalize(paragraph: String): String = paragraph
        .replace(EMPTY_PARENTHESES, "")
        .replace(WHITESPACE, " ")
        .replace(SPACE_BEFORE_PUNCTUATION, "$1")
        .trim()
}
