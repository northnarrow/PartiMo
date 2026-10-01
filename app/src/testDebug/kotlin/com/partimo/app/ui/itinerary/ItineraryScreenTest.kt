package com.partimo.app.ui.itinerary

import android.content.Intent
import android.graphics.Bitmap
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateOf
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
import com.partimo.app.testing.UiTestApplication
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.dashboard.PreviewData
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.common.DataError
import com.partimo.domain.model.plan.DayPlan
import com.partimo.domain.model.plan.TripInterest
import com.partimo.domain.model.plan.TripPace
import com.partimo.domain.model.poi.PointOfInterest
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Test UI dell'itinerario proposto dall'IA, sulla JVM con Robolectric. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = UiTestApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class ItineraryScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(@StringRes id: Int): String = composeRule.activity.getString(id)

    /** Mostra l'itinerario: le schede cambiano davvero, come nell'app. */
    private fun showItinerary(initial: ItineraryUiState = PreviewData.itineraryState(), actions: ItineraryActions = ItineraryActions()) {
        val state = mutableStateOf(initial)
        composeRule.setContent {
            PartiMoTheme {
                ItineraryScreen(
                    state = state.value,
                    actions = actions.copy(
                        onTabSelected = { tab ->
                            state.value = state.value.copy(selectedTab = tab)
                            actions.onTabSelected(tab)
                        },
                    ),
                )
            }
        }
    }

    private fun saveScreenshot(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.activity.window.decorView.rootView.drawToBitmap()
        val screenshot = File("build/outputs/screenshots/$name.png").apply { parentFile?.mkdirs() }
        screenshot.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        assertTrue(screenshot.length() > 0, "Screenshot non generato")
    }

    @Test
    fun `le tappe aprono la scheda del luogo o la ricerca su Google Maps`() {
        val openedPlaces = mutableListOf<PointOfInterest>()
        val openedLinks = mutableListOf<String>()
        showItinerary(actions = ItineraryActions(onOpenPlace = { openedPlaces += it }, onOpenLink = { openedLinks += it }))

        composeRule.onNodeWithText("Giorno 1 · gio 10 dic").assertExists()
        composeRule.onNodeWithText("Arrivo e prime luci nel centro").assertExists()
        composeRule.onNodeWithText("💡 Compra il biglietto dei mezzi da 72 ore: conviene già dal primo giorno.").assertExists()

        composeRule.onNodeWithText("Duomo di Vienna").performClick()
        composeRule.onNodeWithText("Weihnachtsmarkt am Spittelberg").performClick()
        composeRule.onNodeWithText("Café Central").performClick()

        assertEquals(listOf("wikipedia:it:83456", "wikidata:spittelberg"), openedPlaces.map { it.id })
        assertEquals(listOf("https://www.google.com/maps/search/?api=1&query=Caf%C3%A9%20Central%2C%20Vienna"), openedLinks)
        composeRule.onNodeWithText("✨ " + text(R.string.itinerary_suggested_by_ai)).assertExists()
    }

    @Test
    fun `ogni giornata ha il giro a piedi su Google Maps e l'aggiunta al calendario`() {
        val openedLinks = mutableListOf<String>()
        val calendarDays = mutableListOf<Pair<DayPlan, Int>>()
        showItinerary(actions = ItineraryActions(onOpenLink = { openedLinks += it }, onAddToCalendar = { day, number -> calendarDays += day to number }))

        composeRule.onAllNodesWithText("🚶 " + text(R.string.itinerary_walk))[0].performClick()
        composeRule.onAllNodesWithText("📅 " + text(R.string.itinerary_add_to_calendar))[0].performClick()

        assertEquals(
            "https://www.google.com/maps/dir/?api=1&origin=48.208500%2C16.373100" +
                "&destination=48.203000%2C16.354000&waypoints=Caf%C3%A9%20Central%2C%20Vienna&travelmode=walking",
            openedLinks.single(),
        )
        assertEquals(1, calendarDays.single().second)
        assertEquals(LocalDate.of(2026, Month.DECEMBER, 10), calendarDays.single().first.date)
    }

    @Test
    fun `nella valigia si spuntano le voci e nei consigli si può chiedere a PartiMo`() {
        val toggled = mutableListOf<String>()
        var asked = false
        showItinerary(actions = ItineraryActions(onPackedToggled = { toggled += it }, onAskAssistant = { asked = true }))

        composeRule.onNodeWithText("🧳 " + text(R.string.itinerary_tab_packing)).performClick()
        composeRule.onNodeWithText("1 di 5 già in valigia").assertExists()
        composeRule.onNodeWithText("Contanti per i mercatini").performClick()
        assertEquals(listOf("Documenti e soldi › Contanti per i mercatini"), toggled)

        composeRule.onNodeWithText("💡 " + text(R.string.itinerary_tab_tips)).performClick()
        composeRule.onNodeWithText("Molte bancarelle dei mercatini accettano solo contanti.").assertExists()
        composeRule.onNodeWithTag(ITINERARY_LIST_TAG).performScrollToNode(hasText("💬 " + text(R.string.itinerary_ask)))
        composeRule.onNodeWithText("💬 " + text(R.string.itinerary_ask)).performClick()
        assertTrue(asked)
    }

    @Test
    fun `ritmo e interessi si cambiano, poi si aggiorna l'itinerario`() {
        val paces = mutableListOf<TripPace>()
        val interests = mutableListOf<TripInterest>()
        var applied = false
        val changed = PreviewData.itineraryState().let { it.copy(preferences = it.preferences.copy(pace = TripPace.RELAXED)) }
        showItinerary(
            initial = changed,
            actions = ItineraryActions(onPaceSelected = { paces += it }, onInterestToggled = { interests += it }, onApplyPreferences = { applied = true }),
        )

        composeRule.onNodeWithText(text(R.string.pace_intense)).performClick()
        composeRule.onNodeWithTag(ITINERARY_INTERESTS_TAG).performScrollToNode(hasText("🌳 " + text(R.string.interest_nature)))
        composeRule.onNodeWithText("🌳 " + text(R.string.interest_nature)).performClick()
        composeRule.onNodeWithText(text(R.string.itinerary_apply_preferences)).performClick()

        assertEquals(listOf(TripPace.INTENSE), paces)
        assertEquals(listOf(TripInterest.NATURE), interests)
        assertTrue(applied)
    }

    @Test
    fun `durante la preparazione e dopo un errore si può rigenerare o riprovare`() {
        var retried = false
        showItinerary(
            initial = PreviewData.itineraryState().copy(plan = UiState.Error(DataError.RateLimited)),
            actions = ItineraryActions(onRetry = { retried = true }),
        )

        composeRule.onNodeWithText(text(R.string.error_rate_limited), substring = true).assertExists()
        composeRule.onNodeWithText(text(R.string.action_retry)).performClick()
        assertTrue(retried)
    }

    @Test
    fun `senza chiave spiega come attivare l'assistente`() {
        showItinerary(initial = PreviewData.itineraryState().copy(isAvailable = false, plan = UiState.Error(DataError.Unauthorized)))

        composeRule.onNodeWithText(text(R.string.assistant_unavailable)).assertExists()
        composeRule.onNodeWithContentDescription(text(R.string.itinerary_regenerate)).assertDoesNotExist()
    }

    @Test
    fun `calendario e condivisione portano il programma fuori dall'app`() {
        val context = composeRule.activity
        val plan = PreviewData.tripPlan
        val day = plan.days.first()

        val calendar = ItineraryIntents.calendar(context, day, 1, "Vienna")
        assertEquals(Intent.ACTION_INSERT, calendar.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, calendar.data)
        assertEquals("Vienna · Giorno 1: Arrivo e prime luci nel centro", calendar.getStringExtra(CalendarContract.Events.TITLE))
        assertTrue(calendar.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false))
        assertEquals(
            day.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            calendar.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0),
        )
        assertContains(calendar.getStringExtra(CalendarContract.Events.DESCRIPTION)!!, "• Pomeriggio · Duomo di Vienna: Ammira la cattedrale")

        val text = ItineraryIntents.planText(context, plan, "Vienna", day.date, LocalDate.of(2026, Month.DECEMBER, 14))
        assertTrue(text.startsWith("✨ Itinerario a Vienna · 10–14 dic"))
        assertContains(text, "Giorno 2 · ven 11 dic — Arte imperiale")
        assertContains(text, "Abbigliamento: Cappotto caldo, Sciarpa, guanti e berretto, Scarpe impermeabili")
        assertContains(text, "• Molte bancarelle dei mercatini accettano solo contanti.")
        assertTrue(text.endsWith(text(R.string.itinerary_share_footer)))
    }

    @Test
    fun `salva gli screenshot dell'itinerario`() {
        showItinerary()
        saveScreenshot("itinerary")
        composeRule.onNodeWithText("🧳 " + text(R.string.itinerary_tab_packing)).performClick()
        saveScreenshot("itinerary_packing")
    }
}
