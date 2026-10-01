package com.partimo.app.ui.about

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.view.drawToBitmap
import com.partimo.app.R
import com.partimo.app.testing.UiTestApplication
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.model.backup.UserDataSummary
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
@Config(sdk = [34], application = UiTestApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
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
    fun `i tuoi dati - riepilogo, esportazione e importazione`() {
        var exports = 0
        var imports = 0
        composeRule.setContent {
            PartiMoTheme {
                AboutScreen(
                    version = "1.3.0",
                    onBack = {},
                    onOpenLink = {},
                    backup = PreviewBackup,
                    backupActions = BackupActions(onExport = { exports++ }, onImport = { imports++ }),
                )
            }
        }

        composeRule.onNodeWithText(text(R.string.backup_title)).assertExists()
        composeRule.onNodeWithText(
            text(R.string.backup_summary, "3 viaggi salvati · 8 preferiti · 1 avviso · 12 spese · 9 voci della valigia · " + text(R.string.backup_departure)),
        ).assertExists()
        composeRule.onNodeWithText("📤 " + text(R.string.backup_export)).performClick()
        composeRule.onNodeWithText("📥 " + text(R.string.backup_import)).performClick()

        assertEquals(1, exports)
        assertEquals(1, imports)
    }

    @Test
    fun `l'esito dell'importazione compare una volta`() {
        var shown = 0
        val imported = PreviewBackup.copy(message = BackupMessage.Imported(UserDataSummary(trips = 2, favorites = 1)))
        composeRule.setContent {
            PartiMoTheme {
                AboutScreen(version = "1.3.0", onBack = {}, onOpenLink = {}, backup = imported, backupActions = BackupActions(onMessageShown = { shown++ }))
            }
        }

        composeRule.onNodeWithText(text(R.string.backup_imported, "2 viaggi salvati · 1 preferito")).assertExists()
        composeRule.onNodeWithContentDescription("Dismiss").performClick()
        composeRule.waitForIdle()
        assertEquals(1, shown)
    }

    @Test
    fun `salva lo screenshot delle fonti`() {
        composeRule.setContent { PartiMoTheme { AboutScreen(version = "1.3.0", onBack = {}, onOpenLink = {}, backup = PreviewBackup) } }
        composeRule.waitForIdle()

        val bitmap = composeRule.activity.window.decorView.rootView.drawToBitmap()
        val screenshot = File("build/outputs/screenshots/about.png").apply { parentFile?.mkdirs() }
        screenshot.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        assertTrue(screenshot.length() > 0, "Screenshot non generato")
    }
}
