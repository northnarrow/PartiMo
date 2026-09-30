package com.partimo.data.remote.wikipedia

import com.partimo.domain.model.poi.ArticleSection

/** Testo di una voce diviso in introduzione e sezione sulla storia (con le sue sottosezioni). */
internal data class ParsedArticle(val introduction: List<String>, val history: List<ArticleSection>)

/**
 * Interpreta il testo semplice di una voce (TextExtracts con `exsectionformat=wiki`), dove i titoli
 * delle sezioni sono righe come "== Storia ==" o "=== Costruzione ===" e i paragrafi sono righe.
 */
internal object WikipediaArticleParser {

    private val HEADING = Regex("""^(={2,6})\s*(.+?)\s*\1$""")

    /** Titoli che indicano la storia del luogo (it, en e le principali lingue europee). */
    private val HISTORY_KEYWORDS = listOf("stori", "cenni stor", "histor", "geschicht", "histoire", "historia")

    /** Ripiego quando manca una vera sezione storica: "Origini", "Origins". */
    private val ORIGINS_KEYWORDS = listOf("origin")

    fun parse(extract: String): ParsedArticle {
        val sections = sections(extract)
        return ParsedArticle(
            introduction = sections.firstOrNull { it.level == 0 }?.paragraphs.orEmpty(),
            history = historySections(sections),
        )
    }

    private class Section(val level: Int, val title: String?) {
        val paragraphs = mutableListOf<String>()
    }

    private fun sections(extract: String): List<Section> {
        val sections = mutableListOf(Section(level = 0, title = null))
        extract.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val heading = HEADING.matchEntire(line)
            if (heading != null) {
                sections += Section(level = heading.groupValues[1].length, title = heading.groupValues[2])
            } else {
                sections.last().paragraphs += line
            }
        }
        return sections
    }

    /**
     * La prima sezione il cui titolo parla di storia, seguita dalle sue sottosezioni fino alla
     * sezione successiva di pari livello. Il testo che apre la sezione non ha titolo.
     */
    private fun historySections(sections: List<Section>): List<ArticleSection> {
        val headed = sections.drop(1)
        val start = headed.indexOfFirst { it.titleMatches(HISTORY_KEYWORDS) }.takeIf { it >= 0 }
            ?: headed.indexOfFirst { it.titleMatches(ORIGINS_KEYWORDS) }.takeIf { it >= 0 }
            ?: return emptyList()
        val history = headed[start]
        val subsections = headed.drop(start + 1).takeWhile { it.level > history.level }
        return listOf(ArticleSection(title = null, paragraphs = history.paragraphs.toList())) +
            subsections.map { ArticleSection(title = it.title, paragraphs = it.paragraphs.toList()) }
    }

    private fun Section.titleMatches(keywords: List<String>): Boolean {
        val normalized = title?.lowercase() ?: return false
        return keywords.any { keyword -> Regex("(?<!\\p{L})" + Regex.escape(keyword)).containsMatchIn(normalized) }
    }
}
