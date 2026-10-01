package com.partimo.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.partimo.app.R
import com.partimo.domain.model.Travellers

/** Tag della cella dei viaggiatori, usato dai test UI. */
const val TRAVELLERS_TAG = "travellers"

/** "1 adulto", "2 adulti", "2 adulti, 1 bambino". */
@Composable
fun Travellers.label(): String {
    val adultsText = pluralStringResource(R.plurals.travellers_adults, adults, adults)
    if (children == 0) return adultsText
    return adultsText + ", " + pluralStringResource(R.plurals.travellers_children, children, children)
}

/** Età di un bambino: "meno di 1 anno", "1 anno", "8 anni". */
@Composable
fun childAgeLabel(age: Int): String =
    if (age == 0) stringResource(R.string.travellers_age_baby) else pluralStringResource(R.plurals.travellers_age, age, age)

/** Cella «Chi parte» della schermata iniziale: toccandola si scelgono adulti e bambini. */
@Composable
fun TravellersRow(travellers: Travellers, onTravellersSelected: (Travellers) -> Unit, modifier: Modifier = Modifier) {
    var picking by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(onClick = { picking = true }, modifier = modifier.fillMaxWidth().testTag(TRAVELLERS_TAG)) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.travellers_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = travellers.label(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = stringResource(R.string.travellers_change),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    if (picking) {
        TravellersDialog(
            initial = travellers,
            onDismiss = { picking = false },
            onConfirm = {
                picking = false
                onTravellersSelected(it)
            },
        )
    }
}

/**
 * Scelta di adulti e bambini, con l'età di ogni bambino: decide le tariffe dei voli (sotto i 2 anni si viaggia
 * in braccio a un adulto) e le camere. Al massimo [Travellers.MAX_TRAVELLERS] persone, come sui siti dei voli.
 */
@Composable
fun TravellersDialog(initial: Travellers, onDismiss: () -> Unit, onConfirm: (Travellers) -> Unit) {
    var adults by rememberSaveable { mutableIntStateOf(initial.adults) }
    var childAges by rememberSaveable { mutableStateOf(initial.childAges) }
    val total = adults + childAges.size
    val candidate = remember(adults, childAges) { Travellers(adults, childAges) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("👥 " + stringResource(R.string.travellers_dialog_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CounterRow(
                    label = stringResource(R.string.travellers_adults_label),
                    hint = stringResource(R.string.travellers_adults_hint),
                    value = adults.toString(),
                    decreaseDescription = stringResource(R.string.travellers_remove_adult),
                    increaseDescription = stringResource(R.string.travellers_add_adult),
                    onDecrease = { adults -= 1 }.takeIf { adults > 1 },
                    onIncrease = { adults += 1 }.takeIf { total < Travellers.MAX_TRAVELLERS },
                )
                CounterRow(
                    label = stringResource(R.string.travellers_children_label),
                    hint = stringResource(R.string.travellers_children_hint),
                    value = childAges.size.toString(),
                    decreaseDescription = stringResource(R.string.travellers_remove_child),
                    increaseDescription = stringResource(R.string.travellers_add_child),
                    onDecrease = { childAges = childAges.dropLast(1) }.takeIf { childAges.isNotEmpty() },
                    onIncrease = { childAges = childAges + Travellers.DEFAULT_CHILD_AGE }.takeIf { total < Travellers.MAX_TRAVELLERS },
                )
                childAges.forEachIndexed { index, age ->
                    CounterRow(
                        label = stringResource(R.string.travellers_child_age, index + 1),
                        hint = null,
                        value = childAgeLabel(age),
                        decreaseDescription = stringResource(R.string.travellers_younger, index + 1),
                        increaseDescription = stringResource(R.string.travellers_older, index + 1),
                        onDecrease = { childAges = childAges.toMutableList().also { it[index] = age - 1 } }.takeIf { age > 0 },
                        onIncrease = { childAges = childAges.toMutableList().also { it[index] = age + 1 } }.takeIf { age < Travellers.MAX_CHILD_AGE },
                    )
                }
                Text(
                    text = stringResource(if (candidate.infantsHaveLaps) R.string.travellers_infants_note else R.string.travellers_infants_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (candidate.infantsHaveLaps) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(candidate) }, enabled = candidate.infantsHaveLaps) {
                Text(stringResource(R.string.search_dates_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.search_dates_cancel)) } },
    )
}

/** Riga con un valore e i pulsanti − e + (disattivati quando l'azione è `null`). */
@Composable
private fun CounterRow(
    label: String,
    hint: String?,
    value: String,
    decreaseDescription: String,
    increaseDescription: String,
    onDecrease: (() -> Unit)?,
    onIncrease: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            hint?.let { Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        FilledTonalIconButton(
            onClick = { onDecrease?.invoke() },
            enabled = onDecrease != null,
            modifier = Modifier.semantics { contentDescription = decreaseDescription },
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(if (value.length > 2) 96.dp else 40.dp),
        )
        FilledTonalIconButton(
            onClick = { onIncrease?.invoke() },
            enabled = onIncrease != null,
            modifier = Modifier.semantics { contentDescription = increaseDescription },
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}
