package com.partimo.app.ui.place

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.view.drawToBitmap
import com.partimo.app.R
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.dashboard.PreviewData
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.common.DataError
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.poi.PoiCategory
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Test UI della scheda di un luogo, eseguiti sulla JVM con Robolectric. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class PlaceDetailScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(@StringRes id: Int): String = composeRule.activity.getString(id)

    private fun showPlace(state: PlaceDetailUiState, actions: PlaceDetailActions = PlaceDetailActions()) {
        composeRule.setContent { PartiMoTheme { PlaceDetailScreen(state = state, actions = actions) } }
    }

    @Test
    fun `mostra descrizione e storia e il pulsante naviga apre le indicazioni`() {
        var navigations = 0
        var openedLink: String? = null
        showPlace(PreviewData.placeDetailState(), PlaceDetailActions(onNavigate = { navigations++ }, onOpenLink = { openedLink = it }))

        composeRule.onNodeWithText(text(R.string.place_description)).assertExists()
        composeRule.onNodeWithText("Il duomo di Santo Stefano è la cattedrale di Vienna", substring = true).assertExists()
        composeRule.onNodeWithTag(PLACE_DETAIL_LIST_TAG).performScrollToNode(hasText(text(R.string.place_history)))
        composeRule.onNodeWithText("Il gotico").assertExists()
        composeRule.onNodeWithText("Nel 1359 il duca Rodolfo IV", substring = true).assertExists()

        composeRule.onNodeWithTag(PLACE_DETAIL_LIST_TAG).performScrollToNode(hasText(text(R.string.place_read_more)))
        composeRule.onNodeWithText(text(R.string.place_read_more)).performClick()
        composeRule.onNodeWithText("Foto: Bwag · CC BY-SA 4.0", substring = true).assertExists()

        // "Naviga" resta sempre visibile in basso, qualunque sia lo scroll.
        composeRule.onNodeWithContentDescription("Naviga con Google Maps fino a Duomo di Vienna").performClick()

        assertEquals(1, navigations)
        assertEquals("https://it.wikipedia.org/wiki/Duomo_di_Vienna", openedLink)
    }

    @Test
    fun `musei, monumenti e chiese hanno i biglietti su Tiqets, i parchi no`() {
        var openedLink: String? = null
        var state by mutableStateOf(PreviewData.placeDetailState())
        composeRule.setContent { PartiMoTheme { PlaceDetailScreen(state = state, actions = PlaceDetailActions(onOpenLink = { openedLink = it })) } }

        composeRule.onNodeWithText("🎟️ " + text(R.string.place_tickets)).performClick()
        assertEquals("https://www.tiqets.com/it/search?q=Duomo%20di%20Vienna", openedLink)

        state = state.copy(poi = state.poi.copy(category = PoiCategory.PARK))
        composeRule.onNodeWithText("🎟️ " + text(R.string.place_tickets)).assertDoesNotExist()
    }

    @Test
    fun `se la storia non si carica si può riprovare e navigare comunque`() {
        var retried = false
        var navigated = false
        val state = PreviewData.placeDetailState().copy(details = UiState.Error(DataError.NoConnection))
        showPlace(state, PlaceDetailActions(onRetry = { retried = true }, onNavigate = { navigated = true }))

        composeRule.onNodeWithText(text(R.string.error_no_connection)).assertExists()
        composeRule.onNodeWithText("Cattedrale cattolica della città austriaca di Vienna").assertExists()
        composeRule.onNodeWithText(text(R.string.action_retry)).performClick()
        composeRule.onNodeWithText(text(R.string.place_navigate)).performClick()

        assertTrue(retried)
        assertTrue(navigated)
    }

    @Test
    fun `apre google maps con il percorso fino al luogo`() {
        val opened = ExternalLinks.openNavigation(composeRule.activity, GeoPoint(48.2085, 16.3731))

        val intent = shadowOf(composeRule.activity).nextStartedActivity
        assertTrue(opened)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(GOOGLE_MAPS_PACKAGE, intent.`package`)
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=48.208500%2C16.373100", intent.dataString)
    }

    @Test
    fun `i siti si aprono nel browser interno con la barra nei colori dell'app`() {
        val url = "https://www.skyscanner.it/trasporti/voli/mxp/vie/261210/261214/?adultsv2=1"

        val opened = ExternalLinks.openLink(composeRule.activity, url, toolbarColor = 0xFF123456.toInt())

        val intent = shadowOf(composeRule.activity).nextStartedActivity
        assertTrue(opened)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(url, intent.dataString)
        assertNull(intent.`package`, "Nessuna app imposta: si usa la scheda del browser predefinito")
        assertTrue(intent.hasExtra(CustomTabsIntent.EXTRA_SESSION), "Deve aprirsi come Custom Tab dentro PartiMo")
        assertEquals(0xFF123456.toInt(), intent.getIntExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, 0))
        assertEquals(
            CustomTabsIntent.SHOW_PAGE_TITLE,
            intent.getIntExtra(CustomTabsIntent.EXTRA_TITLE_VISIBILITY_STATE, CustomTabsIntent.NO_TITLE),
        )
    }

    @Test
    fun `salva lo screenshot della scheda`() {
        showPlace(PreviewData.placeDetailState())
        composeRule.waitForIdle()

        val bitmap = composeRule.activity.window.decorView.rootView.drawToBitmap()
        val screenshot = File("build/outputs/screenshots/place_detail.png").apply { parentFile?.mkdirs() }
        screenshot.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        assertTrue(screenshot.length() > 0, "Screenshot non generato")
    }
}
