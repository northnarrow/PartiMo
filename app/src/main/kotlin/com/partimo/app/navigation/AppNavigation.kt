package com.partimo.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.partimo.app.di.AppContainer
import com.partimo.app.ui.dashboard.TripDashboardRoute
import com.partimo.app.ui.dashboard.TripDashboardViewModel
import com.partimo.app.ui.departure.DeparturePickerRoute
import com.partimo.app.ui.departure.DeparturePickerViewModel
import com.partimo.app.ui.search.SearchRoute
import com.partimo.app.ui.search.SearchViewModel
import com.partimo.domain.model.Destination
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.TravelPeriod
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
 * Grafo di navigazione: ricerca della meta → dashboard, più la scelta della partenza raggiungibile da
 * entrambe. [pendingDashboard] è il viaggio da aprire subito (tocco su una notifica).
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
            )
        }
        composable<DeparturePickerDestination> {
            val viewModel: DeparturePickerViewModel = viewModel(factory = DeparturePickerViewModel.factory(container))
            DeparturePickerRoute(viewModel = viewModel, onDone = { navController.popBackStack() })
        }
    }
}
