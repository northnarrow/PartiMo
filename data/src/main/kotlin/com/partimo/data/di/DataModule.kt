package com.partimo.data.di

import android.content.Context
import com.partimo.data.cache.CachePolicy
import com.partimo.data.cache.ResponseCache
import com.partimo.data.cache.PartiMoDatabase
import com.partimo.data.config.ApiConfig
import com.partimo.data.demo.DemoCatalog
import com.partimo.data.demo.DemoFlightDataSource
import com.partimo.data.demo.DemoPoiDataSource
import com.partimo.data.demo.DemoRestaurantDataSource
import com.partimo.data.demo.DemoStayDataSource
import com.partimo.data.demo.DemoTransitDataSource
import com.partimo.data.local.BundledAirportsDataSource
import com.partimo.data.local.CuratedDestinationCatalog
import com.partimo.data.local.preferences.DataStorePriceWatchRepository
import com.partimo.data.local.preferences.DataStoreUserPreferencesRepository
import com.partimo.data.local.preferences.createUserDataStore
import com.partimo.data.network.HttpClientFactory
import com.partimo.data.remote.duffel.DuffelApi
import com.partimo.data.remote.duffel.DuffelFlightDataSource
import com.partimo.data.remote.duffel.DuffelStayDataSource
import com.partimo.data.remote.geocoding.OpenMeteoGeocodingDataSource
import com.partimo.data.remote.places.GooglePlacesApi
import com.partimo.data.remote.places.GooglePlacesPoiDataSource
import com.partimo.data.remote.places.GooglePlacesRestaurantDataSource
import com.partimo.data.remote.routes.GoogleRoutesApi
import com.partimo.data.remote.routes.GoogleRoutesTransitDataSource
import com.partimo.data.remote.weather.OpenMeteoWeatherDataSource
import com.partimo.data.repository.DefaultAccommodationRepository
import com.partimo.data.repository.DefaultAirportRepository
import com.partimo.data.repository.DefaultCitySearchRepository
import com.partimo.data.repository.DefaultDestinationCatalogRepository
import com.partimo.data.repository.DefaultFlightRepository
import com.partimo.data.repository.DefaultPoiRepository
import com.partimo.data.repository.DefaultRestaurantRepository
import com.partimo.data.repository.DefaultTransitRepository
import com.partimo.data.repository.DefaultWeatherRepository
import com.partimo.domain.repository.AccommodationRepository
import com.partimo.domain.repository.AirportRepository
import com.partimo.domain.repository.CitySearchRepository
import com.partimo.domain.repository.DestinationCatalogRepository
import com.partimo.domain.repository.FlightRepository
import com.partimo.domain.repository.PoiRepository
import com.partimo.domain.repository.PriceWatchRepository
import com.partimo.domain.repository.RestaurantRepository
import com.partimo.domain.repository.TransitRepository
import com.partimo.domain.repository.UserPreferencesRepository
import com.partimo.domain.repository.WeatherRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock

/**
 * Composition root del data layer (DI manuale).
 *
 * Per ogni modulo sceglie il provider reale se la relativa chiave è configurata, altrimenti la
 * sorgente demo. Client HTTP, database, cache e preferenze sono condivisi e creati in modo lazy:
 * DataModule va istanziato una sola volta per processo (DataStore non ammette istanze duplicate).
 * Il meteo usa sempre Open-Meteo, che non richiede chiavi.
 */
