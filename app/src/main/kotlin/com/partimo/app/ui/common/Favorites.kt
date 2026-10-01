package com.partimo.app.ui.common

import com.partimo.app.ui.dashboard.components.toPointOfInterest
import com.partimo.domain.model.dining.Restaurant
import com.partimo.domain.model.event.TripEvent
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.poi.PointOfInterest
import com.partimo.domain.model.saved.Favorite
import com.partimo.domain.model.saved.FavoriteKind
import com.partimo.domain.model.stay.Lodging

// Conversione tra gli elementi dell'app e i preferiti salvati: dal preferito si riapre la scheda del
// luogo (con la sua voce di Wikipedia) oppure la pagina del locale su Google Maps.

/** Un evento aperto come luogo (categoria evento stagionale) resta un evento anche tra i preferiti. */
fun PointOfInterest.toFavorite(): Favorite = Favorite(
    id = id,
    kind = if (category == PoiCategory.SEASONAL_EVENT) FavoriteKind.EVENT else FavoriteKind.PLACE,
    name = name,
    location = location,
    photoUrl = photoUrl,
    url = mapsUrl,
    wikipediaPage = wikipediaPage,
    category = category,
    description = description,
)

fun TripEvent.toFavorite(): Favorite = toPointOfInterest()?.toFavorite()
    ?: Favorite(id = id, kind = FavoriteKind.EVENT, name = name, description = description)

fun Restaurant.toFavorite(): Favorite = Favorite(
    id = id,
    kind = FavoriteKind.RESTAURANT,
    name = name,
    subtitle = listOfNotNull(cuisine, address).joinToString(" · ").ifBlank { null },
    location = location,
    photoUrl = photoUrl,
    url = mapsUrl ?: website,
)

fun Lodging.toFavorite(mapsUrl: String): Favorite = Favorite(
    id = id,
    kind = FavoriteKind.LODGING,
    name = name,
    subtitle = address,
    location = location,
    url = mapsUrl,
)

/** Scheda da riaprire per luoghi ed eventi con una posizione; `null` per ristoranti e strutture. */
fun Favorite.toPointOfInterest(): PointOfInterest? {
    if (kind != FavoriteKind.PLACE && kind != FavoriteKind.EVENT) return null
    val place = location ?: return null
    return PointOfInterest(
        id = id,
        name = name,
        category = category ?: if (kind == FavoriteKind.EVENT) PoiCategory.SEASONAL_EVENT else PoiCategory.ATTRACTION,
        location = place,
        description = description,
        photoUrl = photoUrl,
        mapsUrl = url,
        wikipediaPage = wikipediaPage,
    )
}
