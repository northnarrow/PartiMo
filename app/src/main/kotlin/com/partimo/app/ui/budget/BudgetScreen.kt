package com.partimo.app.ui.budget

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.partimo.app.R
import com.partimo.app.ui.common.FormDialog
import com.partimo.app.ui.common.Formatters
import com.partimo.app.ui.dashboard.PreviewData
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.model.budget.BudgetSummary
import com.partimo.domain.model.budget.Expense
import com.partimo.domain.model.budget.ExpenseCategory
import java.math.BigDecimal
import java.math.MathContext

/** Tag della lista del budget, usato dai test UI per lo scroll. */
const val BUDGET_LIST_TAG = "budget_list"

data class BudgetActions(
    val onBack: () -> Unit = {},
    val onAddExpense: () -> Unit = {},
    val onDraftChanged: (ExpenseDraft) -> Unit = {},
    val onDismissDraft: () -> Unit = {},
    val onSaveDraft: () -> Unit = {},
    val onRemoveExpense: (Expense) -> Unit = {},
    val onEditLimit: () -> Unit = {},
    val onLimitChanged: (String) -> Unit = {},
    val onDismissLimit: () -> Unit = {},
    val onSaveLimit: () -> Unit = {},
)

@Composable
fun BudgetRoute(viewModel: BudgetViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BudgetScreen(
        state = state,
        actions = BudgetActions(
            onBack = onBack,
            onAddExpense = viewModel::onAddExpense,
            onDraftChanged = viewModel::onDraftChanged,
            onDismissDraft = viewModel::onDismissDraft,
            onSaveDraft = viewModel::onSaveDraft,
            onRemoveExpense = viewModel::onRemoveExpense,
            onEditLimit = viewModel::onEditLimit,
            onLimitChanged = viewModel::onLimitChanged,
            onDismissLimit = viewModel::onDismissLimit,
            onSaveLimit = viewModel::onSaveLimit,
        ),
        modifier = modifier,
    )
}

