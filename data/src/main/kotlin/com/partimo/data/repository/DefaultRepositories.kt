package com.partimo.data.repository

import com.partimo.data.network.safeApiCall
import com.partimo.data.source.AirportDataSource
import com.partimo.data.source.CitySearchDataSource
import com.partimo.data.source.CountryInfoDataSource
import com.partimo.data.source.ExchangeRateDataSource
import com.partimo.data.source.TravelGuideDataSource
import com.partimo.data.source.TripWeatherDataSource
import com.partimo.data.source.DestinationCatalogDataSource
import com.partimo.data.source.EventDataSource
import com.partimo.data.source.FlightOffersDataSource
import com.partimo.data.source.HolidayDataSource
import com.partimo.data.source.LodgingDataSource
import com.partimo.data.source.PoiArticleDataSource
import com.partimo.data.source.PoiDataSource
import com.partimo.data.source.RestaurantDataSource
import com.partimo.data.source.StayOffersDataSource
import com.partimo.data.source.TransitDataSource
import com.partimo.data.source.TravelAssistantDataSource
import com.partimo.data.source.WeatherDataSource
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.Destination
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.dining.Restaurant
import com.partimo.domain.model.dining.RestaurantSearchQuery
import com.partimo.domain.model.event.EventQuery
import com.partimo.domain.model.event.TripEvent
import com.partimo.domain.model.flight.FlightOffer
import com.partimo.domain.model.flight.FlightSearchQuery
import com.partimo.domain.model.guide.CountryInfo
import com.partimo.domain.model.guide.ExchangeRates
import com.partimo.domain.model.guide.TravelGuide
import com.partimo.domain.model.place.Airport
import com.partimo.domain.model.place.CatalogDestination
import com.partimo.domain.model.place.CityPlace
import com.partimo.domain.model.plan.ChatMessage
import com.partimo.domain.model.plan.TripKnowledge
import com.partimo.domain.model.plan.TripPlan
import com.partimo.domain.model.plan.TripPreferences
import com.partimo.domain.model.poi.PoiArticle
import com.partimo.domain.model.poi.PoiQuery
import com.partimo.domain.model.poi.PointOfInterest
import com.partimo.domain.model.stay.AccommodationOffer
import com.partimo.domain.model.stay.AccommodationSearchQuery
import com.partimo.domain.model.stay.Lodging
import com.partimo.domain.model.stay.LodgingQuery
import com.partimo.domain.model.transit.TransitRoute
import com.partimo.domain.model.transit.TransitRouteQuery
import com.partimo.domain.model.weather.DailyForecast
import com.partimo.domain.model.weather.DailyObservation
import com.partimo.domain.model.weather.WeatherSnapshot
import java.time.LocalDate
import com.partimo.domain.repository.AccommodationRepository
import com.partimo.domain.repository.AirportRepository
import com.partimo.domain.repository.CitySearchRepository
import com.partimo.domain.repository.CountryInfoRepository
import com.partimo.domain.repository.ExchangeRateRepository
import com.partimo.domain.repository.TravelGuideRepository
import com.partimo.domain.repository.TripWeatherRepository
import com.partimo.domain.repository.DestinationCatalogRepository
import com.partimo.domain.repository.EventRepository
import com.partimo.domain.repository.FlightRepository
import com.partimo.domain.repository.HolidayRepository
import com.partimo.domain.repository.LodgingRepository
import com.partimo.domain.repository.PoiArticleRepository
import com.partimo.domain.repository.PoiRepository
import com.partimo.domain.repository.RestaurantRepository
import com.partimo.domain.repository.TransitRepository
import com.partimo.domain.repository.TravelAssistantRepository
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
    override val providesOffers: Boolean = true,
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

