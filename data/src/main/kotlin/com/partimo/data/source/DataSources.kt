package com.partimo.data.source

import com.partimo.data.network.Fetched
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.model.Destination
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.dining.Restaurant
import com.partimo.domain.model.dining.RestaurantSearchQuery
import com.partimo.domain.model.event.EventQuery
import com.partimo.domain.model.event.TripEvent
import com.partimo.domain.model.flight.AnywhereQuery
import com.partimo.domain.model.flight.CheapDestination
import com.partimo.domain.model.flight.FareSnapshot
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
import java.time.YearMonth

// Sorgenti dati per provider. Ogni interfaccia ha un'implementazione reale (Ktor + cache Room),
// e quelle che richiedono una chiave anche una demo: sostituire un provider (es. passare a un
// backend proprio) richiede una sola classe.
// Le implementazioni possono lanciare eccezioni, che i repository convertono in DataError.

interface FlightOffersDataSource {
    suspend fun searchOffers(query: FlightSearchQuery, forceRefresh: Boolean): Fetched<List<FlightOffer>>
}

/** Prezzi raccolti dalle ricerche dei viaggiatori: mesi e giorni più convenienti, mete più economiche. */
interface FlightInsightsDataSource {
    suspend fun cheapestByMonth(
        originIata: String,
        destinationIata: String,
        stayNights: LongRange,
        forceRefresh: Boolean,
    ): Fetched<Map<YearMonth, FareSnapshot>>

    suspend fun cheapestByDay(
        originIata: String,
        destinationIata: String,
        month: YearMonth,
        stayNights: LongRange,
        forceRefresh: Boolean,
    ): Fetched<Map<LocalDate, FareSnapshot>>

    suspend fun cheapestDestinations(query: AnywhereQuery, forceRefresh: Boolean): Fetched<List<CheapDestination>>
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

interface LodgingDataSource {
    suspend fun findLodgings(query: LodgingQuery, forceRefresh: Boolean): Fetched<List<Lodging>>

    /** Ultimo elenco salvato sul telefono, anche se scaduto; `null` se non c'è. */
    suspend fun savedLodgings(query: LodgingQuery): List<Lodging>? = null
}

interface EventDataSource {
    suspend fun recurringEvents(query: EventQuery, forceRefresh: Boolean): Fetched<List<TripEvent>>
}

interface HolidayDataSource {
    suspend fun publicHolidays(countryCode: String, year: Int, forceRefresh: Boolean): Fetched<List<TripEvent>>
}

/** Nessun provider di prenotazione configurato: nessuna offerta con prezzo, l'app mostra le strutture reali. */
internal object NoStayOffersDataSource : StayOffersDataSource {
    override suspend fun searchStays(query: AccommodationSearchQuery, forceRefresh: Boolean): Fetched<List<AccommodationOffer>> =
        Fetched(emptyList(), DataOrigin.DEMO)
}

interface TransitDataSource {
    suspend fun routes(query: TransitRouteQuery, forceRefresh: Boolean): Fetched<List<TransitRoute>>
}

interface WeatherDataSource {
    suspend fun currentWeather(location: GeoPoint): Fetched<WeatherSnapshot>
}

/** Assistente di viaggio basato su un modello linguistico (itinerari e domande). */
interface TravelAssistantDataSource {
    suspend fun planTrip(knowledge: TripKnowledge, preferences: TripPreferences, forceRefresh: Boolean): Fetched<TripPlan>

    suspend fun answer(knowledge: TripKnowledge, conversation: List<ChatMessage>): Fetched<String>
}

interface CountryInfoDataSource {
    suspend fun countryInfo(countryCode: String): Fetched<CountryInfo>
}

interface ExchangeRateDataSource {
    suspend fun latestRates(base: String, forceRefresh: Boolean): Fetched<ExchangeRates>
}

interface TravelGuideDataSource {
    suspend fun guide(destination: Destination, forceRefresh: Boolean): Fetched<TravelGuide?>
}

interface TripWeatherDataSource {
    suspend fun dailyForecast(location: GeoPoint, from: LocalDate, to: LocalDate): Fetched<List<DailyForecast>>

    suspend fun dailyHistory(location: GeoPoint, years: Int): Fetched<List<DailyObservation>>
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
