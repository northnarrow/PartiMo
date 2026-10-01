package com.partimo.app.ui.translator

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.partimo.app.R
import com.partimo.app.files.CameraPhotos
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.common.errorMessage
import com.partimo.app.ui.dashboard.PreviewData
import com.partimo.app.ui.place.ExternalLinks
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.common.DataError
import com.partimo.domain.model.ocr.PhotoTranslation
import com.partimo.domain.model.ocr.TranslatedBlock
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

/** Tag della lista del traduttore, usato dai test UI per lo scroll. */
const val TRANSLATOR_LIST_TAG = "translator_list"

/** Pacchetto dell'app Google Traduttore: fotocamera, conversazione e scrittura a mano. */
private const val GOOGLE_TRANSLATE_PACKAGE = "com.google.android.apps.translate"

/** Categoria del frasario: titolo e frasi nelle risorse, così seguono la lingua dell'app. */
data class PhraseCategory(val emoji: String, @StringRes val title: Int, @ArrayRes val phrases: Int)

val PhraseCategories = listOf(
    PhraseCategory("👋", R.string.phrases_basics, R.array.phrases_basics_items),
    PhraseCategory("🍽️", R.string.phrases_restaurant, R.array.phrases_restaurant_items),
    PhraseCategory("🚆", R.string.phrases_transport, R.array.phrases_transport_items),
    PhraseCategory("🏨", R.string.phrases_hotel, R.array.phrases_hotel_items),
    PhraseCategory("🛍️", R.string.phrases_shopping, R.array.phrases_shopping_items),
    PhraseCategory("🆘", R.string.phrases_emergency, R.array.phrases_emergency_items),
)

data class TranslatorActions(
    val onBack: () -> Unit = {},
    val onInputChanged: (String) -> Unit = {},
    val onClear: () -> Unit = {},
    val onSwap: () -> Unit = {},
    val onForeignLanguageSelected: (String) -> Unit = {},
    val onDownloadPacks: () -> Unit = {},
    val onTranslate: () -> Unit = {},
    val onTranslatePhrases: (List<String>) -> Unit = {},
    /** Pronuncia un testo nella lingua indicata. */
    val onSpeak: (text: String, language: String) -> Unit = { _, _ -> },
    val onCopy: (String) -> Unit = {},
    /** Apre Google Traduttore (app o sito) con il testo, per la conversazione e le scritture che il telefono non legge. */
    val onOpenGoogleTranslate: (text: String, from: String, to: String) -> Unit = { _, _, _ -> },
    /** Foto da tradurre: scatto con la fotocamera, scelta dalla galleria, originale o traduzione, chiusura. */
    val onTakePhoto: () -> Unit = {},
    val onPickPhoto: () -> Unit = {},
    val onPhotoOriginalToggled: () -> Unit = {},
    val onPhotoClosed: () -> Unit = {},
)

@Composable
fun TranslatorRoute(viewModel: TranslatorViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val speaker = rememberSpeaker()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()
    val noVoice = stringResource(R.string.translator_no_voice)
    val copied = stringResource(R.string.translator_copied)
    val noApp = stringResource(R.string.place_no_browser)
    val noCamera = stringResource(R.string.translator_photo_no_camera)
    // Indirizzo della foto in corso: sopravvive alla chiusura dell'app mentre la fotocamera è aperta.
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingPhoto
        pendingPhoto = null
        if (saved && uri != null) viewModel.onPhotoSelected(uri, "image/jpeg")
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { viewModel.onPhotoSelected(it.toString(), context.contentResolver.getType(it)) }
    }
    TranslatorScreen(
        state = state,
        actions = TranslatorActions(
            onBack = onBack,
            onInputChanged = viewModel::onInputChanged,
            onClear = viewModel::onClear,
            onSwap = viewModel::onSwap,
            onForeignLanguageSelected = viewModel::onForeignLanguageSelected,
            onDownloadPacks = viewModel::onDownloadPacks,
            onTranslate = viewModel::onTranslate,
            onTranslatePhrases = viewModel::onTranslatePhrases,
            onSpeak = { text, language -> if (!speaker.speak(text, language)) Toast.makeText(context, noVoice, Toast.LENGTH_LONG).show() },
            onCopy = { text ->
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("PartiMo", text))) }
                // Da Android 13 il sistema mostra già la conferma della copia.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            },
            onOpenGoogleTranslate = { text, from, to ->
                if (!openGoogleTranslate(context, text, from, to, toolbarColor)) Toast.makeText(context, noApp, Toast.LENGTH_LONG).show()
            },
            onTakePhoto = {
                val uri = CameraPhotos.newPhotoUri(context)
                pendingPhoto = uri.toString()
                try {
                    takePicture.launch(uri)
                } catch (e: ActivityNotFoundException) {
                    pendingPhoto = null
                    Toast.makeText(context, noCamera, Toast.LENGTH_LONG).show()
                }
            },
            onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onPhotoOriginalToggled = viewModel::onPhotoOriginalToggled,
            onPhotoClosed = viewModel::onPhotoClosed,
        ),
        modifier = modifier,
    )
}

