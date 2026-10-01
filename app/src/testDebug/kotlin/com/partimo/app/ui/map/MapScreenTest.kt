package com.partimo.app.ui.map

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.drawToBitmap
import com.partimo.app.R
import com.partimo.app.ui.dashboard.PreviewData
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.poi.PointOfInterest
import com.partimo.domain.model.saved.FavoriteKind
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test UI della mappa del viaggio, sulla JVM con Robolectric: qui la libreria nativa di MapLibre
 * non c'è, quindi la mappa è lo schema dei punti (con gli stessi tocchi).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class MapScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(@StringRes id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    @Test
    fun `i filtri fanno da legenda con i conteggi e nascondono i tipi`() {
        val toggled = mutableListOf<FavoriteKind>()
        val favoritesOnly = mutableListOf<Boolean>()
        composeRule.setContent {
            PartiMoTheme {
                MapScreen(
                    PreviewData.mapState(),
                    MapActions(onKindToggled = { toggled += it }, onFavoritesOnlyChanged = { favoritesOnly += it }),
                )
            }
        }

        composeRule.onNodeWithText("🗺️ " + text(R.string.map_title, "Vienna")).assertExists()
        composeRule.onNodeWithTag(SCHEMATIC_MAP_TAG).assertExists()
        composeRule.onNodeWithText(text(R.string.favorites_places) + " · 2").performClick()
        composeRule.onNodeWithText(text(R.string.favorites_events) + " · 3").assertExists()
        composeRule.onNodeWithTag(MAP_FILTERS_TAG).performScrollToNode(hasText(text(R.string.favorites_lodgings) + " · 3"))
        composeRule.onNodeWithText(text(R.string.favorites_lodgings) + " · 3").performClick()
        composeRule.onNodeWithTag(MAP_FILTERS_TAG).performScrollToNode(hasText(text(R.string.map_favorites_only)))
        composeRule.onNodeWithText(text(R.string.map_favorites_only)).performClick()

        assertEquals(listOf(FavoriteKind.PLACE, FavoriteKind.LODGING), toggled)
        assertEquals(listOf(true), favoritesOnly)
    }

    @Test
    fun `la scheda del punto apre il luogo, la pagina del locale e la navigazione`() {
        val places = mutableListOf<PointOfInterest>()
        val links = mutableListOf<String>()
        val destinations = mutableListOf<GeoPoint>()
        val selections = mutableListOf<String?>()
        var state by mutableStateOf(PreviewData.mapState(selectFirst = true))
        composeRule.setContent {
            PartiMoTheme {
                MapScreen(
                    state,
                    MapActions(
                        onOpenPlace = { places += it },
                        onOpenLink = { links += it },
                        onNavigate = { destinations += it },
                        onPointSelected = { selections += it },
                    ),
                )
            }
        }

        composeRule.onNodeWithTag(MAP_SELECTED_CARD_TAG).assertExists()
        composeRule.onNodeWithText("Wiener Christkindlmarkt al Rathausplatz").assertExists()
        composeRule.onNodeWithText(text(R.string.map_open_details)).performClick()
        composeRule.onNodeWithText("🧭 " + text(R.string.place_navigate)).performClick()

        state = state.copy(selectedKey = "RESTAURANT:osm:node/12")
        composeRule.onNodeWithText("Griechenbeisl").assertExists()
        composeRule.onNodeWithText(text(R.string.map_open_page)).performClick()
        composeRule.onNodeWithContentDescription(text(R.string.map_close_point)).performClick()
        // Un tocco lontano dai punti chiude la scheda, come sulla mappa vera.
        composeRule.onNodeWithTag(SCHEMATIC_MAP_TAG).performTouchInput { click(Offset(2f, 2f)) }

        assertEquals(listOf("xmas"), places.map { it.id })
        assertEquals(listOf(GeoPoint(48.2108, 16.3573)), destinations)
        assertEquals(1, links.size)
        assertTrue(links.single().startsWith("https://www.google.com/maps/search/?api=1&query=Griechenbeisl"))
        assertEquals(listOf<String?>(null, null), selections)
    }

    @Test
    fun `con solo preferiti e nessun preferito spiega come aggiungerli`() {
        composeRule.setContent {
            PartiMoTheme { MapScreen(PreviewData.mapState().copy(favorites = emptyList(), favoritesOnly = true), MapActions()) }
        }

        composeRule.onNodeWithText(text(R.string.map_no_favorites)).assertExists()
    }

    @Test
    fun `salva lo screenshot della mappa`() {
        composeRule.setContent { PartiMoTheme { MapScreen(PreviewData.mapState(selectFirst = true), MapActions()) } }
        composeRule.waitForIdle()

        val bitmap = composeRule.activity.window.decorView.rootView.drawToBitmap()
        val screenshot = File("build/outputs/screenshots/map.png").apply { parentFile?.mkdirs() }
        screenshot.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        assertTrue(screenshot.length() > 0, "Screenshot non generato")
    }
}
