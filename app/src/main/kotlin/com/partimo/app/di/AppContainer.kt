package com.partimo.app.di

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.partimo.app.BuildConfig
import com.partimo.app.files.BookingDocuments
import com.partimo.app.files.BookingFiles
import com.partimo.app.files.ContentResolverDocuments
import com.partimo.app.files.UserDocuments
import com.partimo.app.notifications.BookingReminderNotifier
import com.partimo.app.notifications.BookingReminderScheduler
import com.partimo.app.notifications.DealCheckScheduler
import com.partimo.app.notifications.DealNotifier
import com.partimo.app.notifications.TripReminderNotifier
import com.partimo.app.notifications.TripWorkScheduler
import com.partimo.app.widget.NextTripWidget
import com.partimo.data.config.ApiConfig
import com.partimo.data.di.DataModule
import com.partimo.domain.usecase.AskTravelAssistantUseCase
import com.partimo.domain.usecase.BookingRemindersUseCase
import com.partimo.domain.usecase.CheckPriceWatchesUseCase
import com.partimo.domain.usecase.DeleteBookingUseCase
import com.partimo.domain.usecase.EditTripBudgetUseCase
import com.partimo.domain.usecase.ExportUserDataUseCase
import com.partimo.domain.usecase.FindBudgetRestaurantsUseCase
import com.partimo.domain.usecase.FindCheapDestinationsUseCase
import com.partimo.domain.usecase.FindDepartureAirportsUseCase
import com.partimo.domain.usecase.FindLodgingsUseCase
import com.partimo.domain.usecase.GetCountryInfoUseCase
import com.partimo.domain.usecase.GetExchangeRateUseCase
import com.partimo.domain.usecase.GetMonthPricesUseCase
import com.partimo.domain.usecase.GetPoiDetailsUseCase
import com.partimo.domain.usecase.GetPriceCalendarUseCase
import com.partimo.domain.usecase.GetSeasonalHighlightsUseCase
import com.partimo.domain.usecase.GetTravelGuideUseCase
import com.partimo.domain.usecase.GetTripEventsUseCase
import com.partimo.domain.usecase.GetTripWeatherUseCase
import com.partimo.domain.usecase.ImportUserDataUseCase
import com.partimo.domain.usecase.LanguagePacksUseCase
import com.partimo.domain.usecase.LoadTripKnowledgeUseCase
import com.partimo.domain.usecase.ObserveBookingsUseCase
import com.partimo.domain.usecase.ObserveDepartureUseCase
import com.partimo.domain.usecase.ObservePriceAlertUseCase
import com.partimo.domain.usecase.ObserveSavedTripUseCase
import com.partimo.domain.usecase.ObserveSavedTripsUseCase
import com.partimo.domain.usecase.ObserveTravellersUseCase
import com.partimo.domain.usecase.ObserveTripBudgetUseCase
import com.partimo.domain.usecase.PackingChecklistUseCase
import com.partimo.domain.usecase.PlanTransitRouteUseCase
import com.partimo.domain.usecase.PlanTripUseCase
import com.partimo.domain.usecase.PrefetchTripUseCase
import com.partimo.domain.usecase.ReadBookingDocumentUseCase
import com.partimo.domain.usecase.ReadBookingTextUseCase
import com.partimo.domain.usecase.RecommendDestinationsUseCase
import com.partimo.domain.usecase.ResolveDestinationUseCase
import com.partimo.domain.usecase.SaveBookingUseCase
import com.partimo.domain.usecase.SaveDepartureUseCase
import com.partimo.domain.usecase.SaveTravellersUseCase
import com.partimo.domain.usecase.SearchAccommodationsUseCase
import com.partimo.domain.usecase.SearchCitiesUseCase
import com.partimo.domain.usecase.SearchFlightsUseCase
import com.partimo.domain.usecase.SetPriceAlertUseCase
import com.partimo.domain.usecase.SetTripSavedUseCase
import com.partimo.domain.usecase.SummarizeBudgetUseCase
import com.partimo.domain.usecase.SummarizeUserDataUseCase
import com.partimo.domain.usecase.ToggleFavoriteUseCase
import com.partimo.domain.usecase.TranslatePhotoUseCase
import com.partimo.domain.usecase.TranslateTextUseCase
import com.partimo.domain.usecase.TripRemindersUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/** Dipendenze esposte alla presentation: solo casi d'uso, nessun dettaglio del data layer. */
interface AppContainer {
    val clock: Clock
    val isDemoMode: Boolean
    val searchFlights: SearchFlightsUseCase
    val searchAccommodations: SearchAccommodationsUseCase
    val findLodgings: FindLodgingsUseCase
    val getSeasonalHighlights: GetSeasonalHighlightsUseCase
    val getTripEvents: GetTripEventsUseCase
    val getPoiDetails: GetPoiDetailsUseCase
    val planTransitRoute: PlanTransitRouteUseCase
    val findBudgetRestaurants: FindBudgetRestaurantsUseCase
    val searchCities: SearchCitiesUseCase
    val resolveDestination: ResolveDestinationUseCase
    val recommendDestinations: RecommendDestinationsUseCase
    val findDepartureAirports: FindDepartureAirportsUseCase
    val observeDeparture: ObserveDepartureUseCase
    val saveDeparture: SaveDepartureUseCase

