package com.partimo.data.source

import com.partimo.data.network.Fetched
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.dining.Restaurant
import com.partimo.domain.model.dining.RestaurantSearchQuery
import com.partimo.domain.model.flight.FlightOffer
import com.partimo.domain.model.flight.FlightSearchQuery
import com.partimo.domain.model.place.Airport
import com.partimo.domain.model.place.CatalogDestination
import com.partimo.domain.model.place.CityPlace
import com.partimo.domain.model.poi.PoiArticle
import com.partimo.domain.model.poi.PoiQuery
import com.partimo.domain.model.poi.PointOfInterest
import com.partimo.domain.model.stay.AccommodationOffer
import com.partimo.domain.model.stay.AccommodationSearchQuery
import com.partimo.domain.model.transit.TransitRoute
import com.partimo.domain.model.transit.TransitRouteQuery
import com.partimo.domain.model.weather.WeatherSnapshot

// Sorgenti dati per provider. Ogni interfaccia ha un'implementazione reale (Ktor + cache Room),
// e quelle che richiedono una chiave anche una demo: sostituire un provider (es. passare a un
// backend proprio) richiede una sola classe.
// Le implementazioni possono lanciare eccezioni, che i repository convertono in DataError.

interface FlightOffersDataSource {
    suspend fun searchOffers(query: FlightSearchQuery, forceRefresh: Boolean): Fetched<List<FlightOffer>>
}

interface StayOffersDataSource {
    suspend fun searchStays(query: AccommodationSearchQuery, forceRefresh: Boolean): Fetched<List<AccommodationOffer>>
}

interface PoiDataSource {
    suspend fun pointsOfInterest(query: PoiQuery, forceRefresh: Boolean): Fetched<List<PointOfInterest>>
}

interface PoiArticleDataSource {
    /** Voce enciclopedica che descrive [poi]; `null` se non ne esiste una che lo riguardi con certezza. */
    suspend fun findArticle(poi: PointOfInterest, forceRefresh: Boolean): Fetched<PoiArticle?>
}

interface RestaurantDataSource {
    suspend fun searchRestaurants(query: RestaurantSearchQuery, forceRefresh: Boolean): Fetched<List<Restaurant>>
}

interface TransitDataSource {
    suspend fun routes(query: TransitRouteQuery, forceRefresh: Boolean): Fetched<List<TransitRoute>>
}

interface WeatherDataSource {
    suspend fun currentWeather(location: GeoPoint): Fetched<WeatherSnapshot>
}

interface CitySearchDataSource {
    suspend fun searchCities(query: String, limit: Int): Fetched<List<CityPlace>>
}

interface AirportDataSource {
    /** Aeroporti entro [radiusKm], ordinati per distanza crescente. */
    suspend fun airportsNear(location: GeoPoint, radiusKm: Double): Fetched<List<Airport>>

    suspend fun findByIata(iata: String): Airport?
}

interface DestinationCatalogDataSource {
    suspend fun destinations(): Fetched<List<CatalogDestination>>
}