/**
 * Traduttore del viaggio: dalla lingua dell'utente a quella del posto (e viceversa per cartelli e
 * menù), con i pacchetti lingua scaricati una volta e poi offline, la pronuncia, "Mostra in grande"
 * per far leggere la traduzione a chi si ha davanti, la traduzione delle foto (menù, cartelli) e un
 * frasario pronto per categoria.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorScreen(state: TranslatorUiState, actions: TranslatorActions, modifier: Modifier = Modifier) {
    val user = state.userLanguage
    var expandedCategory by rememberSaveable { mutableStateOf<Int?>(null) }
    var bigText by remember { mutableStateOf<TranslatedText?>(null) }
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
                            text = "🗣️ " + stringResource(R.string.translator_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = state.destination.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag(TRANSLATOR_LIST_TAG),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "languages") { LanguageBar(state = state, actions = actions) }
            item(key = "packs") { PacksStatus(state = state, actions = actions) }
            item(key = "input") { InputSection(state = state, actions = actions) }
            item(key = "result") { ResultSection(state = state, actions = actions, onShowBig = { bigText = it }) }
            item(key = "photo") { PhotoSection(state = state, actions = actions) }
            item(key = "phrases-title") {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp)) {
                    Text(
                        text = "💬 " + stringResource(R.string.translator_phrases),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.translator_phrases_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            PhraseCategories.forEachIndexed { index, category ->
                item(key = "category-$index") {
                    val expanded = expandedCategory == index
                    val phrases = stringArrayResource(category.phrases).toList()
                    if (expanded) {
                        // Le frasi si traducono quando la categoria è aperta e i pacchetti sono pronti.
                        LaunchedEffect(state.foreignLanguage, state.packs) { actions.onTranslatePhrases(phrases) }
                    }
                    PhraseCategoryItem(
                        category = category,
                        phrases = phrases,
                        expanded = expanded,
                        state = state,
                        onToggle = { expandedCategory = if (expanded) null else index },
                        onSpeak = actions.onSpeak,
                    )
                }
            }
        }
    }
    bigText?.let { text -> BigTextDialog(text = text, userLanguage = user, onDismiss = { bigText = null }) }
}

@Composable
private fun LanguageBar(state: TranslatorUiState, actions: TranslatorActions) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // La lingua dell'utente è fissa: un'etichetta, non un pulsante.
        val userChip = @Composable { modifier: Modifier ->
            Surface(
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = modifier.height(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = languageName(state.userLanguage, state.userLanguage),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        val foreignChip = @Composable { modifier: Modifier -> ForeignLanguagePicker(state = state, onSelected = actions.onForeignLanguageSelected, modifier = modifier) }
        if (state.reversed) foreignChip(Modifier.weight(1f)) else userChip(Modifier.weight(1f))
        val swapDescription = stringResource(R.string.translator_swap)
        IconButton(onClick = actions.onSwap, modifier = Modifier.semantics { contentDescription = swapDescription }) {
            Text("⇄", style = MaterialTheme.typography.titleLarge)
        }
        if (state.reversed) userChip(Modifier.weight(1f)) else foreignChip(Modifier.weight(1f))
    }
}

@Composable
private fun ForeignLanguagePicker(state: TranslatorUiState, onSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val foreign = state.foreignLanguage
    Box(modifier = modifier) {
        FilledTonalButton(onClick = { open = true }, enabled = foreign != null, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = (foreign?.let { languageName(it, state.userLanguage) } ?: "…") + " ▾",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val others = state.supportedLanguages.filter { it != state.userLanguage && it !in state.countryLanguages }
                .sortedBy { languageName(it, state.userLanguage) }
            if (state.countryLanguages.isNotEmpty()) {
                MenuHeader(stringResource(R.string.translator_country_languages))
                state.countryLanguages.forEach { code ->
                    DropdownMenuItem(text = { Text(languageName(code, state.userLanguage)) }, onClick = { open = false; onSelected(code) })
                }
                HorizontalDivider()
                MenuHeader(stringResource(R.string.translator_all_languages))
            }
            others.forEach { code ->
                DropdownMenuItem(text = { Text(languageName(code, state.userLanguage)) }, onClick = { open = false; onSelected(code) })
            }
        }
    }
}

@Composable
private fun MenuHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun PacksStatus(state: TranslatorUiState, actions: TranslatorActions) {
    val user = state.userLanguage
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        when (val packs = state.packs) {
            LanguagePackState.Checking -> Text(
                text = stringResource(R.string.translator_checking),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is LanguagePackState.Missing -> Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "📦 " + stringResource(R.string.translator_pack_missing, packs.languages.sorted().joinToString { languageName(it, user, capitalize = false) }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Button(onClick = actions.onDownloadPacks) { Text(stringResource(R.string.translator_download)) }
                }
            }
            LanguagePackState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.translator_downloading), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is LanguagePackState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.translator_download_failed, errorMessage(packs.error)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = actions.onDownloadPacks) { Text(stringResource(R.string.action_retry)) }
            }
            LanguagePackState.Ready -> Text(
                text = stringResource(R.string.translator_ready),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InputSection(state: TranslatorUiState, actions: TranslatorActions) {
    val from = state.from
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.input,
            onValueChange = actions.onInputChanged,
            label = { Text(stringResource(R.string.translator_input_label, from?.let { languageName(it, state.userLanguage, capitalize = false) } ?: "…")) },
            minLines = 2,
            maxLines = 6,
            trailingIcon = if (state.input.isNotEmpty()) {
                { IconButton(onClick = actions.onClear) { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.translator_clear)) } }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = actions.onTranslate, enabled = state.canTranslate) { Text(stringResource(R.string.translator_translate)) }
    }
}

/** Tag della sezione delle foto, usato dai test UI. */
const val TRANSLATOR_PHOTO_TAG = "translator_photo"

