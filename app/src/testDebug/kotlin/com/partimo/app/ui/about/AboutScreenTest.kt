package com.partimo.app.ui.about

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.view.drawToBitmap
import com.partimo.app.R
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

/** Test UI di fonti, licenze e privacy, sulla JVM con Robolectric. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class AboutScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(@StringRes id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    @Test
    fun `mostra versione, privacy e fonti con le loro licenze`() {
        val opened = mutableListOf<String>()
        composeRule.setContent { PartiMoTheme { AboutScreen(version = "1.0.0", onBack = {}, onOpenLink = { opened += it }) } }

        composeRule.onNodeWithText(text(R.string.about_version, "1.0.0")).assertExists()
        composeRule.onNodeWithText("• " + text(R.string.about_privacy_location)).assertExists()
        composeRule.onNodeWithTag(ABOUT_LIST_TAG).performScrollToNode(hasText("OpenStreetMap ↗"))
        composeRule.onNodeWithText("OpenStreetMap ↗").performClick()
        composeRule.onNodeWithTag(ABOUT_LIST_TAG).performScrollToNode(hasText("OpenFreeMap ↗"))
        composeRule.onNodeWithText("OpenFreeMap ↗").performClick()

        assertEquals(listOf("https://www.openstreetmap.org/copyright", "https://openfreemap.org"), opened)
    }

    @Test
    fun `ogni fonte ha un collegamento sicuro`() {
        (DataSources + MapSources + ConnectedServices).forEach { entry ->
            assertTrue(entry.url.startsWith("https://"), entry.name)
        }
        assertEquals(16, (DataSources + MapSources + ConnectedServices).map { it.url }.distinct().size)
    }

    @Test
    fun `salva lo screenshot delle fonti`() {
        composeRule.setContent { PartiMoTheme { AboutScreen(version = "1.0.0", onBack = {}, onOpenLink = {}) } }
        composeRule.waitForIdle()

        val bitmap = composeRule.activity.window.decorView.rootView.drawToBitmap()
        val screenshot = File("build/outputs/screenshots/about.png").apply { parentFile?.mkdirs() }
        screenshot.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        assertTrue(screenshot.length() > 0, "Screenshot non generato")
    }
}
