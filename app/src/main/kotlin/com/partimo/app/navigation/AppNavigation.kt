package com.partimo.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.partimo.app.R
import com.partimo.app.di.AppContainer
import com.partimo.app.ui.budget.BudgetRoute
import com.partimo.app.ui.budget.BudgetViewModel
import com.partimo.app.ui.chat.ChatRoute
import com.partimo.app.ui.chat.ChatViewModel
import com.partimo.app.ui.dashboard.TripDashboardRoute
import com.partimo.app.ui.dashboard.TripDashboardViewModel
import com.partimo.app.ui.departure.DeparturePickerRoute
import com.partimo.app.ui.departure.DeparturePickerViewModel
import com.partimo.app.ui.favorites.FavoritesRoute
import com.partimo.app.ui.favorites.FavoritesViewModel
import com.partimo.app.ui.guide.GuideRoute
import com.partimo.app.ui.guide.GuideViewModel
import com.partimo.app.ui.itinerary.ItineraryRoute
import com.partimo.app.ui.itinerary.ItineraryViewModel
import com.partimo.app.ui.map.MapRoute
import com.partimo.app.ui.map.MapViewModel
import com.partimo.app.ui.place.PlaceDetailRoute
import com.partimo.app.ui.place.PlaceDetailViewModel
import com.partimo.app.ui.search.SearchRoute
import com.partimo.app.ui.search.SearchViewModel
import com.partimo.app.ui.translator.TranslatorRoute
import com.partimo.app.ui.translator.TranslatorViewModel
import com.partimo.domain.model.Destination
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.poi.PoiTag
import com.partimo.domain.model.poi.PointOfInterest
import com.partimo.domain.model.poi.WikipediaPage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.ZoneId
import java.time.ZoneOffset

/** Schermata iniziale: "Dove vuoi andare?". */
@Serializable
data object SearchDestination

/** Scelta del punto di partenza ("Da dove parti?"). */
@Serializable
data object DeparturePickerDestination

/**
 * Dashboard di una meta. La rotta contiene tutti i dati della destinazione (tipi semplici), così
 * viene ripristinata anche dopo la chiusura del processo e può viaggiare dentro una notifica.
 */
@Serializable
data class DashboardDestination(
    val cityName: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val timeZone: String,
    val airportIata: String,
    val airportName: String,
    val airportLatitude: Double,
    val airportLongitude: Double,
    /** Chiave del [TravelPeriod]: "NEXT_DAYS" oppure un mese, es. "2026-12". */
    val period: String,
) {
    fun toDestination(): Destination = Destination(
        name = cityName,
        countryCode = countryCode,
        airportIata = airportIata,
        center = GeoPoint(latitude, longitude),
        arrivalHub = GeoPoint(airportLatitude, airportLongitude),
        arrivalHubName = airportName,
        timeZone = runCatching { ZoneId.of(timeZone) }.getOrDefault(ZoneOffset.UTC),
    )

    fun tripPeriod(): TravelPeriod = TravelPeriod.fromKey(period) ?: TravelPeriod.NextDays

    fun toJson(): String = RouteJson.encodeToString(serializer(), this)

    companion object {
        private val RouteJson = Json { ignoreUnknownKeys = true }

        fun from(destination: Destination, period: TravelPeriod) = DashboardDestination(
            cityName = destination.name,
            countryCode = destination.countryCode,
            latitude = destination.center.latitude,
            longitude = destination.center.longitude,
            timeZone = destination.timeZone.id,
            airportIata = destination.airportIata,
            airportName = destination.arrivalHubName,
            airportLatitude = destination.arrivalHub.latitude,
            airportLongitude = destination.arrivalHub.longitude,
            period = period.key,
        )

        /** `null` se il testo non è una rotta valida (l'intent può arrivare da fuori dell'app). */
        fun fromJson(json: String): DashboardDestination? =
            runCatching { RouteJson.decodeFromString(serializer(), json) }.getOrNull()
    }
}

/**
 * Scheda di un luogo da vedere. Come per la dashboard la rotta contiene i dati del luogo (tipi
 * semplici): la scheda mostra subito nome e foto e viene ripristinata anche dopo la chiusura del processo.
 */
