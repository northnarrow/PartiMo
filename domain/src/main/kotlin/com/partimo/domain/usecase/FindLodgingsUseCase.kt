package com.partimo.domain.usecase

import com.partimo.domain.common.DataResult
import com.partimo.domain.common.map
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.stay.Lodging
import com.partimo.domain.model.stay.LodgingQuery
import com.partimo.domain.repository.LodgingRepository

/**
 * Strutture ricettive reali vicino al centro, dalla più vicina. Senza prezzi la distanza è il
 * criterio più utile; a parità di distanza vengono prima le strutture con le stelle.
 */
class FindLodgingsUseCase(
    private val repository: LodgingRepository,
    private val maxResults: Int = DEFAULT_MAX_RESULTS,
) {

    suspend operator fun invoke(center: GeoPoint, forceRefresh: Boolean = false): DataResult<List<Lodging>> =
        repository.findLodgings(LodgingQuery(center), forceRefresh).map { lodgings -> arrange(lodgings, center) }

    /** L'ultimo elenco salvato, anche se scaduto, nello stesso ordine: si mostra subito mentre si aggiorna. */
    suspend fun saved(center: GeoPoint): List<Lodging>? =
        repository.savedLodgings(LodgingQuery(center))?.let { lodgings -> arrange(lodgings, center) }?.takeIf { it.isNotEmpty() }

    private fun arrange(lodgings: List<Lodging>, center: GeoPoint): List<Lodging> = lodgings
        .distinctBy { it.id }
        .sortedWith(compareBy<Lodging> { it.location.distanceTo(center) }.thenByDescending { it.starRating ?: 0 })
        .take(maxResults)

    private companion object {
        const val DEFAULT_MAX_RESULTS = 25
    }
}
