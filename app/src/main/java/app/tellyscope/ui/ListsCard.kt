// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tellyscope.data.ListEntry
import app.tellyscope.data.Suggestions

/** Body of the "Suggested for this TV" section: the TV's list, cut down to what it has. */
@Composable
fun SuggestionsBlock(
    lists: Suggestions?,
    disabling: Boolean,
    onPick: (String) -> Unit,
    onDisable: (List<String>) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    // Ticks start from the list and reset whenever it reloads.
    var ticked by remember(lists) {
        mutableStateOf((lists as? Suggestions.Ready)?.groups.orEmpty().flatMap { it.packages }.filter { it.ticked }.map { it.pkg }.toSet())
    }
    val devices = when (lists) {
        is Suggestions.Pick -> lists.devices
        is Suggestions.Ready -> lists.devices
        else -> emptyList()
    }
    if (picking) PickDialog(devices, onPick = { picking = false; onPick(it) }, onDismiss = { picking = false })
    if (confirming) DisableDialog(ticked.size, onDisable = { confirming = false; onDisable(ticked.toList()) }, onDismiss = { confirming = false })

    when (lists) {
        null -> Muted("Looking up this TV's list…")
        Suggestions.Unavailable -> Muted(
            "Couldn't load the device lists. They download the first time you open a TV; check the phone's internet and open this TV again.",
        )
        is Suggestions.Pick -> {
            Text("Tellyscope doesn't know this model yet. Pick your TV to see what's usually safe to turn off.", style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = { picking = true }) { Text("Pick your TV") }
        }
        is Suggestions.Ready -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("From the list for ${lists.list.name}.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { picking = true }) { Text("Not this TV?") }
            }
            if (!lists.sdkMatches) Muted(
                lists.list.testedOs?.let { "Tested on $it, not this TV's Android version, so nothing is ticked. Tick only what you know you don't use." }
                    ?: "This list doesn't say which Android it was tested on, so nothing is ticked. Tick only what you know you don't use.",
            )
            if (lists.groups.isEmpty()) Muted("Nothing on the list is still on here.")
            lists.groups.forEach { g ->
                Text(g.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                g.packages.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !disabling) { ticked = if (p.pkg in ticked) ticked - p.pkg else ticked + p.pkg },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(p.pkg in ticked, onCheckedChange = null, enabled = !disabling)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                listOfNotNull(p.pkg, p.note, "optional".takeIf { p.optional }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (lists.groups.isNotEmpty()) {
                Button(onClick = { confirming = true }, enabled = ticked.isNotEmpty() && !disabling, modifier = Modifier.padding(top = 8.dp)) {
                    Text(if (disabling) "Disabling…" else "Disable ${ticked.size} selected")
                }
            }
        }
    }
    if (lists is Suggestions.Pick || lists is Suggestions.Ready) Muted("Lists adapted from cleanyourtv (MIT).")
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Searchable "Pick your TV". The choice is remembered for this TV. */
@Composable
fun PickDialog(devices: List<ListEntry>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val shown = devices.filter { q.isBlank() || it.name.contains(q.trim(), ignoreCase = true) }.sortedBy { it.name.lowercase() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick your TV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(q, { q = it }, placeholder = { Text("Brand or model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (shown.isEmpty()) Muted("No list matches \"${q.trim()}\".")
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.id }) { d ->
                        Text(
                            d.name,
                            Modifier.fillMaxWidth().clickable { onPick(d.id) }.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DisableDialog(count: Int, onDisable: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disable $count ${if (count == 1) "app" else "apps"}?") },
        text = { Text("Each one is hidden and stops running. Nothing is deleted; every one shows in History, where you can turn it back on.") },
        confirmButton = { Button(onClick = onDisable) { Text("Yes, disable them") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** About and licences; [commit] is the cleanyourtv commit the downloaded lists were built from. */
@Composable
fun AboutDialog(commit: String?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About Tellyscope") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Free software under the GNU GPL v3.", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Device lists are adapted from cleanyourtv by Gathin (MIT licence), " +
                        (commit?.let { "commit ${it.take(12)}." } ?: "once they have downloaded."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Uses Kadb (Apache 2.0) and spake2-java (GPL v3). Full licence texts are in the NOTICE file.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
