package com.partimo.app.ui.bookings

import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.partimo.app.R
import com.partimo.app.files.BookingDocuments
import com.partimo.app.ui.common.FormDialog
import com.partimo.app.ui.common.Formatters
import com.partimo.app.ui.dashboard.PreviewData
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.model.booking.BookingDraft
import com.partimo.domain.model.booking.BookingKind
import java.time.LocalDate
import java.time.LocalTime

/** Tag dei campi del modulo, usati dai test UI. */
const val BOOKING_FORM_TAG = "booking_form"
const val BOOKING_TITLE_FIELD_TAG = "booking_title"
const val BOOKING_PASTE_FIELD_TAG = "booking_paste"
const val BOOKING_START_DATE_TAG = "booking_start_date"

private const val MILLIS_PER_DAY = 86_400_000L

/** Azioni del modulo (predefinite vuote per anteprime e test). */
data class BookingEditorActions(
    val onBack: () -> Unit = {},
    val onDraftChanged: (BookingDraft) -> Unit = {},
    val onPastedTextChanged: (String) -> Unit = {},
    val onReadPastedText: () -> Unit = {},
    val onPickDocument: () -> Unit = {},
    val onOpenDocument: () -> Unit = {},
    val onRemoveAttachment: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

/** Collega il ViewModel al modulo: selettore dei documenti, apertura del documento e chiusura a lavoro finito. */
@Composable
fun BookingEditorRoute(
    viewModel: BookingEditorViewModel,
    documents: BookingDocuments,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(state.finished) { if (state.finished) done() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.onDocumentPicked(it.toString(), context.contentResolver.getType(it)) }
    }
    val noApp = stringResource(R.string.booking_document_no_app)
    val missing = stringResource(R.string.booking_document_missing)
    BookingEditorScreen(
        state = state,
        actions = BookingEditorActions(
            onBack = onBack,
            onDraftChanged = viewModel::onDraftChanged,
            onPastedTextChanged = viewModel::onPastedTextChanged,
            onReadPastedText = viewModel::onReadPastedText,
            onPickDocument = { picker.launch(DOCUMENT_TYPES) },
            onOpenDocument = {
                when (state.attachment?.let { openStoredDocument(context, documents, it.name, it.mimeType) }) {
                    DocumentOpening.OPENED -> Unit
                    DocumentOpening.NO_APP -> Toast.makeText(context, noApp, Toast.LENGTH_LONG).show()
                    DocumentOpening.MISSING, null -> Toast.makeText(context, missing, Toast.LENGTH_LONG).show()
                }
            },
            onRemoveAttachment = viewModel::onRemoveAttachment,
            onSave = viewModel::onSave,
            onDelete = viewModel::onDelete,
            onMessageShown = viewModel::onMessageShown,
        ),
        modifier = modifier,
    )
}

/** Documenti che si possono allegare e leggere: PDF e immagini (foto, screenshot). */
private val DOCUMENT_TYPES = arrayOf("application/pdf", "image/*")

