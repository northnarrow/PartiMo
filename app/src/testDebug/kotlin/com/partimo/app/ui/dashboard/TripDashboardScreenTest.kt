package com.partimo.app.ui.dashboard

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.view.drawToBitmap
import com.partimo.app.R
import com.partimo.app.ui.common.PERIOD_CHIPS_TAG
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.dashboard.components.GETTING_THERE_TAG
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.common.DataError
import com.partimo.domain.model.Money
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.deal.PriceChange
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.poi.PointOfInterest
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Month
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test UI della dashboard, eseguiti sulla JVM con Robolectric (nessun emulatore).
 *
 * Si trovano in `testDebug` perché l'activity di test di Compose è dichiarata dalla dipendenza
 * `ui-test-manifest`, inclusa solo nella build debug. SDK 34: le immagini Robolectric di Android 15+
 * richiedono JDK 21.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class TripDashboardScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(@StringRes id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    /** Mostra la dashboard con uno stato modificabile: i pulsanti delle sezioni cambiano davvero sezione. */
    private fun showDashboard(
        initial: TripDashboardUiState,
        actions: DashboardActions = DashboardActions(),
    ): MutableState<TripDashboardUiState> {
        val state = mutableStateOf(initial)
        composeRule.setContent {
            PartiMoTheme {
                TripDashboardScreen(
                    state = state.value,
                    actions = actions.copy(
                        onSectionSelected = { section ->
                            state.value = state.value.copy(selectedSection = section)
                            actions.onSectionSelected(section)
                        },
                    ),
                )
            }
        }
        return state
    }

    private fun saveScreenshot(name: String) {
        composeRule.waitForIdle()
        // Disegno software della view radice: su Robolectric è più affidabile di captureToImage().
        val bitmap = composeRule.activity.window.decorView.rootView.drawToBitmap()
        val screenshot = File("build/outputs/screenshots/$name").apply { parentFile?.mkdirs() }
        screenshot.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        assertTrue(screenshot.length() > 0, "Screenshot non generato")
    }

    @Test
    fun `ogni pulsante della barra apre la sua sezione`() {
        showDashboard(PreviewData.loadedState())

        val expectations = listOf(
            R.string.nav_flights to "Austrian Airlines",
            R.string.nav_stays to "Pension Mozartgasse",
            R.string.nav_highlights to "Wiener Christkindlmarkt al Rathausplatz",
            R.string.nav_transit to "Vienna International Airport",
            R.string.nav_restaurants to "Beisl zum Goldenen Hirschen",
        )
        expectations.forEach { (button, content) ->
            composeRule.onNodeWithText(text(button)).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(DASHBOARD_LIST_TAG).performScrollToNode(hasText(content, substring = true))
            composeRule.onNodeWithText(content, substring = true).assertExists()
        }
    }

    @Test
    fun `toccando un luogo da vedere si apre la sua scheda`() {
        var opened: PointOfInterest? = null
        showDashboard(
            PreviewData.loadedState().copy(selectedSection = DashboardSection.HIGHLIGHTS),
            DashboardActions(onOpenPlace = { opened = it }),
        )

        composeRule.onNodeWithTag(DASHBOARD_LIST_TAG).performScrollToNode(hasText("Kahlenberg"))
        composeRule.onNodeWithText("Monte del Bosco Viennese", substring = true).assertExists()
        composeRule.onNodeWithText("Kahlenberg").performClick()

        assertEquals("kahlenberg", opened?.id)
    }

    @Test
    fun `in cima a da vedere ci sono gli eventi del soggiorno, che si aprono come i luoghi`() {
        var opened: PointOfInterest? = null
        val links = mutableListOf<String>()
        val loaded = PreviewData.loadedState()
        val trip = (loaded.events as UiState.Success).data
        showDashboard(
            loaded.copy(
                selectedSection = DashboardSection.HIGHLIGHTS,
                events = UiState.Success(trip.copy(events = trip.events + PreviewData.immaculateConception)),
            ),
            DashboardActions(onOpenPlace = { opened = it }, onOpenLink = { links += it }),
        )

        composeRule.onNodeWithText(text(R.string.section_events), substring = true).assertExists()
        composeRule.onNodeWithText("Spittelberg · Di solito dal 15 nov al 24 dic").assertExists()
        composeRule.onNodeWithText("Weihnachtsmarkt am Spittelberg").performClick()
        assertEquals("wikidata:spittelberg", opened?.id)
        assertEquals(PoiCategory.SEASONAL_EVENT, opened?.category)

        composeRule.onNodeWithTag(DASHBOARD_LIST_TAG).performScrollToNode(hasText("8 dic · festa nazionale"))
        composeRule.onNodeWithText("Mariä Empfängnis", substring = true).assertExists()

        composeRule.onNodeWithTag(DASHBOARD_LIST_TAG).performScrollToNode(hasText("Mercatini di Natale a Vienna", substring = true))
        composeRule.onNodeWithText("Mercatini di Natale a Vienna", substring = true).performClick()
        composeRule.onNodeWithText(text(R.string.events_search_link)).performClick()
        assertEquals(
            listOf(
                "https://www.google.com/maps/search/?api=1&query=mercatini%20di%20Natale%20Vienna",
                "https://www.google.com/search?q=eventi%20a%20Vienna%20dal%2010%20al%2014%20dicembre%202026",
            ),
            links,
        )
    }

    @Test
    fun `se gli eventi non si caricano restano i pulsanti di ricerca e si può riprovare`() {
        var retried = false
        showDashboard(
            PreviewData.loadedState().copy(selectedSection = DashboardSection.HIGHLIGHTS, events = UiState.Error(DataError.RateLimited)),
            DashboardActions(onRetryEvents = { retried = true }),
        )

        composeRule.onNodeWithText("Mercatini di Natale a Vienna", substring = true).assertExists()
        composeRule.onNodeWithText(text(R.string.events_search_link)).assertExists()
        composeRule.onNodeWithText(text(R.string.error_rate_limited)).assertExists()
        composeRule.onAllNodesWithText(text(R.string.action_retry))[0].performClick()

        assertTrue(retried)
    }

    @Test
    fun `aggiorna e la campanella invocano le rispettive azioni`() {
        var refreshed = false
        var alertToggled = false
        showDashboard(PreviewData.loadedState(), DashboardActions(onRefresh = { refreshed = true }, onToggleAlert = { alertToggled = true }))

        composeRule.onNodeWithText(text(R.string.action_refresh)).performClick()
        composeRule.onNodeWithContentDescription(text(R.string.action_alert_disable)).performClick()

        assertTrue(refreshed)
        assertTrue(alertToggled)
        composeRule.onNodeWithTag(DASHBOARD_LIST_TAG).performScrollToNode(hasText("Prezzi aggiornati alle", substring = true))
        composeRule.onNodeWithText("Prezzi aggiornati alle", substring = true).assertExists()
    }

    @Test
    fun `dopo aggiorna la snackbar evidenzia il ribasso`() {
        val summary = RefreshSummary(flight = PriceChange(Money.of(168, "EUR"), Money.of(139, "EUR")), stay = null)
        showDashboard(PreviewData.loadedState().copy(refreshSummary = summary))

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Volo sceso a", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `senza partenza la sezione voli chiede di sceglierla`() {
        var chooseDeparture = false
        showDashboard(PreviewData.noDepartureState(), DashboardActions(onChooseDeparture = { chooseDeparture = true }))

        composeRule.onNodeWithText(text(R.string.flights_need_departure)).assertExists()
        composeRule.onNodeWithText("Partenza da scegliere", substring = true).assertExists()
        composeRule.onNodeWithText(text(R.string.flights_choose_departure)).performClick()

        assertTrue(chooseDeparture)
    }

    @Test
    fun `si può scegliere qualunque mese dei prossimi dodici`() {
        var selected: TravelPeriod? = null
        showDashboard(PreviewData.loadedState(), DashboardActions(onPeriodSelected = { selected = it }))

        composeRule.onNodeWithTag(PERIOD_CHIPS_TAG).performScrollToNode(hasText("Settembre 2027", substring = true))
        composeRule.onNodeWithText("Settembre 2027", substring = true).performClick()

        assertEquals(TravelPeriod.InMonth(YearMonth.of(2027, Month.SEPTEMBER)), selected)
    }

    @Test
    fun `gli errori mostrano il messaggio e il pulsante riprova`() {
        var retried: DashboardSection? = null
        showDashboard(PreviewData.mixedStates(), DashboardActions(onRetry = { retried = it }))

        composeRule.onNodeWithText(text(R.string.error_no_connection)).assertExists()
        composeRule.onAllNodesWithText(text(R.string.action_retry))[0].performClick()
        assertEquals(DashboardSection.FLIGHTS, retried)

        composeRule.onNodeWithText(text(R.string.nav_transit)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(text(R.string.empty_transit)).assertExists()
    }

    @Test
    fun `i voli stimati portano ai prezzi reali su Google Voli e Skyscanner con tratta e date`() {
        val opened = mutableListOf<String>()
        showDashboard(PreviewData.loadedState(), DashboardActions(onOpenLink = { opened += it }))

        composeRule.onNodeWithText(text(R.string.flights_links_estimates)).assertExists()
        composeRule.onNodeWithText(text(R.string.link_google_flights)).performClick()
        composeRule.onNodeWithText(text(R.string.link_skyscanner)).performClick()

        assertEquals(
            listOf(
                "https://www.google.com/travel/flights?q=Flights%20from%20MXP%20to%20VIE%20on%202026-12-10%20through%202026-12-14&hl=it&curr=EUR",
                "https://www.skyscanner.it/trasporti/voli/mxp/vie/261210/261214/?adultsv2=1",
            ),
            opened,
        )
    }

    @Test
    fun `i prezzi trovati su Aviasales mostrano date, notti e giorno del prezzo e si verificano su Aviasales`() {
        val opened = mutableListOf<String>()
        showDashboard(PreviewData.recentFlightPricesState(), DashboardActions(onOpenLink = { opened += it }))

        composeRule.onNodeWithText(text(R.string.flights_links_recent)).assertExists()
        composeRule.onNodeWithText(text(R.string.section_flights_subtitle_cities, "Milano", "Vienna")).assertExists()
        composeRule.onNodeWithText(text(R.string.demo_banner_transit)).assertExists()
        composeRule.onNodeWithText(text(R.string.flight_outbound_day, "ven 11 dic")).assertExists()
        composeRule.onNodeWithText(text(R.string.flight_inbound_day, "dom 13 dic")).assertExists()
        composeRule.onNodeWithText("21:10 BGY → 22:40 VIE · 1 h 30 min · " + text(R.string.flight_direct)).assertExists()
        val nights = composeRule.activity.resources.getQuantityString(R.plurals.flight_stay_nights, 2, 2)
        composeRule.onNodeWithText(nights + " · " + text(R.string.flight_price_found_on, "30 set")).assertExists()
        composeRule.onAllNodesWithText(text(R.string.flight_open_booking, "aviasales.com"))[0].performClick()

        assertEquals(listOf("https://www.aviasales.com/search/MIL1112VIE13121"), opened)
    }

    @Test
    fun `con le date scelte il volo di quei giorni è segnalato e i giorni vicini vengono dopo`() {
        showDashboard(PreviewData.recentFlightPricesDatesState())

        composeRule.onNodeWithText(text(R.string.flights_links_recent_dates)).assertExists()
        composeRule.onNodeWithText("📅 11–13 dic").assertExists()
        composeRule.onAllNodesWithText("📅 " + text(R.string.flight_on_trip_dates)).assertCountEquals(1)
        composeRule.onNodeWithText(text(R.string.flight_outbound_day, "gio 10 dic")).assertExists()
        // Il miglior rapporto qualità/prezzo è il volo del giorno prima, anche se viene dopo.
        composeRule.onAllNodesWithText("⭐ " + text(R.string.best_value_badge)).assertCountEquals(1)
    }

    @Test
    fun `senza prezzi recenti per la tratta lo dice e restano Google Voli e Skyscanner`() {
        showDashboard(PreviewData.recentFlightPricesState().copy(flights = UiState.Empty))

        composeRule.onNodeWithText(text(R.string.empty_flights_recent)).assertExists()
        composeRule.onNodeWithText(text(R.string.link_google_flights)).assertExists()
    }

    @Test
    fun `senza chiavi gli alloggi sono strutture reali collegate a Booking con le date del viaggio`() {
        val opened = mutableListOf<String>()
        showDashboard(
            PreviewData.openDataState().copy(selectedSection = DashboardSection.STAYS),
            DashboardActions(onOpenLink = { opened += it }),
        )

        composeRule.onNodeWithText(text(R.string.section_lodgings)).assertExists()
        composeRule.onNodeWithText("Hotel · ★★★★★ · ", substring = true).assertExists()
        composeRule.onNodeWithText(text(R.string.link_booking)).performClick()
        composeRule.onAllNodesWithText(text(R.string.lodging_prices))[0].performClick()
        composeRule.onNodeWithText(text(R.string.lodging_website)).performClick()
        composeRule.onNodeWithTag(DASHBOARD_LIST_TAG).performScrollToNode(hasText(text(R.string.osm_attribution)))
        composeRule.onNodeWithText(text(R.string.osm_attribution)).performClick()

        assertEquals(
            listOf(
                "https://www.booking.com/searchresults.it.html?ss=Vienna&checkin=2026-12-10&checkout=2026-12-14&group_adults=1&no_rooms=1&group_children=0",
                "https://www.booking.com/searchresults.it.html?ss=Hotel%20Sacher%20Wien%2C%20Vienna&checkin=2026-12-10&checkout=2026-12-14&group_adults=1&no_rooms=1&group_children=0",
                "https://www.sacher.com/",
                "https://www.openstreetmap.org/copyright",
            ),
            opened,
        )
    }

    @Test
    fun `come arrivare confronta treno, pullman, aereo e auto con la CO2 e apre Google Maps e Rome2rio`() {
        val opened = mutableListOf<String>()
        val state = showDashboard(
            PreviewData.openDataState().copy(selectedSection = DashboardSection.TRANSIT),
            DashboardActions(onOpenLink = { opened += it }),
        )

        composeRule.onNodeWithText("🧭 " + text(R.string.getting_there_title, "Milano")).assertExists()
        composeRule.onNodeWithText(text(R.string.footprint_title)).assertExists()
        composeRule.onNodeWithText("🚆 " + text(R.string.footprint_train)).assertExists()
        composeRule.onNodeWithText("🚗 " + text(R.string.footprint_car_alone)).assertExists()
        composeRule.onNodeWithText("🚆 " + text(R.string.getting_there_google)).performClick()
        composeRule.onNodeWithText(text(R.string.getting_there_rome2rio)).performClick()

        assertEquals(
            listOf(
                "https://www.google.com/maps/dir/?api=1&origin=Milano&destination=Vienna&travelmode=transit",
                "https://www.rome2rio.com/s/Milano/Vienna",
            ),
            opened,
        )
        // Senza partenza la scheda non c'è: i voli chiedono di sceglierla.
        state.value = state.value.copy(trip = state.value.trip.copy(departure = null))
        composeRule.onNodeWithTag(GETTING_THERE_TAG).assertDoesNotExist()
    }

    @Test
    fun `i trasporti aprono il percorso reale con i mezzi su Google Maps`() {
        var opened: String? = null
        showDashboard(
            PreviewData.openDataState().copy(selectedSection = DashboardSection.TRANSIT),
            DashboardActions(onOpenLink = { opened = it }),
        )

        composeRule.onNodeWithTag(DASHBOARD_LIST_TAG).performScrollToNode(hasText(text(R.string.transit_open_maps)))
        composeRule.onNodeWithText(text(R.string.transit_links_estimates)).assertExists()
        composeRule.onNodeWithText(text(R.string.transit_open_maps)).performClick()

        assertEquals(
            "https://www.google.com/maps/dir/?api=1&origin=48.110300%2C16.569700&destination=48.208500%2C16.373100&travelmode=transit",
            opened,
        )
    }

    @Test
    fun `senza chiavi i ristoranti reali si aprono su Google Maps per recensioni e foto`() {
        var opened: String? = null
        showDashboard(
            PreviewData.openDataState().copy(selectedSection = DashboardSection.RESTAURANTS),
            DashboardActions(onOpenLink = { opened = it }),
        )

        composeRule.onNodeWithText(text(R.string.section_restaurants_nearby)).assertExists()
        composeRule.onNodeWithText("Pizza · ", substring = true).assertExists()
        composeRule.onNodeWithText("Figlmüller").performClick()

        assertEquals("https://www.google.com/maps/search/?api=1&query=Figlm%C3%BCller%2C%20Wollzeile%205%2C%20Vienna", opened)
    }

    @Test
    fun `con l'assistente attivo la dashboard porta all'itinerario e alle domande`() {
        var itineraries = 0
        var chats = 0
        val state = showDashboard(
            PreviewData.loadedState(),
            DashboardActions(onOpenItinerary = { itineraries++ }, onOpenAssistant = { chats++ }),
        )

        composeRule.onNodeWithText(text(R.string.assistant_itinerary_chip)).performClick()
        // La riga degli strumenti scorre in orizzontale: "Chiedi" è l'ultimo.
        composeRule.onNodeWithTag(TRIP_TOOLS_TAG).performScrollToNode(hasText(text(R.string.assistant_chat_chip)))
        composeRule.onNodeWithText(text(R.string.assistant_chat_chip)).performClick()
        assertEquals(1, itineraries)
        assertEquals(1, chats)

        state.value = state.value.copy(assistantAvailable = false)
        composeRule.onNodeWithText(text(R.string.assistant_chat_chip)).assertDoesNotExist()
    }

    @Test
    fun `i ristoranti mostrano orari della settimana del viaggio, aperto ora e accessibilità`() {
        val state = showDashboard(PreviewData.openDataState().copy(selectedSection = DashboardSection.RESTAURANTS))

        composeRule.onNodeWithText("🕒 lun–dom 11:00–24:00").assertExists()
        composeRule.onNodeWithText("🕒 lun–ven 11:30–14:30, 18:00–22:00 · sab 18:00–22:00 · dom chiuso").assertExists()
        composeRule.onNodeWithText(text(R.string.wheelchair_yes)).assertExists()
        composeRule.onNodeWithText(text(R.string.wheelchair_limited)).assertExists()
        composeRule.onNodeWithText("🟢 Aperto ora", substring = true).assertDoesNotExist()

        // Viaggio imminente: alle 23:30 di venerdì a Vienna Figlmüller ha chiuso, Pizza Bizi è aperta fino a mezzanotte.
        state.value = state.value.copy(nowAtDestination = java.time.LocalDateTime.of(2026, 12, 11, 23, 30))
        composeRule.onNodeWithText("🟢 Aperto ora · chiude alle 00:00").assertExists()
        composeRule.onNodeWithText("🔴 Chiuso ora · apre domani alle 11:00").assertExists()
    }

    @Test
    fun `mappa, guida, traduttore e budget si aprono sempre, anche senza assistente`() {
        var guides = 0
        var maps = 0
        var translators = 0
        var budgets = 0
        val state = showDashboard(
            PreviewData.loadedState().copy(assistantAvailable = false),
            DashboardActions(onOpenGuide = { guides++ }, onOpenMap = { maps++ }, onOpenTranslator = { translators++ }, onOpenBudget = { budgets++ }),
        )

        composeRule.onNodeWithText(text(R.string.guide_chip)).performClick()
        composeRule.onNodeWithText(text(R.string.map_chip)).performClick()
        composeRule.onNodeWithTag(TRIP_TOOLS_TAG).performScrollToNode(hasText(text(R.string.translator_chip)))
        composeRule.onNodeWithText(text(R.string.translator_chip)).performClick()
        composeRule.onNodeWithTag(TRIP_TOOLS_TAG).performScrollToNode(hasText(text(R.string.budget_chip)))
        composeRule.onNodeWithText(text(R.string.budget_chip)).performClick()
        assertEquals(1, guides)
        assertEquals(1, maps)
        assertEquals(1, translators)
        assertEquals(1, budgets)
        composeRule.onNodeWithText(text(R.string.assistant_itinerary_chip)).assertDoesNotExist()
        state.value = state.value.copy(assistantAvailable = true)
        composeRule.onNodeWithTag(TRIP_TOOLS_TAG).performScrollToNode(hasText(text(R.string.assistant_itinerary_chip)))
        composeRule.onNodeWithText(text(R.string.assistant_itinerary_chip)).assertExists()
    }

    @Test
    fun `stelle e segnalibro salvano luoghi, eventi, ristoranti e il viaggio`() {
        val favorites = mutableListOf<com.partimo.domain.model.saved.Favorite>()
        var saves = 0
        var opened = 0
        val state = showDashboard(
            PreviewData.loadedState().copy(selectedSection = DashboardSection.HIGHLIGHTS),
            DashboardActions(onToggleFavorite = { favorites += it }, onToggleTripSaved = { saves++ }, onOpenFavorites = { opened++ }),
        )

        composeRule.onNodeWithContentDescription(text(R.string.trip_unsave)).performClick()
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.favorite_remove, "Weihnachtsmarkt am Spittelberg")).assertExists()
        val addMarket = composeRule.activity.getString(R.string.favorite_add, "Wiener Christkindlmarkt al Rathausplatz")
        composeRule.onNodeWithTag(DASHBOARD_LIST_TAG).performScrollToNode(hasContentDescription(addMarket))
        composeRule.onNodeWithContentDescription(addMarket).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.favorites_chip, 3)).performClick()

        state.value = PreviewData.openDataState().copy(selectedSection = DashboardSection.RESTAURANTS)
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.favorite_add, "Pizza Bizi")).performClick()

        assertEquals(1, saves)
        assertEquals(1, opened)
        // Un mercatino tra i luoghi da vedere è un evento anche tra i preferiti.
        assertEquals(listOf("EVENT:xmas", "RESTAURANT:osm:node/13"), favorites.map { it.key })
    }

    @Test
    fun `salva gli screenshot della dashboard`() {
        val state = showDashboard(PreviewData.loadedState())
        saveScreenshot("trip_dashboard.png")

        state.value = state.value.copy(selectedSection = DashboardSection.HIGHLIGHTS)
        saveScreenshot("trip_dashboard_explore.png")

        state.value = PreviewData.openDataState().copy(selectedSection = DashboardSection.STAYS)
        saveScreenshot("trip_dashboard_stays.png")

        state.value = state.value.copy(selectedSection = DashboardSection.RESTAURANTS)
        saveScreenshot("trip_dashboard_restaurants.png")

        state.value = state.value.copy(selectedSection = DashboardSection.TRANSIT)
        saveScreenshot("trip_dashboard_transit.png")

        state.value = PreviewData.recentFlightPricesState()
        saveScreenshot("trip_dashboard_flights.png")
    }

    @Test
    fun `salva lo screenshot dei voli per le date scelte`() {
        showDashboard(PreviewData.recentFlightPricesDatesState())
        saveScreenshot("trip_dashboard_dates.png")
    }
}
