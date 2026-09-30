package com.partimo.domain.service

/**
 * Verifica se [keyword] compare nel testo all'inizio di una parola.
 *
 * Il confronto per prefisso gestisce varianti e composti ("panoram" → "panoramica",
 * "weihnacht" → "Weihnachtsmarkt") evitando falsi positivi a metà parola
 * ("ski" non corrisponde a "Nevski").
 */
internal fun String.containsWordPrefix(keyword: String): Boolean {
    val pattern = Regex("(?<!\\p{L})" + Regex.escape(keyword.lowercase()))
    return pattern.containsMatchIn(lowercase())
}

internal fun String.containsAnyWordPrefix(keywords: Collection<String>): Boolean =
    keywords.any { containsWordPrefix(it) }
