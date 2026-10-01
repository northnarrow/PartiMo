package com.partimo.app.ui.search

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.drawToBitmap
import com.partimo.app.R
import com.partimo.app.testing.UiTestApplication
import com.partimo.app.ui.common.Formatters
import com.partimo.app.ui.common.PERIOD_CHIPS_TAG
import com.partimo.app.ui.common.TRAVELLERS_TAG
import com.partimo.app.ui.common.TravellersDialog
import com.partimo.app.ui.dashboard.PreviewData
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.model.Money
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.Travellers
import com.partimo.domain.model.place.CityPlace
import com.partimo.domain.model.place.DestinationSuggestion
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Test UI della schermata iniziale "Dove vuoi andare?", eseguiti sulla JVM con Robolectric. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = UiTestApplication::class, qualifiers = "w411dp-h1500dp-xxhdpi")
class SearchScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(@StringRes id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    @Test
    fun `mostra marchio, titolo, partenza, campo di ricerca e consigliami`() {
        var abouts = 0
        composeRule.setContent {
            PartiMoTheme { SearchScreen(PreviewData.searchIdleState(), query = "", actions = SearchActions(onOpenAbout = { abouts++ })) }
        }
        composeRule.onNodeWithContentDescription(text(R.string.about_open)).performClick()
        kotlin.test.assertEquals(1, abouts)

        composeRule.onNodeWithText("PartiMo").assertExists()
        composeRule.onNodeWithText(text(R.string.search_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.search_subtitle)).assertExists()
        composeRule.onNodeWithText(text(R.string.search_placeholder)).assertExists()
        composeRule.onNodeWithText("Milano · MXP").assertExists()
        composeRule.onNodeWithText(text(R.string.search_recommend), substring = true).assertExists()
    }

    @Test
    fun `senza partenza invita a sceglierla e il tocco apre la scelta`() {
        var chooseDeparture = false
        composeRule.setContent {
            PartiMoTheme {
                SearchScreen(
                    state = SearchUiState(today = PreviewData.TODAY),
                    query = "",
                    actions = SearchActions(onChooseDeparture = { chooseDeparture = true }),
                )
            }
        }

        composeRule.onNodeWithText(text(R.string.departure_missing_hint)).assertExists()
        composeRule.onNodeWithText(text(R.string.departure_choose)).performClick()

        assertTrue(chooseDeparture)
    }

    @Test
    fun `i periodi comprendono tutti i mesi dei prossimi dodici`() {
        var selected: TravelPeriod? = null
        composeRule.setContent {
            PartiMoTheme {
                SearchScreen(PreviewData.searchIdleState(), query = "", actions = SearchActions(onPeriodSelected = { selected = it }))
            }
        }

        listOf("Prossimi giorni", "Ottobre", "Dicembre", "Febbraio 2027", "Agosto 2027", "Settembre 2027").forEach { label ->
            composeRule.onNodeWithTag(PERIOD_CHIPS_TAG).performScrollToNode(hasText(label, substring = true))
        }
        composeRule.onNodeWithTag(PERIOD_CHIPS_TAG).performScrollToNode(hasText("Febbraio 2027", substring = true))
        composeRule.onNodeWithText("Febbraio 2027", substring = true).performClick()

        assertEquals(TravelPeriod.InMonth(YearMonth.of(2027, Month.FEBRUARY)), selected)
    }

    @Test
    fun `mentre si scrive l'intestazione si compatta per lasciare spazio ai risultati`() {
        composeRule.setContent {
            PartiMoTheme { SearchScreen(PreviewData.searchResultsState(), query = "Par", actions = SearchActions()) }
        }

        composeRule.onNodeWithText(text(R.string.search_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.search_subtitle)).assertDoesNotExist()
        composeRule.onNodeWithText("Parigi").assertExists()
    }

    @Test
    fun `digitare e toccare un risultato invoca le azioni`() {
        var typed = ""
        var selected: CityPlace? = null
        composeRule.setContent {
            PartiMoTheme {
                SearchScreen(
                    state = PreviewData.searchResultsState(),
                    query = "Par",
                    actions = SearchActions(onQueryChange = { typed = it }, onCitySelected = { selected = it }),
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextReplacement("Roma")
        composeRule.onNodeWithText("Parigi").performClick()
        composeRule.waitForIdle()

        assertEquals("Roma", typed)
        assertEquals("geonames:2988507", selected?.id)
    }

    @Test
    fun `i consigli mostrano la meta con i motivi e si possono aprire`() {
        var chosen: DestinationSuggestion? = null
        composeRule.setContent {
            PartiMoTheme {
                SearchScreen(
                    state = PreviewData.searchIdeasState(),
                    query = "",
                    actions = SearchActions(onSuggestionSelected = { chosen = it }),
                )
            }
        }

        composeRule.onNodeWithTag(SEARCH_LIST_TAG).performScrollToNode(hasText("Tokyo"))
        composeRule.onNodeWithText(text(R.string.theme_foliage), substring = true).assertExists()
        composeRule.onNodeWithText("Tokyo").performClick()
        composeRule.waitForIdle()

        assertEquals("Tokyo", chosen?.destination?.city?.name)
    }

    @Test
    fun `i viaggi salvati si riaprono o si tolgono dalla schermata iniziale`() {
        val opened = mutableListOf<String>()
        val removed = mutableListOf<String>()
        composeRule.setContent {
            PartiMoTheme {
                SearchScreen(
                    PreviewData.searchIdleState().copy(savedTrips = PreviewData.savedTrips),
                    query = "",
                    actions = SearchActions(onSavedTripSelected = { opened += it.id }, onRemoveSavedTrip = { removed += it.id }),
                )
            }
        }

        composeRule.onNodeWithText(text(R.string.saved_trips_title)).assertExists()
        composeRule.onNodeWithText("10–14 dic").assertExists()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.saved_trip_in_days, 71)).assertExists()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.saved_trip_favorites, 3)).assertExists()
        composeRule.onNodeWithText("🇦🇹 Vienna").performClick()
        val removeLisbon = composeRule.activity.getString(R.string.saved_trip_remove, "Lisbona")
        composeRule.onNodeWithTag(SAVED_TRIPS_TAG).performScrollToNode(hasContentDescription(removeLisbon))
        composeRule.onNodeWithContentDescription(removeLisbon).performClick()

        assertEquals(listOf("AT:Vienna:2026-12"), opened)
        assertEquals(listOf("PT:Lisbona:2027-03"), removed)
    }

    @Test
    fun `sotto i mesi ci sono le celle andata e ritorno che aprono il calendario`() {
        composeRule.setContent {
            PartiMoTheme { SearchScreen(PreviewData.searchIdleState(), query = "", actions = SearchActions()) }
        }

        composeRule.onNodeWithTag(SEARCH_LIST_TAG).performScrollToNode(hasTestTag(RETURN_DATE_TAG))
        composeRule.onNodeWithTag(DEPARTURE_DATE_TAG).assert(hasText(text(R.string.search_departure_date)) and hasText(text(R.string.search_pick_date)))
        composeRule.onNodeWithTag(RETURN_DATE_TAG).assert(hasText(text(R.string.search_return_date)) and hasText(text(R.string.search_pick_date)))

        composeRule.onNodeWithTag(RETURN_DATE_TAG).performClick()
        composeRule.onNodeWithText(text(R.string.search_dates_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.search_dates_confirm)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.search_dates_cancel)).performClick()
        composeRule.onNodeWithText(text(R.string.search_dates_title)).assertDoesNotExist()
    }

    @Test
    fun `con le date scelte le celle le mostrano e il calendario le conferma`() {
        val chosen = mutableListOf<Pair<LocalDate, LocalDate>>()
        composeRule.setContent {
            PartiMoTheme {
                SearchScreen(PreviewData.searchDatesState(), query = "", actions = SearchActions(onDatesSelected = { from, to -> chosen += from to to }))
            }
        }

        composeRule.onNodeWithTag(SEARCH_LIST_TAG).performScrollToNode(hasTestTag(RETURN_DATE_TAG))
        composeRule.onNodeWithTag(DEPARTURE_DATE_TAG).assert(hasText("ven 11 dic"))
        composeRule.onNodeWithTag(RETURN_DATE_TAG).assert(hasText("dom 13 dic"))
        composeRule.onNodeWithTag(DEPARTURE_DATE_TAG).performClick()
        composeRule.onNodeWithText(text(R.string.search_dates_confirm)).performClick()

        assertEquals(listOf(LocalDate.of(2026, Month.DECEMBER, 11) to LocalDate.of(2026, Month.DECEMBER, 13)), chosen)
    }

    @Test
    fun `chi parte si sceglie con adulti, bambini e la loro età`() {
        val chosen = mutableListOf<Travellers>()
        composeRule.setContent {
            PartiMoTheme { SearchScreen(PreviewData.searchIdleState(), query = "", actions = SearchActions(onTravellersSelected = { chosen += it })) }
        }

        composeRule.onNodeWithTag(SEARCH_LIST_TAG).performScrollToNode(hasTestTag(TRAVELLERS_TAG))
        composeRule.onNodeWithTag(TRAVELLERS_TAG).assert(hasText("1 adulto")).performClick()
        composeRule.onNodeWithContentDescription(text(R.string.travellers_add_adult)).performClick()
        composeRule.onNodeWithContentDescription(text(R.string.travellers_add_child)).performClick()
        composeRule.onNodeWithText("8 anni").assertExists()
        composeRule.onNodeWithContentDescription(text(R.string.travellers_younger, 1)).performClick()
        composeRule.onNodeWithText(text(R.string.search_dates_confirm)).performClick()

        assertEquals(listOf(Travellers(adults = 2, childAges = listOf(7))), chosen)
    }

    @Test
    fun `due neonati con un solo adulto non si confermano finché non c'è un altro adulto`() {
        val chosen = mutableListOf<Travellers>()
        composeRule.setContent {
            PartiMoTheme { TravellersDialog(initial = Travellers(adults = 1, childAges = listOf(1, 2)), onDismiss = {}, onConfirm = { chosen += it }) }
        }

        composeRule.onNodeWithText("2 anni").assertExists()
        composeRule.onNodeWithContentDescription(text(R.string.travellers_younger, 2)).performClick()
        composeRule.onNodeWithText(text(R.string.travellers_infants_error)).assertExists()
        composeRule.onNodeWithText(text(R.string.search_dates_confirm)).assertIsNotEnabled()

        composeRule.onNodeWithContentDescription(text(R.string.travellers_add_adult)).performClick()
        composeRule.onNodeWithText(text(R.string.travellers_infants_note)).assertExists()
        composeRule.onNodeWithText(text(R.string.search_dates_confirm)).performClick()

        assertEquals(listOf(Travellers(adults = 2, childAges = listOf(1, 1))), chosen)
    }

    @Test
    fun `ovunque mostra le mete più economiche con prezzo e date e le filtra per prezzo`() {
        var searched = 0
        val budgets = mutableListOf<Int?>()
        val chosen = mutableListOf<String>()
        composeRule.setContent {
            PartiMoTheme {
                SearchScreen(
                    PreviewData.searchAnywhereState(),
                    query = "",
                    actions = SearchActions(
                        onAnywhere = { searched++ },
                        onAnywhereMaxPrice = { budgets += it },
                        onCheapDestinationSelected = { chosen += it.city.name },
                    ),
                )
            }
        }

        composeRule.onNodeWithTag(SEARCH_LIST_TAG).performScrollToNode(hasText("🌍  " + text(R.string.anywhere_button, "Milano")))
        composeRule.onNodeWithText("🌍  " + text(R.string.anywhere_button, "Milano")).performClick()
        composeRule.onNodeWithTag(SEARCH_LIST_TAG).performScrollToNode(hasText("Palermo"))
        composeRule.onNodeWithText("Palermo").assertExists()
        composeRule.onNodeWithText(text(R.string.anywhere_max_price, Formatters.money(Money.of(50, "EUR")))).performClick()
        composeRule.onNodeWithText("Palermo").performClick()

        assertEquals(1, searched)
        assertEquals(listOf<Int?>(50), budgets)
        assertEquals(listOf("Palermo"), chosen)
    }

    @Test
    fun `salva lo screenshot di ovunque`() {
        composeRule.setContent {
            PartiMoTheme { SearchScreen(PreviewData.searchAnywhereState(), query = "", actions = SearchActions()) }
        }
        composeRule.onNodeWithTag(SEARCH_LIST_TAG).performScrollToNode(hasText("Bucarest"))
        composeRule.waitForIdle()

        val bitmap = composeRule.activity.window.decorView.rootView.drawToBitmap()
        val screenshot = File("build/outputs/screenshots/search_anywhere.png").apply { parentFile?.mkdirs() }
        screenshot.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        assertTrue(screenshot.length() > 0, "Screenshot non generato")
    }

    @Test
    fun `salva lo screenshot della schermata con le date scelte`() {
        composeRule.setContent {
            PartiMoTheme { SearchScreen(PreviewData.searchDatesState(), query = "", actions = SearchActions()) }
        }
        composeRule.onNodeWithTag(SEARCH_LIST_TAG).performScrollToNode(hasTestTag(TRAVELLERS_TAG))
        composeRule.waitForIdle()

        val bitmap = composeRule.activity.window.decorView.rootView.drawToBitmap()
        val screenshot = File("build/outputs/screenshots/search_dates.png").apply { parentFile?.mkdirs() }
        screenshot.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }

        assertTrue(screenshot.length() > 0, "Screenshot non generato")
    }

    @Test
    fun `salva lo screenshot della schermata con i consigli`() {
        composeRule.setContent {
            PartiMoTheme { SearchScreen(PreviewData.searchIdeasState(), query = "", actions = SearchActions()) }
        }
        composeRule.waitForIdle()

        val bitmap = composeRule.activity.window.decorView.rootView.drawToBitmap()
        val screenshot = File("build/outputs/screenshots/search_ideas.png").apply { parentFile?.mkdirs() }
        screenshot.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }

        assertTrue(screenshot.length() > 0, "Screenshot non generato")
    }
}
