package com.partimo.app.ui.search

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
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
import com.partimo.app.ui.common.PERIOD_CHIPS_TAG
import com.partimo.app.ui.dashboard.PreviewData
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.place.CityPlace
import com.partimo.domain.model.place.DestinationSuggestion
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

/** Test UI della schermata iniziale "Dove vuoi andare?", eseguiti sulla JVM con Robolectric. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h1500dp-xxhdpi")
class SearchScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(@StringRes id: Int): String = composeRule.activity.getString(id)

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
