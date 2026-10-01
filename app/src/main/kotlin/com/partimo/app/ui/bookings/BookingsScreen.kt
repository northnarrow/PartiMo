package com.partimo.app.ui.bookings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.partimo.app.R
import com.partimo.app.files.BookingDocuments
import com.partimo.app.ui.common.Formatters
import com.partimo.app.ui.dashboard.PreviewData
import com.partimo.app.ui.place.ExternalLinks
import com.partimo.app.ui.place.googleMapsSearchUrl
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.model.booking.Booking
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Tag della lista delle prenotazioni, usato dai test UI per lo scroll. */
const val BOOKINGS_LIST_TAG = "bookings_list"

/** Azioni della linea del tempo (predefinite vuote per anteprime e test). */
data class BookingsActions(
    val onBack: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onEdit: (Booking) -> Unit = {},
    val onOpenDocument: (Booking) -> Unit = {},
    val onCopyReference: (String) -> Unit = {},
    val onOpenAddress: (String) -> Unit = {},
    val onTogglePast: () -> Unit = {},
)

@Composable
fun BookingsRoute(
    viewModel: BookingsViewModel,
    documents: BookingDocuments,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Booking) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()
    val noApp = stringResource(R.string.booking_document_no_app)
    val missing = stringResource(R.string.booking_document_missing)
    val copied = stringResource(R.string.booking_reference_copied)
    BookingsScreen(
        state = state,
        actions = BookingsActions(
            onBack = onBack,
            onAdd = onAdd,
            onEdit = onEdit,
            onOpenDocument = { booking ->
                when (booking.attachment?.let { openStoredDocument(context, documents, it, booking.attachmentType) }) {
                    DocumentOpening.OPENED -> Unit
                    DocumentOpening.NO_APP -> Toast.makeText(context, noApp, Toast.LENGTH_LONG).show()
                    DocumentOpening.MISSING, null -> Toast.makeText(context, missing, Toast.LENGTH_LONG).show()
                }
            },
            onCopyReference = { reference ->
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(reference, reference))
                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            },
            onOpenAddress = { address -> ExternalLinks.openLink(context, googleMapsSearchUrl(address), toolbarColor) },
            onTogglePast = viewModel::onTogglePast,
        ),
        modifier = modifier,
    )
}

/** Esito dell'apertura di un documento: aperto, nessuna app adatta, file non più sul telefono (es. dopo un ripristino). */
internal enum class DocumentOpening { OPENED, NO_APP, MISSING }

/** Apre il documento [name] con l'app adatta (lettore PDF, galleria), con il permesso di leggerlo. */
internal fun openStoredDocument(context: Context, documents: BookingDocuments, name: String, mimeType: String?): DocumentOpening {
    val uri = documents.shareableUri(name) ?: return DocumentOpening.MISSING
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(uri), mimeType ?: "*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return if (runCatching { context.startActivity(intent) }.isSuccess) DocumentOpening.OPENED else DocumentOpening.NO_APP
}

/**
 * Le mie prenotazioni: voli, alloggi, treni e attività in una linea del tempo, giorno per giorno, consultabile
 * anche senza rete. Dalla dashboard di un viaggio mostra solo quelle delle sue date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingsScreen(state: BookingsUiState, actions: BookingsActions, modifier: Modifier = Modifier) {
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
                        Text("🎫 " + stringResource(R.string.bookings_title), fontWeight = FontWeight.Bold)
                        state.tripName?.let { name ->
                            Text(
                                text = stringResource(R.string.bookings_for_trip, name),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions.onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.bookings_add)) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag(BOOKINGS_LIST_TAG),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.isEmpty) {
                item(key = "empty") { EmptyBookings(onAdd = actions.onAdd) }
            }
            state.upcoming.forEach { day ->
                item(key = "day-${day.date}") { DayHeader(day.date, state.today) }
                items(day.bookings, key = { it.id }) { booking -> BookingCard(booking, actions) }
            }
            if (state.past.isNotEmpty()) {
                item(key = "past-toggle") {
                    TextButton(onClick = actions.onTogglePast) {
                        Text(
                            if (state.showPast) {
                                stringResource(R.string.bookings_hide_past)
                            } else {
                                pluralStringResource(R.plurals.bookings_show_past, state.past.size, state.past.size)
                            },
                        )
                    }
                }
                if (state.showPast) items(state.past, key = { "past-" + it.id }) { booking -> BookingCard(booking, actions, past = true) }
            }
        }
    }
}

@Composable
private fun EmptyBookings(onAdd: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = stringResource(R.string.bookings_empty_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(text = stringResource(R.string.bookings_empty_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAdd) { Text(stringResource(R.string.bookings_add)) }
    }
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate) {
    val days = ChronoUnit.DAYS.between(today, date).toInt()
    val relative = when {
        days == 0 -> stringResource(R.string.bookings_today)
        days == 1 -> stringResource(R.string.bookings_tomorrow)
        days > 1 -> pluralStringResource(R.plurals.bookings_in_days, days, days)
        else -> null
    }
    Text(
        text = "📅 " + listOfNotNull(Formatters.weekdayDayMonth(date) + " " + date.year, relative).joinToString(" · "),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookingCard(booking: Booking, actions: BookingsActions, past: Boolean = false) {
    OutlinedCard(onClick = { actions.onEdit(booking) }, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = booking.kind.emoji(), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = booking.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = (if (past) Formatters.weekdayDayMonth(booking.startDate) + " · " else "") + booking.whenText(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            booking.routeText?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
            booking.provider?.takeIf { it !in booking.title }?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (booking.notes.isNotBlank()) Text(text = booking.notes, style = MaterialTheme.typography.bodySmall)
            // Codice, documento e indirizzo a portata di tocco; la riga va a capo se non ci stanno.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                booking.reference?.let { reference ->
                    TextButton(onClick = { actions.onCopyReference(reference) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("🔖 " + stringResource(R.string.booking_reference_value, reference))
                    }
                }
                if (booking.attachment != null) {
                    TextButton(onClick = { actions.onOpenDocument(booking) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("📄 " + stringResource(R.string.booking_open_document))
                    }
                }
                booking.address?.let { address ->
                    TextButton(onClick = { actions.onOpenAddress(address) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("📍 $address")
                    }
                }
            }
        }
    }
}

// ---- Anteprime -----------------------------------------------------------------------------------

@Preview(name = "Prenotazioni", showBackground = true, heightDp = 900)
@Composable
private fun BookingsPreview() {
    PartiMoTheme { BookingsScreen(PreviewData.bookingsState(), BookingsActions()) }
}

@Preview(name = "Prenotazioni · tema scuro", showBackground = true, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BookingsDarkPreview() {
    PartiMoTheme(darkTheme = true) { BookingsScreen(PreviewData.bookingsState(), BookingsActions()) }
}
