package com.partimo.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Finestra con un modulo (campi di testo, scelte), con l'aspetto di un AlertDialog di Material 3.
 * La larghezza la decide il contenuto e non la piattaforma: così i campi di testo si misurano in modo
 * stabile anche nei test sulla JVM, dove AlertDialog con un campo di testo non si assesta mai.
 */
@Composable
fun FormDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            modifier = modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(text = title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content,
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    dismissButton()
                    confirmButton()
                }
            }
        }
    }
}
