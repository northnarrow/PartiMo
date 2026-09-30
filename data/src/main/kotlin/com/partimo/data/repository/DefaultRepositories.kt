package com.partimo.data.repository

import com.partimo.data.network.safeApiCall
import com.partimo.data.source.AirportDataSource
import com.partimo.data.source.CitySearchDataSource
import com.partimo.data.source.DestinationCatalogDataSource
import com.partimo.data.source.FlightOffersDataSource
import com.partimo.data.source.PoiDataSource
import com.partimo.data.source.RestaurantDataSource
import com.partimo.data.source.StayOffersDataSource
import com.partimo.data.source.TransitDataSource
import com.partimo.data.source.WeatherDataSource
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.dining.Restaurant
import com.partimo.domain.model.dining.RestaurantSearchQuery
import com.partimo.domain.model.flight.FlightOffer
import com.partimo.domain.model.flight.FlightSearchQuery
import com.partimo.domain.model.place.Airport
import com.partimo.domain.model.place.CatalogDestination
import com.partimo.domain.model.place.CityPlace
import com.partimo.domain.model.poi.PoiQuery
import com.partimo.domain.model.poi.PointOfInterest
import com.partimo.domain.model.stay.AccommodationOffer
import com.partimo.domain.model.stay.AccommodationSearchQuery
import com.partimo.domain.model.transit.TransitRoute
import com.partimo.domain.model.transit.TransitRouteQuery
import com.partimo.domain.model.weather.WeatherSnapshot
import com.partimo.domain.repository.AccommodationRepository
import com.partimo.domain.repository.AirportRepository
import com.partimo.domain.repository.CitySearchRepository
import com.partimo.domain.repository.DestinationCatalogRepository
import com.partimo.domain.repository.FlightRepository
import com.partimo.domain.repository.PoiRepository
import com.partimo.domain.repository.RestaurantRepository
import com.partimo.domain.repository.TransitRepository
import com.partimo.domain.repository.WeatherRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// Implementazioni dei repository di dominio: eseguono la sorgente dati sul dispatcher di I/O e
// convertono ogni eccezione in DataError (safeApiCall), così i casi d'uso non gestiscono eccezioni.

class DefaultFlightRepository(
    private val dataSource: FlightOffersDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FlightRepository {
    override suspend fun searchFlights(query: FlightSearchQuery, forceRefresh: Boolean): DataResult<List<FlightOffer>> =
        safeApiCall(ioDispatcher) { dataSource.searchOffers(query, forceRefresh) }
}

class DefaultAccommodationRepository(
    private val dataSource: StayOffersDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AccommodationRepository {
    override suspend fun searchAccommodations(
        query: AccommodationSearchQuery,
        forceRefresh: Boolean,
    ): DataResult<List<AccommodationOffer>> = safeApiCall(ioDispatcher) { dataSource.searchStays(query, forceRefresh) }
}

class DefaultPoiRepository(
    private val dataSource: PoiDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PoiRepository {
    override suspend fun getPointsOfInterest(query: PoiQuery, forceRefresh: Boolean): DataResult<List<PointOfInterest>> =
        safeApiCall(ioDispatcher) { dataSource.pointsOfInterest(query, forceRefresh) }
}

class DefaultWeatherRepository(
    private val dataSource: WeatherDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WeatherRepository {
    override suspend fun getCurrentWeather(location: GeoPoint): DataResult<WeatherSnapshot> =
        safeApiCall(ioDispatcher) { dataSource.currentWeather(location) }
}

class DefaultTransitRepository(
    private val dataSource: TransitDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TransitRepository {
    override suspend fun getRoutes(query: TransitRouteQuery, forceRefresh: Boolean): DataResult<List<TransitRoute>> =
        safeApiCall(ioDispatcher) { dataSource.routes(query, forceRefresh) }
}

class DefaultRestaurantRepository(
    private val dataSource: RestaurantDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RestaurantRepository {
    override suspend fun searchRestaurants(query: RestaurantSearchQuery, forceRefresh: Boolean): DataResult<List<Restaurant>> =
        safeApiCall(ioDispatcher) { dataSource.searchRestaurants(query, forceRefresh) }
}

class DefaultCitySearchRepository(
    private val dataSource: CitySearchDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CitySearchRepository {
    override suspend fun searchCities(query: String, limit: Int): DataResult<List<CityPlace>> =
        safeApiCall(ioDispatcher) { dataSource.searchCities(query, limit) }
}

class DefaultAirportRepository(
    private val dataSource: AirportDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AirportRepository {
    override suspend fun airportsNear(location: GeoPoint, radiusKm: Double): DataResult<List<Airport>> =
        safeApiCall(ioDispatcher) { dataSource.airportsNear(location, radiusKm) }
}

class DefaultDestinationCatalogRepository(
    private val dataSource: DestinationCatalogDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DestinationCatalogRepository {
    override suspend fun destinations(): DataResult<List<CatalogDestination>> =
        safeApiCall(ioDispatcher) { dataSource.destinations() }
}
