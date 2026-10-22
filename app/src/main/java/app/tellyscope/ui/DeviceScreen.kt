// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tellyscope.data.Action
import app.tellyscope.data.AppInfo
import app.tellyscope.data.Device
import app.tellyscope.data.DeviceState
import app.tellyscope.data.HistoryEntry
import app.tellyscope.data.NetworkInfo
import app.tellyscope.data.Proc
import app.tellyscope.data.Snapshot
import app.tellyscope.ui.theme.SmallNumber
import app.tellyscope.ui.theme.StatNumber

data class DeviceActions(
    val select: (String) -> Unit = {},
    val addDevice: () -> Unit = {},
    val showOffline: (Boolean) -> Unit = {},
    val query: (String) -> Unit = {},
    val propose: (String, Action) -> Unit = { _, _ -> },
    val confirm: () -> Unit = {},
    val cancel: () -> Unit = {},
    val undo: (HistoryEntry) -> Unit = {},
    val reconnect: () -> Unit = {},
    val forget: (String) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    state: UiState,
    actions: DeviceActions,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val device = state.selected ?: return
    val ui = state.device
    var searching by rememberSaveable { mutableStateOf(false) }
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
                            placeholder = { Text("Search apps, processes, connections") },
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
                    title = { DevicePicker(state, actions) },
                    actions = {
                        IconButton(onClick = { searching = true }, enabled = !offline) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                )
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    listOfNotNull(device.model, device.android?.let { "Android $it" }, device.hit.ip).joinToString(" · "),
                    style = SmallNumber,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (offline) item { OfflineCard(device, state.now, actions.reconnect) }
            item { StatGrid(ui.snapshot, Modifier.alpha(if (offline) 0.4f else 1f)) }
            ui.snapshot?.let { snap ->
                // The :adb source has no per-process list yet; no bars beats an empty card.
                if (q.isEmpty() && snap.procs.isNotEmpty()) item { UsageCard(snap, ui.apps) }
            }
            section(
                key = "apps",
                title = "Apps",
                count = ui.apps.size,
                expandedByDefault = true,
                forceOpen = q.isNotEmpty(),
            ) {
                val apps = ui.apps
                    .filter { q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.contains(q) }
                    .sortedWith(compareBy({ it.protectedReason != null }, { !it.enabled }, { it.system }, { it.label }))
                if (apps.isEmpty()) EmptyLine(q)
                apps.forEach { app ->
                    AppRow(app, ui.snapshot?.procs.orEmpty(), ui.pending, ui.busy == app.packageName, actions)
                }
            }
            section("procs", "Processes", ui.snapshot?.procs?.size ?: 0, forceOpen = q.isNotEmpty()) {
                ProcessTable(ui.snapshot?.procs.orEmpty(), ui.apps, q)
            }
            section("net", "Network", ui.network?.sockets?.size ?: 0, forceOpen = q.isNotEmpty()) {
                ui.network?.let { NetworkBlock(it, q) }
            }
            section("history", "History", ui.history.size) {
                HistoryList(ui.history, state.now, actions.undo)
            }
        }
    }
}