@Serializable
data class PlaceDetailDestination(
    val id: String,
    val name: String,
    /** Nome della [PoiCategory]. */
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val description: String? = null,
    val photoUrl: String? = null,
    val rating: Double? = null,
    val reviewCount: Int? = null,
    val isIndoor: Boolean = false,
    /** Nomi delle [PoiTag] assegnate nella dashboard. */
    val tags: List<String> = emptyList(),
    val mapsUrl: String? = null,
    val wikipediaLanguage: String? = null,
    val wikipediaTitle: String? = null,
    val popularity: Double? = null,
    /** Viaggio da cui si è aperta la scheda ([TripArgs] in JSON): permette di salvare il luogo tra i preferiti. */
    val trip: String? = null,
) {
    fun tripArgs(): TripArgs? = trip?.let(TripArgs::fromJson)

    fun toPointOfInterest(): PointOfInterest {
        val poiCategory = PoiCategory.entries.firstOrNull { it.name == category } ?: PoiCategory.ATTRACTION
        return PointOfInterest(
            id = id,
            name = name,
            category = poiCategory,
            location = GeoPoint(latitude, longitude),
            rating = rating,
            reviewCount = reviewCount,
            description = description,
            photoUrl = photoUrl,
            isIndoor = isIndoor,
            tags = tags.mapNotNull { tag -> PoiTag.entries.firstOrNull { it.name == tag } }.toSet(),
            mapsUrl = mapsUrl,
            wikipediaPage = if (wikipediaLanguage != null && wikipediaTitle != null) WikipediaPage(wikipediaLanguage, wikipediaTitle) else null,
            popularity = popularity?.coerceIn(0.0, 1.0),
        )
    }

    companion object {
        fun from(poi: PointOfInterest, trip: TripArgs? = null) = PlaceDetailDestination(
            id = poi.id,
            name = poi.name,
            category = poi.category.name,
            latitude = poi.location.latitude,
            longitude = poi.location.longitude,
            description = poi.description,
            photoUrl = poi.photoUrl,
            rating = poi.rating,
            reviewCount = poi.reviewCount,
            isIndoor = poi.isIndoor,
            tags = poi.tags.map { it.name }.sorted(),
            mapsUrl = poi.mapsUrl,
            wikipediaLanguage = poi.wikipediaPage?.language,
            wikipediaTitle = poi.wikipediaPage?.title,
            popularity = poi.popularity,
            trip = trip?.toJson(),
        )
    }
}

/**
 * Grafo di navigazione: ricerca della meta → dashboard → scheda di un luogo, più la scelta della
 * partenza raggiungibile da ricerca e dashboard. [pendingDashboard] è il viaggio da aprire subito
 * (tocco su una notifica).
 */
