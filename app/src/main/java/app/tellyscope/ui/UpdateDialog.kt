// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tellyscope.data.Update

/** A newer release is out. Get it opens its GitHub page; the app never downloads it itself. */
@Composable
fun UpdateDialog(update: Update, onGet: () -> Unit, onLater: () -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Tellyscope ${update.version} is out") },
        text = {
            // Release notes are Markdown; shown as written rather than rendered.
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(update.notes.ifEmpty { "No release notes." }, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { Button(onClick = onGet) { Text("Get it") } },
        dismissButton = {
            Row {
                TextButton(onClick = onSkip) { Text("Skip this version") }
                TextButton(onClick = onLater) { Text("Later") }
            }
        },
    )
}