    /** Chi parte (adulti e bambini), per tutti i viaggi. */
    val observeTravellers: ObserveTravellersUseCase
    val saveTravellers: SaveTravellersUseCase

    /** Prezzi di Aviasales per scegliere quando e dove andare: mesi, calendario, «Ovunque». */
    val getMonthPrices: GetMonthPricesUseCase
    val getPriceCalendar: GetPriceCalendarUseCase
    val findCheapDestinations: FindCheapDestinationsUseCase
    val observePriceAlert: ObservePriceAlertUseCase
    val setPriceAlert: SetPriceAlertUseCase
    val checkPriceWatches: CheckPriceWatchesUseCase

    /** Assistente di viaggio con l'IA: itinerario, lista per la valigia e domande. */
    val loadTripKnowledge: LoadTripKnowledgeUseCase
    val planTrip: PlanTripUseCase
    val askTravelAssistant: AskTravelAssistantUseCase
    val packingChecklist: PackingChecklistUseCase

    /** Guida del viaggio: paese, valuta, meteo per le date e guida della città. */
    val getCountryInfo: GetCountryInfoUseCase
    val getExchangeRate: GetExchangeRateUseCase
    val getTravelGuide: GetTravelGuideUseCase
    val getTripWeather: GetTripWeatherUseCase

    /** Traduttore sul telefono, anche offline, anche delle foto (menù, cartelli). */
    val translateText: TranslateTextUseCase
    val languagePacks: LanguagePacksUseCase
    val translatePhoto: TranslatePhotoUseCase

    /** Budget e spese del viaggio. */
    val observeTripBudget: ObserveTripBudgetUseCase
    val editTripBudget: EditTripBudgetUseCase
    val summarizeBudget: SummarizeBudgetUseCase

    /** Promemoria e preparazione offline dei viaggi salvati. */
    val tripReminders: TripRemindersUseCase
    val prefetchTrip: PrefetchTripUseCase

    /** Viaggi salvati e preferiti. */
    val observeSavedTrips: ObserveSavedTripsUseCase
    val observeSavedTrip: ObserveSavedTripUseCase
    val setTripSaved: SetTripSavedUseCase
    val toggleFavorite: ToggleFavoriteUseCase

    /** Backup di tutti i dati dell'utente in un file e importazione. */
    val exportUserData: ExportUserDataUseCase
    val importUserData: ImportUserDataUseCase
    val summarizeUserData: SummarizeUserDataUseCase

    /** File scelti dall'utente con il selettore di Android. */
    val documents: UserDocuments

    /** Le mie prenotazioni: linea del tempo, lettura delle conferme (testo, PDF, foto) e documenti. */
    val observeBookings: ObserveBookingsUseCase
    val saveBooking: SaveBookingUseCase
    val deleteBooking: DeleteBookingUseCase
    val readBookingText: ReadBookingTextUseCase
    val readBookingDocument: ReadBookingDocumentUseCase
    val bookingReminders: BookingRemindersUseCase
    val bookingDocuments: BookingDocuments
}

