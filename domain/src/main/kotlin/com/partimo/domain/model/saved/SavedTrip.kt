package com.partimo.domain.model.saved

import com.partimo.domain.model.Destination
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.poi.WikipediaPage
import java.time.Instant
import java.time.LocalDate

/** Che cosa è stato salvato tra i preferiti. */
enum class FavoriteKind { PLACE, EVENT, RESTAURANT, LODGING }

/**
 * Elemento preferito di un viaggio, con i dati per mostrarlo e riaprirlo anche senza rete: la scheda
 * del luogo (con la voce di Wikipedia) o la pagina di Google Maps.
 */
data class Favorite(
    /** Identificativo della fonte (es. "wikipedia:it:123", "osm:node/42"). */
    val id: String,
    val kind: FavoriteKind,
    val name: String,
    /** Categoria, cucina o tipo di struttura, già pronti da mostrare. */
    val subtitle: String? = null,
    val location: GeoPoint? = null,
    val photoUrl: String? = null,
    /** Pagina da aprire per ristoranti e strutture (Google Maps, sito). */
    val url: String? = null,
    val wikipediaPage: WikipediaPage? = null,
    val category: PoiCategory? = null,
    val description: String? = null,
) {
    /** Lo stesso elemento può comparire in più sezioni: si distingue per tipo e identificativo. */
    val key: String get() = "${kind.name}:$id"
}

/** Viaggio salvato dall'utente (meta e periodo), con i suoi preferiti. */
data class SavedTrip(
    val destination: Destination,
    val period: TravelPeriod,
    val savedAt: Instant,
    val favorites: List<Favorite> = emptyList(),
) {
    val id: String get() = idOf(destination, period)

    fun isFavorite(favoriteKey: String): Boolean = favorites.any { it.key == favoriteKey }

    fun departureDate(today: LocalDate): LocalDate = period.departureDate(today)

    fun returnDate(today: LocalDate): LocalDate = period.returnDate(today)

    companion object {
        /** Un viaggio è la meta in un periodo (gli stessi dati della dashboard). */
        fun idOf(destination: Destination, period: TravelPeriod): String =
            "${destination.countryCode}:${destination.name}:${period.key}"
    }
}
