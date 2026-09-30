package com.partimo.domain.repository

import com.partimo.domain.common.DataResult
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.deal.PriceWatch
import com.partimo.domain.model.dining.Restaurant
import com.partimo.domain.model.dining.RestaurantSearchQuery
import com.partimo.domain.model.flight.FlightOffer
import com.partimo.domain.model.flight.FlightSearchQuery
import com.partimo.domain.model.place.Airport
import com.partimo.domain.model.place.CatalogDestination
import com.partimo.domain.model.place.CityPlace
import com.partimo.domain.model.place.DeparturePoint
import com.partimo.domain.model.poi.PoiQuery
import com.partimo.domain.model.poi.PointOfInterest
import com.partimo.domain.model.stay.AccommodationOffer
import com.partimo.domain.model.stay.AccommodationSearchQuery
import com.partimo.domain.model.transit.TransitRoute
import com.partimo.domain.model.transit.TransitRouteQuery
import com.partimo.domain.model.weather.WeatherSnapshot
import kotlinx.coroutines.flow.Flow

// Contratti del dominio verso il data layer. Le implementazioni decidono provider, cache e
// threading; il parametro forceRefresh permette di ignorare una cache ancora valida.

interface FlightRepository {
    suspend fun searchFlights(query: FlightSearchQuery, forceRefresh: Boolean = false): DataResult<List<FlightOffer>>
}

interface AccommodationRepository {
    suspend fun searchAccommodations(
        query: AccommodationSearchQuery,
        forceRefresh: Boolean = false,
    ): DataResult<List<AccommodationOffer>>
}

interface PoiRepository {
    suspend fun getPointsOfInterest(query: PoiQuery, forceRefresh: Boolean = false): DataResult<List<PointOfInterest>>
}

interface WeatherRepository {
    suspend fun getCurrentWeather(location: GeoPoint): DataResult<WeatherSnapshot>
}

interface TransitRepository {
    suspend fun getRoutes(query: TransitRouteQuery, forceRefresh: Boolean = false): DataResult<List<TransitRoute>>
}

interface RestaurantRepository {
    suspend fun searchRestaurants(
        query: RestaurantSearchQuery,
        forceRefresh: Boolean = false,
    ): DataResult<List<Restaurant>>
}

/** Ricerca di città in tutto il mondo (geocoding). */
interface CitySearchRepository {
    suspend fun searchCities(query: String, limit: Int = 10): DataResult<List<CityPlace>>
}

interface AirportRepository {
    /** Aeroporti entro [radiusKm] dal punto indicato, ordinati per distanza crescente. */
    suspend fun airportsNear(location: GeoPoint, radiusKm: Double): DataResult<List<Airport>>
}

/** Catalogo delle mete usato dal motore di ispirazione "Consigliami". */
interface DestinationCatalogRepository {
    suspend fun destinations(): DataResult<List<CatalogDestination>>
}

/** Preferenze dell'utente salvate sul dispositivo. */
interface UserPreferencesRepository {
    /** Punto di partenza scelto dall'utente; `null` finché non ne ha indicato uno. */
    val departure: Flow<DeparturePoint?>

    suspend fun setDeparture(departure: DeparturePoint)
}

/** Viaggi seguiti per gli avvisi sulle offerte convenienti. */
interface PriceWatchRepository {
    val watches: Flow<List<PriceWatch>>

    /** Aggiunge l'avviso se non esiste già: un avviso esistente conserva il suo storico dei prezzi. */
    suspend fun add(watch: PriceWatch)

    /** Sostituisce l'avviso con lo stesso id solo se esiste ancora (l'utente può averlo appena rimosso). */
    suspend fun update(watch: PriceWatch)

    suspend fun remove(id: String)
}
