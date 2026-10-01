package com.partimo.app.ui.bookings

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.core.view.drawToBitmap
import com.partimo.app.R
import com.partimo.app.testing.UiTestApplication
import com.partimo.app.ui.common.Formatters
import com.partimo.app.ui.dashboard.PreviewData
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.model.booking.Booking
import com.partimo.domain.model.booking.BookingDraft
import com.partimo.domain.model.booking.BookingKind
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Test UI delle prenotazioni (linea del tempo e modulo), sulla JVM con Robolectric. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = UiTestApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class BookingsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(@StringRes id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    private fun plural(@PluralsRes id: Int, count: Int): String = composeRule.activity.resources.getQuantityString(id, count, count)

    @Test
    fun `la linea del tempo mostra giorni, orari, tratte e codici e nasconde le passate`() {
        val copied = mutableListOf<String>()
        val opened = mutableListOf<Booking>()
        val edited = mutableListOf<Booking>()
        var state by mutableStateOf(PreviewData.bookingsState())
        composeRule.setContent {
            PartiMoTheme {
                BookingsScreen(
                    state,
                    BookingsActions(
                        onCopyReference = { copied += it },
                        onOpenDocument = { opened += it },
                        onEdit = { edited += it },
                        onTogglePast = { state = state.copy(showPast = !state.showPast) },
                    ),
                )
            }
        }
        val flightDay = LocalDate.of(2026, 12, 11)

        composeRule.onNodeWithText("📅 " + Formatters.weekdayDayMonth(flightDay) + " 2026 · " + plural(R.plurals.bookings_in_days, 72)).assertExists()
        composeRule.onNodeWithText("Ryanair FR 7178").assertExists()
        composeRule.onNodeWithText("21:10 → 22:55").assertExists()
        composeRule.onNodeWithText("BGY → VIE").assertExists()
        composeRule.onNodeWithText("Posto 12A · solo bagaglio a mano").assertExists()
        // Andata e ritorno hanno lo stesso codice: si copia quello dell'andata.
        composeRule.onAllNodesWithText("🔖 " + text(R.string.booking_reference_value, "K7M2QX"))[0].performClick()
        composeRule.onNodeWithText("📄 " + text(R.string.booking_open_document)).performClick()
        val hotelDates = Formatters.weekdayDayMonth(flightDay) + " → " + Formatters.weekdayDayMonth(flightDay.plusDays(2))
        composeRule.onNodeWithText(hotelDates + " · " + text(R.string.booking_check_in_at, "15:00")).performClick()

        composeRule.onNodeWithTag(BOOKINGS_LIST_TAG).performScrollToNode(hasText(plural(R.plurals.bookings_show_past, 1)))
        composeRule.onNodeWithText("Frecciarossa 9517").assertDoesNotExist()
        composeRule.onNodeWithText(plural(R.plurals.bookings_show_past, 1)).performClick()
        composeRule.onNodeWithTag(BOOKINGS_LIST_TAG).performScrollToNode(hasText("Frecciarossa 9517"))
        composeRule.onNodeWithText("Milano Centrale → Roma Termini").assertExists()

        assertEquals(listOf("K7M2QX"), copied)
        assertEquals(listOf("preview-andata"), opened.map { it.id })
        assertEquals(listOf("preview-hotel"), edited.map { it.id })
    }

    @Test
    fun `senza prenotazioni spiega come aggiungerle`() {
        var adds = 0
        composeRule.setContent { PartiMoTheme { BookingsScreen(PreviewData.bookingsEmptyState(), BookingsActions(onAdd = { adds++ })) } }

        composeRule.onNodeWithText(text(R.string.bookings_empty_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.bookings_empty_text)).assertExists()
        // Il pulsante nel messaggio e quello in basso.
        composeRule.onAllNodesWithText(text(R.string.bookings_add), useUnmergedTree = true).assertCountEquals(2)
        composeRule.onAllNodesWithText(text(R.string.bookings_add))[0].performClick()
        assertEquals(1, adds)
    }

    @Test
    fun `il modulo importa il testo incollato e salva solo con titolo e giorno`() {
        val drafts = mutableListOf<BookingDraft>()
        var pasted = ""
        var reads = 0
        var saves = 0
        var state by mutableStateOf(PreviewData.bookingEditorState().copy(pastedText = ""))
        composeRule.setContent {
            PartiMoTheme {
                BookingEditorScreen(
                    state,
                    BookingEditorActions(
                        onDraftChanged = { drafts += it },
                        onPastedTextChanged = { pasted = it },
                        onReadPastedText = { reads++ },
                        onSave = { saves++ },
                    ),
                )
            }
        }

        composeRule.onNodeWithText(text(R.string.booking_import_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.booking_import_read)).assertIsNotEnabled()
        composeRule.onNodeWithTag(BOOKING_PASTE_FIELD_TAG).performTextInput("FR 7178 BGY - VIE 11/12/2026 21:10")
        assertEquals("FR 7178 BGY - VIE 11/12/2026 21:10", pasted)
        state = state.copy(pastedText = pasted)
        composeRule.onNodeWithText(text(R.string.booking_import_read)).assertIsEnabled().performClick()
        assertEquals(1, reads)

        composeRule.onNodeWithText("🏨 " + text(R.string.booking_kind_lodging)).performClick()
        assertEquals(BookingKind.LODGING, drafts.last().kind)
        composeRule.onNodeWithText(text(R.string.booking_save)).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.booking_save_hint)).assertExists()

        state = state.copy(draft = BookingDraft(kind = BookingKind.LODGING, title = "Hotel Sacher Wien", startDate = LocalDate.of(2026, 12, 11)))
        composeRule.onNodeWithText(text(R.string.booking_field_check_in)).assertExists()
        composeRule.onNodeWithText(text(R.string.booking_field_check_out)).assertExists()
        composeRule.onNodeWithText(text(R.string.booking_save)).performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, saves)
    }

    @Test
    fun `le prenotazioni lette si controllano una alla volta con il documento allegato`() {
        var removed = 0
        composeRule.setContent { PartiMoTheme { BookingEditorScreen(PreviewData.bookingEditorReadState(), BookingEditorActions(onRemoveAttachment = { removed++ })) } }

        composeRule.onNodeWithText(text(R.string.booking_read_position, 1, 2)).assertExists()
        composeRule.onNodeWithText(text(R.string.booking_import_title)).assertDoesNotExist()
        composeRule.onNodeWithText("Ryanair FR 7178").assertExists()
        composeRule.onNodeWithText(text(R.string.booking_field_departure)).assertExists()
        composeRule.onNodeWithText("📄 " + text(R.string.booking_attachment_pdf)).performScrollTo()
        composeRule.onNodeWithText(text(R.string.booking_attachment_remove)).performClick()
        composeRule.onNodeWithText(text(R.string.booking_save_next)).performScrollTo().assertIsEnabled()
        assertEquals(1, removed)
    }

    @Test
    fun `salva gli screenshot delle prenotazioni`() {
        var editor by mutableStateOf(false)
        composeRule.setContent {
            PartiMoTheme {
                if (editor) BookingEditorScreen(PreviewData.bookingEditorReadState(), BookingEditorActions()) else BookingsScreen(PreviewData.bookingsState(), BookingsActions())
            }
        }
        composeRule.waitForIdle()
        saveScreenshot("bookings.png")

        editor = true
        composeRule.waitForIdle()
        saveScreenshot("booking_editor.png")
    }

    private fun saveScreenshot(name: String) {
        val bitmap = composeRule.activity.window.decorView.rootView.drawToBitmap()
        val screenshot = File("build/outputs/screenshots/$name").apply { parentFile?.mkdirs() }
        screenshot.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        assertTrue(screenshot.length() > 0, "Screenshot non generato")
    }
}
