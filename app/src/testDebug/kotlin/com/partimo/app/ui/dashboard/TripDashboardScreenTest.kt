package com.partimo.app.ui.dashboard

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.MutableState
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
import com.partimo.app.ui.common.PERIOD_CHIPS_TAG
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.model.Money
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.deal.PriceChange
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

    private fun text(@StringRes id: Int): String = composeRule.activity.getString(id)

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
    fun `aggiorna e la campanella invocano le rispettive azioni`() {
        var refreshed = false
        var alertToggled = false
        showDashboard(PreviewData.loadedState(), DashboardActions(onRefresh = { refreshed = true }, onToggleAlert = { alertToggled = true }))

        composeRule.onNodeWithText(text(R.string.action_refresh)).performClick()
        composeRule.onNodeWithContentDescription(text(R.string.action_alert_disable)).performClick()

        assertTrue(refreshed)
        assertTrue(alertToggled)
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
    fun `salva gli screenshot della dashboard`() {
        val state = showDashboard(PreviewData.loadedState())
        saveScreenshot("trip_dashboard.png")

        state.value = state.value.copy(selectedSection = DashboardSection.HIGHLIGHTS)
        saveScreenshot("trip_dashboard_explore.png")
    }
}
