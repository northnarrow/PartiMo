package com.partimo.app.ui.about

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.partimo.app.BuildConfig
import com.partimo.app.R
import com.partimo.app.ui.place.ExternalLinks
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.model.backup.UserDataSummary

/** Tag della lista delle fonti, usato dai test UI per lo scroll. */
const val ABOUT_LIST_TAG = "about_list"

/** Fonte o servizio usato dall'app, con il collegamento alla sua pagina (licenza o termini). */
data class SourceEntry(val name: String, @StringRes val detail: Int, val url: String)

val DataSources = listOf(
    SourceEntry("Wikipedia e Wikimedia Commons", R.string.about_src_wikipedia, "https://creativecommons.org/licenses/by-sa/4.0/deed.it"),
    SourceEntry("Wikivoyage", R.string.about_src_wikivoyage, "https://it.wikivoyage.org/wiki/Wikivoyage:Copyright"),
    SourceEntry("Wikidata", R.string.about_src_wikidata, "https://www.wikidata.org/wiki/Wikidata:Licensing"),
    SourceEntry("OpenStreetMap", R.string.about_src_osm, "https://www.openstreetmap.org/copyright"),
    SourceEntry("Open-Meteo", R.string.about_src_openmeteo, "https://open-meteo.com/en/license"),
    SourceEntry("Nager.Date", R.string.about_src_nager, "https://date.nager.at"),
    SourceEntry("ExchangeRate-API", R.string.about_src_exchangerate, "https://www.exchangerate-api.com"),
    SourceEntry("OurAirports", R.string.about_src_ourairports, "https://ourairports.com/data/"),
    SourceEntry("DESNZ (governo britannico)", R.string.about_src_desnz, "https://www.gov.uk/government/publications/greenhouse-gas-reporting-conversion-factors-2025"),
    SourceEntry("Viaggiare Sicuri", R.string.about_src_viaggiaresicuri, "https://www.viaggiaresicuri.it"),
)

val MapSources = listOf(
    SourceEntry("OpenFreeMap", R.string.about_map_openfreemap, "https://openfreemap.org"),
    SourceEntry("OpenMapTiles", R.string.about_map_openmaptiles, "https://openmaptiles.org"),
    SourceEntry("MapLibre Native", R.string.about_map_maplibre, "https://maplibre.org"),
)

val ConnectedServices = listOf(
    SourceEntry("Google Gemini", R.string.about_service_gemini, "https://ai.google.dev/gemini-api/terms"),
    SourceEntry("Aviasales · Travelpayouts", R.string.about_service_travelpayouts, "https://support.travelpayouts.com/hc/en-us/articles/203956163-Aviasales-Data-API"),
    SourceEntry("ML Kit", R.string.about_service_mlkit, "https://developers.google.com/ml-kit/terms"),
)

/** Tipi di file accettati quando si importa un backup (alcune app indicano il JSON come testo o binario). */
private val BACKUP_MIME_TYPES = arrayOf("application/json", "text/*", "application/octet-stream")

/** Azioni della sezione «I tuoi dati» (predefinite vuote per anteprime e test). */
data class BackupActions(
    val onExport: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@Composable
fun AboutRoute(backupViewModel: BackupViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()
    val noBrowser = stringResource(R.string.place_no_browser)
    val backup by backupViewModel.uiState.collectAsStateWithLifecycle()
    // Il file lo sceglie l'utente con il selettore di Android: nessun permesso di archiviazione.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { backupViewModel.onExport(it.toString()) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { backupViewModel.onImport(it.toString()) }
    }
    AboutScreen(
        version = BuildConfig.VERSION_NAME,
        onBack = onBack,
        onOpenLink = { url -> if (!ExternalLinks.openLink(context, url, toolbarColor)) Toast.makeText(context, noBrowser, Toast.LENGTH_LONG).show() },
        modifier = modifier,
        backup = backup,
        backupActions = BackupActions(
            onExport = { exportLauncher.launch(backup.suggestedFileName) },
            onImport = { importLauncher.launch(BACKUP_MIME_TYPES) },
            onMessageShown = backupViewModel::onMessageShown,
        ),
    )
}