/**
 * Modulo di una prenotazione. Una nuova si può importare incollando la mail di conferma o scegliendo il PDF o la
 * foto del biglietto: il testo si legge sul telefono e i campi si compilano da soli, da controllare prima di salvare.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookingEditorScreen(state: BookingEditorUiState, actions: BookingEditorActions, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.let { stringResource(it.textRes()) }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            actions.onMessageShown()
        }
    }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val draft = state.draft
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(if (state.isNew) R.string.booking_new_title else R.string.booking_edit_title), fontWeight = FontWeight.Bold) },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.booking_delete))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag(BOOKING_FORM_TAG),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isNew && state.readCount == 0) ImportCard(state, actions)
            if (state.isReading) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.booking_import_reading), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (state.position > 0) {
                Text(
                    text = stringResource(R.string.booking_read_position, state.position, state.readCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            KindChips(selected = draft.kind, onSelected = { actions.onDraftChanged(draft.copy(kind = it)) })
            TextField(
                value = draft.title,
                label = stringResource(R.string.booking_field_title),
                placeholder = stringResource(draft.kind.titleHintRes()),
                onValueChange = { actions.onDraftChanged(draft.copy(title = it)) },
                modifier = Modifier.testTag(BOOKING_TITLE_FIELD_TAG),
            )
            DateTimeRow(
                label = stringResource(draft.kind.startLabelRes()),
                date = draft.startDate,
                time = draft.startTime,
                today = state.today,
                onDateSelected = { date ->
                    // Spostando l'inizio dopo la fine, la fine si sposta con lui.
                    val end = draft.endDate?.takeIf { !it.isBefore(date) }
                    actions.onDraftChanged(draft.copy(startDate = date, endDate = end))
                },
                onTimeSelected = { actions.onDraftChanged(draft.copy(startTime = it)) },
                dateTag = BOOKING_START_DATE_TAG,
            )
            DateTimeRow(
                label = stringResource(draft.kind.endLabelRes()),
                date = draft.endDate,
                time = draft.endTime,
                today = draft.startDate ?: state.today,
                onDateSelected = { actions.onDraftChanged(draft.copy(endDate = it)) },
                onTimeSelected = { actions.onDraftChanged(draft.copy(endTime = it)) },
            )
            if (draft.kind.hasRoute) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        value = draft.origin,
                        label = stringResource(R.string.booking_field_origin),
                        onValueChange = { actions.onDraftChanged(draft.copy(origin = it)) },
                        modifier = Modifier.weight(1f),
                    )
                    TextField(
                        value = draft.destination,
                        label = stringResource(R.string.booking_field_destination),
                        onValueChange = { actions.onDraftChanged(draft.copy(destination = it)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            TextField(
                value = draft.reference,
                label = stringResource(R.string.booking_field_reference),
                onValueChange = { actions.onDraftChanged(draft.copy(reference = it)) },
                capitalization = KeyboardCapitalization.Characters,
            )
            TextField(
                value = draft.provider,
                label = stringResource(R.string.booking_field_provider),
                onValueChange = { actions.onDraftChanged(draft.copy(provider = it)) },
            )
            if (!draft.kind.hasRoute || draft.address.isNotEmpty()) {
                TextField(
                    value = draft.address,
                    label = stringResource(R.string.booking_field_address),
                    onValueChange = { actions.onDraftChanged(draft.copy(address = it)) },
                )
            }
            TextField(
                value = draft.notes,
                label = stringResource(R.string.booking_field_notes),
                onValueChange = { actions.onDraftChanged(draft.copy(notes = it)) },
                singleLine = false,
                capitalization = KeyboardCapitalization.Sentences,
            )
            AttachmentRow(state, actions)
            Button(onClick = actions.onSave, enabled = draft.isComplete && !state.isReading, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.pending.isNotEmpty()) R.string.booking_save_next else R.string.booking_save))
            }
            if (!draft.isComplete) {
                Text(
                    text = stringResource(R.string.booking_save_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.booking_delete_title)) },
            text = { Text(stringResource(R.string.booking_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    actions.onDelete()
                }) { Text(stringResource(R.string.booking_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.booking_cancel)) } },
        )
    }
}

@Composable
private fun ImportCard(state: BookingEditorUiState, actions: BookingEditorActions) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.booking_import_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.booking_import_text), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = state.pastedText,
                onValueChange = actions.onPastedTextChanged,
                placeholder = { Text(stringResource(R.string.booking_import_hint)) },
                minLines = 2,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().testTag(BOOKING_PASTE_FIELD_TAG),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = actions.onReadPastedText, enabled = state.pastedText.isNotBlank() && !state.isReading) {
                    Text(stringResource(R.string.booking_import_read))
                }
                OutlinedButton(onClick = actions.onPickDocument, enabled = !state.isReading) {
                    Text(stringResource(R.string.booking_import_document))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KindChips(selected: BookingKind, onSelected: (BookingKind) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BookingKind.entries.forEach { kind ->
            FilterChip(
                selected = kind == selected,
                onClick = { onSelected(kind) },
                label = { Text(kind.emoji() + " " + stringResource(kind.labelRes())) },
            )
        }
    }
}

@Composable
private fun TextField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(capitalization = capitalization),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Giorno e ora (facoltativa) d'inizio o di fine, ciascuno nella sua cella. */
@Composable
private fun DateTimeRow(
    label: String,
    date: LocalDate?,
    time: LocalTime?,
    today: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onTimeSelected: (LocalTime?) -> Unit,
    dateTag: String? = null,
) {
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Cell(
            label = label,
            value = date?.let { Formatters.weekdayDayMonth(it) + " " + it.year },
            placeholder = stringResource(R.string.booking_pick_date),
            onClick = { pickingDate = true },
            modifier = Modifier.weight(2f).let { if (dateTag != null) it.testTag(dateTag) else it },
        )
        Cell(
            label = stringResource(R.string.booking_field_time),
            value = time?.let(Formatters::time),
            placeholder = stringResource(R.string.booking_pick_time),
            onClick = { pickingTime = true },
            modifier = Modifier.weight(1f),
        )
    }
    if (pickingDate) {
        DateDialog(
            initial = date ?: today,
            onDismiss = { pickingDate = false },
            onConfirm = {
                pickingDate = false
                onDateSelected(it)
            },
        )
    }
    if (pickingTime) {
        TimeDialog(
            initial = time,
            onDismiss = { pickingTime = false },
            onConfirm = {
                pickingTime = false
                onTimeSelected(it)
            },
        )
    }
}