/**
 * Budget del viaggio: quanto si è speso (in euro, anche per le spese nella valuta del posto), quanto
 * resta del budget, le spese per categoria e l'elenco dalla più recente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(state: BudgetUiState, actions: BudgetActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "💶 " + stringResource(R.string.budget_title, state.destination.name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = Formatters.dateRange(state.from, state.to),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions.onAddExpense,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.budget_add)) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag(BUDGET_LIST_TAG),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            state.summary?.let { summary ->
                item(key = "summary") { SummaryCard(summary = summary, onEditLimit = actions.onEditLimit) }
                if (summary.byCategory.isNotEmpty()) {
                    item(key = "categories") { CategoryBreakdown(summary) }
                }
                item(key = "note") {
                    // Sopra l'elenco, così il pulsante "Aggiungi spesa" non la copre mai.
                    Text(
                        text = stringResource(R.string.budget_rates_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (summary.unconverted.isNotEmpty()) {
                    item(key = "unconverted") {
                        Text(
                            text = stringResource(R.string.budget_unconverted, summary.unconverted.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            val expenses = state.expenses
            if (state.budget != null && expenses.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.budget_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            if (expenses.isNotEmpty()) {
                item(key = "expenses-title") {
                    Text(
                        text = stringResource(R.string.budget_expenses),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(expenses, key = { it.id }) { expense ->
                    ExpenseRow(expense = expense, converted = state.summary?.converted?.get(expense.id), homeCurrency = state.homeCurrency, onRemove = actions.onRemoveExpense)
                }
            }
        }
    }
    state.draft?.let { draft -> ExpenseDialog(state = state, draft = draft, actions = actions) }
    state.limitText?.let { text -> LimitDialog(text = text, currency = state.homeCurrency, actions = actions) }
}

@Composable
private fun SummaryCard(summary: BudgetSummary, onEditLimit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.budget_spent),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = Formatters.currencyAmount(summary.total, summary.currency),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            val limit = summary.limit
            if (limit != null) {
                Text(
                    text = stringResource(R.string.budget_of_limit, Formatters.currencyAmount(limit, summary.currency)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                LinearProgressIndicator(
                    progress = { summary.usedShare ?: 0f },
                    color = if (summary.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
                val remaining = summary.remaining ?: BigDecimal.ZERO
                Text(
                    text = if (summary.isOverBudget) {
                        "⚠️ " + stringResource(R.string.budget_over, Formatters.currencyAmount(remaining.negate(), summary.currency))
                    } else {
                        stringResource(R.string.budget_remaining, Formatters.currencyAmount(remaining, summary.currency))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (summary.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            TextButton(onClick = onEditLimit, modifier = Modifier.padding(top = 2.dp)) {
                Text(stringResource(if (limit == null) R.string.budget_set_limit else R.string.budget_edit_limit))
            }
        }
    }
}

@Composable
private fun CategoryBreakdown(summary: BudgetSummary) {
    val max = summary.byCategory.values.maxOrNull()?.takeIf { it.signum() > 0 } ?: return
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.budget_by_category),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        summary.byCategory.entries.sortedByDescending { it.value }.forEach { (category, amount) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = category.emoji() + " " + stringResource(category.labelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(120.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(amount.divide(max, MathContext.DECIMAL64).toFloat().coerceIn(0.02f, 1f))
                            .height(8.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
                    )
                }
                Text(
                    text = Formatters.currencyAmount(amount, summary.currency),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, converted: BigDecimal?, homeCurrency: String, onRemove: (Expense) -> Unit) {
    val title = expense.note.ifBlank { stringResource(expense.category.labelRes()) }
    ListItem(
        leadingContent = { Text(expense.category.emoji(), style = MaterialTheme.typography.titleLarge) },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(Formatters.weekdayDayMonth(expense.date)) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(Formatters.currencyAmount(expense.amount, expense.currency), fontWeight = FontWeight.SemiBold)
                    if (expense.currency != homeCurrency && converted != null) {
                        Text(
                            text = stringResource(R.string.budget_converted, Formatters.currencyAmount(converted, homeCurrency)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { onRemove(expense) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.budget_delete, title))
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseDialog(state: BudgetUiState, draft: ExpenseDraft, actions: BudgetActions) {
    FormDialog(
        title = stringResource(R.string.budget_new_expense),
        onDismissRequest = actions.onDismissDraft,
        confirmButton = {
            TextButton(onClick = actions.onSaveDraft, enabled = draft.amount != null) { Text(stringResource(R.string.budget_save)) }
        },
        dismissButton = { TextButton(onClick = actions.onDismissDraft) { Text(stringResource(R.string.budget_cancel)) } },
    ) {
        OutlinedTextField(
            value = draft.amountText,
            onValueChange = { actions.onDraftChanged(draft.copy(amountText = it)) },
            label = { Text(stringResource(R.string.budget_amount)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            suffix = { Text(draft.currency) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.currencies.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.currencies.forEach { currency ->
                    FilterChip(
                        selected = draft.currency == currency,
                        onClick = { actions.onDraftChanged(draft.copy(currency = currency)) },
                        label = { Text(currency) },
                    )
                }
            }
        }
        Text(stringResource(R.string.budget_category), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpenseCategory.entries.forEach { category ->
                FilterChip(
                    selected = draft.category == category,
                    onClick = { actions.onDraftChanged(draft.copy(category = category)) },
                    label = { Text(category.emoji() + " " + stringResource(category.labelRes())) },
                )
            }
        }
        if (state.days.size > 1) {
            Text(stringResource(R.string.budget_day), style = MaterialTheme.typography.labelLarge)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.days.forEach { day ->
                    FilterChip(
                        selected = draft.date == day,
                        onClick = { actions.onDraftChanged(draft.copy(date = day)) },
                        label = { Text(Formatters.weekdayDay(day)) },
                    )
                }
            }
        }
        OutlinedTextField(
            value = draft.note,
            onValueChange = { actions.onDraftChanged(draft.copy(note = it)) },
            label = { Text(stringResource(R.string.budget_note)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LimitDialog(text: String, currency: String, actions: BudgetActions) {
    FormDialog(
        title = stringResource(R.string.budget_edit_limit),
        onDismissRequest = actions.onDismissLimit,
        confirmButton = {
            TextButton(onClick = actions.onSaveLimit, enabled = text.isBlank() || parseAmount(text) != null) { Text(stringResource(R.string.budget_save)) }
        },
        dismissButton = { TextButton(onClick = actions.onDismissLimit) { Text(stringResource(R.string.budget_cancel)) } },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = actions.onLimitChanged,
            label = { Text(stringResource(R.string.budget_limit_label, currency)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.budget_limit_hint), style = MaterialTheme.typography.bodySmall)
    }
}

fun ExpenseCategory.emoji(): String = when (this) {
    ExpenseCategory.TRANSPORT -> "🚆"
    ExpenseCategory.LODGING -> "🏨"
    ExpenseCategory.FOOD -> "🍝"
    ExpenseCategory.ACTIVITIES -> "🎟️"
    ExpenseCategory.SHOPPING -> "🛍️"
    ExpenseCategory.OTHER -> "📦"
}

fun ExpenseCategory.labelRes(): Int = when (this) {
    ExpenseCategory.TRANSPORT -> R.string.budget_category_transport
    ExpenseCategory.LODGING -> R.string.budget_category_lodging
    ExpenseCategory.FOOD -> R.string.budget_category_food
    ExpenseCategory.ACTIVITIES -> R.string.budget_category_activities
    ExpenseCategory.SHOPPING -> R.string.budget_category_shopping
    ExpenseCategory.OTHER -> R.string.budget_category_other
}

// ---- Anteprime -----------------------------------------------------------------------------------

@Preview(name = "Budget", showBackground = true, heightDp = 900)
@Composable
private fun BudgetPreview() {
    PartiMoTheme { BudgetScreen(PreviewData.budgetState(), BudgetActions()) }
}

@Preview(name = "Budget · nuova spesa · tema scuro", showBackground = true, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BudgetDraftDarkPreview() {
    PartiMoTheme(darkTheme = true) { BudgetScreen(PreviewData.budgetDraftState(), BudgetActions()) }
}