/**
 * Composition root dell'app: collega configurazione (BuildConfig ← local.properties), data layer
 * e casi d'uso. Adottando Hilt o Koin questo file diventerebbe un modulo di DI senza cambiare
 * le classi coinvolte.
 */
class DefaultAppContainer(context: Context) : AppContainer {

    private val appContext = context.applicationContext

    override val clock: Clock = Clock.systemDefaultZone()

    private val dataModule = DataModule(
        context = context,
        config = ApiConfig(
            duffelAccessToken = BuildConfig.DUFFEL_ACCESS_TOKEN,
            googleMapsApiKey = BuildConfig.GOOGLE_MAPS_API_KEY,
            geminiApiKey = BuildConfig.GEMINI_API_KEY,
            travelpayoutsToken = BuildConfig.TRAVELPAYOUTS_TOKEN,
            languageCode = Locale.getDefault().language.ifBlank { DEFAULT_LANGUAGE },
            enableHttpLogging = BuildConfig.DEBUG,
            userAgent = PARTIMO_USER_AGENT,
            androidApp = androidAppIdentity(context),
        ),
        clock = clock,
    )

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val isDemoMode: Boolean
        get() = dataModule.usesDemoData

    override val searchFlights: SearchFlightsUseCase by lazy {
        SearchFlightsUseCase(dataModule.flightRepository, clock = clock)
    }

    override val searchAccommodations: SearchAccommodationsUseCase by lazy {
        SearchAccommodationsUseCase(dataModule.accommodationRepository, clock = clock)
    }

    override val findLodgings: FindLodgingsUseCase by lazy { FindLodgingsUseCase(dataModule.lodgingRepository) }

    override val getSeasonalHighlights: GetSeasonalHighlightsUseCase by lazy {
        GetSeasonalHighlightsUseCase(dataModule.poiRepository, dataModule.weatherRepository, clock = clock)
    }

    override val getTripEvents: GetTripEventsUseCase by lazy {
        GetTripEventsUseCase(dataModule.eventRepository, dataModule.holidayRepository)
    }

    override val getPoiDetails: GetPoiDetailsUseCase by lazy { GetPoiDetailsUseCase(dataModule.poiArticleRepository) }

    override val planTransitRoute: PlanTransitRouteUseCase by lazy {
        PlanTransitRouteUseCase(dataModule.transitRepository, clock)
    }

    override val findBudgetRestaurants: FindBudgetRestaurantsUseCase by lazy {
        FindBudgetRestaurantsUseCase(dataModule.restaurantRepository)
    }

    override val searchCities: SearchCitiesUseCase by lazy { SearchCitiesUseCase(dataModule.citySearchRepository) }

    override val resolveDestination: ResolveDestinationUseCase by lazy {
        ResolveDestinationUseCase(dataModule.airportRepository)
    }

    override val recommendDestinations: RecommendDestinationsUseCase by lazy {
        RecommendDestinationsUseCase(dataModule.destinationCatalogRepository, dataModule.weatherRepository)
    }

    override val findDepartureAirports: FindDepartureAirportsUseCase by lazy {
        FindDepartureAirportsUseCase(dataModule.airportRepository)
    }

    override val observeDeparture: ObserveDepartureUseCase by lazy { ObserveDepartureUseCase(dataModule.userPreferencesRepository) }

    override val saveDeparture: SaveDepartureUseCase by lazy { SaveDepartureUseCase(dataModule.userPreferencesRepository) }

    override val observeTravellers: ObserveTravellersUseCase by lazy { ObserveTravellersUseCase(dataModule.userPreferencesRepository) }

    override val saveTravellers: SaveTravellersUseCase by lazy { SaveTravellersUseCase(dataModule.userPreferencesRepository) }

    override val getMonthPrices: GetMonthPricesUseCase by lazy { GetMonthPricesUseCase(dataModule.flightInsightsRepository) }

    override val getPriceCalendar: GetPriceCalendarUseCase by lazy { GetPriceCalendarUseCase(dataModule.flightInsightsRepository) }

    override val findCheapDestinations: FindCheapDestinationsUseCase by lazy {
        FindCheapDestinationsUseCase(dataModule.flightInsightsRepository, clock)
    }