class DefaultPoiArticleRepository(
    private val dataSource: PoiArticleDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PoiArticleRepository {
    override suspend fun findArticle(poi: PointOfInterest, forceRefresh: Boolean): DataResult<PoiArticle?> =
        safeApiCall(ioDispatcher) { dataSource.findArticle(poi, forceRefresh) }
}

class DefaultWeatherRepository(
    private val dataSource: WeatherDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WeatherRepository {
    override suspend fun getCurrentWeather(location: GeoPoint): DataResult<WeatherSnapshot> =
        safeApiCall(ioDispatcher) { dataSource.currentWeather(location) }
}

class DefaultTripWeatherRepository(
    private val dataSource: TripWeatherDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TripWeatherRepository {
    override suspend fun dailyForecast(location: GeoPoint, from: LocalDate, to: LocalDate): DataResult<List<DailyForecast>> =
        safeApiCall(ioDispatcher) { dataSource.dailyForecast(location, from, to) }

    override suspend fun dailyHistory(location: GeoPoint, years: Int): DataResult<List<DailyObservation>> =
        safeApiCall(ioDispatcher) { dataSource.dailyHistory(location, years) }
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
    override val providesRatings: Boolean = true,
) : RestaurantRepository {
    override suspend fun searchRestaurants(query: RestaurantSearchQuery, forceRefresh: Boolean): DataResult<List<Restaurant>> =
        safeApiCall(ioDispatcher) { dataSource.searchRestaurants(query, forceRefresh) }
}

class DefaultLodgingRepository(
    private val dataSource: LodgingDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LodgingRepository {
    override suspend fun findLodgings(query: LodgingQuery, forceRefresh: Boolean): DataResult<List<Lodging>> =
        safeApiCall(ioDispatcher) { dataSource.findLodgings(query, forceRefresh) }
}

class DefaultEventRepository(
    private val dataSource: EventDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : EventRepository {
    override suspend fun recurringEvents(query: EventQuery, forceRefresh: Boolean): DataResult<List<TripEvent>> =
        safeApiCall(ioDispatcher) { dataSource.recurringEvents(query, forceRefresh) }
}

class DefaultHolidayRepository(
    private val dataSource: HolidayDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HolidayRepository {
    override suspend fun publicHolidays(countryCode: String, year: Int, forceRefresh: Boolean): DataResult<List<TripEvent>> =
        safeApiCall(ioDispatcher) { dataSource.publicHolidays(countryCode, year, forceRefresh) }
}

/** Assistente di viaggio; senza sorgente (nessuna chiave configurata) non è disponibile. */
class DefaultTravelAssistantRepository(
    private val dataSource: TravelAssistantDataSource?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TravelAssistantRepository {
    override val isAvailable: Boolean get() = dataSource != null

    override suspend fun planTrip(knowledge: TripKnowledge, preferences: TripPreferences, forceRefresh: Boolean): DataResult<TripPlan> {
        val source = dataSource ?: return DataResult.Failure(DataError.Unauthorized)
        return safeApiCall(ioDispatcher) { source.planTrip(knowledge, preferences, forceRefresh) }
    }

    override suspend fun answer(knowledge: TripKnowledge, conversation: List<ChatMessage>): DataResult<String> {
        val source = dataSource ?: return DataResult.Failure(DataError.Unauthorized)
        return safeApiCall(ioDispatcher) { source.answer(knowledge, conversation) }
    }
}

class DefaultCountryInfoRepository(
    private val dataSource: CountryInfoDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CountryInfoRepository {
    override suspend fun countryInfo(countryCode: String): DataResult<CountryInfo> =
        safeApiCall(ioDispatcher) { dataSource.countryInfo(countryCode) }
}

class DefaultExchangeRateRepository(
    private val dataSource: ExchangeRateDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ExchangeRateRepository {
    override suspend fun latestRates(base: String, forceRefresh: Boolean): DataResult<ExchangeRates> =
        safeApiCall(ioDispatcher) { dataSource.latestRates(base, forceRefresh) }
}

class DefaultTravelGuideRepository(
    private val dataSource: TravelGuideDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TravelGuideRepository {
    override suspend fun guide(destination: Destination, forceRefresh: Boolean): DataResult<TravelGuide?> =
        safeApiCall(ioDispatcher) { dataSource.guide(destination, forceRefresh) }
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