@Composable
private fun Cell(label: String, value: String?, placeholder: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(onClick = onClick, modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value ?: placeholder,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (value != null) FontWeight.SemiBold else FontWeight.Normal,
                color = if (value != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toEpochDay() * MILLIS_PER_DAY,
        yearRange = (initial.year - 1)..(initial.year + 2),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onConfirm(LocalDate.ofEpochDay(Math.floorDiv(it, MILLIS_PER_DAY))) } },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.booking_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.booking_cancel)) } },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

/** Ora con l'inserimento da tastiera, a 24 ore; «Senza ora» la toglie (i promemoria arrivano la sera prima). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime?, onDismiss: () -> Unit, onConfirm: (LocalTime?) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial?.hour ?: DEFAULT_HOUR, initialMinute = initial?.minute ?: 0, is24Hour = true)
    FormDialog(
        title = stringResource(R.string.booking_field_time),
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.booking_ok)) } },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { onConfirm(null) }) { Text(stringResource(R.string.booking_time_clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.booking_cancel)) }
            }
        },
    ) {
        TimeInput(state = state)
    }
}

private const val DEFAULT_HOUR = 9

@Composable
private fun AttachmentRow(state: BookingEditorUiState, actions: BookingEditorActions) {
    val attachment = state.attachment
    if (attachment == null) {
        OutlinedButton(onClick = actions.onPickDocument, enabled = !state.isReading, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.booking_attach))
        }
        return
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (attachment.mimeType.startsWith("image/")) "🖼️ " + stringResource(R.string.booking_attachment_image) else "📄 " + stringResource(R.string.booking_attachment_pdf),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = actions.onOpenDocument) { Text(stringResource(R.string.booking_attachment_open)) }
            TextButton(onClick = actions.onRemoveAttachment) { Text(stringResource(R.string.booking_attachment_remove)) }
        }
    }
}

private fun BookingEditorMessage.textRes(): Int = when (this) {
    BookingEditorMessage.NOTHING_FOUND -> R.string.booking_message_nothing_found
    BookingEditorMessage.READ_FAILED -> R.string.booking_message_read_failed
    BookingEditorMessage.MODEL_DOWNLOADING -> R.string.booking_message_model_downloading
    BookingEditorMessage.DOCUMENT_TOO_LARGE -> R.string.booking_message_too_large
    BookingEditorMessage.SAVED_NEXT -> R.string.booking_message_saved_next
}

private fun BookingKind.titleHintRes(): Int = when (this) {
    BookingKind.FLIGHT -> R.string.booking_title_hint_flight
    BookingKind.LODGING -> R.string.booking_title_hint_lodging
    BookingKind.TRAIN -> R.string.booking_title_hint_train
    BookingKind.BUS -> R.string.booking_title_hint_bus
    BookingKind.CAR_RENTAL -> R.string.booking_title_hint_car
    BookingKind.ACTIVITY -> R.string.booking_title_hint_activity
    BookingKind.OTHER -> R.string.booking_title_hint_other
}

private fun BookingKind.startLabelRes(): Int = when (this) {
    BookingKind.FLIGHT, BookingKind.TRAIN, BookingKind.BUS -> R.string.booking_field_departure
    BookingKind.LODGING -> R.string.booking_field_check_in
    BookingKind.CAR_RENTAL -> R.string.booking_field_pick_up
    BookingKind.ACTIVITY, BookingKind.OTHER -> R.string.booking_field_start
}

private fun BookingKind.endLabelRes(): Int = when (this) {
    BookingKind.FLIGHT, BookingKind.TRAIN, BookingKind.BUS -> R.string.booking_field_arrival
    BookingKind.LODGING -> R.string.booking_field_check_out
    BookingKind.CAR_RENTAL -> R.string.booking_field_drop_off
    BookingKind.ACTIVITY, BookingKind.OTHER -> R.string.booking_field_end
}

// ---- Anteprime -----------------------------------------------------------------------------------

@Preview(name = "Nuova prenotazione", showBackground = true, heightDp = 1100)
@Composable
private fun BookingEditorNewPreview() {
    PartiMoTheme { BookingEditorScreen(PreviewData.bookingEditorState(), BookingEditorActions()) }
}

@Preview(name = "Prenotazione letta · tema scuro", showBackground = true, heightDp = 1100, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BookingEditorReadPreview() {
    PartiMoTheme(darkTheme = true) { BookingEditorScreen(PreviewData.bookingEditorReadState(), BookingEditorActions()) }
}