class DataModule(
    context: Context,
    private val config: ApiConfig,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext

    private val httpClient: HttpClient by lazy { HttpClientFactory.create(enableLogging = config.enableHttpLogging) }
    private val database: PartiMoDatabase by lazy { PartiMoDatabase.create(appContext) }
    private val responseCache: ResponseCache by lazy { ResponseCache(database.cachedResponseDao(), clock) }
    private val airportsDataSource: BundledAirportsDataSource by lazy {
        BundledAirportsDataSource { appContext.assets.open(AIRPORTS_ASSET) }
    }
    private val demoCatalog: DemoCatalog by lazy {
        DemoCatalog(airportLocator = { iata -> airportsDataSource.findByIata(iata)?.location }, clock = clock)
    }
    private val userDataStore by lazy { createUserDataStore(appContext) }

    private val duffelApi by lazy { DuffelApi(httpClient, config.duffelAccessToken) }
    private val placesApi by lazy { GooglePlacesApi(httpClient, config.googleMapsApiKey) }
    private val routesApi by lazy { GoogleRoutesApi(httpClient, config.googleMapsApiKey) }

    val flightRepository: FlightRepository by lazy {
        val source = if (config.hasDuffelToken) DuffelFlightDataSource(duffelApi, responseCache) else DemoFlightDataSource(demoCatalog)
        DefaultFlightRepository(source, ioDispatcher)
    }

    val accommodationRepository: AccommodationRepository by lazy {
        val source = if (config.hasDuffelToken) DuffelStayDataSource(duffelApi, responseCache) else DemoStayDataSource(demoCatalog)
        DefaultAccommodationRepository(source, ioDispatcher)
    }

    val poiRepository: PoiRepository by lazy {
        val source = if (config.hasGoogleMapsKey) {
            GooglePlacesPoiDataSource(placesApi, responseCache, config.languageCode)
        } else {
            DemoPoiDataSource(demoCatalog)
        }
        DefaultPoiRepository(source, ioDispatcher)
    }

    val restaurantRepository: RestaurantRepository by lazy {
        val source = if (config.hasGoogleMapsKey) {
            GooglePlacesRestaurantDataSource(placesApi, responseCache, config.languageCode)
        } else {
            DemoRestaurantDataSource(demoCatalog)
        }
        DefaultRestaurantRepository(source, ioDispatcher)
    }

    val transitRepository: TransitRepository by lazy {
        val source = if (config.hasGoogleMapsKey) {
            GoogleRoutesTransitDataSource(routesApi, responseCache, config.languageCode)
        } else {
            DemoTransitDataSource(demoCatalog)
        }
        DefaultTransitRepository(source, ioDispatcher)
    }

    val weatherRepository: WeatherRepository by lazy {
        DefaultWeatherRepository(OpenMeteoWeatherDataSource(httpClient, responseCache), ioDispatcher)
    }

    /** Ricerca città mondiale con Open-Meteo Geocoding: gratuita e senza chiave, sempre reale. */
    val citySearchRepository: CitySearchRepository by lazy {
        DefaultCitySearchRepository(OpenMeteoGeocodingDataSource(httpClient, responseCache, config.languageCode), ioDispatcher)
    }

    val airportRepository: AirportRepository by lazy { DefaultAirportRepository(airportsDataSource, ioDispatcher) }

    val destinationCatalogRepository: DestinationCatalogRepository by lazy {
        DefaultDestinationCatalogRepository(CuratedDestinationCatalog(), ioDispatcher)
    }

    /** Preferenze dell'utente (punto di partenza), salvate sul dispositivo. */
    val userPreferencesRepository: UserPreferencesRepository by lazy { DataStoreUserPreferencesRepository(userDataStore) }

    /** Viaggi seguiti per gli avvisi sulle offerte convenienti. */
    val priceWatchRepository: PriceWatchRepository by lazy { DataStorePriceWatchRepository(userDataStore) }

    /** `true` se almeno un modulo sta usando dati dimostrativi. */
    val usesDemoData: Boolean
        get() = !config.hasDuffelToken || !config.hasGoogleMapsKey

    /** Rimuove le risposte troppo vecchie anche per il fallback offline (da chiamare all'avvio). */
    suspend fun trimCache() {
        withContext(ioDispatcher) { responseCache.purgeOlderThan(CachePolicy.MAX_ENTRY_AGE) }
    }

    private companion object {
        const val AIRPORTS_ASSET = "airports.csv"
    }
}
