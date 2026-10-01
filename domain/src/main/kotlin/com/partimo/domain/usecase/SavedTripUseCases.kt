package com.partimo.domain.usecase

import com.partimo.domain.model.Destination
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.saved.Favorite
import com.partimo.domain.model.saved.SavedTrip
import com.partimo.domain.repository.SavedTripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** Viaggi salvati: prima quelli in arrivo (dal più vicino), poi quelli ormai passati. */
class ObserveSavedTripsUseCase(
    private val repository: SavedTripRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    operator fun invoke(): Flow<List<SavedTrip>> = repository.trips.map { trips ->
        val today = LocalDate.now(clock)
        trips.sortedWith(compareBy<SavedTrip>({ it.period.isOver(today) }, { it.departureDate(today) }, { it.destination.name }))
    }
}

/** Il viaggio salvato per una meta e un periodo; `null` se non è salvato. */
class ObserveSavedTripUseCase(private val repository: SavedTripRepository) {
    operator fun invoke(destination: Destination, period: TravelPeriod): Flow<SavedTrip?> {
        val id = SavedTrip.idOf(destination, period)
        return repository.trips.map { trips -> trips.firstOrNull { it.id == id } }.distinctUntilChanged()
    }
}

/** Salva un viaggio o lo toglie (con i suoi preferiti). */
class SetTripSavedUseCase(
    private val repository: SavedTripRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend operator fun invoke(destination: Destination, period: TravelPeriod, saved: Boolean) {
        val id = SavedTrip.idOf(destination, period)
        repository.update { trips ->
            when {
                !saved -> trips.filterNot { it.id == id }
                trips.any { it.id == id } -> trips
                else -> trips + SavedTrip(destination, period, Instant.now(clock))
            }
        }
    }
}

/**
 * Aggiunge un preferito al viaggio, o lo toglie se c'è già. Il primo preferito salva anche il viaggio,
 * così lo si ritrova nella schermata iniziale.
 *
 * @return `true` se dopo l'operazione l'elemento è tra i preferiti.
 */
class ToggleFavoriteUseCase(
    private val repository: SavedTripRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend operator fun invoke(destination: Destination, period: TravelPeriod, favorite: Favorite): Boolean {
        val id = SavedTrip.idOf(destination, period)
        var nowFavorite = false
        repository.update { trips ->
            val trip = trips.firstOrNull { it.id == id } ?: SavedTrip(destination, period, Instant.now(clock))
            val favorites = if (trip.isFavorite(favorite.key)) {
                trip.favorites.filterNot { it.key == favorite.key }
            } else {
                nowFavorite = true
                trip.favorites + favorite
            }
            trips.filterNot { it.id == id } + trip.copy(favorites = favorites)
        }
        return nowFavorite
    }
}
