package com.partimo.domain.repository

import com.partimo.domain.common.DataResult
import com.partimo.domain.model.Destination
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.Travellers
import com.partimo.domain.model.backup.UserData
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
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

// Contratti del dominio verso il data layer. Le implementazioni decidono provider, cache e
// threading; il parametro forceRefresh permette di ignorare una cache ancora valida.

interface FlightRepository {
    /** Da dove arrivano i prezzi: le stime e i prezzi trovati di recente vanno presentati come tali. */
    val priceSource: FlightPriceSource
        get() = FlightPriceSource.LIVE_OFFERS

    suspend fun searchFlights(query: FlightSearchQuery, forceRefresh: Boolean = false): DataResult<List<FlightOffer>>
}

interface AccommodationRepository {
    /**
     * `false` se nessun provider di prenotazione è configurato: non ci sono offerte con prezzo e
     * l'app mostra le strutture reali di [LodgingRepository] con i collegamenti ai siti di prenotazione.
     */
    val providesOffers: Boolean
        get() = true

    suspend fun searchAccommodations(
        query: AccommodationSearchQuery,
        forceRefresh: Boolean = false,
    ): DataResult<List<AccommodationOffer>>
}

interface PoiRepository {
    suspend fun getPointsOfInterest(query: PoiQuery, forceRefresh: Boolean = false): DataResult<List<PointOfInterest>>
}

/** Voci enciclopediche (Wikipedia) che raccontano i luoghi da visitare. */
interface PoiArticleRepository {
    /** Voce che descrive [poi]; `null` se non ne esiste una che lo riguardi con certezza. */
    suspend fun findArticle(poi: PointOfInterest, forceRefresh: Boolean = false): DataResult<PoiArticle?>
}

interface WeatherRepository {
    suspend fun getCurrentWeather(location: GeoPoint): DataResult<WeatherSnapshot>
}

/** Meteo per le date di un viaggio: previsioni dei prossimi giorni e dati misurati negli anni passati. */
interface TripWeatherRepository {
    /** Previsioni giornaliere tra [from] e [to], entrambi entro l'orizzonte delle previsioni (circa 16 giorni). */
    suspend fun dailyForecast(location: GeoPoint, from: LocalDate, to: LocalDate): DataResult<List<DailyForecast>>

    /** Dati giornalieri misurati negli ultimi [years] anni completi, per il clima tipico di un periodo. */
    suspend fun dailyHistory(location: GeoPoint, years: Int): DataResult<List<DailyObservation>>
}

interface TransitRepository {
    suspend fun getRoutes(query: TransitRouteQuery, forceRefresh: Boolean = false): DataResult<List<TransitRoute>>
}

interface RestaurantRepository {
    /**
     * `false` se il provider non ha valutazioni né fasce di prezzo (es. OpenStreetMap): i criteri
     * di qualità non sono verificabili e i locali restano nell'ordine del provider.
     */
    val providesRatings: Boolean
        get() = true

    suspend fun searchRestaurants(
        query: RestaurantSearchQuery,
        forceRefresh: Boolean = false,
    ): DataResult<List<Restaurant>>
}

/** Strutture ricettive reali (senza prezzi) attorno a un punto. */
interface LodgingRepository {
    suspend fun findLodgings(query: LodgingQuery, forceRefresh: Boolean = false): DataResult<List<Lodging>>
}

/**
 * Eventi che si ripetono ogni anno attorno a un punto: mercatini di Natale, festival, ricorrenze.
 * Ognuno ha il suo periodo ([TripEvent.timing]); quali cadono nel soggiorno lo decide il dominio.
 */
interface EventRepository {
    suspend fun recurringEvents(query: EventQuery, forceRefresh: Boolean = false): DataResult<List<TripEvent>>
}

/** Festività nazionali di un paese (codice ISO 3166-1 alpha-2) in un anno. */
interface HolidayRepository {
    suspend fun publicHolidays(countryCode: String, year: Int, forceRefresh: Boolean = false): DataResult<List<TripEvent>>
}