/**
 * I tuoi dati, fonti, licenze e privacy: backup dei dati salvati sul telefono, da dove arrivano i dati
 * (con le licenze che chiedono l'attribuzione), cosa resta sul telefono e cosa va ai servizi esterni.
 * Senza [backup] la sezione dei dati non compare.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    version: String,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    backup: BackupUiState? = null,
    backupActions: BackupActions = BackupActions(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = backup?.message?.let { it.text() }
    val onMessageShown by rememberUpdatedState(backupActions.onMessageShown)
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message, withDismissAction = true)
            onMessageShown()
        }
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text("ⓘ " + stringResource(R.string.about_title), fontWeight = FontWeight.Bold) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag(ABOUT_LIST_TAG),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "version") {
                Text(
                    text = stringResource(R.string.about_version, version),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
                Text(
                    text = stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            backup?.let { backupSection(it, backupActions) }
            sectionTitle(R.string.about_privacy)
            items(
                listOf(
                    R.string.about_privacy_device,
                    R.string.about_privacy_location,
                    R.string.about_privacy_ai,
                    R.string.about_privacy_flights,
                    R.string.about_privacy_translator,
                    R.string.about_privacy_sources,
                ),
            ) { text ->
                Text(
                    text = "• " + stringResource(text),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                )
            }
            sources(R.string.about_sources, DataSources, onOpenLink)
            sources(R.string.about_maps, MapSources, onOpenLink)
            sources(R.string.about_services, ConnectedServices, onOpenLink)
            item(key = "links") {
                Text(
                    text = stringResource(R.string.about_service_links),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            sectionTitle(R.string.about_libraries)
            item(key = "libraries") {
                Text(
                    text = stringResource(R.string.about_libraries_text),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

/** «I tuoi dati»: cosa c'è sul telefono, con esportazione in un file e importazione. */
private fun LazyListScope.backupSection(backup: BackupUiState, actions: BackupActions) {
    sectionTitle(R.string.backup_title)
    item(key = "backup") {
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.backup_text), style = MaterialTheme.typography.bodyMedium)
            backup.summary?.let { summary ->
                Text(
                    text = if (summary.isEmpty) stringResource(R.string.backup_summary_empty) else stringResource(R.string.backup_summary, summary.describe()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = actions.onExport, enabled = !backup.isWorking, modifier = Modifier.weight(1f)) {
                    Text("📤 " + stringResource(R.string.backup_export))
                }
                OutlinedButton(onClick = actions.onImport, enabled = !backup.isWorking, modifier = Modifier.weight(1f)) {
                    Text("📥 " + stringResource(R.string.backup_import))
                }
            }
        }
    }
}

/** "2 viaggi salvati · 5 preferiti · 1 avviso": le voci a zero non compaiono. */
@Composable
fun UserDataSummary.describe(): String = buildList {
    if (trips > 0) add(pluralStringResource(R.plurals.backup_trips, trips, trips))
    if (favorites > 0) add(pluralStringResource(R.plurals.backup_favorites, favorites, favorites))
    if (alerts > 0) add(pluralStringResource(R.plurals.backup_alerts, alerts, alerts))
    if (expenses > 0) add(pluralStringResource(R.plurals.backup_expenses, expenses, expenses))
    if (checkedItems > 0) add(pluralStringResource(R.plurals.backup_checked_items, checkedItems, checkedItems))
    if (hasDeparture) add(stringResource(R.string.backup_departure))
}.joinToString(" · ")

@Composable
private fun BackupMessage.text(): String = when (this) {
    is BackupMessage.Exported -> stringResource(R.string.backup_exported, summary.describeOrNothing())
    is BackupMessage.Imported -> if (summary.isEmpty) stringResource(R.string.backup_imported_empty) else stringResource(R.string.backup_imported, summary.describe())
    BackupMessage.ExportFailed -> stringResource(R.string.backup_export_failed)
    BackupMessage.ImportFailed -> stringResource(R.string.backup_import_failed)
    BackupMessage.NotABackup -> stringResource(R.string.backup_not_a_backup)
}

@Composable
private fun UserDataSummary.describeOrNothing(): String = if (isEmpty) stringResource(R.string.backup_nothing) else describe()

private fun LazyListScope.sectionTitle(@StringRes title: Int) {
    item(key = "title-$title") {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        )
    }
}

private fun LazyListScope.sources(@StringRes title: Int, entries: List<SourceEntry>, onOpenLink: (String) -> Unit) {
    sectionTitle(title)
    items(entries, key = { it.url }) { entry ->
        ListItem(
            modifier = Modifier.clickable { onOpenLink(entry.url) },
            headlineContent = { Text(entry.name + " ↗", fontWeight = FontWeight.Medium) },
            supportingContent = { Text(stringResource(entry.detail)) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

// ---- Anteprime -----------------------------------------------------------------------------------

@Preview(name = "Fonti e licenze", showBackground = true, heightDp = 900)
@Composable
private fun AboutPreview() {
    PartiMoTheme { AboutScreen(version = "1.0.0", onBack = {}, onOpenLink = {}, backup = PreviewBackup) }
}

/** Sezione «I tuoi dati» delle anteprime e degli screenshot. */
val PreviewBackup = BackupUiState(
    suggestedFileName = "PartiMo-backup-2026-10-01.json",
    summary = UserDataSummary(trips = 3, favorites = 8, alerts = 1, expenses = 12, checkedItems = 9, hasDeparture = true),
)

@Preview(name = "Fonti e licenze · tema scuro", showBackground = true, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AboutDarkPreview() {
    PartiMoTheme(darkTheme = true) { AboutScreen(version = "1.0.0", onBack = {}, onOpenLink = {}) }
}