/**
 * «Traduci con la fotocamera»: si scatta o si sceglie la foto di un menù o di un cartello e la traduzione compare
 * sopra il testo originale, blocco per blocco, con l'elenco delle traduzioni sotto la foto.
 */
@Composable
private fun PhotoSection(state: TranslatorUiState, actions: TranslatorActions) {
    if (!state.photoAvailable) return
    val user = state.userLanguage
    val foreign = state.foreignLanguage
    val foreignName = foreign?.let { languageName(it, user, capitalize = false) } ?: "…"
    Column(modifier = Modifier.padding(top = 16.dp).testTag(TRANSLATOR_PHOTO_TAG)) {
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "📷 " + stringResource(R.string.translator_photo_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (foreign != null && !state.photoScriptSupported) {
                    Text(text = stringResource(R.string.translator_photo_unsupported, foreignName), style = MaterialTheme.typography.bodySmall)
                    AssistChip(onClick = { actions.onOpenGoogleTranslate("", foreign, user) }, label = { Text(stringResource(R.string.translator_google) + " ↗") })
                } else {
                    Text(
                        text = stringResource(R.string.translator_photo_text, foreignName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = actions.onTakePhoto, enabled = state.canTranslatePhoto) { Text(stringResource(R.string.translator_photo_take)) }
                        OutlinedButton(onClick = actions.onPickPhoto, enabled = state.canTranslatePhoto) { Text(stringResource(R.string.translator_photo_pick)) }
                    }
                    if (state.packs != LanguagePackState.Ready) {
                        Text(
                            text = stringResource(R.string.translator_photo_needs_pack),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        state.photo?.let { photo -> PhotoResult(photo = photo, from = foreign, actions = actions) }
    }
}

@Composable
private fun PhotoResult(photo: PhotoState, from: String?, actions: TranslatorActions) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val translation = photo.translation) {
            UiState.Loading -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(text = stringResource(R.string.translator_photo_reading), style = MaterialTheme.typography.bodySmall)
            }
            UiState.Empty -> Unit
            is UiState.Error -> {
                Text(
                    text = when (translation.error) {
                        DataError.NoConnection -> stringResource(R.string.translator_photo_model_downloading)
                        DataError.InvalidResponse -> stringResource(R.string.translator_photo_no_text)
                        else -> stringResource(R.string.translator_failed, errorMessage(translation.error))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = actions.onPhotoClosed) { Text(stringResource(R.string.translator_photo_close)) }
            }
            is UiState.Success -> {
                val result = translation.data
                if (result.hasLayout) PhotoWithTranslation(uri = photo.uri, translation = result, showOriginal = photo.showOriginal)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (result.hasLayout) {
                        FilterChip(
                            selected = photo.showOriginal,
                            onClick = actions.onPhotoOriginalToggled,
                            label = { Text(stringResource(R.string.translator_photo_original)) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = actions.onPhotoClosed) { Text(stringResource(R.string.translator_photo_close)) }
                }
                result.blocks.forEach { block -> PhotoBlockItem(block = block, from = from, onSpeak = actions.onSpeak) }
            }
        }
    }
}

/** La foto con la traduzione di ogni blocco nella sua posizione, scritta grande quanto ci sta. */
@Composable
private fun PhotoWithTranslation(uri: String, translation: PhotoTranslation, showOriginal: Boolean) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(translation.width.toFloat() / translation.height)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceVariant),
    ) {
        AsyncImage(
            model = uri,
            contentDescription = stringResource(R.string.translator_photo_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        if (!showOriginal) {
            // Pixel dello schermo per ogni pixel della foto analizzata.
            val scale = constraints.maxWidth.toFloat() / translation.width
            val density = LocalDensity.current
            translation.blocks.forEach { block ->
                val box = block.original
                if (box.right <= box.left || box.bottom <= box.top) return@forEach
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .offset { IntOffset((box.left * scale).roundToInt(), (box.top * scale).roundToInt()) }
                        .size(with(density) { ((box.right - box.left) * scale).toDp() }, with(density) { ((box.bottom - box.top) * scale).toDp() })
                        .background(colors.surface.copy(alpha = OVERLAY_ALPHA), RoundedCornerShape(4.dp))
                        .padding(horizontal = 2.dp),
                ) {
                    BasicText(
                        text = block.translation,
                        style = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface, fontWeight = FontWeight.Medium),
                        autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = 22.sp, stepSize = 0.5.sp),
                    )
                }
            }
        }
    }
}

