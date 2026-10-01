package com.partimo.app.ui.departure

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.drawToBitmap
import com.partimo.app.R
import com.partimo.app.testing.UiTestApplication
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.dashboard.PreviewData
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.model.place.AirportOption
import com.partimo.domain.model.place.CityPlace
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Test UI della scelta del punto di partenza, eseguiti sulla JVM con Robolectric. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = UiTestApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class DeparturePickerScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(@StringRes id: Int): String = composeRule.activity.getString(id)

    @Test
    fun `mostra gli aeroporti della città con il consigliato e si può sceglierne un altro`() {
        var chosen: AirportOption? = null
        composeRule.setContent {
            PartiMoTheme {
                DeparturePickerScreen(
                    state = PreviewData.departureAirportsState(),
                    query = "Milano",
                    actions = DeparturePickerActions(onAirportSelected = { chosen = it }),
                )
            }
        }

        composeRule.onNodeWithText(text(R.string.departure_title)).assertExists()
        composeRule.onNodeWithText("Aeroporti vicino a Milano", substring = true).assertExists()
        composeRule.onNodeWithText("Milan Malpensa").assertExists()
        composeRule.onNodeWithText(text(R.string.departure_recommended), substring = true).assertExists()
        composeRule.onNodeWithText("Milano Linate").performClick()

        assertEquals("LIN", chosen?.airport?.iata)
    }

    @Test
    fun `nella ricerca toccare una città ne mostra gli aeroporti`() {
        var selected: CityPlace? = null
        val milan = PreviewData.departureAirportsState().selectedCity!!
        composeRule.setContent {
            PartiMoTheme {
                DeparturePickerScreen(
                    state = DeparturePickerUiState(results = UiState.Success(listOf(milan))),
                    query = "Mil",
                    actions = DeparturePickerActions(onCitySelected = { selected = it }),
                )
            }
        }

        composeRule.onNodeWithText(text(R.string.departure_placeholder)).assertDoesNotExist() // il campo contiene già "Mil"
        composeRule.onNodeWithText("Milano").performClick()

        assertEquals(milan, selected)
    }

    @Test
    fun `salva lo screenshot della scelta dell'aeroporto`() {
        composeRule.setContent {
            PartiMoTheme {
                DeparturePickerScreen(PreviewData.departureAirportsState(), query = "Milano", actions = DeparturePickerActions())
            }
        }
        composeRule.waitForIdle()

        val bitmap = composeRule.activity.window.decorView.rootView.drawToBitmap()
        val screenshot = File("build/outputs/screenshots/departure_picker.png").apply { parentFile?.mkdirs() }
        screenshot.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }

        assertTrue(screenshot.length() > 0, "Screenshot non generato")
    }
}
