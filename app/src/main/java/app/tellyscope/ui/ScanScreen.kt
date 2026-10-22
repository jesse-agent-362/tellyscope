// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.tellyscope.data.ScanHit
import app.tellyscope.data.ScanKind
import app.tellyscope.ui.theme.SmallNumber
import app.tellyscope.ui.theme.StatNumber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScanScreen(
    scan: ScanUi,
    now: Long,
    onRescan: () -> Unit,
    onConnect: (ScanHit) -> Unit,
    onCode: (ScanHit, String) -> Unit,
    onPair: (ScanHit) -> Unit,
    onAddByIp: (String) -> String?,
    onBack: (() -> Unit)? = null,
) {
    var adding by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tellyscope") },
                navigationIcon = { if (onBack != null) TextButton(onClick = onBack) { Text("Done") } },
                actions = {
                    IconButton(onClick = onRescan, enabled = !scan.scanning) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan again")
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("What's running on your TV?", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Live stats, apps and network for the Android TVs on your Wi-Fi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { ScanStatus(scan) }
            items(scan.hits, key = { it.ip }) { hit ->
                HitCard(hit, scan.rows[hit.ip] ?: RowState.Found, now, onConnect, onCode, onPair)
            }
            if (!scan.scanning && scan.hits.isEmpty()) {
                item { GuideCard() }
            }
            item {
                OutlinedButton(onClick = { adding = true }) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add by IP address")
                }
            }
        }
    }
    if (adding) AddByIpDialog(subnet = scan.progress?.subnet, onDismiss = { adding = false }, onSubmit = { onAddByIp(it).also { e -> if (e == null) adding = false } })
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScanStatus(scan: ScanUi) {
    val p = scan.progress
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (scan.scanning) {
            Text(
                "Looking for TVs on your Wi-Fi…  ${p?.probed ?: 0}/${p?.total ?: 254}",
                style = MaterialTheme.typography.labelLarge,
            )
            LinearWavyProgressIndicator(
                progress = { p?.let { it.probed / it.total.toFloat() } ?: 0f },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            val n = scan.hits.size
            Text(
                if (n == 0) "No TVs found yet" else "Found $n ${if (n == 1) "TV" else "TVs"} on your Wi-Fi",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HitCard(
    hit: ScanHit,
    row: RowState,
    now: Long,
    onConnect: (ScanHit) -> Unit,
    onCode: (ScanHit, String) -> Unit,
    onPair: (ScanHit) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(hit.hostname ?: hit.maker ?: "Android device", style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(hit.maker ?: "Unknown maker", hit.ip).joinToString(" · "),
                        style = SmallNumber,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when (row) {
                    RowState.Found -> FilledTonalButton(onClick = { onConnect(hit) }) { Text("Connect") }
                    RowState.NotAllowed -> FilledTonalButton(onClick = { onConnect(hit) }) { Text("Try again") }
                    else -> Unit
                }
            }
            when (row) {
                RowState.Found -> Text(
                    if (hit.kind == ScanKind.Wireless) "Needs the pairing code from the TV" else "Ready to connect",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is RowState.Waiting -> if (hit.kind == ScanKind.Wireless) Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Connecting…", style = MaterialTheme.typography.bodyMedium)
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(Modifier.size(48.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Check your TV", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "A box saying \"Allow USB debugging?\" is on screen. Tick \"Always allow from this computer\", then press Allow.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Waiting… ${((now - row.since) / 1000).coerceAtLeast(0)} s",
                            style = SmallNumber,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                RowState.NotAllowed -> Column {
                    Text("No answer from the TV", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                    Text(
                        "Is the box still on screen? If you pressed Deny by mistake, tap Try again and it will ask once more.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is RowState.Code -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "On the TV: Settings → Developer options → Wireless debugging → Pair device with pairing code. Type the 6 digits it shows:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = row.input,
                            onValueChange = { onCode(hit, it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !row.busy,
                            textStyle = StatNumber,
                            placeholder = { Text("000000", style = StatNumber) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            isError = row.error != null,
                        )
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = { onPair(hit) }, enabled = !row.busy && row.input.length == 6) {
                            Text(if (row.busy) "Pairing…" else "Pair")
                        }
                    }
                    row.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun AddByIpDialog(subnet: String?, onDismiss: () -> Unit, onSubmit: (String) -> String?) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add by IP address") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Find it on the TV under Settings → Network → your Wi-Fi.")
                // The scan screen says "your Wi-Fi"; the range is only useful here, while typing.
                if (subnet != null) Text("Your Wi-Fi: $subnet", style = SmallNumber, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    singleLine = true,
                    textStyle = StatNumber,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = { Button(onClick = { error = onSubmit(text) }) { Text("Connect") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
