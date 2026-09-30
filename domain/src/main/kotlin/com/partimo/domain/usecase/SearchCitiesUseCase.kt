package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.common.QueryIssue
import com.partimo.domain.common.map
import com.partimo.domain.model.place.CityPlace
import com.partimo.domain.repository.CitySearchRepository
import java.text.Normalizer

/**
 * Ricerca di città in tutto il mondo per nome (anche parziale, es. "Tok" → Tokyo).
 *
 * I risultati sono ordinati per pertinenza: prima il nome identico, poi quelli che iniziano con il
 * testo cercato, infine per popolazione. Il confronto ignora maiuscole e accenti ("reykjavik" → Reykjavík).
 */
class SearchCitiesUseCase(private val repository: CitySearchRepository) {

    suspend operator fun invoke(query: String, limit: Int = DEFAULT_LIMIT): DataResult<List<CityPlace>> {
        val text = query.trim().replace(WHITESPACE, " ")
        if (text.length < MIN_QUERY_LENGTH) return DataResult.Failure(DataError.InvalidQuery(QueryIssue.QUERY_TOO_SHORT))

        val normalizedQuery = text.normalizedForSearch()
        // Si chiedono più risultati del necessario: il riordinamento può premiare quelli in fondo.
        return repository.searchCities(text, limit = limit * 2).map { cities ->
            cities
                .distinctBy { it.id }
                .sortedWith(
                    compareByDescending<CityPlace> { matchRank(it.name.normalizedForSearch(), normalizedQuery) }
                        .thenByDescending { it.population ?: 0 },
                )
                .take(limit)
        }
    }

    private fun matchRank(name: String, query: String): Int = when {
        name == query -> 2
        name.startsWith(query) -> 1
        else -> 0
    }

    private fun String.normalizedForSearch(): String =
        Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")

    companion object {
        const val MIN_QUERY_LENGTH = 2
        const val DEFAULT_LIMIT = 8
        private val WHITESPACE = Regex("\\s+")
        private val DIACRITICS = Regex("\\p{Mn}+")
    }
}
