package com.partimo.app.di

import android.content.Context
import android.util.Log
import com.partimo.app.BuildConfig
import com.partimo.data.config.ApiConfig
import com.partimo.app.notifications.DealCheckScheduler
import com.partimo.app.notifications.DealNotifier
import com.partimo.data.di.DataModule
import com.partimo.domain.usecase.CheckPriceWatchesUseCase
import com.partimo.domain.usecase.FindBudgetRestaurantsUseCase
import com.partimo.domain.usecase.FindDepartureAirportsUseCase
import com.partimo.domain.usecase.GetPoiDetailsUseCase
import com.partimo.domain.usecase.GetSeasonalHighlightsUseCase
import com.partimo.domain.usecase.ObserveDepartureUseCase
import com.partimo.domain.usecase.ObservePriceAlertUseCase
import com.partimo.domain.usecase.PlanTransitRouteUseCase
import com.partimo.domain.usecase.RecommendDestinationsUseCase
import com.partimo.domain.usecase.ResolveDestinationUseCase
import com.partimo.domain.usecase.SaveDepartureUseCase
import com.partimo.domain.usecase.SearchAccommodationsUseCase
import com.partimo.domain.usecase.SearchCitiesUseCase
import com.partimo.domain.usecase.SearchFlightsUseCase
import com.partimo.domain.usecase.SetPriceAlertUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val getSeasonalHighlights: GetSeasonalHighlightsUseCase
    val getPoiDetails: GetPoiDetailsUseCase
    val planTransitRoute: PlanTransitRouteUseCase
    val findBudgetRestaurants: FindBudgetRestaurantsUseCase
    val searchCities: SearchCitiesUseCase
    val resolveDestination: ResolveDestinationUseCase
    val recommendDestinations: RecommendDestinationsUseCase
    val findDepartureAirports: FindDepartureAirportsUseCase
    val observeDeparture: ObserveDepartureUseCase
    val saveDeparture: SaveDepartureUseCase
    val observePriceAlert: ObservePriceAlertUseCase
    val setPriceAlert: SetPriceAlertUseCase
    val checkPriceWatches: CheckPriceWatchesUseCase
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

    override val getSeasonalHighlights: GetSeasonalHighlightsUseCase by lazy {
        GetSeasonalHighlightsUseCase(dataModule.poiRepository, dataModule.weatherRepository, clock = clock)
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

    override val observePriceAlert: ObservePriceAlertUseCase by lazy { ObservePriceAlertUseCase(dataModule.priceWatchRepository) }

    override val setPriceAlert: SetPriceAlertUseCase by lazy { SetPriceAlertUseCase(dataModule.priceWatchRepository, clock) }

    override val checkPriceWatches: CheckPriceWatchesUseCase by lazy {
        CheckPriceWatchesUseCase(dataModule.priceWatchRepository, searchFlights, searchAccommodations, clock = clock)
    }

    /**
     * Attività di avvio non bloccanti: pulizia delle risposte troppo vecchie in cache, canale delle
     * notifiche e controllo periodico delle offerte, attivo solo finché c'è almeno un viaggio seguito.
     */
    fun onAppStart() {
        runSafely("Pulizia della cache non riuscita") { dataModule.trimCache() }
        runSafely("Creazione del canale delle notifiche non riuscita") { DealNotifier(appContext).ensureChannel() }
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