@Composable
fun PartiMoNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier,
    pendingDashboard: DashboardDestination? = null,
    onPendingDashboardOpened: () -> Unit = {},
) {
    val navController = rememberNavController()
    val onOpened by rememberUpdatedState(onPendingDashboardOpened)
    LaunchedEffect(pendingDashboard) {
        pendingDashboard?.let { route ->
            navController.navigate(route) { launchSingleTop = true }
            onOpened()
        }
    }
    NavHost(navController = navController, startDestination = SearchDestination, modifier = modifier) {
        composable<SearchDestination> {
            val viewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory(container))
            SearchRoute(
                viewModel = viewModel,
                onOpenDestination = { destination, period ->
                    navController.navigate(DashboardDestination.from(destination, period))
                },
                onChooseDeparture = { navController.navigate(DeparturePickerDestination) },
            )
        }
        composable<DashboardDestination> { backStackEntry ->
            val route = backStackEntry.toRoute<DashboardDestination>()
            val viewModel: TripDashboardViewModel = viewModel(
                factory = TripDashboardViewModel.factory(container, route.toDestination(), route.tripPeriod()),
            )
            TripDashboardRoute(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onChooseDeparture = { navController.navigate(DeparturePickerDestination) },
                onOpenPlace = { poi, trip -> navController.navigate(PlaceDetailDestination.from(poi, trip)) },
                onOpenItinerary = { trip -> navController.navigate(ItineraryDestination(trip.toJson())) },
                onOpenAssistant = { trip -> navController.navigate(AssistantChatDestination(trip.toJson())) },
                onOpenGuide = { trip -> navController.navigate(GuideDestination(trip.toJson())) },
                onOpenFavorites = { trip -> navController.navigate(FavoritesDestination(trip.toJson())) },
                onOpenMap = { trip -> navController.navigate(MapDestination(trip.toJson())) },
                onOpenTranslator = { trip -> navController.navigate(TranslatorDestination(trip.toJson())) },
                onOpenBudget = { trip -> navController.navigate(BudgetDestination(trip.toJson())) },
            )
        }
        composable<BudgetDestination> { backStackEntry ->
            val trip = TripArgs.fromJson(backStackEntry.toRoute<BudgetDestination>().trip)
            val period = trip?.travelPeriod()
            if (trip == null || period == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            val viewModel: BudgetViewModel = viewModel(
                factory = BudgetViewModel.factory(container, trip.destination(), period, trip.fromDate(), trip.toDate()),
            )
            BudgetRoute(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable<TranslatorDestination> { backStackEntry ->
            val trip = TripArgs.fromJson(backStackEntry.toRoute<TranslatorDestination>().trip)
            if (trip == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            val userLanguage = stringResource(R.string.translator_user_language)
            val viewModel: TranslatorViewModel = viewModel(factory = TranslatorViewModel.factory(container, trip.destination(), userLanguage))
            TranslatorRoute(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable<MapDestination> { backStackEntry ->
            val route = backStackEntry.toRoute<MapDestination>()
            val trip = TripArgs.fromJson(route.trip)
            if (trip == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            val viewModel: MapViewModel = viewModel(
                factory = MapViewModel.factory(container, trip.destination(), trip.fromDate(), trip.toDate(), trip.travelPeriod(), route.favoritesOnly),
            )
            MapRoute(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenPlace = { poi -> navController.navigate(PlaceDetailDestination.from(poi, trip)) },
            )
        }
        composable<FavoritesDestination> { backStackEntry ->
            val tripJson = backStackEntry.toRoute<FavoritesDestination>().trip
            val trip = TripArgs.fromJson(tripJson)
            val period = trip?.travelPeriod()
            if (trip == null || period == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            val viewModel: FavoritesViewModel = viewModel(
                factory = FavoritesViewModel.factory(container, trip.destination(), period, trip.fromDate(), trip.toDate()),
            )
            FavoritesRoute(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenPlace = { poi -> navController.navigate(PlaceDetailDestination.from(poi, trip)) },
                onOpenMap = { navController.navigate(MapDestination(tripJson, favoritesOnly = true)) },
            )
        }
        composable<GuideDestination> { backStackEntry ->
            val trip = TripArgs.fromJson(backStackEntry.toRoute<GuideDestination>().trip)
            if (trip == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            val viewModel: GuideViewModel = viewModel(
                factory = GuideViewModel.factory(container, trip.destination(), trip.fromDate(), trip.toDate()),
            )
            GuideRoute(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenTranslator = { navController.navigate(TranslatorDestination(trip.toJson())) },
            )
        }
        composable<ItineraryDestination> { backStackEntry ->
            val tripJson = backStackEntry.toRoute<ItineraryDestination>().trip
            val trip = TripArgs.fromJson(tripJson)
            if (trip == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            val viewModel: ItineraryViewModel = viewModel(
                factory = ItineraryViewModel.factory(container, trip.destination(), trip.fromDate(), trip.toDate()),
            )
            ItineraryRoute(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenPlace = { poi -> navController.navigate(PlaceDetailDestination.from(poi, trip)) },
                onAskAssistant = { navController.navigate(AssistantChatDestination(tripJson)) },
            )
        }
        composable<AssistantChatDestination> { backStackEntry ->
            val trip = TripArgs.fromJson(backStackEntry.toRoute<AssistantChatDestination>().trip)
            if (trip == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            val viewModel: ChatViewModel = viewModel(
                factory = ChatViewModel.factory(container, trip.destination(), trip.fromDate(), trip.toDate()),
            )
            ChatRoute(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable<PlaceDetailDestination> { backStackEntry ->
            val route = backStackEntry.toRoute<PlaceDetailDestination>()
            val viewModel: PlaceDetailViewModel = viewModel(factory = PlaceDetailViewModel.factory(container, route.toPointOfInterest(), route.tripArgs()))
            PlaceDetailRoute(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable<DeparturePickerDestination> {
            val viewModel: DeparturePickerViewModel = viewModel(factory = DeparturePickerViewModel.factory(container))
            DeparturePickerRoute(viewModel = viewModel, onDone = { navController.popBackStack() })
        }
    }
}
