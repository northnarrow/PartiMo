package com.partimo.app.ui.guide

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.view.drawToBitmap
import com.partimo.app.R
import com.partimo.app.ui.common.Formatters
import com.partimo.app.ui.dashboard.PreviewData
import com.partimo.app.ui.theme.PartiMoTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Test UI della guida del viaggio, sulla JVM con Robolectric. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class GuideScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(@StringRes id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    private fun showGuide(initial: GuideUiState, actions: GuideActions = GuideActions()): MutableState<GuideUiState> {
        val state = mutableStateOf(initial)
        composeRule.setContent {
            PartiMoTheme {
                GuideScreen(
                    state = state.value,
                    actions = actions.copy(
                        onSectionToggled = { title ->
                            state.value = state.value.copy(
                                expandedSections = state.value.expandedSections.let { if (title in it) it - title else it + title },
                            )
                            actions.onSectionToggled(title)
                        },
                    ),
                )
            }
        }
        return state
    }

    private fun scrollTo(text: String) {
        composeRule.onNodeWithTag(GUIDE_LIST_TAG).performScrollToNode(hasText(text, substring = true))
    }

    private fun saveScreenshot(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.activity.window.decorView.rootView.drawToBitmap()
        val screenshot = File("build/outputs/screenshots/$name.png").apply { parentFile?.mkdirs() }
        screenshot.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        assertTrue(screenshot.length() > 0, "Screenshot non generato")
    }

    @Test
    fun `clima, luce del giorno, informazioni pratiche ed emergenze dell'Austria`() {
        val dialed = mutableListOf<String>()
        val opened = mutableListOf<String>()
        showGuide(PreviewData.guideState(), GuideActions(onDial = { dialed += it }, onOpenLink = { opened += it }))

        composeRule.onNodeWithText(text(R.string.guide_weather_climate, "5°", "0°", 3)).assertExists()
        composeRule.onNodeWithText("🌅 Alba 07:34", substring = true).assertExists()
        composeRule.onNodeWithText("📸 Ora d'oro dalle 15:11", substring = true).assertExists()
        composeRule.onNodeWithText("tedesco").assertExists()
        composeRule.onNodeWithText("+43").assertExists()
        composeRule.onNodeWithText(text(R.string.guide_driving_right)).assertExists()
        composeRule.onNodeWithText("✅ " + text(R.string.guide_plugs_ok)).assertExists()

        scrollTo(text(R.string.guide_emergency_title))
        composeRule.onNodeWithContentDescription(text(R.string.guide_emergency_call, "112", text(R.string.guide_emergency_general))).performClick()
        composeRule.onNodeWithContentDescription(text(R.string.guide_emergency_call, "144", text(R.string.guide_emergency_ambulance))).performClick()
        assertEquals(listOf("112", "144"), dialed)

        scrollTo(text(R.string.guide_currency_euro))
        scrollTo(text(R.string.guide_travel_advice))
        composeRule.onNodeWithText("🛡️ " + text(R.string.guide_travel_advice) + " ↗").performClick()
        assertEquals(listOf("https://www.viaggiaresicuri.it/find-country/country/AUT"), opened)
    }

    @Test
    fun `i capitoli di Wikivoyage si aprono e portano alla guida completa`() {
        val opened = mutableListOf<String>()
        showGuide(PreviewData.guideState().copy(expandedSections = emptySet()), GuideActions(onOpenLink = { opened += it }))

        scrollTo("🚇 Come spostarsi")
        composeRule.onNodeWithText("🚇 Come spostarsi").performClick()
        composeRule.onNodeWithText("Per il servizio taxi chiamare il numero 40100.").assertExists()

        scrollTo(text(R.string.guide_wikivoyage_read))
        composeRule.onNodeWithText(text(R.string.guide_wikivoyage_read) + " ↗").performClick()
        assertEquals(listOf("https://it.wikivoyage.org/wiki/Vienna"), opened)
        composeRule.onNodeWithText(text(R.string.guide_wikivoyage_license)).assertExists()
    }

    @Test
    fun `a Praga previsioni giorno per giorno e convertitore da euro a corone`() {
        var swapped = false
        showGuide(PreviewData.pragueGuideState(), GuideActions(onSwapCurrencies = { swapped = true }))

        composeRule.onNodeWithText("💧 75%").assertExists()
        scrollTo(text(R.string.guide_currency_title))
        // Il formato italiano separa importo e valuta con uno spazio unificatore.
        composeRule.onNodeWithText("1 € = " + Formatters.currencyAmount(BigDecimal("24.431512"), "CZK")).assertExists()
        assertEquals("2.443\u00a0CZK", Formatters.currencyAmount(BigDecimal("2443.1512"), "CZK"))
        composeRule.onNodeWithText("= 2.443\u00a0CZK").assertExists()
        composeRule.onNodeWithContentDescription(text(R.string.guide_currency_swap)).performClick()
        assertTrue(swapped)
    }

    @Test
    fun `salva gli screenshot della guida`() {
        val state = showGuide(PreviewData.guideState())
        saveScreenshot("guide")
        state.value = PreviewData.pragueGuideState()
        scrollTo(text(R.string.guide_wikivoyage_empty, "Praga"))
        saveScreenshot("guide_currency")
    }
}
