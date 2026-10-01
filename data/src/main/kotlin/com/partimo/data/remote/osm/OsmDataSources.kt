package com.partimo.data.remote.osm

import com.partimo.data.cache.CacheKey
import com.partimo.data.cache.CachePolicy
import com.partimo.data.cache.ResponseCache
import com.partimo.data.network.Fetched
import com.partimo.data.network.NetworkJson
import com.partimo.data.network.mapNotNullSafely
import com.partimo.data.source.LodgingDataSource
import com.partimo.data.source.RestaurantDataSource
import com.partimo.domain.model.WheelchairAccess
import com.partimo.domain.model.dining.Restaurant
import com.partimo.domain.model.dining.RestaurantSearchQuery
import com.partimo.domain.model.stay.Lodging
import com.partimo.domain.model.stay.LodgingQuery
import com.partimo.domain.model.stay.LodgingType
import java.net.URLEncoder

/**
 * Locali reali da OpenStreetMap, senza chiave: nome, cucina, indirizzo e sito. OpenStreetMap non ha
 * valutazioni né fasce di prezzo, quindi i locali sono ordinati per vicinanza al centro e completezza
 * dei dati, con i ristoranti prima dei fast food e le catene in fondo. Ogni locale ha il collegamento
 * a Google Maps, dove si leggono recensioni e prezzi.
 */
class OsmRestaurantDataSource internal constructor(
    private val api: OverpassApi,
    private val cache: ResponseCache,
    private val languageCode: String,
) : RestaurantDataSource {

    override suspend fun searchRestaurants(query: RestaurantSearchQuery, forceRefresh: Boolean): Fetched<List<Restaurant>> {
        val radius = query.radiusMeters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
        val overpassQl = OverpassQueries.restaurants(query.location, radius, ELEMENT_LIMIT)
        return cache.getOrFetch(
            key = CacheKey.of("osm-restaurants", overpassQl),
            ttl = CachePolicy.RESTAURANTS,
            forceRefresh = forceRefresh,
            fetch = { api.query(overpassQl) },
            parse = { body ->
                parseElements(body)
                    .mapNotNullSafely { element -> element.toRankedRestaurant(query) }
                    .sortedByDescending { it.score }
                    .map { it.restaurant }
                    .take(MAX_RESULTS)
            },
        )
    }

    private class RankedRestaurant(val restaurant: Restaurant, val score: Double)

    private fun OsmElement.toRankedRestaurant(query: RestaurantSearchQuery): RankedRestaurant? {
        val name = localizedName(languageCode) ?: return null
        val location = location ?: return null
        val amenity = tags["amenity"]
        val address = address()
        val restaurant = Restaurant(
            id = "osm:$type/$id",
            name = name,
            priceLevel = null,
            rating = null,
            cuisine = OsmLabels.cuisine(tags["cuisine"], amenity),
            address = address,
            location = location,
            mapsUrl = googleMapsSearchUrl(listOfNotNull(name, address, query.areaName)),
            website = website(),
            openingHours = tags["opening_hours"]?.trim()?.takeIf { it.isNotEmpty() },
            wheelchair = OsmLabels.wheelchair(tags["wheelchair"]),
        )
        return RankedRestaurant(restaurant, score(amenity, location.distanceTo(query.location)))
    }

    /** Punteggio di ordinamento: completezza dei dati e vicinanza al centro; le catene scendono in fondo. */
    private fun OsmElement.score(amenity: String?, distanceMeters: Double): Double {
        val base = when (amenity) {
            "restaurant" -> 1.0
            "food_court" -> 0.6
            else -> 0.3
        }
        val details = listOf("cuisine", "opening_hours").count { it in tags } * DETAIL_BONUS +
            (if (website() != null) DETAIL_BONUS else 0.0) +
            (if ("wikidata" in tags) NOTABLE_BONUS else 0.0)
        val chain = if ("brand" in tags || "brand:wikidata" in tags) CHAIN_PENALTY else 0.0
        return base + details - chain - distanceMeters / METERS_PER_KM * DISTANCE_PENALTY_PER_KM
    }

    internal companion object {
        const val MIN_RADIUS_METERS = 500
        const val MAX_RADIUS_METERS = 1_500
        const val ELEMENT_LIMIT = 600
        const val MAX_RESULTS = 20
        private const val DETAIL_BONUS = 0.3
        private const val NOTABLE_BONUS = 0.5
        private const val CHAIN_PENALTY = 1.0
        private const val DISTANCE_PENALTY_PER_KM = 0.8
        private const val METERS_PER_KM = 1_000.0
    }
}

