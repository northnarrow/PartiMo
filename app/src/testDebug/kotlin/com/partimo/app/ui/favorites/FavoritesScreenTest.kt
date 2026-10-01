package com.partimo.app.ui.favorites

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.drawToBitmap
import com.partimo.app.R
import com.partimo.app.ui.dashboard.PreviewData
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.model.poi.PointOfInterest
import com.partimo.domain.model.saved.Favorite
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Test UI dei preferiti di un viaggio, sulla JVM con Robolectric. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class FavoritesScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(@StringRes id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    @Test
    fun `i preferiti si aprono, si tolgono e diventano un giro a piedi`() {
        val places = mutableListOf<PointOfInterest>()
        val links = mutableListOf<String>()
        val removed = mutableListOf<Favorite>()
        composeRule.setContent {
            PartiMoTheme {
                FavoritesScreen(
                    PreviewData.favoritesState(),
                    FavoritesActions(onOpenPlace = { places += it }, onOpenLink = { links += it }, onRemove = { removed += it }),
                )
            }
        }

        composeRule.onNodeWithText("📸 " + text(R.string.favorites_places)).assertExists()
        composeRule.onNodeWithText("🍝 " + text(R.string.favorites_restaurants)).assertExists()
        composeRule.onNodeWithText("Duomo di Vienna").performClick()
        composeRule.onNodeWithText("Figlmüller").performClick()
        composeRule.onNodeWithContentDescription(text(R.string.favorite_remove, "Weihnachtsmarkt am Spittelberg")).performClick()
        composeRule.onNodeWithText("🚶 " + text(R.string.favorites_walk)).performClick()

        assertEquals(listOf("wikipedia:it:83456"), places.map { it.id })
        assertEquals("https://www.google.com/maps/search/?api=1&query=Figlm%C3%BCller%2C%20Wollzeile%205%2C%20Vienna", links.first())
        assertEquals(listOf("Weihnachtsmarkt am Spittelberg"), removed.map { it.name })
        // Dal Duomo il più vicino è Figlmüller, poi il mercatino dello Spittelberg.
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&origin=48.208500%2C16.373100&destination=48.203000%2C16.354000" +
                "&waypoints=48.209100%2C16.374700&travelmode=walking",
            links.last(),
        )
    }

    @Test
    fun `senza preferiti spiega come aggiungerli`() {
        composeRule.setContent { PartiMoTheme { FavoritesScreen(PreviewData.favoritesState().copy(favorites = emptyList()), FavoritesActions()) } }

        composeRule.onNodeWithText(text(R.string.favorites_empty)).assertExists()
    }

    @Test
    fun `salva lo screenshot dei preferiti`() {
        composeRule.setContent { PartiMoTheme { FavoritesScreen(PreviewData.favoritesState(), FavoritesActions()) } }
        composeRule.waitForIdle()
        val bitmap = composeRule.activity.window.decorView.rootView.drawToBitmap()
        val screenshot = File("build/outputs/screenshots/favorites.png").apply { parentFile?.mkdirs() }
        screenshot.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        assertTrue(screenshot.length() > 0, "Screenshot non generato")
    }
}
