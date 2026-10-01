package com.partimo.app.ui.chat

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.partimo.app.R
import com.partimo.app.ui.common.Formatters
import com.partimo.app.ui.common.SectionError
import com.partimo.app.ui.dashboard.PreviewData
import com.partimo.app.ui.itinerary.AssistantUnavailable
import com.partimo.app.ui.theme.PartiMoTheme
import com.partimo.domain.model.plan.ChatMessage
import com.partimo.domain.model.plan.ChatRole

/** Tag della conversazione, usato dai test UI per lo scroll. */
const val CHAT_LIST_TAG = "chat_list"

/** Azioni di "Chiedi a PartiMo" (predefinite vuote per anteprime e test). */
data class ChatActions(
    val onBack: () -> Unit = {},
    val onInputChanged: (String) -> Unit = {},
    val onSend: () -> Unit = {},
    /** Tocco su una domanda suggerita: viene inviata subito. */
    val onAsk: (String) -> Unit = {},
    val onRetry: () -> Unit = {},
)

@Composable
fun ChatRoute(viewModel: ChatViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChatScreen(
        state = state,
        actions = ChatActions(
            onBack = onBack,
            onInputChanged = viewModel::onInputChanged,
            onSend = viewModel::send,
            onAsk = viewModel::ask,
            onRetry = viewModel::retry,
        ),
        modifier = modifier,
    )
}

/**
 * "Chiedi a PartiMo": conversazione con l'assistente sul viaggio, con domande suggerite per
 * cominciare. Le risposte si possono selezionare e copiare. È stateless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(state: ChatUiState, actions: ChatActions, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    // Ogni nuovo messaggio, l'attesa della risposta o un errore portano in fondo alla conversazione.
    LaunchedEffect(state.messages.size, state.isAnswering, state.error) {
        if (listState.layoutInfo.totalItemsCount > 0) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
    }
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
                            text = "💬 " + stringResource(R.string.chat_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = state.destination.name + " · " + Formatters.dateRange(state.from, state.to),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (state.isAvailable) {
                ChatInput(text = state.input, canSend = state.canSend, onTextChanged = actions.onInputChanged, onSend = actions.onSend)
            }
        },
    ) { innerPadding ->
        if (!state.isAvailable) {
            Box(Modifier.padding(innerPadding)) { AssistantUnavailable() }
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag(CHAT_LIST_TAG),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "intro") { MessageBubble(ChatMessage(ChatRole.ASSISTANT, stringResource(R.string.chat_intro, state.destination.name))) }
            item(key = "privacy") {
                Text(
                    text = stringResource(R.string.chat_privacy),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.messages.isEmpty()) {
                item(key = "suggestions") { Suggestions(cityName = state.destination.name, onAsk = actions.onAsk) }
            }
            itemsIndexed(state.messages, key = { index, _ -> "message-$index" }) { _, message -> MessageBubble(message) }
            if (state.isAnswering) item(key = "thinking") { ThinkingBubble() }
            state.error?.let { error -> item(key = "error") { SectionError(error = error, onRetry = actions.onRetry, modifier = Modifier.padding(horizontal = 0.dp)) } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(cityName: String, onAsk: (String) -> Unit) {
    val questions = listOf(
        stringResource(R.string.chat_suggestion_food, cityName),
        stringResource(R.string.chat_suggestion_airport),
        stringResource(R.string.chat_suggestion_packing),
        stringResource(R.string.chat_suggestion_tipping, cityName),
        stringResource(R.string.chat_suggestion_rain),
        stringResource(R.string.chat_suggestion_free),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        questions.forEach { question ->
            SuggestionChip(onClick = { onAsk(question) }, label = { Text(question) })
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val fromUser = message.role == ChatRole.USER
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (fromUser) 16.dp else 4.dp,
                bottomEnd = if (fromUser) 4.dp else 16.dp,
            ),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            val text = @Composable {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (fromUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            // Le risposte si possono selezionare e copiare (es. un indirizzo o un piatto da cercare).
            if (fromUser) text() else SelectionContainer { text() }
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.size(10.dp))
        Text(
            text = stringResource(R.string.chat_thinking),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChatInput(text: String, canSend: Boolean, onTextChanged: (String) -> Unit, onSend: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChanged,
                placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSend, enabled = canSend) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send))
            }
        }
    }
}

// ---- Anteprime ---------------------------------------------------------------------------------

@Preview(name = "Chiedi a PartiMo · conversazione", showBackground = true, heightDp = 800)
@Composable
private fun ChatPreview() {
    PartiMoTheme { ChatScreen(PreviewData.chatState(), ChatActions()) }
}

@Preview(name = "Chiedi a PartiMo · inizio, tema scuro", showBackground = true, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatEmptyPreview() {
    PartiMoTheme(darkTheme = true) { ChatScreen(PreviewData.chatState().copy(messages = emptyList()), ChatActions()) }
}
