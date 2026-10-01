package com.partimo.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.partimo.app.R

/** Giallo delle stelle dei preferiti, leggibile sia sul tema chiaro sia su quello scuro. */
private val FavoriteYellow = Color(0xFFF2A900)

/**
 * Stella dei preferiti: piena se l'elemento è salvato. Con [onPhoto] ha uno sfondo, per restare
 * visibile sopra una foto.
 */
@Composable
fun FavoriteButton(isFavorite: Boolean, name: String, onToggle: () -> Unit, modifier: Modifier = Modifier, onPhoto: Boolean = false) {
    val description = stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add, name)
    val state = stringResource(if (isFavorite) R.string.favorite_state_on else R.string.favorite_state_off)
    IconButton(
        onClick = onToggle,
        modifier = modifier.semantics {
            contentDescription = description
            stateDescription = state
            role = Role.Button
        },
    ) {
        val star = @Composable {
            Text(
                text = if (isFavorite) "★" else "☆",
                style = MaterialTheme.typography.titleLarge,
                color = if (isFavorite) FavoriteYellow else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onPhoto) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { star() }
            }
        } else {
            star()
        }
    }
}
