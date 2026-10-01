package com.partimo.app.ui.dashboard

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.partimo.app.R
import com.partimo.app.testing.UiTestApplication
import com.partimo.app.ui.dashboard.components.PriceCalendarActions
import com.partimo.app.ui.dashboard.components.PriceCalendarContent
import com.partimo.app.ui.dashboard.components.priceDayTag
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.model.flight.FareSnapshot
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Test UI del calendario dei prezzi, sulla JVM con Robolectric. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = UiTestApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class PriceCalendarTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(@StringRes id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    private val state = PreviewData.priceCalendarState().priceCalendar!!

    @Test
    fun `ogni giorno mostra il prezzo e un tocco porta le date della sua tariffa`() {
        val selected = mutableListOf<FareSnapshot>()
        val months = mutableListOf<Int>()
        composeRule.setContent {
            PartiMoTheme {
                Surface {
                    PriceCalendarContent(state, PriceCalendarActions(onDaySelected = { selected += it }, onMonthChanged = { months += it }))
                }
            }
        }

        composeRule.onNodeWithText("Dicembre 2026").assertExists()
        composeRule.onNodeWithTag(priceDayTag(LocalDate.of(2026, 12, 1))).assertIsNotEnabled()
        composeRule.onNodeWithTag(priceDayTag(LocalDate.of(2026, 12, 7))).assertIsEnabled().performClick()
        composeRule.onNodeWithContentDescription(text(R.string.price_calendar_previous)).performClick()
        composeRule.onNodeWithContentDescription(text(R.string.price_calendar_next)).performClick()

        assertEquals(listOf(LocalDate.of(2026, 12, 7) to LocalDate.of(2026, 12, 10)), selected.map { it.departureDate to it.returnDate })
        assertEquals(listOf(-1, 1), months)
    }

    @Test
    fun `salva lo screenshot del calendario dei prezzi`() {
        composeRule.setContent {
            PartiMoTheme { Surface(Modifier.testTag(CALENDAR_TAG)) { PriceCalendarContent(state, PriceCalendarActions(), Modifier.padding(16.dp)) } }
        }
        composeRule.waitForIdle()

        // Solo il calendario, senza lo spazio vuoto sotto.
        val bounds = composeRule.onNodeWithTag(CALENDAR_TAG).fetchSemanticsNode().boundsInWindow
        val window = composeRule.activity.window.decorView.rootView.drawToBitmap()
        val top = bounds.top.roundToInt().coerceIn(0, window.height - 1)
        val bitmap = Bitmap.createBitmap(window, 0, top, window.width, bounds.bottom.roundToInt().coerceIn(top + 1, window.height) - top)
        val screenshot = File("build/outputs/screenshots/price_calendar.png").apply { parentFile?.mkdirs() }
        screenshot.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        assertTrue(screenshot.length() > 0, "Screenshot non generato")
    }

    private companion object {
        const val CALENDAR_TAG = "price_calendar"
    }
}