/**
 * Assistente di viaggio basato su un modello linguistico: propone l'itinerario giorno per giorno e
 * risponde alle domande sul viaggio. [isAvailable] è `false` se nessun modello è configurato.
 */
interface TravelAssistantRepository {
    val isAvailable: Boolean

    /** Itinerario per il viaggio descritto da [knowledge], dando priorità ai suoi luoghi ed eventi. */
    suspend fun planTrip(knowledge: TripKnowledge, preferences: TripPreferences, forceRefresh: Boolean = false): DataResult<TripPlan>

    /** Risposta all'ultimo messaggio di [conversation], che è sempre una domanda dell'utente. */
    suspend fun answer(knowledge: TripKnowledge, conversation: List<ChatMessage>): DataResult<String>
}

/** Informazioni pratiche sui paesi (lingua, valuta, prese, numeri di emergenza), incluse nell'app. */
interface CountryInfoRepository {
    suspend fun countryInfo(countryCode: String): DataResult<CountryInfo>
}

/**
 * Traduttore sul telefono: ogni lingua richiede un pacchetto scaricato una volta, poi traduce anche
 * senza Internet. Le lingue sono codici ISO 639-1 (es. "de").
 */
interface TranslatorRepository {
    /** Lingue che il traduttore conosce. */
    val supportedLanguages: Set<String>

    /** Lingue con il pacchetto già sul telefono. */
    suspend fun downloadedLanguages(): DataResult<Set<String>>

    /** Scarica, se mancano, i pacchetti per tradurre da [from] a [to]. */
    suspend fun download(from: String, to: String): DataResult<Unit>

    suspend fun translate(text: String, from: String, to: String): DataResult<String>
}

/** Promemoria dei viaggi già mostrati, per non ripeterli. */
interface ReminderLogRepository {
    suspend fun sentReminders(): Set<String>

    suspend fun markSent(keys: Collection<String>)
}

/** Budget e spese dei viaggi, salvati sul telefono (un budget per viaggio, cioè meta e periodo). */
interface BudgetRepository {
    fun budget(tripId: String): Flow<TripBudget>

    suspend fun update(tripId: String, transform: (TripBudget) -> TripBudget)
}

/** Tassi di cambio aggiornati una volta al giorno. */
interface ExchangeRateRepository {
    suspend fun latestRates(base: String, forceRefresh: Boolean = false): DataResult<ExchangeRates>
}

/** Guide di viaggio delle città (es. Wikivoyage). */
interface TravelGuideRepository {
    /** Guida di [destination] con tutti i suoi capitoli; `null` se non ne esiste una. */
    suspend fun guide(destination: Destination, forceRefresh: Boolean = false): DataResult<TravelGuide?>
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

    /** Chi parte di solito (adulti e bambini); una persona sola finché l'utente non sceglie. */
    val travellers: Flow<Travellers>

    suspend fun setTravellers(travellers: Travellers)
}

/** Viaggi salvati con i loro preferiti, sul dispositivo. */
interface SavedTripRepository {
    val trips: Flow<List<SavedTrip>>

    /** Modifica atomica dell'elenco: lettura e scrittura nella stessa transazione. */
    suspend fun update(transform: (List<SavedTrip>) -> List<SavedTrip>)
}

/** Liste di controllo salvate sul dispositivo (es. la valigia di un viaggio): le voci spuntate di ogni lista. */
interface ChecklistRepository {
    fun checkedItems(listId: String): Flow<Set<String>>

    suspend fun setChecked(listId: String, item: String, checked: Boolean)
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

/**
 * Tutti i dati dell'utente insieme (partenza, viaggi e preferiti, avvisi, budget, liste), per il file di
 * backup. Il formato del file lo decide il data layer.
 */
interface UserDataRepository {
    suspend fun read(): UserData

    /** Modifica atomica di tutti i dati: lettura e scrittura nella stessa transazione. */
    suspend fun update(transform: (UserData) -> UserData)

    /** Contenuto del file di backup con [data]. */
    fun encode(data: UserData, exportedAt: Instant): String

    /** Dati di un file di backup; `null` se [content] non è un backup di PartiMo leggibile. */
    fun decode(content: String): UserData?
}
