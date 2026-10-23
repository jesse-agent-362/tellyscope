// SPDX-License-Identifier: GPL-3.0-only
// Apps, Files and Tweaks icons are the Material Icons "apps", "folder" and "tune" (Apache-2.0), see NOTICE.

package app.tellyscope.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tellyscope.data.DeviceState
import app.tellyscope.data.Suggestions
import app.tellyscope.data.deviceKind

/** The TV page's destinations, one job each, in the bottom bar. */
enum class TvTab(val label: String) { Overview("Overview"), Apps("Apps"), Files("Files"), Tweaks("Tweaks") }

private object TabIcons {
    val Apps = icon(
        "Apps",
        "M4 8h4V4H4v4zm6 12h4v-4h-4v4zm-6 0h4v-4H4v4zm0-6h4v-4H4v4zm6 0h4v-4h-4v4zm6-10v4h4V4h-4zm-6 4h4V4h-4v4zm6 6h4v-4h-4v4zm0 6h4v-4h-4v4z",
    )
    val Folder = icon("Folder", "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z")
    val Tune = icon(
        "Tune",
        "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z",
    )

    private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), pathFillType = PathFillType.NonZero, fill = SolidColor(Color.Black))
        .build()
}

private fun TvTab.icon() = when (this) {
    TvTab.Overview -> Icons.Default.Home
    TvTab.Apps -> TabIcons.Apps
    TvTab.Files -> TabIcons.Folder
    TvTab.Tweaks -> TabIcons.Tune
}

