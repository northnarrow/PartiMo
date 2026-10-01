package com.partimo.app.ui.chat

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.view.drawToBitmap
import com.partimo.app.R
import com.partimo.app.ui.dashboard.PreviewData
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.common.DataError
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Test UI di "Chiedi a PartiMo", sulla JVM con Robolectric. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class ChatScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(@StringRes id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    /** Mostra la conversazione con uno stato modificabile dal test. */
    private fun showChat(initial: ChatUiState, actions: ChatActions = ChatActions()): MutableState<ChatUiState> {
        val state = mutableStateOf(initial)
        composeRule.setContent { PartiMoTheme { ChatScreen(state = state.value, actions = actions) } }
        return state
    }

    @Test
    fun `all'inizio propone domande che si inviano con un tocco`() {
        val asked = mutableListOf<String>()
        showChat(PreviewData.chatState().copy(messages = emptyList()), ChatActions(onAsk = { asked += it }))

        composeRule.onNodeWithText(text(R.string.chat_intro, "Vienna")).assertExists()
        composeRule.onNodeWithText(text(R.string.chat_privacy)).assertExists()
        composeRule.onNodeWithText("Cosa devo assolutamente mangiare a Vienna?").performClick()

        assertEquals(listOf("Cosa devo assolutamente mangiare a Vienna?"), asked)
    }

    @Test
    fun `la domanda scritta si invia e la risposta si legge nella conversazione`() {
        var sent = false
        lateinit var state: MutableState<ChatUiState>
        state = showChat(
            PreviewData.chatState(),
            ChatActions(onInputChanged = { state.value = state.value.copy(input = it) }, onSend = { sent = true }),
        )

        composeRule.onNodeWithText("• Tafelspitz: bollito di manzo con salsa di rafano e mele, da Plachutta.", substring = true).assertExists()
        composeRule.onNodeWithContentDescription(text(R.string.chat_send)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.chat_input_hint)).performTextInput("E i dolci?")
        assertEquals("E i dolci?", state.value.input)

        composeRule.onNodeWithContentDescription(text(R.string.chat_send)).assertIsEnabled().performClick()
        assertTrue(sent)
    }

    @Test
    fun `mentre arriva la risposta lo dice, e dopo un errore si può riprovare`() {
        var retried = false
        val state = showChat(PreviewData.chatState().copy(isAnswering = true), ChatActions(onRetry = { retried = true }))
        composeRule.onNodeWithText(text(R.string.chat_thinking)).assertExists()

        state.value = state.value.copy(isAnswering = false, error = DataError.NoConnection)
        composeRule.onNodeWithText(text(R.string.chat_thinking)).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.action_retry)).performClick()
        assertTrue(retried)
    }

    @Test
    fun `salva lo screenshot della conversazione`() {
        showChat(PreviewData.chatState())
        composeRule.waitForIdle()
        val bitmap = composeRule.activity.window.decorView.rootView.drawToBitmap()
        val screenshot = File("build/outputs/screenshots/chat.png").apply { parentFile?.mkdirs() }
        screenshot.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        assertTrue(screenshot.length() > 0, "Screenshot non generato")
    }
}