private const val OVERLAY_ALPHA = 0.9f

@Composable
private fun PhotoBlockItem(block: TranslatedBlock, from: String?, onSpeak: (String, String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SelectionContainer {
                Text(text = block.translation, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
            Text(text = block.original.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (from != null) {
            val listen = stringResource(R.string.translator_photo_listen)
            IconButton(onClick = { onSpeak(block.original.text, from) }, modifier = Modifier.semantics { contentDescription = listen }) {
                Text("🔊")
            }
        }
    }
    HorizontalDivider()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultSection(state: TranslatorUiState, actions: TranslatorActions, onShowBig: (TranslatedText) -> Unit) {
    when (val result = state.result) {
        null -> Unit
        UiState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        UiState.Empty -> Unit
        is UiState.Error -> Text(
            text = stringResource(R.string.translator_failed, errorMessage(result.error)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        is UiState.Success -> {
            val translated = result.data
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = languageName(translated.language, state.userLanguage),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    SelectionContainer {
                        Text(text = translated.translation, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { actions.onSpeak(translated.translation, translated.language) },
                            label = { Text(stringResource(R.string.translator_listen)) },
                            leadingIcon = { Text("🔊") },
                        )
                        AssistChip(onClick = { actions.onCopy(translated.translation) }, label = { Text(stringResource(R.string.translator_copy)) }, leadingIcon = { Text("📋") })
                        AssistChip(onClick = { onShowBig(translated) }, label = { Text(stringResource(R.string.translator_show_big)) }, leadingIcon = { Text("🔍") })
                        AssistChip(
                            onClick = { actions.onOpenGoogleTranslate(translated.source, state.from.orEmpty(), translated.language) },
                            label = { Text(stringResource(R.string.translator_google) + " ↗") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhraseCategoryItem(
    category: PhraseCategory,
    phrases: List<String>,
    expanded: Boolean,
    state: TranslatorUiState,
    onToggle: () -> Unit,
    onSpeak: (String, String) -> Unit,
) {
    Column {
        ListItem(
            modifier = Modifier.clickable(onClick = onToggle),
            headlineContent = { Text(category.emoji + " " + stringResource(category.title), fontWeight = FontWeight.Medium) },
            trailingContent = { Text(if (expanded) "▴" else "▾") },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        if (expanded) {
            val foreign = state.foreignLanguage
            phrases.forEach { phrase ->
                val translation = state.phraseTranslation(phrase)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = phrase, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = translation ?: if (state.packs == LanguagePackState.Ready) "…" else "—",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (translation != null && foreign != null) {
                        val description = stringResource(R.string.translator_listen_description, translation)
                        IconButton(onClick = { onSpeak(translation, foreign) }, modifier = Modifier.semantics { contentDescription = description }) {
                            Text(text = "🔊")
                        }
                    }
                }
            }
        }
    }
}

/** Traduzione a tutto schermo, da mostrare a chi si ha davanti (un cameriere, un tassista). */
@Composable
private fun BigTextDialog(text: TranslatedText, userLanguage: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = languageName(text.language, userLanguage),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = text.translation,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = text.source,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onDismiss) { Text(stringResource(R.string.translator_close)) }
            }
        }
    }
}

/** Nome della lingua [code] nella lingua [inLanguage], es. "tedesco" (o "Tedesco" con [capitalize]). */
fun languageName(code: String, inLanguage: String, capitalize: Boolean = true): String {
    val locale = Locale.forLanguageTag(inLanguage)
    val name = Locale.forLanguageTag(code).getDisplayLanguage(locale).ifBlank { code }
    return if (capitalize) name.replaceFirstChar { it.titlecase(locale) } else name
}

/** Pagina di Google Traduttore sul web con il testo e le lingue già impostati. */
fun googleTranslateUrl(text: String, from: String, to: String): String =
    "https://translate.google.com/?sl=$from&tl=$to&op=translate&text=" + URLEncoder.encode(text, Charsets.UTF_8.name()).replace("+", "%20")

/** App Google Traduttore se installata (fotocamera e conversazione), altrimenti il sito. */
private fun openGoogleTranslate(context: Context, text: String, from: String, to: String, toolbarColor: Int): Boolean {
    val app = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).setPackage(GOOGLE_TRANSLATE_PACKAGE)
    return try {
        context.startActivity(app)
        true
    } catch (e: ActivityNotFoundException) {
        ExternalLinks.openLink(context, googleTranslateUrl(text, from, to), toolbarColor)
    }
}

// ---- Anteprime -----------------------------------------------------------------------------------

@Preview(name = "Traduttore", showBackground = true, heightDp = 900)
@Composable
private fun TranslatorPreview() {
    PartiMoTheme { TranslatorScreen(PreviewData.translatorState(), TranslatorActions()) }
}

@Preview(name = "Traduttore · foto di un menù", showBackground = true, heightDp = 1400)
@Composable
private fun TranslatorPhotoPreview() {
    PartiMoTheme { TranslatorScreen(PreviewData.translatorPhotoState(), TranslatorActions()) }
}

@Preview(name = "Traduttore · pacchetto da scaricare · tema scuro", showBackground = true, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TranslatorMissingPackDarkPreview() {
    PartiMoTheme(darkTheme = true) { TranslatorScreen(PreviewData.translatorMissingPackState(), TranslatorActions()) }
}
