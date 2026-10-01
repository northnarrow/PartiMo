package com.partimo.app.ui.budget

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
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
import com.partimo.domain.model.budget.Expense
import com.partimo.domain.model.budget.ExpenseCategory
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

/** Test UI del budget del viaggio, sulla JVM con Robolectric. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class BudgetScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(@StringRes id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    @Test
    fun `il riepilogo mostra speso, budget, categorie e spese in corone convertite`() {
        val removed = mutableListOf<Expense>()
        var limitEdits = 0
        composeRule.setContent {
            PartiMoTheme { BudgetScreen(PreviewData.budgetState(), BudgetActions(onRemoveExpense = { removed += it }, onEditLimit = { limitEdits++ })) }
        }
        val summary = PreviewData.budgetState().summary!!

        composeRule.onNodeWithText(Formatters.currencyAmount(summary.total, "EUR")).assertExists()
        composeRule.onNodeWithText(text(R.string.budget_of_limit, Formatters.currencyAmount(BigDecimal("800"), "EUR"))).assertExists()
        composeRule.onNodeWithText(text(R.string.budget_remaining, Formatters.currencyAmount(BigDecimal("800").subtract(summary.total), "EUR"))).assertExists()
        composeRule.onNodeWithText("🏨 " + text(R.string.budget_category_lodging)).assertExists()
        composeRule.onNodeWithText(text(R.string.budget_edit_limit)).performClick()

        composeRule.onNodeWithTag(BUDGET_LIST_TAG).performScrollToNode(hasText("Cena al Lokál"))
        composeRule.onNodeWithText(Formatters.currencyAmount(BigDecimal("890"), "CZK")).assertExists()
        composeRule.onNodeWithText(text(R.string.budget_converted, Formatters.currencyAmount(BigDecimal("36.42"), "EUR"))).assertExists()
        composeRule.onNodeWithContentDescription(text(R.string.budget_delete, "Cena al Lokál")).performClick()

        assertEquals(1, limitEdits)
        assertEquals(listOf("3"), removed.map { it.id })
    }

    @Test
    fun `la nuova spesa sceglie valuta, categoria e giorno`() {
        val drafts = mutableListOf<ExpenseDraft>()
        var saves = 0
        var state by mutableStateOf(PreviewData.budgetDraftState())
        composeRule.setContent {
            PartiMoTheme { BudgetScreen(state, BudgetActions(onDraftChanged = { drafts += it }, onSaveDraft = { saves++ })) }
        }

        composeRule.onNodeWithText(text(R.string.budget_new_expense)).assertExists()
        composeRule.onNodeWithText("EUR").performClick()
        // "Cibo" compare anche nel riepilogo dietro la finestra: si tocca la scelta nella finestra.
        composeRule.onNode(hasText("🍝 " + text(R.string.budget_category_food)) and hasAnyAncestor(isDialog())).performClick()
        composeRule.onNodeWithText(text(R.string.budget_save)).performClick()

        assertEquals("EUR", drafts[0].currency)
        assertEquals(ExpenseCategory.FOOD, drafts[1].category)
        assertEquals(1, saves)

        state = state.copy(draft = state.draft?.copy(amountText = ""))
        composeRule.onNodeWithText(text(R.string.budget_save)).performClick()
        assertEquals(1, saves, "Senza importo non si salva")
    }

    @Test
    fun `senza spese spiega cosa registrare`() {
        composeRule.setContent {
            PartiMoTheme {
                BudgetScreen(PreviewData.budgetState().let { it.copy(budget = it.budget?.copy(expenses = emptyList()), summary = null) }, BudgetActions())
            }
        }

        composeRule.onNodeWithText(text(R.string.budget_empty)).assertExists()
    }

    @Test
    fun `salva lo screenshot del budget`() {
        composeRule.setContent { PartiMoTheme { BudgetScreen(PreviewData.budgetState(), BudgetActions()) } }
        composeRule.waitForIdle()
        saveScreenshot("budget.png")
    }

    private fun saveScreenshot(name: String) {
        val bitmap = composeRule.activity.window.decorView.rootView.drawToBitmap()
        val screenshot = File("build/outputs/screenshots/$name").apply { parentFile?.mkdirs() }
        screenshot.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        assertTrue(screenshot.length() > 0, "Screenshot non generato")
    }
}