    override val observePriceAlert: ObservePriceAlertUseCase by lazy { ObservePriceAlertUseCase(dataModule.priceWatchRepository) }

    override val setPriceAlert: SetPriceAlertUseCase by lazy { SetPriceAlertUseCase(dataModule.priceWatchRepository, clock) }

    override val checkPriceWatches: CheckPriceWatchesUseCase by lazy {
        CheckPriceWatchesUseCase(dataModule.priceWatchRepository, searchFlights, searchAccommodations, clock = clock)
    }

    override val loadTripKnowledge: LoadTripKnowledgeUseCase by lazy {
        LoadTripKnowledgeUseCase(getSeasonalHighlights, getTripEvents, getTripWeather, observeTravellers = observeTravellers)
    }

    override val getCountryInfo: GetCountryInfoUseCase by lazy { GetCountryInfoUseCase(dataModule.countryInfoRepository) }

    override val getExchangeRate: GetExchangeRateUseCase by lazy { GetExchangeRateUseCase(dataModule.exchangeRateRepository) }

    override val getTravelGuide: GetTravelGuideUseCase by lazy { GetTravelGuideUseCase(dataModule.travelGuideRepository) }

    override val getTripWeather: GetTripWeatherUseCase by lazy { GetTripWeatherUseCase(dataModule.tripWeatherRepository, clock) }

    override val translateText: TranslateTextUseCase by lazy { TranslateTextUseCase(dataModule.translatorRepository) }

    override val languagePacks: LanguagePacksUseCase by lazy { LanguagePacksUseCase(dataModule.translatorRepository) }

    override val translatePhoto: TranslatePhotoUseCase by lazy { TranslatePhotoUseCase(dataModule.textRecognitionRepository, translateText) }

    override val observeTripBudget: ObserveTripBudgetUseCase by lazy { ObserveTripBudgetUseCase(dataModule.budgetRepository) }

    override val editTripBudget: EditTripBudgetUseCase by lazy { EditTripBudgetUseCase(dataModule.budgetRepository) }

    override val summarizeBudget: SummarizeBudgetUseCase by lazy { SummarizeBudgetUseCase(dataModule.exchangeRateRepository) }

    override val tripReminders: TripRemindersUseCase by lazy {
        TripRemindersUseCase(dataModule.savedTripRepository, dataModule.reminderLogRepository, clock)
    }

    override val prefetchTrip: PrefetchTripUseCase by lazy {
        PrefetchTripUseCase(
            getSeasonalHighlights = getSeasonalHighlights,
            getTripEvents = getTripEvents,
            findBudgetRestaurants = findBudgetRestaurants,
            findLodgings = findLodgings,
            getTravelGuide = getTravelGuide,
            getTripWeather = getTripWeather,
            getCountryInfo = getCountryInfo,
            getExchangeRate = getExchangeRate,
        )
    }

    override val observeSavedTrips: ObserveSavedTripsUseCase by lazy { ObserveSavedTripsUseCase(dataModule.savedTripRepository, clock) }

    override val observeSavedTrip: ObserveSavedTripUseCase by lazy { ObserveSavedTripUseCase(dataModule.savedTripRepository) }

    override val setTripSaved: SetTripSavedUseCase by lazy { SetTripSavedUseCase(dataModule.savedTripRepository, clock) }

    override val toggleFavorite: ToggleFavoriteUseCase by lazy { ToggleFavoriteUseCase(dataModule.savedTripRepository, clock) }

    override val planTrip: PlanTripUseCase by lazy { PlanTripUseCase(dataModule.travelAssistantRepository, loadTripKnowledge) }

    override val askTravelAssistant: AskTravelAssistantUseCase by lazy { AskTravelAssistantUseCase(dataModule.travelAssistantRepository) }

    override val packingChecklist: PackingChecklistUseCase by lazy { PackingChecklistUseCase(dataModule.checklistRepository) }

    override val exportUserData: ExportUserDataUseCase by lazy { ExportUserDataUseCase(dataModule.userDataRepository, clock) }

    override val importUserData: ImportUserDataUseCase by lazy { ImportUserDataUseCase(dataModule.userDataRepository) }

