package com.partimo.app.ui.translator

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.core.view.drawToBitmap
import com.partimo.app.R
import com.partimo.app.testing.UiTestApplication
import com.partimo.app.ui.dashboard.PreviewData
import com.partimo.app.ui.theme.PartiMoTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Test UI del traduttore, sulla JVM con Robolectric. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = UiTestApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class TranslatorScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(@StringRes id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    @Test
    fun `al primo uso propone il pacchetto lingua da scaricare`() {
        var downloads = 0
        val inputs = mutableListOf<String>()
        composeRule.setContent {
            PartiMoTheme {
                TranslatorScreen(
                    PreviewData.translatorMissingPackState(),
                    TranslatorActions(onDownloadPacks = { downloads++ }, onInputChanged = { inputs += it }),
                )
            }
        }

        composeRule.onNodeWithText("Tedesco ▾").assertExists()
        composeRule.onNodeWithText("📦 " + text(R.string.translator_pack_missing, "tedesco, italiano")).assertExists()
        composeRule.onNodeWithText(text(R.string.translator_download)).performClick()
        composeRule.onNodeWithText(text(R.string.translator_input_label, "italiano")).performTextInput("Ciao")
        composeRule.onNodeWithText(text(R.string.translator_translate)).assertIsNotEnabled()

        assertEquals(1, downloads)
        assertEquals(listOf("Ciao"), inputs)
    }

    @Test
    fun `la traduzione si ascolta, si copia, si mostra in grande e si apre in Google Traduttore`() {
        val spoken = mutableListOf<Pair<String, String>>()
        val copied = mutableListOf<String>()
        val google = mutableListOf<Triple<String, String, String>>()
        var swaps = 0
        composeRule.setContent {
            PartiMoTheme {
                TranslatorScreen(
                    PreviewData.translatorState(),
                    TranslatorActions(
                        onSpeak = { text, language -> spoken += text to language },
                        onCopy = { copied += it },
                        onOpenGoogleTranslate = { text, from, to -> google += Triple(text, from, to) },
                        onSwap = { swaps++ },
                    ),
                )
            }
        }
        val translation = "Wo ist die Straßenbahnhaltestelle nach Schönbrunn?"

        composeRule.onNodeWithText(text(R.string.translator_ready)).assertExists()
        composeRule.onNodeWithText(text(R.string.translator_listen)).performClick()
        composeRule.onNodeWithText(text(R.string.translator_copy)).performClick()
        composeRule.onNodeWithText(text(R.string.translator_google) + " ↗").performClick()
        composeRule.onNodeWithContentDescription(text(R.string.translator_swap)).performClick()
        composeRule.onNodeWithText(text(R.string.translator_show_big)).performClick()
        composeRule.onNodeWithText(text(R.string.translator_close)).performClick()

        assertEquals(listOf(translation to "de"), spoken)
        assertEquals(listOf(translation), copied)
        assertEquals(listOf(Triple("Dov'è la fermata del tram per lo Schönbrunn?", "it", "de")), google)
        assertEquals(1, swaps)
    }

    @Test
    fun `il frasario si apre per categoria, si traduce e si ascolta`() {
        val requested = mutableListOf<List<String>>()
        val spoken = mutableListOf<Pair<String, String>>()
        var state by mutableStateOf(PreviewData.translatorState())
        composeRule.setContent {
            PartiMoTheme {
                TranslatorScreen(state, TranslatorActions(onTranslatePhrases = { requested += it }, onSpeak = { text, language -> spoken += text to language }))
            }
        }

        val basics = "👋 " + text(R.string.phrases_basics)
        composeRule.onNodeWithTag(TRANSLATOR_LIST_TAG).performScrollToNode(hasText(basics))
        composeRule.onNodeWithText(basics).performClick()
        composeRule.onNodeWithTag(TRANSLATOR_LIST_TAG).performScrollToNode(hasText("Vielen Dank"))
        composeRule.onNodeWithContentDescription(text(R.string.translator_listen_description, "Vielen Dank")).performClick()

        assertEquals("Buongiorno", requested.single().first())
        assertEquals(listOf("Vielen Dank" to "de"), spoken)
        state = state.copy(phraseTranslations = state.phraseTranslations + (TranslatorUiState.phraseKey("de", "Mi scusi") to "Entschuldigung"))
        composeRule.onNodeWithTag(TRANSLATOR_LIST_TAG).performScrollToNode(hasText("Entschuldigung"))
    }

    @Test
    fun `salva gli screenshot del traduttore`() {
        var state by mutableStateOf(PreviewData.translatorState())
        composeRule.setContent { PartiMoTheme { TranslatorScreen(state, TranslatorActions()) } }
        composeRule.waitForIdle()
        saveScreenshot("translator.png")

        state = PreviewData.translatorMissingPackState()
        composeRule.waitForIdle()
        saveScreenshot("translator_pack.png")
    }

    private fun saveScreenshot(name: String) {
        val bitmap = composeRule.activity.window.decorView.rootView.drawToBitmap()
        val screenshot = File("build/outputs/screenshots/$name").apply { parentFile?.mkdirs() }
        screenshot.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        assertTrue(screenshot.length() > 0, "Screenshot non generato")
    }
}
