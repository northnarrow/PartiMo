package com.partimo.app.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.partimo.app.R
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import kotlin.math.roundToInt

/**
 * Sezione della dashboard: intestazione comune e rendering dei quattro stati UI
 * (Loading, Success, Empty, Error). [headerContent] resta visibile in ogni stato,
 * così i controlli (es. filtri) permettono sempre di cambiare la richiesta.
 */
@Composable
fun <T> DashboardSection(
    title: String,
    state: UiState<T>,
    emptyMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    headerContent: (@Composable () -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        SectionHeader(title = title, subtitle = subtitle, origin = (state as? UiState.Success<*>)?.origin)
        headerContent?.let { header ->
            Box(Modifier.padding(horizontal = 16.dp)) { header() }
        }
        Spacer(Modifier.height(8.dp))
        when (state) {
            UiState.Loading -> SectionLoading()
            UiState.Empty -> SectionMessage(emptyMessage)
            is UiState.Error -> SectionError(error = state.error, onRetry = onRetry)
            is UiState.Success -> content(state.data)
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String?, origin: DataOrigin?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f, fill = false),
            )
            origin?.let { OriginBadge(it) }
        }
        subtitle?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Segnala dati non "live": dalla cache, offline (scaduti) oppure dimostrativi. */
@Composable
fun OriginBadge(origin: DataOrigin, modifier: Modifier = Modifier) {
    val (label, color) = when (origin) {
        DataOrigin.REMOTE, DataOrigin.LOCAL -> return
        DataOrigin.CACHE -> stringResource(R.string.origin_cache) to MaterialTheme.colorScheme.secondaryContainer
        DataOrigin.STALE_CACHE -> stringResource(R.string.origin_stale) to MaterialTheme.colorScheme.errorContainer
        DataOrigin.DEMO -> stringResource(R.string.origin_demo) to MaterialTheme.colorScheme.tertiaryContainer
    }
    Surface(shape = CircleShape, color = color, modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Segnaposto animato mostrato durante il caricamento. */
@Composable
fun SectionLoading(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.loading)
    val transition = rememberInfiniteTransition(label = "placeholder")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 800), RepeatMode.Reverse),
        label = "placeholderAlpha",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(2) {
            Box(
                Modifier
                    .weight(1f)
                    .height(120.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
fun SectionMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun SectionError(error: DataError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                text = errorMessage(error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

/** Barra del punteggio qualità/prezzo (0–100%). */
@Composable
fun ValueScoreBar(score: Double, modifier: Modifier = Modifier) {
    val percent = (score * 100).roundToInt().coerceIn(0, 100)
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
        )
        Text(
            text = stringResource(R.string.value_score, percent),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun BestValueBadge(modifier: Modifier = Modifier) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = modifier) {
        Text(
            text = "⭐ " + stringResource(R.string.best_value_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
