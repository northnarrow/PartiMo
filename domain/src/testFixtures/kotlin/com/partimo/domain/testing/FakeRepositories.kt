package com.partimo.domain.testing

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.Destination
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.budget.TripBudget
import com.partimo.domain.model.deal.PriceWatch
import com.partimo.domain.model.dining.Restaurant
import com.partimo.domain.model.dining.RestaurantSearchQuery
import com.partimo.domain.model.event.EventQuery
import com.partimo.domain.model.event.TripEvent
import com.partimo.domain.model.flight.FlightOffer
import com.partimo.domain.model.flight.FlightPriceSource
import com.partimo.domain.model.flight.FlightSearchQuery
import com.partimo.domain.model.guide.CountryInfo
import com.partimo.domain.model.guide.ExchangeRates
import com.partimo.domain.model.guide.TravelGuide
import com.partimo.domain.model.place.Airport
import com.partimo.domain.model.place.CatalogDestination
import com.partimo.domain.model.place.CityPlace
import com.partimo.domain.model.place.DeparturePoint
import com.partimo.domain.model.plan.ChatMessage
import com.partimo.domain.model.plan.TripKnowledge
import com.partimo.domain.model.plan.TripPlan
import com.partimo.domain.model.plan.TripPreferences
import com.partimo.domain.model.poi.PoiArticle
import com.partimo.domain.model.poi.PoiQuery
import com.partimo.domain.model.poi.PointOfInterest
import com.partimo.domain.model.saved.SavedTrip
import com.partimo.domain.model.stay.AccommodationOffer
import com.partimo.domain.model.stay.AccommodationSearchQuery
import com.partimo.domain.model.stay.Lodging
import com.partimo.domain.model.stay.LodgingQuery
import com.partimo.domain.model.transit.TransitRoute
import com.partimo.domain.model.transit.TransitRouteQuery
import com.partimo.domain.model.weather.DailyForecast
import com.partimo.domain.model.weather.DailyObservation
import com.partimo.domain.model.weather.WeatherSnapshot
import com.partimo.domain.repository.AccommodationRepository
import com.partimo.domain.repository.AirportRepository
import com.partimo.domain.repository.BudgetRepository
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
import com.partimo.domain.repository.ReminderLogRepository
import com.partimo.domain.repository.RestaurantRepository
import com.partimo.domain.repository.SavedTripRepository
import com.partimo.domain.repository.TransitRepository
import com.partimo.domain.repository.TranslatorRepository
import com.partimo.domain.repository.TravelAssistantRepository
import com.partimo.domain.repository.TravelGuideRepository
import com.partimo.domain.repository.TripWeatherRepository
import com.partimo.domain.repository.UserPreferencesRepository
import com.partimo.domain.repository.WeatherRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate

// Fake dei repository condivisi fra i moduli tramite testFixtures: restituiscono un risultato
// configurabile, registrano le richieste ricevute e possono simulare latenza (tempo virtuale).

class FakeFlightRepository(
    var result: DataResult<List<FlightOffer>> = DataResult.Success(emptyList()),
    var delayMillis: Long = 0,
    override val priceSource: FlightPriceSource = FlightPriceSource.LIVE_OFFERS,
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
    override var providesOffers: Boolean = true,
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
    override var providesRatings: Boolean = true,
) : RestaurantRepository {
    val queries = mutableListOf<RestaurantSearchQuery>()

    override suspend fun searchRestaurants(query: RestaurantSearchQuery, forceRefresh: Boolean): DataResult<List<Restaurant>> {
        queries += query
        if (delayMillis > 0) delay(delayMillis)
        return result
    }
}

class FakeEventRepository(
    var result: DataResult<List<TripEvent>> = DataResult.Success(emptyList()),
    var delayMillis: Long = 0,
) : EventRepository {
    val queries = mutableListOf<EventQuery>()
    val forceRefreshFlags = mutableListOf<Boolean>()

    override suspend fun recurringEvents(query: EventQuery, forceRefresh: Boolean): DataResult<List<TripEvent>> {
        queries += query
        forceRefreshFlags += forceRefresh
        if (delayMillis > 0) delay(delayMillis)
        return result
    }
}

/** Festività per anno; un anno senza risultato configurato restituisce [default]. */
class FakeHolidayRepository(
    var byYear: Map<Int, DataResult<List<TripEvent>>> = emptyMap(),
    var default: DataResult<List<TripEvent>> = DataResult.Success(emptyList()),
) : HolidayRepository {
    val requests = mutableListOf<Pair<String, Int>>()

    override suspend fun publicHolidays(countryCode: String, year: Int, forceRefresh: Boolean): DataResult<List<TripEvent>> {
        requests += countryCode to year
        return byYear[year] ?: default
    }
}