    override val summarizeUserData: SummarizeUserDataUseCase by lazy { SummarizeUserDataUseCase(dataModule.userDataRepository) }

    override val documents: UserDocuments by lazy { ContentResolverDocuments(appContext) }

    override val observeBookings: ObserveBookingsUseCase by lazy { ObserveBookingsUseCase(dataModule.bookingRepository) }

    override val saveBooking: SaveBookingUseCase by lazy { SaveBookingUseCase(dataModule.bookingRepository, dataModule.flightCodesRepository) }

    override val deleteBooking: DeleteBookingUseCase by lazy { DeleteBookingUseCase(dataModule.bookingRepository) }

    override val readBookingText: ReadBookingTextUseCase by lazy { ReadBookingTextUseCase(dataModule.flightCodesRepository, clock) }

    override val readBookingDocument: ReadBookingDocumentUseCase by lazy {
        ReadBookingDocumentUseCase(dataModule.textRecognitionRepository, readBookingText)
    }

    override val bookingReminders: BookingRemindersUseCase by lazy {
        BookingRemindersUseCase(dataModule.bookingRepository, dataModule.reminderLogRepository, clock)
    }

    override val bookingDocuments: BookingDocuments by lazy { BookingFiles(appContext, documents) }

    /**
     * Attività di avvio non bloccanti: pulizia delle risposte troppo vecchie in cache, canale delle
     * notifiche e controllo periodico delle offerte, attivo solo finché c'è almeno un viaggio seguito.
     */
    fun onAppStart() {
        runSafely("Pulizia della cache non riuscita") { dataModule.trimCache() }
        runSafely("Creazione del canale delle notifiche non riuscita") { DealNotifier(appContext).ensureChannel() }
        runSafely("Creazione del canale dei promemoria non riuscita") { TripReminderNotifier(appContext).ensureChannel() }
        runSafely("Pianificazione dei promemoria e dell'uso offline non riuscita") {
            // Promemoria finché c'è un viaggio salvato con una data futura; preparazione offline finché c'è un viaggio salvato.
            val scheduler = TripWorkScheduler(appContext)
            dataModule.savedTripRepository.trips
                .map { trips -> trips.isNotEmpty() to tripReminders.hasUpcoming() }
                .distinctUntilChanged()
                .collect { (anyTrip, upcoming) ->
                    if (upcoming) scheduler.scheduleReminders() else scheduler.cancelReminders()
                    if (anyTrip) scheduler.schedulePrefetch() else scheduler.cancelPrefetch()
                }
        }
        runSafely("Creazione del canale delle prenotazioni non riuscita") { BookingReminderNotifier(appContext).ensureChannel() }
        runSafely("Pulizia dei documenti delle prenotazioni non riuscita") {
            bookingDocuments.deleteUnused(dataModule.bookingRepository.bookings.first().mapNotNull { it.attachment }.toSet())
        }
        runSafely("Pianificazione dei promemoria delle prenotazioni non riuscita") {
            // Ogni modifica alle prenotazioni ripianifica i loro promemoria (check-in, partenza per l'aeroporto...).
            val scheduler = BookingReminderScheduler(appContext)
            dataModule.bookingRepository.bookings.distinctUntilChanged().collect { scheduler.schedule(bookingReminders.nextCheck()) }
        }
        runSafely("Aggiornamento del widget non riuscito") {
            // Il widget "Prossimo viaggio" segue i viaggi salvati.
            dataModule.savedTripRepository.trips.distinctUntilChanged().collect { NextTripWidget().updateAll(appContext) }
        }
        runSafely("Pianificazione del controllo delle offerte non riuscita") {
            val scheduler = DealCheckScheduler(appContext)
            dataModule.priceWatchRepository.watches
                .map { it.isNotEmpty() }
                .distinctUntilChanged()
                .collect { active -> if (active) scheduler.schedule() else scheduler.cancel() }
        }
    }

    private fun runSafely(failureMessage: String, block: suspend () -> Unit) {
        applicationScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, failureMessage, e)
            }
        }
    }

    private companion object {
        const val DEFAULT_LANGUAGE = "it"
        const val TAG = "AppContainer"
    }
}
