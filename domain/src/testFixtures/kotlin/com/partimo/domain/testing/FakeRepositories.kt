package com.partimo.domain.testing

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
import com.partimo.domain.model.poi.PoiArticle
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
import com.partimo.domain.repository.PoiArticleRepository
import com.partimo.domain.repository.PoiRepository
import com.partimo.domain.repository.PriceWatchRepository
import com.partimo.domain.repository.RestaurantRepository
import com.partimo.domain.repository.TransitRepository
import com.partimo.domain.repository.UserPreferencesRepository
import com.partimo.domain.repository.WeatherRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

// Fake dei repository condivisi fra i moduli tramite testFixtures: restituiscono un risultato
// configurabile, registrano le richieste ricevute e possono simulare latenza (tempo virtuale).

class FakeFlightRepository(
    var result: DataResult<List<FlightOffer>> = DataResult.Success(emptyList()),
    var delayMillis: Long = 0,
) : FlightRepository {
    val queries = mutableListOf<FlightSearchQuery>()
    val forceRefreshFlags = mutableListOf<Boolean>()

    override suspend fun searchFlights(query: FlightSearchQuery, forceRefresh: Boolean): DataResult<List<FlightOffer>> {
        queries += query
        forceRefreshFlags += forceRefresh
        if (delayMillis > 0) delay(delayMillis)
        return result
    }
}

class FakeAccommodationRepository(
    var result: DataResult<List<AccommodationOffer>> = DataResult.Success(emptyList()),
    var delayMillis: Long = 0,
) : AccommodationRepository {
    val queries = mutableListOf<AccommodationSearchQuery>()
    val forceRefreshFlags = mutableListOf<Boolean>()

    override suspend fun searchAccommodations(
        query: AccommodationSearchQuery,
        forceRefresh: Boolean,
    ): DataResult<List<AccommodationOffer>> {
        queries += query
        forceRefreshFlags += forceRefresh
        if (delayMillis > 0) delay(delayMillis)
        return result
    }
}

class FakePoiRepository(
    var result: DataResult<List<PointOfInterest>> = DataResult.Success(emptyList()),
    var delayMillis: Long = 0,
) : PoiRepository {
    val queries = mutableListOf<PoiQuery>()

    override suspend fun getPointsOfInterest(query: PoiQuery, forceRefresh: Boolean): DataResult<List<PointOfInterest>> {
        queries += query
        if (delayMillis > 0) delay(delayMillis)
        return result
    }
}

class FakePoiArticleRepository(
    var result: DataResult<PoiArticle?> = DataResult.Success(null),
    var delayMillis: Long = 0,
) : PoiArticleRepository {
    val requestedPois = mutableListOf<PointOfInterest>()
    val forceRefreshFlags = mutableListOf<Boolean>()

    override suspend fun findArticle(poi: PointOfInterest, forceRefresh: Boolean): DataResult<PoiArticle?> {
        requestedPois += poi
        forceRefreshFlags += forceRefresh
        if (delayMillis > 0) delay(delayMillis)
        return result
    }
}

class FakeWeatherRepository(
    var result: DataResult<WeatherSnapshot> = DataResult.Success(TestData.weather()),
    var delayMillis: Long = 0,
) : WeatherRepository {
    val requestedLocations = mutableListOf<GeoPoint>()

    override suspend fun getCurrentWeather(location: GeoPoint): DataResult<WeatherSnapshot> {
        requestedLocations += location
        if (delayMillis > 0) delay(delayMillis)
        return result
    }
}

class FakeTransitRepository(
    var result: DataResult<List<TransitRoute>> = DataResult.Success(emptyList()),
    var delayMillis: Long = 0,
) : TransitRepository {
    val queries = mutableListOf<TransitRouteQuery>()

    override suspend fun getRoutes(query: TransitRouteQuery, forceRefresh: Boolean): DataResult<List<TransitRoute>> {
        queries += query
        if (delayMillis > 0) delay(delayMillis)
        return result
    }
}

class FakeRestaurantRepository(
    var result: DataResult<List<Restaurant>> = DataResult.Success(emptyList()),
    var delayMillis: Long = 0,
) : RestaurantRepository {
    val queries = mutableListOf<RestaurantSearchQuery>()

    override suspend fun searchRestaurants(query: RestaurantSearchQuery, forceRefresh: Boolean): DataResult<List<Restaurant>> {
        queries += query
        if (delayMillis > 0) delay(delayMillis)
        return result
    }
}

class FakeCitySearchRepository(
    var result: DataResult<List<CityPlace>> = DataResult.Success(emptyList()),
    var delayMillis: Long = 0,
) : CitySearchRepository {
    val queries = mutableListOf<String>()

    override suspend fun searchCities(query: String, limit: Int): DataResult<List<CityPlace>> {
        queries += query
        if (delayMillis > 0) delay(delayMillis)
        return result
    }
}

class FakeAirportRepository(
    var result: DataResult<List<Airport>> = DataResult.Success(listOf(TestData.airport())),
) : AirportRepository {
    val requestedLocations = mutableListOf<GeoPoint>()

    override suspend fun airportsNear(location: GeoPoint, radiusKm: Double): DataResult<List<Airport>> {
        requestedLocations += location
        return result
    }
}

class FakeDestinationCatalogRepository(
    var result: DataResult<List<CatalogDestination>> = DataResult.Success(emptyList()),
) : DestinationCatalogRepository {
    var requests = 0
        private set

    override suspend fun destinations(): DataResult<List<CatalogDestination>> {
        requests++
        return result
    }
}

class FakeUserPreferencesRepository(initial: DeparturePoint? = null) : UserPreferencesRepository {
    private val state = MutableStateFlow(initial)
    override val departure: Flow<DeparturePoint?> = state

    override suspend fun setDeparture(departure: DeparturePoint) {
        state.value = departure
    }
}

class FakePriceWatchRepository(initial: List<PriceWatch> = emptyList()) : PriceWatchRepository {
    private val state = MutableStateFlow(initial)
    override val watches: Flow<List<PriceWatch>> = state

    /** Avvisi attualmente salvati. */
    val current: List<PriceWatch> get() = state.value

    override suspend fun add(watch: PriceWatch) {
        state.update { watches -> if (watches.any { it.id == watch.id }) watches else watches + watch }
    }

    override suspend fun update(watch: PriceWatch) {
        state.update { watches -> watches.map { if (it.id == watch.id) watch else it } }
    }

    override suspend fun remove(id: String) {
        state.update { watches -> watches.filterNot { it.id == id } }
    }
}