class FakeLodgingRepository(
    var result: DataResult<List<Lodging>> = DataResult.Success(emptyList()),
    var delayMillis: Long = 0,
) : LodgingRepository {
    val queries = mutableListOf<LodgingQuery>()

    override suspend fun findLodgings(query: LodgingQuery, forceRefresh: Boolean): DataResult<List<Lodging>> {
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

class FakeTravelAssistantRepository(
    var planResult: DataResult<TripPlan> = DataResult.Success(TripPlan(days = emptyList())),
    var answerResult: DataResult<String> = DataResult.Success("Risposta dell'assistente"),
    override var isAvailable: Boolean = true,
    var delayMillis: Long = 0,
) : TravelAssistantRepository {
    /** Richieste di itinerario ricevute: viaggio, preferenze e forceRefresh. */
    val planRequests = mutableListOf<Triple<TripKnowledge, TripPreferences, Boolean>>()
    val conversations = mutableListOf<List<ChatMessage>>()
    val chatKnowledge = mutableListOf<TripKnowledge>()

    override suspend fun planTrip(knowledge: TripKnowledge, preferences: TripPreferences, forceRefresh: Boolean): DataResult<TripPlan> {
        planRequests += Triple(knowledge, preferences, forceRefresh)
        if (delayMillis > 0) delay(delayMillis)
        return planResult
    }

    override suspend fun answer(knowledge: TripKnowledge, conversation: List<ChatMessage>): DataResult<String> {
        chatKnowledge += knowledge
        conversations += conversation
        if (delayMillis > 0) delay(delayMillis)
        return answerResult
    }
}

class FakeChecklistRepository : ChecklistRepository {
    private val lists = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    override fun checkedItems(listId: String): Flow<Set<String>> = lists.map { it[listId].orEmpty() }

    override suspend fun setChecked(listId: String, item: String, checked: Boolean) {
        lists.update { current ->
            val items = current[listId].orEmpty()
            current + (listId to if (checked) items + item else items - item)
        }
    }
}

class FakeTripWeatherRepository(
    var forecastResult: DataResult<List<DailyForecast>> = DataResult.Success(emptyList()),
    var historyResult: DataResult<List<DailyObservation>> = DataResult.Success(emptyList()),
) : TripWeatherRepository {
    val forecastRequests = mutableListOf<Pair<LocalDate, LocalDate>>()
    val historyRequests = mutableListOf<Int>()

    override suspend fun dailyForecast(location: GeoPoint, from: LocalDate, to: LocalDate): DataResult<List<DailyForecast>> {
        forecastRequests += from to to
        return forecastResult
    }

    override suspend fun dailyHistory(location: GeoPoint, years: Int): DataResult<List<DailyObservation>> {
        historyRequests += years
        return historyResult
    }
}

class FakeCountryInfoRepository(var result: DataResult<CountryInfo>) : CountryInfoRepository {
    val requests = mutableListOf<String>()

    override suspend fun countryInfo(countryCode: String): DataResult<CountryInfo> {
        requests += countryCode
        return result
    }
}

class FakeExchangeRateRepository(var result: DataResult<ExchangeRates>) : ExchangeRateRepository {
    val requests = mutableListOf<Pair<String, Boolean>>()

    override suspend fun latestRates(base: String, forceRefresh: Boolean): DataResult<ExchangeRates> {
        requests += base to forceRefresh
        return result
    }
}

class FakeTravelGuideRepository(var result: DataResult<TravelGuide?> = DataResult.Success(null)) : TravelGuideRepository {
    val requests = mutableListOf<Destination>()

    override suspend fun guide(destination: Destination, forceRefresh: Boolean): DataResult<TravelGuide?> {
        requests += destination
        return result
    }
}

class FakeSavedTripRepository(initial: List<SavedTrip> = emptyList()) : SavedTripRepository {
    private val state = MutableStateFlow(initial)

    override val trips: Flow<List<SavedTrip>> = state

    val current: List<SavedTrip> get() = state.value

    override suspend fun update(transform: (List<SavedTrip>) -> List<SavedTrip>) {
        state.update(transform)
    }
}

/**
 * Traduttore finto: "traduce" scrivendo la lingua di arrivo tra parentesi quadre (es. "[de] Grazie")
 * e simula i pacchetti lingua scaricati.
 */
class FakeTranslatorRepository(
    override val supportedLanguages: Set<String> = setOf("en", "it", "de", "fr", "es", "cs", "pt"),
    downloaded: Set<String> = setOf("en"),
    var translateResult: ((String, String, String) -> DataResult<String>)? = null,
    var downloadResult: DataResult<Unit> = DataResult.Success(Unit),
    var delayMillis: Long = 0,
) : TranslatorRepository {
    val downloaded = downloaded.toMutableSet()
    val downloads = mutableListOf<Pair<String, String>>()
    val translations = mutableListOf<Triple<String, String, String>>()

    override suspend fun downloadedLanguages(): DataResult<Set<String>> = DataResult.Success(downloaded.toSet(), DataOrigin.LOCAL)

    override suspend fun download(from: String, to: String): DataResult<Unit> {
        if (delayMillis > 0) delay(delayMillis)
        downloads += from to to
        if (downloadResult is DataResult.Success) downloaded += setOf(from, to)
        return downloadResult
    }

    override suspend fun translate(text: String, from: String, to: String): DataResult<String> {
        if (delayMillis > 0) delay(delayMillis)
        translations += Triple(text, from, to)
        translateResult?.let { return it(text, from, to) }
        return if (from in downloaded && to in downloaded) DataResult.Success("[$to] $text", DataOrigin.LOCAL) else DataResult.Failure(DataError.Unknown("Pacchetto mancante"))
    }
}

/** Budget dei viaggi in memoria. */
class FakeBudgetRepository : BudgetRepository {
    private val state = MutableStateFlow<Map<String, TripBudget>>(emptyMap())

    val budgets: Map<String, TripBudget> get() = state.value

    override fun budget(tripId: String): Flow<TripBudget> = state.map { it[tripId] ?: TripBudget(tripId) }.distinctUntilChanged()

    override suspend fun update(tripId: String, transform: (TripBudget) -> TripBudget) {
        state.update { budgets -> budgets + (tripId to transform(budgets[tripId] ?: TripBudget(tripId))) }
    }
}

/** Promemoria già mostrati, in memoria. */
class FakeReminderLogRepository(sent: Set<String> = emptySet()) : ReminderLogRepository {
    val sent = sent.toMutableSet()

    override suspend fun sentReminders(): Set<String> = sent.toSet()

    override suspend fun markSent(keys: Collection<String>) {
        sent += keys
    }
}