/**
 * Strutture ricettive reali da OpenStreetMap, senza chiave: nome, tipo, stelle e indirizzo.
 * Tariffe e disponibilità non ci sono: l'app rimanda a Booking.com o Airbnb con le date del viaggio.
 */
class OsmLodgingDataSource internal constructor(
    private val api: OverpassApi,
    private val cache: ResponseCache,
    private val languageCode: String,
) : LodgingDataSource {

    override suspend fun findLodgings(query: LodgingQuery, forceRefresh: Boolean): Fetched<List<Lodging>> {
        val radius = query.radiusMeters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
        val overpassQl = OverpassQueries.lodgings(query.location, radius, ELEMENT_LIMIT)
        return cache.getOrFetch(
            key = CacheKey.of("osm-lodgings", overpassQl),
            ttl = CachePolicy.LODGINGS,
            forceRefresh = forceRefresh,
            fetch = { api.query(overpassQl) },
            parse = { body -> parseElements(body).mapNotNullSafely { it.toLodging() } },
        )
    }

    private fun OsmElement.toLodging(): Lodging? {
        val lodgingType = OsmLabels.lodgingType(tags["tourism"]) ?: return null
        val name = localizedName(languageCode) ?: return null
        val location = location ?: return null
        return Lodging(
            id = "osm:$type/$id",
            name = name,
            type = lodgingType,
            location = location,
            starRating = OsmLabels.stars(tags["stars"]),
            address = address(),
            website = website(),
            wheelchair = OsmLabels.wheelchair(tags["wheelchair"]),
        )
    }

    internal companion object {
        const val MIN_RADIUS_METERS = 1_000
        const val MAX_RADIUS_METERS = 3_000
        const val ELEMENT_LIMIT = 600
    }
}

/** Traduzione dei valori di OpenStreetMap nei concetti e nei testi (italiani) dell'app. */
internal object OsmLabels {

    private const val MAX_CUISINES = 2

    /** Tag `wheelchair`: "yes", "limited", "no" (altri valori, come "designated", contano come accessibile). */
    fun wheelchair(value: String?): WheelchairAccess? = when (value?.trim()?.lowercase()) {
        "yes", "designated" -> WheelchairAccess.YES
        "limited" -> WheelchairAccess.LIMITED
        "no" -> WheelchairAccess.NO
        else -> null
    }

    private val CUISINES = mapOf(
        "italian" to "Italiana", "pizza" to "Pizza", "regional" to "Regionale", "local" to "Locale",
        "austrian" to "Austriaca", "german" to "Tedesca", "french" to "Francese", "spanish" to "Spagnola",
        "portuguese" to "Portoghese", "greek" to "Greca", "turkish" to "Turca", "kebab" to "Kebab",
        "burger" to "Hamburger", "sandwich" to "Panini", "sushi" to "Sushi", "japanese" to "Giapponese",
        "chinese" to "Cinese", "thai" to "Thailandese", "vietnamese" to "Vietnamita", "korean" to "Coreana",
        "indian" to "Indiana", "asian" to "Asiatica", "mexican" to "Messicana", "american" to "Americana",
        "lebanese" to "Libanese", "middle_eastern" to "Mediorientale", "mediterranean" to "Mediterranea",
        "seafood" to "Pesce", "fish" to "Pesce", "fish_and_chips" to "Fish and chips", "steak_house" to "Carne alla griglia",
        "vegetarian" to "Vegetariana", "vegan" to "Vegana", "international" to "Internazionale",
        "breakfast" to "Colazioni", "coffee_shop" to "Caffetteria", "cake" to "Dolci", "ice_cream" to "Gelateria",
        "chicken" to "Pollo", "noodle" to "Noodles", "ramen" to "Ramen", "tapas" to "Tapas", "georgian" to "Georgiana",
        "ethiopian" to "Etiope", "african" to "Africana", "polish" to "Polacca", "czech" to "Ceca",
        "hungarian" to "Ungherese", "russian" to "Russa", "british" to "Britannica", "irish" to "Irlandese",
        "brazilian" to "Brasiliana", "peruvian" to "Peruviana", "argentinian" to "Argentina", "persian" to "Persiana",
        "syrian" to "Siriana", "falafel" to "Falafel", "crepe" to "Crêpes", "bagel" to "Bagel", "hot_dog" to "Hot dog",
        "sausage" to "Würstel", "wine" to "Vineria", "heuriger" to "Heuriger", "bistro" to "Bistrot",
        "swiss" to "Svizzera", "jewish" to "Ebraica", "israeli" to "Israeliana", "belgian" to "Belga",
        "dutch" to "Olandese", "scandinavian" to "Scandinava", "danish" to "Danese", "swedish" to "Svedese",
        "ukrainian" to "Ucraina", "balkan" to "Balcanica", "croatian" to "Croata", "serbian" to "Serba",
        "romanian" to "Rumena", "moroccan" to "Marocchina", "tunisian" to "Tunisina", "nepalese" to "Nepalese",
        "pakistani" to "Pakistana", "indonesian" to "Indonesiana", "malaysian" to "Malese", "filipino" to "Filippina",
        "taiwanese" to "Taiwanese", "hawaiian" to "Hawaiana", "poke" to "Poke", "cuban" to "Cubana",
        "caribbean" to "Caraibica", "colombian" to "Colombiana", "arab" to "Araba", "afghan" to "Afghana",
        "armenian" to "Armena", "uzbek" to "Uzbeka", "fusion" to "Fusion", "barbecue" to "Barbecue",
        "grill" to "Griglia", "soup" to "Zuppe", "dumpling" to "Ravioli cinesi", "bubble_tea" to "Bubble tea",
    )