@Composable
private fun DevicePicker(state: UiState, actions: DeviceActions) {
    var open by remember { mutableStateOf(false) }
    var forgetting by remember { mutableStateOf(false) }
    val device = state.selected ?: return
    if (forgetting) ForgetDialog(name(device), onForget = { forgetting = false; actions.forget(device.id) }, onDismiss = { forgetting = false })
    val offlineCount = state.devices.count { it.state != DeviceState.Online }
    Box {
        Row(
            Modifier.clip(MaterialTheme.shapes.large).clickable { open = true }.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StateDot(device.state)
            Spacer(Modifier.width(8.dp))
            Text(name(device), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Switch TV")
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            state.devices
                .filter { state.showOffline || it.state == DeviceState.Online || it.id == state.selectedId }
                .forEach { d ->
                    DropdownMenuItem(
                        text = { Text(name(d)) },
                        leadingIcon = { StateDot(d.state) },
                        trailingIcon = {
                            if (d.state != DeviceState.Online) Text(
                                d.lastSeen?.let { "seen ${Copy.ago(it, state.now)}" } ?: "offline",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        onClick = { open = false; actions.select(d.id) },
                    )
                }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Show offline ($offlineCount)") },
                leadingIcon = { Checkbox(state.showOffline, onCheckedChange = null) },
                onClick = { actions.showOffline(!state.showOffline) },
            )
            DropdownMenuItem(
                text = { Text("Add a TV") },
                leadingIcon = { Icon(Icons.Default.Add, null) },
                onClick = { open = false; actions.addDevice() },
            )
            DropdownMenuItem(
                text = { Text("Forget this TV") },
                leadingIcon = { Icon(Icons.Default.Delete, null) },
                onClick = { open = false; forgetting = true },
            )
        }
    }
}

private fun name(d: Device) = d.hit.hostname ?: d.model ?: d.hit.ip

/** Forgetting only touches this phone; the TV keeps its own list of allowed computers. */
@Composable
fun ForgetDialog(name: String, onForget: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forget $name?") },
        text = { Text("Tellyscope stops reconnecting to it and removes it from this phone. Nothing on the TV changes. You can add it again from a scan.") },
        confirmButton = { Button(onClick = onForget) { Text("Forget") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StateDot(state: DeviceState) {
    val c = when (state) {
        DeviceState.Online -> MaterialTheme.colorScheme.primary
        DeviceState.Waiting, DeviceState.Found, DeviceState.NeedsPairing -> MaterialTheme.colorScheme.tertiary
        DeviceState.Offline, DeviceState.NotAllowed -> MaterialTheme.colorScheme.outline
    }
    Box(Modifier.size(10.dp).clip(CircleShape).background(c))
}

@Composable
private fun OfflineCard(device: Device, now: Long, onReconnect: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Offline", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Last seen ${device.lastSeen?.let { Copy.ago(it, now) } ?: "a while ago"}. It reconnects on its own when the TV is back on.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(onClick = onReconnect) { Text("Reconnect") }
        }
    }
}

/** Stat strip, phone-shaped: a 3×2 grid. Missing stats show "—", never 0. */
@Composable
private fun StatGrid(s: Snapshot?, modifier: Modifier = Modifier) {
    val cells = listOf(
        "CPU" to s?.let { "${it.cpuPercent}%" },
        // Units live in the label so the value fits a third of a phone width.
        "RAM GB" to s?.let { "%.1f/%.0f".format(it.ramUsedMb / 1024.0, it.ramTotalMb / 1024.0) },
        "Storage GB" to s?.storageUsedGb?.let { u -> s.storageTotalGb?.let { t -> "%.1f/%.0f".format(u, t) } },
        "Temp" to s?.tempC?.let { "$it°C" },
        "Up" to s?.let { Copy.uptime(it.uptimeSeconds) },
        "Screen" to s?.screenOn?.let { if (it) "on" else "off" },
    )
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            cells.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { (label, value) ->
                        Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value ?: "—", style = StatNumber, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Grey bars, no chart library: top 6 by value, the rest summed as "other". */
@Composable
private fun UsageCard(snap: Snapshot, apps: List<AppInfo>) {
    val labels = apps.associate { it.packageName to it.label }
    fun label(p: Proc) = p.packageName?.let { labels[it] ?: it } ?: Copy.ANDROID_SYSTEM
    val byApp = snap.procs.groupBy(::label)
    var cpu by rememberSaveable { mutableStateOf(false) }
    val rows = byApp.map { (name, ps) ->
        name to if (cpu) ps.sumOf { it.cpuPercent } else ps.sumOf { it.ramMb }.toDouble()
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (cpu) "CPU by app" else "RAM by app", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { cpu = !cpu }) { Text(if (cpu) "Show RAM" else "Show CPU") }
            }
            Bars(rows, fmt = { if (cpu) "%.0f%%".format(it) else Copy.mb(it.toInt()) })
        }
    }
}

@Composable
private fun Bars(rows: List<Pair<String, Double>>, fmt: (Double) -> String, top: Int = 6) {
    val sorted = rows.sortedByDescending { it.second }
    val rest = sorted.drop(top).sumOf { it.second }
    val shown = sorted.take(top) + if (rest > 0) listOf("Other" to rest) else emptyList()
    val max = shown.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
    shown.forEach { (name, v) ->
        val frac by animateFloatAsState((v / max).toFloat(), MaterialTheme.motionScheme.defaultSpatialSpec(), label = "bar")
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Long app names wrap to two lines rather than cut off; the value column only needs "1.2 GB".
            Text(name, Modifier.width(148.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).height(12.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primary))
            }
            Text(fmt(v), Modifier.width(60.dp), style = SmallNumber, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

/** A collapsible card section. */
private fun LazyListScope.section(
    key: String,
    title: String,
    count: Int,
    expandedByDefault: Boolean = false,
    forceOpen: Boolean = false,
    body: @Composable () -> Unit,
) {
    item(key = key) {
        var open by rememberSaveable { mutableStateOf(expandedByDefault) }
        val shown = open || forceOpen
        val turn by animateFloatAsState(if (shown) 180f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "chevron")
        Card(
            Modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("$count", style = SmallNumber, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = if (shown) "Collapse" else "Expand", Modifier.rotate(turn))
            }
            AnimatedVisibility(shown) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    body()
                }
            }
        }
    }
}

@Composable
private fun EmptyLine(q: String) {
    Text(
        if (q.isEmpty()) "Nothing here yet." else "Nothing matches \"$q\".",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun AppIcon(app: AppInfo, modifier: Modifier = Modifier) {
    val bg = app.iconColor?.let { Color(it) } ?: MaterialTheme.colorScheme.secondaryContainer
    val fg = if (app.iconColor != null) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
    Box(modifier.size(40.dp).clip(MaterialTheme.shapes.medium).background(bg), contentAlignment = Alignment.Center) {
        Text(app.label.take(1).uppercase(), color = fg, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppRow(
    app: AppInfo,
    procs: List<Proc>,
    pending: Pair<String, Action>?,
    busy: Boolean,
    actions: DeviceActions,
) {
    var open by rememberSaveable(app.packageName) { mutableStateOf(false) }
    val ram = procs.filter { it.packageName == app.packageName }.sumOf { it.ramMb }
    val status = when {
        !app.enabled -> "Disabled"
        ram > 0 -> "Running · ${Copy.mb(ram)}"
        else -> "Not running"
    }
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(enabled = app.protectedReason == null) { open = !open }.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, Modifier.alpha(if (app.enabled) 1f else 0.4f))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    app.protectedReason?.let { "Protected: $it" } ?: status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (busy) androidx.compose.material3.LoadingIndicator(Modifier.size(32.dp))
        }
        val proposed = pending?.takeIf { it.first == app.packageName }?.second
        AnimatedVisibility((open || proposed != null) && app.protectedReason == null) {
            Column(Modifier.padding(start = 52.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (proposed == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (app.enabled && ram > 0) OutlinedButton(onClick = { actions.propose(app.packageName, Action.Stop) }) { Text(Copy.button(Action.Stop)) }
                        if (app.enabled) OutlinedButton(onClick = { actions.propose(app.packageName, Action.Disable) }) { Text(Copy.button(Action.Disable)) }
                        else FilledTonalButton(onClick = { actions.propose(app.packageName, Action.Enable) }) { Text(Copy.button(Action.Enable)) }
                    }
                } else {
                    Text(Copy.explain(proposed, app.label), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = actions.confirm) { Text(Copy.confirm(proposed)) }
                        TextButton(onClick = actions.cancel) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessTable(procs: List<Proc>, apps: List<AppInfo>, q: String) {
    val labels = apps.associate { it.packageName to it.label }
    val groups = procs
        .groupBy { p -> p.packageName?.let { labels[it] ?: it } ?: Copy.ANDROID_SYSTEM }
        .filter { (name, ps) -> q.isEmpty() || name.lowercase().contains(q) || ps.any { it.name.lowercase().contains(q) } }
        .toList()
        .sortedByDescending { (_, ps) -> ps.sumOf { it.cpuPercent } }
    if (groups.isEmpty()) return EmptyLine(q)
    Row {
        Text("App", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("CPU", Modifier.width(56.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        Text("RAM", Modifier.width(72.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
    groups.forEach { (name, ps) ->
        var open by rememberSaveable(name) { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 6.dp)) {
            Row {
                Text(
                    if (ps.size > 1) "$name  ×${ps.size}" else name,
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text("%.0f%%".format(ps.sumOf { it.cpuPercent }), Modifier.width(56.dp), style = SmallNumber, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                Text(Copy.mb(ps.sumOf { it.ramMb }), Modifier.width(72.dp), style = SmallNumber, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
            if (open) ps.forEach { p ->
                Row(Modifier.padding(start = 12.dp, top = 2.dp)) {
                    Text("${p.pid}  ${p.name}", Modifier.weight(1f), style = SmallNumber, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("%.1f%%".format(p.cpuPercent), Modifier.width(56.dp), style = SmallNumber, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    Text(Copy.mb(p.ramMb), Modifier.width(72.dp), style = SmallNumber, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun NetworkBlock(n: NetworkInfo, q: String) {
    if (q.isEmpty()) {
        KeyValue("Connection", listOfNotNull(if (n.iface == "wifi") "Wi-Fi" else "Ethernet", n.ssid).joinToString(" · "))
        KeyValue("IP", n.ip)
        n.mac?.let { KeyValue("MAC", it) }
        n.rssi?.let { r -> KeyValue("Signal", "$r dBm · ${signalWord(r)}" + (n.linkMbps?.let { " · $it Mbps" } ?: "")) }
        Spacer(Modifier.height(8.dp))
        Text("Data used", style = MaterialTheme.typography.titleSmall)
        Bars(n.traffic.map { it.app to (it.rxMb + it.txMb).toDouble() }, fmt = { Copy.mb(it.toInt()) })
        Spacer(Modifier.height(8.dp))
    }
    Text("Open connections", style = MaterialTheme.typography.titleSmall)
    val socks = n.sockets.filter { q.isEmpty() || it.app.lowercase().contains(q) || it.remote.lowercase().contains(q) }
    if (socks.isEmpty()) EmptyLine(q)
    socks.forEach { s ->
        Row(Modifier.padding(vertical = 2.dp)) {
            Text(s.app, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.remote, style = SmallNumber, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun signalWord(rssi: Int) = when {
    rssi >= -55 -> "strong"
    rssi >= -67 -> "good"
    rssi >= -75 -> "weak"
    else -> "very weak"
}

@Composable
private fun KeyValue(k: String, v: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(k, Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, style = SmallNumber)
    }
}

@Composable
private fun HistoryList(history: List<HistoryEntry>, now: Long, onUndo: (HistoryEntry) -> Unit) {
    if (history.isEmpty()) {
        Text(
            "Nothing changed yet. Anything you stop, disable or turn on shows up here, with a way to undo it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    history.forEach { h ->
        Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Copy.done(h.action, h.label), style = MaterialTheme.typography.bodyMedium)
                Text(Copy.ago(h.at, now), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                h.undoneAt != null -> Text("Undone", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                h.undo != null -> TextButton(onClick = { onUndo(h) }) { Text(Copy.undo(h.undo)) }
            }
        }
    }
}