/**
 * The TV page as four tabs under a bottom bar, in place of one long scroll of folded sections.
 * The app bar is the same on every tab: the TV's name opens the TV menu, search jumps to Apps,
 * and the overflow holds the app-wide items.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeviceTabsScreen(
    state: UiState,
    actions: DeviceActions,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    startTab: TvTab = TvTab.Overview,
) {
    val device = state.selected ?: return
    val ui = state.device
    var tab by rememberSaveable { mutableStateOf(startTab) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var onlyLocked by rememberSaveable { mutableStateOf(false) }
    val q = state.query.trim().lowercase()
    val offline = device.state != DeviceState.Online

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (searching) {
                TopAppBar(
                    title = {
                        TextField(
                            value = state.query,
                            onValueChange = actions.query,
                            placeholder = { Text("Search apps") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    actions = {
                        IconButton(onClick = { searching = false; actions.query("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { TvTitle(state, actions) },
                    actions = {
                        if (REMOTE_ENABLED) TextButton(onClick = actions.remote, enabled = !offline) { Text("Remote") }
                        IconButton(onClick = { searching = true; tab = TvTab.Apps }, enabled = !offline) {
                            Icon(Icons.Default.Search, contentDescription = "Search apps")
                        }
                        AppMenu(state, actions)
                    },
                )
            }
        },
        bottomBar = {
            ShortNavigationBar {
                TvTab.entries.forEach { t ->
                    ShortNavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon(), contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val snap = ui.snapshot
            val live = snap?.takeIf { !offline }
            when (tab) {
                TvTab.Overview -> {
                    item { TvCard(state, live) }
                    val restart = state.restarting[device.id]
                    if (restart != null) {
                        item { RestartingCard(restart, state.now) }
                    } else if (device.state == DeviceState.NotAllowed) {
                        item { NotAllowedCard(actions.reconnect) }
                    } else if (offline) {
                        item { OfflineCard(device, state.now, actions.reconnect) }
                    }
                    if (live != null) {
                        item { SystemWidget(live) }
                    } else if (offline && snap == null) {
                        item { Muted(Copy.noStats(device.state)) }
                    }
                    if (!offline) {
                        item {
                            ActionTiles(
                                actions,
                                canDebloat = ui.lists is Suggestions.Pick || ui.lists is Suggestions.Ready,
                                disabling = ui.busy == DISABLING,
                            )
                        }
                        item { Panel("Suggested for this TV") { SuggestionsSummary(ui.lists) } }
                    }
                }
                TvTab.Apps -> {
                    val locked = ui.apps.count { it.protectedReason != null }
                    item(key = "filter") { AppsFilter(ui.apps.size - locked, locked, onlyLocked) { onlyLocked = it } }
                    val shown = ui.apps
                        .filter { q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.contains(q) }
                        // A search looks through every app; the switch only narrows the plain list.
                        .filter { q.isNotEmpty() || (it.protectedReason != null) == onlyLocked }
                        .sortedWith(compareBy({ !it.enabled }, { it.system }, { it.label }))
                    if (shown.isEmpty()) item { EmptyLine(q) }
                    items(shown, key = { it.packageName }) { app ->
                        AppRow(app, snap?.procs.orEmpty(), ui.pending, ui.busy == app.packageName, actions, homeLabel(ui.apps, app))
                    }
                }
                TvTab.Files -> {
                    if (offline) item { Muted("Connect to the TV to see its files.") }
                    else {
                        item {
                            // The folder switch below picks where it lands, so no menu is needed here.
                            @Suppress("DEPRECATION") // the AutoMirrored set isn't in icons-core
                            FilledTonalButton(onClick = { actions.pickFile(ui.files.folder) }, Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Send, contentDescription = null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Send a file to ${Copy.folder(ui.files.folder)}")
                            }
                        }
                        item { FilesBlock(ui.files, state.now, actions) }
                    }
                }
                TvTab.Tweaks -> {
                    if (offline) item { Muted("Connect to the TV to change its settings.") }
                    else item { Panel(Copy.TWEAKS) { TweaksBlock(ui, actions) } }
                    item {
                        Panel("Recent changes") {
                            HistoryList(ui.history, state.now, actions.undo, restoring = ui.busy == RESTORING, onRestoreAll = actions.restoreAll)
                        }
                    }
                }
            }
        }
        if (!offline) ui.request?.let { RequestDialog(it, ui.busy == ANSWERING, actions.answerRequest, actions.dismissRequest) }
        ui.sideload?.let { SideloadDialog(it, actions) }
        ui.transfer?.let { TransferDialog(it, actions.cancelTransfer) }
    }
}

/** Apps the user can change by default; the switch's other side shows the ones Tellyscope protects. */
@Composable
private fun AppsFilter(changeable: Int, locked: Int, onlyLocked: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(false to "Changeable ($changeable)", true to "${Copy.CANT_CHANGE} ($locked)").forEachIndexed { i, (side, label) ->
            SegmentedButton(
                selected = onlyLocked == side,
                onClick = { onChange(side) },
                shape = SegmentedButtonDefaults.itemShape(i, 2),
            ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** A titled card for a tab's block, not folded: each tab has room for it. */
@Composable
private fun Panel(title: String, body: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
            body()
        }
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The app bar's title: which TV, and its menu of TV things (switch, add, rename, forget). */
@Composable
private fun TvTitle(state: UiState, actions: DeviceActions) {
    val device = state.selected ?: return
    var open by remember { mutableStateOf(false) }
    var forgetting by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    if (forgetting) ForgetDialog(name(device), onForget = { forgetting = false; actions.forget(device.id) }, onDismiss = { forgetting = false })
    if (renaming) RenameDialog(device.nickname.orEmpty(), device.model ?: networkName(device), onSave = { renaming = false; actions.rename(device.id, it) }, onDismiss = { renaming = false })
    val offlineCount = state.devices.count { it.state != DeviceState.Online }
    Box {
        Row(
            Modifier.clip(MaterialTheme.shapes.large).clickable { open = true }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val kind = deviceKind(device)
            Icon(DeviceIcons.of(kind), contentDescription = kind.word(), Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f, fill = false)) {
                val title = name(device)
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Under a model-name title, the model would only say it twice.
                val maker = device.manufacturer ?: device.hit.maker
                val made = if (title == device.model) maker.orEmpty() else made(maker, device.model)
                if (made.isNotEmpty()) {
                    Text(made, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Switch TV")
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            state.devices
                .filter { state.showOffline || it.state == DeviceState.Online || it.id == state.selectedId }
                .forEach { d ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(name(d), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StateDot(d.state)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (d.state == DeviceState.Online) Copy.state(d.state)
                                        else d.lastSeen?.let { "${Copy.state(d.state)}, seen ${Copy.ago(it, state.now)}" } ?: Copy.state(d.state),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        leadingIcon = { Icon(DeviceIcons.of(deviceKind(d)), contentDescription = deviceKind(d).word()) },
                        onClick = { open = false; actions.select(d.id) },
                    )
                }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Show offline ($offlineCount)") },
                leadingIcon = { Checkbox(state.showOffline, onCheckedChange = null) },
                onClick = { actions.showOffline(!state.showOffline) },
            )
            DropdownMenuItem(text = { Text("Add a TV") }, leadingIcon = { Icon(Icons.Default.Add, null) }, onClick = { open = false; actions.addDevice() })
            DropdownMenuItem(text = { Text("Rename this TV") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { open = false; renaming = true })
            DropdownMenuItem(text = { Text("Forget this TV") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { open = false; forgetting = true })
        }
    }
}

/** The overflow: app-wide items, the same whichever TV is shown. */
@Composable
private fun AppMenu(state: UiState, actions: DeviceActions) {
    var open by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    if (about) AboutDialog(
        when (val l = state.device.lists) {
            is Suggestions.Pick -> l.commit
            is Suggestions.Ready -> l.commit
            else -> null
        },
        onDismiss = { about = false },
    )
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Advanced") }, leadingIcon = { Icon(Icons.Default.List, null) }, onClick = { open = false; actions.advanced() })
            DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { open = false; actions.settings() })
            DropdownMenuItem(text = { Text("About & licences") }, leadingIcon = { Icon(Icons.Default.Info, null) }, onClick = { open = false; about = true })
        }
    }
}