    /** Fino a due cucine ("regional;international" → "Regionale · Internazionale"). */
    fun cuisine(value: String?, amenity: String?): String? {
        val labels = value.orEmpty()
            .split(';')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .map { cuisine -> CUISINES[cuisine] ?: cuisine.replace('_', ' ').replaceFirstChar { it.titlecase() } }
            .distinct()
            .take(MAX_CUISINES)
        if (labels.isNotEmpty()) return labels.joinToString(" · ")
        return when (amenity) {
            "fast_food" -> "Fast food"
            "food_court" -> "Area ristoro"
            else -> null
        }
    }

    fun lodgingType(tourism: String?): LodgingType? = when (tourism) {
        "hotel" -> LodgingType.HOTEL
        "hostel" -> LodgingType.HOSTEL
        "guest_house" -> LodgingType.GUEST_HOUSE
        "apartment" -> LodgingType.APARTMENT
        "motel" -> LodgingType.MOTEL
        else -> null
    }

    /** Stelle dalla prima cifra del valore ("4", "4S", "3.5" → 4, 4, 3); fuori scala → nessuna. */
    fun stars(value: String?): Int? = value?.trim()?.firstOrNull()?.digitToIntOrNull()?.takeIf { it in 1..5 }
}

private val WEBSITE = Regex("^https?://\\S+$")

private fun parseElements(body: String): List<OsmElement> {
    val response = NetworkJson.decodeFromString(OverpassResponse.serializer(), body)
    // Un errore del server (es. query scaduta) produce una risposta incompleta: meglio non mostrarla.
    require(response.remark?.contains("error", ignoreCase = true) != true) { "Risposta Overpass incompleta: ${response.remark}" }
    return response.elements
}

/** Nome nella lingua dell'app se presente (es. "name:it"), altrimenti quello locale. */
private fun OsmElement.localizedName(languageCode: String): String? =
    (tags["name:$languageCode"] ?: tags["name"])?.trim()?.takeIf { it.isNotEmpty() }

/** "Via Roma 12" dai campi addr:*; `null` se manca la via. */
private fun OsmElement.address(): String? {
    val street = tags["addr:street"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return listOfNotNull(street, tags["addr:housenumber"]?.trim()?.takeIf { it.isNotEmpty() }).joinToString(" ")
}

private fun OsmElement.website(): String? =
    (tags["website"] ?: tags["contact:website"])?.trim()?.takeIf { WEBSITE.matches(it) }

/** Ricerca su Google Maps (senza chiave): la scheda del locale mostra recensioni, foto, prezzi e indicazioni. */
internal fun googleMapsSearchUrl(parts: List<String>): String =
    "https://www.google.com/maps/search/?api=1&query=" +
        URLEncoder.encode(parts.joinToString(", "), Charsets.UTF_8.name()).replace("+", "%20")
