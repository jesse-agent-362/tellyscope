// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shown before the first scan on Android 17+, where talking to anything on the
 * Wi-Fi needs the "Nearby devices" permission. Once the user has said no twice,
 * Android stops showing its dialog, so the button sends them to Settings instead.
 */
@Composable
fun PermissionScreen(denied: Boolean, onAllow: () -> Unit, onSettings: () -> Unit) {
    Scaffold { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, androidx.compose.ui.Alignment.CenterVertically),
        ) {
            Text("Find TVs on your Wi-Fi", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Android asks before an app talks to other devices on your Wi-Fi. " +
                    "Tellyscope only connects to the TVs you pick.",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (denied) {
                Text(
                    "Without it, Tellyscope can't see any TV. In Settings, open Permissions → Nearby devices and choose Allow.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onSettings) { Text("Open Settings") }
            } else {
                Button(onClick = onAllow) { Text("Continue") }
            }
        }
    }
}
