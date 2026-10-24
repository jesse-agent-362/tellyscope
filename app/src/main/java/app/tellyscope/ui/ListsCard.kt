// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.tellyscope.data.ListEntry
import app.tellyscope.data.Suggested
import app.tellyscope.data.Suggestions
import app.tellyscope.data.listKind
import app.tellyscope.data.listMakers
import app.tellyscope.data.sourceName

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Debloat in two steps. First the TV's model: the matched list on its own, with a way to change
 * it; an unknown model starts at a search for the TV's maker, with nothing chosen. Then every
 * app that list would turn off, ticked as the list says, with a final confirm that names each one.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DebloatScreen(
    lists: Suggestions?,
    onPick: (String) -> Unit,
    onDisable: (List<String>) -> Unit,
    back: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    /** The user's own list source, null for the default; set in Settings. */
    source: String? = null,
    /** The TV's brand, then its manufacturer; the first that names a list starts the search. */
    tvMakers: List<String> = emptyList(),
) {
    val ready = lists as? Suggestions.Ready
    val devices = when (lists) {
        is Suggestions.Pick -> lists.devices
        is Suggestions.Ready -> lists.devices
        else -> emptyList()
    }
    var step by rememberSaveable { mutableIntStateOf(1) }
    var chosen by rememberSaveable { mutableStateOf(ready?.list?.id) }
    // The matched list shows on its own; "Change", or no match, opens the search.
    var searching by rememberSaveable { mutableStateOf(false) }
    val makers = remember(devices) { listMakers(devices) }
    fun finds(term: String) = devices.filter { it.name.contains(term, ignoreCase = true) || makers.getValue(it.id).contains(term, ignoreCase = true) }
    // Nobody typed the prefill, so a maker no list names shows the full list, not "No results".
    val prefill = remember(devices) { tvMakers.map { it.trim() }.firstOrNull { it.isNotEmpty() && finds(it).isNotEmpty() } }
    var typed by rememberSaveable { mutableStateOf<String?>(null) }
    val q = typed ?: prefill.orEmpty()
    // Ticks start from the list and reset whenever it reloads.
    var ticked by remember(lists) {
        mutableStateOf(ready?.groups.orEmpty().flatMap { it.packages }.filter { it.ticked }.map { it.pkg }.toSet())
    }
    var confirming by remember { mutableStateOf(false) }
    val showing = ready?.takeIf { it.list.id == chosen }
    if (confirming && showing != null) {
        // The dialog's list is exactly what gets disabled.
        val apps = showing.groups.flatMap { it.packages }.filter { it.pkg in ticked }
        DisableDialog(apps, onDisable = { confirming = false; onDisable(apps.map { it.pkg }); back() }, onDismiss = { confirming = false })
    }
    val next = { chosen?.let { if (it != ready?.list?.id) onPick(it) }; step = 2 }
    val keyboard = LocalSoftwareKeyboardController.current
    val goBack = {
        when {
            step == 2 -> step = 1
            searching && ready != null -> searching = false
            else -> back()
        }
    }
    BackHandler(onBack = goBack)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (step == 1) "Which TV is this?" else "Review the apps")
                        Text("Debloat · step $step of 2", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = goBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        bottomBar = {
            // Above the keyboard while the search is typed into.
            Surface(tonalElevation = 3.dp, modifier = Modifier.imePadding()) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    if (step == 1) {
                        Button(
                            onClick = next,
                            enabled = chosen != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Next") }
                    } else {
                        Button(
                            onClick = { confirming = true },
                            enabled = showing != null && ticked.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Disable ${ticked.size} ${if (ticked.size == 1) "app" else "apps"}") }
                    }
                }
            }
        },
    ) { pad ->
        if (step == 1) {
            val picked = devices.firstOrNull { it.id == chosen }
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (!searching && ready != null && picked != null) {
                    // A match or an earlier pick: just that one, and a way to change it.
                    item {
                        Text("Tellyscope uses this list for your TV.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                        ListRow(picked, makers.getValue(picked.id), selected = true, onClick = null)
                        TextButton(onClick = { searching = true }, modifier = Modifier.padding(top = 4.dp)) { Text("Change") }
                    }
                    return@LazyColumn
                }
                item {
                    OutlinedTextField(
                        q, { typed = it },
                        placeholder = { Text("Brand or model") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (q.isNotEmpty()) IconButton(onClick = { typed = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
                        },
                        singleLine = true,
                        // The keyboard's action key closes it, and moves on once a TV is picked.
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); if (chosen != null) next() }),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
                // Empty while a picked list reloads, if they came back to change it.
                if (devices.isEmpty()) {
                    item { Muted("Loading the lists…") }
                    return@LazyColumn
                }
                val term = q.trim()
                val shown = if (term.isEmpty()) emptyList() else finds(term).sortedBy { it.name.lowercase() }
                // What found nothing: their search, or else the TV's own maker.
                val missed = term.ifEmpty { if (typed == null) tvMakers.firstOrNull()?.trim()?.ifEmpty { null } else null }
                if (shown.isNotEmpty()) {
                    items(shown, key = { it.id }) { d -> ListRow(d, makers.getValue(d.id), d.id == chosen) { chosen = d.id } }
                    return@LazyColumn
                }
                if (missed != null) item { Muted("No list for \u201c$missed\u201d. Pick the closest model.") }
                // Every list, by maker, with the maker in view while scrolling.
                devices.groupBy { makers.getValue(it.id).lowercase().filter(Char::isLetterOrDigit) }.toSortedMap().forEach { (key, group) ->
                    stickyHeader(key = "m:$key") {
                        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                            Text(makers.getValue(group.first().id), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        }
                    }
                    items(group.sortedBy { it.name.lowercase() }, key = { it.id }) { d -> ListRow(d, makers.getValue(d.id), d.id == chosen) { chosen = d.id } }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (showing == null) {
                    item {
                        Muted(
                            if (lists == Suggestions.Unavailable) "Couldn't load that list. Check the phone's internet and try again."
                            else "Loading the list…",
                        )
                    }
                    return@LazyColumn
                }
                item {
                    Text(
                        "From the list for ${showing.list.name}. Untick anything you use. Nothing is deleted: each app is hidden, and History can turn it back on.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    // Nobody should debloat from a list they didn't mean to use.
                    if (source != null) Text(
                        "This list comes from ${sourceName(source)}, not tellyscope-devices.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (!showing.sdkMatches) Muted(
                        showing.list.testedOs?.let { "Tested on $it, not this TV's Android version, so nothing is ticked. Tick only what you know you don't use." }
                            ?: "This list doesn't say which Android it was tested on, so nothing is ticked. Tick only what you know you don't use.",
                    )
                    if (showing.groups.isEmpty()) Muted("Nothing on the list is still on here.")
                }
                showing.groups.forEach { g ->
                    item(key = "g:" + g.title) { Text(g.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
                    items(g.packages, key = { it.pkg }) { p ->
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                                .clickable { ticked = if (p.pkg in ticked) ticked - p.pkg else ticked + p.pkg },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(p.pkg in ticked, onCheckedChange = null)
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
            }
        }
    }
}

@Composable
internal fun DisableDialog(apps: List<Suggested>, onDisable: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disable ${apps.size} ${if (apps.size == 1) "app" else "apps"}?") },
        text = {
            // Every name, so a list can't slip something in behind a count.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Each one is hidden and stops running. Nothing is deleted; every one shows in History, where you can turn it back on.")
                LazyColumn(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(apps, key = { it.pkg }) { a ->
                        Column {
                            Text(a.label, style = MaterialTheme.typography.bodyLarge)
                            Text(a.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
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

/** "Android 11" for SDK 30; just the SDK where the release name isn't known. */
internal fun androidName(sdk: Int): String = when (sdk) {
    21 -> "Android 5.0"
    22 -> "Android 5.1"
    23 -> "Android 6"
    24 -> "Android 7"
    25 -> "Android 7.1"
    26 -> "Android 8"
    27 -> "Android 8.1"
    28 -> "Android 9"
    32 -> "Android 12L"
    in 29..31 -> "Android ${sdk - 19}"
    in 33..36 -> "Android ${sdk - 20}"
    else -> "API $sdk"
}

/** One list in the picker: the kind of device, the model and the Android it was tested on. The chosen one is tinted and ticked. */
@Composable
private fun ListRow(e: ListEntry, maker: String, selected: Boolean, onClick: (() -> Unit)?) {
    val kind = listKind(e, maker)
    val pick = if (onClick == null) Modifier else Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick)
    ListItem(
        headlineContent = { Text(e.name) },
        // The maker is already in the name; say what the list was tested on instead.
        supportingContent = { Text(listOfNotNull(kind.word(), e.sdk?.let(::androidName)).joinToString(" · ")) },
        leadingContent = { Icon(DeviceIcons.of(kind), contentDescription = kind.word()) },
        trailingContent = if (selected) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
        colors = if (selected) ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            headlineColor = MaterialTheme.colorScheme.onSecondaryContainer,
            supportingColor = MaterialTheme.colorScheme.onSecondaryContainer,
            leadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            trailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) else ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).then(pick),
    )
}
