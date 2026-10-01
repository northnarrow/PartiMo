package com.partimo.data.di

import android.content.Context
import com.partimo.data.cache.CachePolicy
import com.partimo.data.cache.PartiMoDatabase
import com.partimo.data.cache.ResponseCache
import com.partimo.data.config.ApiConfig
import com.partimo.data.demo.DemoCatalog
import com.partimo.data.demo.DemoFlightDataSource
import com.partimo.data.demo.DemoTransitDataSource
import com.partimo.data.local.BundledAirportsDataSource
import com.partimo.data.local.BundledCountryInfoDataSource
import com.partimo.data.local.CuratedDestinationCatalog
import com.partimo.data.local.preferences.DataStoreChecklistRepository
import com.partimo.data.local.preferences.DataStorePriceWatchRepository
import com.partimo.data.local.preferences.DataStoreSavedTripRepository
import com.partimo.data.local.preferences.DataStoreUserPreferencesRepository
import com.partimo.data.local.preferences.createUserDataStore
import com.partimo.data.network.HttpClientFactory
import com.partimo.data.remote.currency.ExchangeRateApiDataSource
import com.partimo.data.remote.duffel.DuffelApi
import com.partimo.data.remote.duffel.DuffelFlightDataSource
import com.partimo.data.remote.duffel.DuffelStayDataSource
import com.partimo.data.remote.gemini.GeminiApi
import com.partimo.data.remote.gemini.GeminiTravelAssistantDataSource
import com.partimo.data.remote.geocoding.OpenMeteoGeocodingDataSource
import com.partimo.data.remote.holidays.NagerHolidayDataSource
import com.partimo.data.remote.osm.OsmLodgingDataSource
import com.partimo.data.remote.osm.OsmRestaurantDataSource
import com.partimo.data.remote.osm.OverpassApi
import com.partimo.data.remote.places.GooglePlacesApi
import com.partimo.data.remote.places.GooglePlacesPoiDataSource
import com.partimo.data.remote.places.GooglePlacesRestaurantDataSource
import com.partimo.data.remote.routes.GoogleRoutesApi
import com.partimo.data.remote.routes.GoogleRoutesTransitDataSource
import com.partimo.data.remote.weather.OpenMeteoTripWeatherDataSource
import com.partimo.data.remote.weather.OpenMeteoWeatherDataSource
import com.partimo.data.remote.wikidata.WikidataApi
import com.partimo.data.remote.wikidata.WikidataEventDataSource
import com.partimo.data.remote.wikipedia.WikipediaApi
import com.partimo.data.remote.wikipedia.WikipediaArticleDataSource
import com.partimo.data.remote.wikipedia.WikipediaPoiDataSource
import com.partimo.data.remote.wikipedia.wikipediaLanguages
import com.partimo.data.remote.wikivoyage.WikivoyageApi
import com.partimo.data.remote.wikivoyage.WikivoyageGuideDataSource
import com.partimo.data.repository.DefaultAccommodationRepository
import com.partimo.data.repository.DefaultAirportRepository
import com.partimo.data.repository.DefaultCitySearchRepository
import com.partimo.data.repository.DefaultCountryInfoRepository
import com.partimo.data.repository.DefaultDestinationCatalogRepository
import com.partimo.data.repository.DefaultEventRepository
import com.partimo.data.repository.DefaultExchangeRateRepository
import com.partimo.data.repository.DefaultFlightRepository
import com.partimo.data.repository.DefaultHolidayRepository
import com.partimo.data.repository.DefaultLodgingRepository
import com.partimo.data.repository.DefaultPoiArticleRepository
import com.partimo.data.repository.DefaultPoiRepository
import com.partimo.data.repository.DefaultRestaurantRepository
import com.partimo.data.repository.DefaultTransitRepository
import com.partimo.data.repository.DefaultTravelAssistantRepository
import com.partimo.data.repository.DefaultTravelGuideRepository
import com.partimo.data.repository.DefaultTripWeatherRepository
import com.partimo.data.repository.DefaultWeatherRepository
import com.partimo.data.source.NoStayOffersDataSource
import com.partimo.domain.repository.AccommodationRepository
import com.partimo.domain.repository.AirportRepository
import com.partimo.domain.repository.ChecklistRepository
import com.partimo.domain.repository.CitySearchRepository
import com.partimo.domain.repository.CountryInfoRepository
import com.partimo.domain.repository.DestinationCatalogRepository
import com.partimo.domain.repository.EventRepository
import com.partimo.domain.repository.ExchangeRateRepository
import com.partimo.domain.repository.FlightRepository
import com.partimo.domain.repository.HolidayRepository
import com.partimo.domain.repository.LodgingRepository
import com.partimo.domain.repository.PoiArticleRepository
import com.partimo.domain.repository.PoiRepository
import com.partimo.domain.repository.PriceWatchRepository
import com.partimo.domain.repository.RestaurantRepository
import com.partimo.domain.repository.SavedTripRepository
import com.partimo.domain.repository.TransitRepository
import com.partimo.domain.repository.TravelAssistantRepository
import com.partimo.domain.repository.TravelGuideRepository
import com.partimo.domain.repository.TripWeatherRepository
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
 * Per ogni modulo sceglie il provider con chiave se configurato, altrimenti una fonte reale gratuita
 * (Wikipedia per i luoghi, OpenStreetMap per ristoranti e strutture ricettive) oppure, per voli e
 * trasporti, la sorgente demo. Client HTTP, database, cache e preferenze sono condivisi e creati in
 * modo lazy: DataModule va istanziato una sola volta per processo (DataStore non ammette istanze duplicate).
 * Meteo (Open-Meteo), luoghi, eventi, festività, ristoranti e alloggi non richiedono chiavi e sono sempre reali.
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
    private val placesApi by lazy { GooglePlacesApi(httpClient, config.googleMapsApiKey, androidApp = config.androidApp) }
    private val routesApi by lazy { GoogleRoutesApi(httpClient, config.googleMapsApiKey, androidApp = config.androidApp) }
    private val wikipediaApi by lazy { WikipediaApi(httpClient, config.userAgent) }
    private val overpassApi by lazy { OverpassApi(httpClient, config.userAgent) }
    private val wikidataApi by lazy { WikidataApi(httpClient, config.userAgent) }
    private val wikivoyageApi by lazy { WikivoyageApi(httpClient, config.userAgent) }
    private val geminiApi by lazy { GeminiApi(httpClient, config.geminiApiKey, androidApp = config.androidApp) }
    private val wikipediaLanguages by lazy { wikipediaLanguages(config.languageCode) }

    val flightRepository: FlightRepository by lazy {
        val source = if (config.hasDuffelToken) DuffelFlightDataSource(duffelApi, responseCache) else DemoFlightDataSource(demoCatalog)
        DefaultFlightRepository(source, ioDispatcher)
    }

    /**
     * Offerte di alloggio con prezzo da Duffel Stays. Senza token non ci sono prezzi da mostrare (né
     * da seguire con gli avvisi): l'app mostra le strutture reali di [lodgingRepository].
     */
    val accommodationRepository: AccommodationRepository by lazy {
        if (config.hasDuffelToken) {
            DefaultAccommodationRepository(DuffelStayDataSource(duffelApi, responseCache), ioDispatcher)
        } else {
            DefaultAccommodationRepository(NoStayOffersDataSource, ioDispatcher, providesOffers = false)
        }
    }

    /** Strutture ricettive reali da OpenStreetMap: gratuite e senza chiave, sempre disponibili. */
    val lodgingRepository: LodgingRepository by lazy {
        DefaultLodgingRepository(OsmLodgingDataSource(overpassApi, responseCache, config.languageCode), ioDispatcher)
    }

    /**
     * Luoghi da vedere: Google Places se la chiave è configurata (valutazioni e temi stagionali),
     * altrimenti Wikipedia, gratuita e senza chiave, con luoghi e foto reali.
     */
    val poiRepository: PoiRepository by lazy {
        val source = if (config.hasGoogleMapsKey) {
            GooglePlacesPoiDataSource(placesApi, responseCache, config.languageCode)
        } else {
            WikipediaPoiDataSource(wikipediaApi, responseCache, wikipediaLanguages)
        }
        DefaultPoiRepository(source, ioDispatcher)
    }

    /** Descrizione e storia dei luoghi da Wikipedia, qualunque sia il provider dei luoghi. */
    val poiArticleRepository: PoiArticleRepository by lazy {
        DefaultPoiArticleRepository(WikipediaArticleDataSource(wikipediaApi, responseCache, wikipediaLanguages), ioDispatcher)
    }

    /**
     * Ristoranti: Google Places se la chiave è configurata (valutazioni, fasce di prezzo, foto),
     * altrimenti i locali reali di OpenStreetMap, gratuiti e senza chiave ma senza valutazioni.
     */
    val restaurantRepository: RestaurantRepository by lazy {
        if (config.hasGoogleMapsKey) {
            DefaultRestaurantRepository(GooglePlacesRestaurantDataSource(placesApi, responseCache, config.languageCode), ioDispatcher)
        } else {
            DefaultRestaurantRepository(
                OsmRestaurantDataSource(overpassApi, responseCache, config.languageCode),
                ioDispatcher,
                providesRatings = false,
            )
        }
    }

    val transitRepository: TransitRepository by lazy {
        val source = if (config.hasGoogleMapsKey) {
            GoogleRoutesTransitDataSource(routesApi, responseCache, config.languageCode)
        } else {
            DemoTransitDataSource(demoCatalog)
        }
        DefaultTransitRepository(source, ioDispatcher)
    }

    /** Mercatini di Natale, festival e ricorrenze da Wikidata: gratuita e senza chiave, sempre disponibile. */
    val eventRepository: EventRepository by lazy {
        DefaultEventRepository(WikidataEventDataSource(wikidataApi, responseCache, config.languageCode), ioDispatcher)
    }

    /** Festività nazionali da Nager.Date: gratuito e senza chiave. */
    val holidayRepository: HolidayRepository by lazy {
        DefaultHolidayRepository(NagerHolidayDataSource(httpClient, responseCache, config.userAgent, config.languageCode), ioDispatcher)
    }

    /**
     * Assistente di viaggio (itinerari e domande) con Google Gemini: disponibile solo se la chiave è
     * configurata. Il livello gratuito basta per un uso personale.
     */
    val travelAssistantRepository: TravelAssistantRepository by lazy {
        val source = if (config.hasGeminiKey) GeminiTravelAssistantDataSource(geminiApi, responseCache, clock) else null
        DefaultTravelAssistantRepository(source, ioDispatcher)
    }

    /** Informazioni pratiche sui paesi: dati del sistema più il catalogo curato, senza rete. */
    val countryInfoRepository: CountryInfoRepository by lazy {
        DefaultCountryInfoRepository(BundledCountryInfoDataSource(config.languageCode), ioDispatcher)
    }

    /** Tassi di cambio da ExchangeRate-API (accesso aperto, senza chiave). */
    val exchangeRateRepository: ExchangeRateRepository by lazy {
        DefaultExchangeRateRepository(ExchangeRateApiDataSource(httpClient, responseCache, config.userAgent), ioDispatcher)
    }

    /** Guide delle città da Wikivoyage: gratuite e senza chiave. */
    val travelGuideRepository: TravelGuideRepository by lazy {
        DefaultTravelGuideRepository(WikivoyageGuideDataSource(wikivoyageApi, responseCache, wikipediaLanguages), ioDispatcher)
    }

    val weatherRepository: WeatherRepository by lazy {
        DefaultWeatherRepository(OpenMeteoWeatherDataSource(httpClient, responseCache), ioDispatcher)
    }

    /** Previsioni e clima tipico per le date del viaggio, da Open-Meteo (gratuito e senza chiave). */
    val tripWeatherRepository: TripWeatherRepository by lazy {
        DefaultTripWeatherRepository(OpenMeteoTripWeatherDataSource(httpClient, responseCache, clock), ioDispatcher)
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

    /** Viaggi salvati e preferiti, sul dispositivo. */
    val savedTripRepository: SavedTripRepository by lazy { DataStoreSavedTripRepository(userDataStore) }

    /** Liste di controllo dei viaggi (la valigia), salvate sul dispositivo. */
    val checklistRepository: ChecklistRepository by lazy { DataStoreChecklistRepository(userDataStore) }

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
