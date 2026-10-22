// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tellyscope.ui.theme.SmallNumber

/**
 * Shown when a scan finds nothing. Steps differ per brand because the menu
 * names do. The scan keeps re-running behind this card, so the user never has
 * to come back and press anything.
 */
private val guides = linkedMapOf(
    "Google TV" to listOf(
        "Settings → System → About",
        "Highlight \"Android TV OS build\" and press OK 7 times, until it says you're a developer",
        "Back to System → Developer options",
        "Turn on \"USB debugging\"",
    ),
    "Android TV" to listOf(
        "Settings → Device Preferences → About",
        "Press OK on \"Build\" 7 times",
        "Back to Device Preferences → Developer options",
        "Turn on \"USB debugging\", and \"Network debugging\" if you see it",
    ),
    "Fire TV" to listOf(
        "Settings → My Fire TV → About",
        "Press OK on the first row (your Fire TV's name) 7 times",
        "Back to My Fire TV → Developer options",
        "Turn on \"ADB debugging\"",
    ),
    "Xiaomi" to listOf(
        "Settings → Device Preferences → About",
        "Press OK on \"Build\" 7 times",
        "Back to Device Preferences → Developer options",
        "Turn on \"USB debugging\"",
    ),
    "XGIMI" to listOf(
        "Settings → Device Preferences → About",
        "Press OK on \"Build\" 7 times",
        "Back to Device Preferences → Developer options",
        "Turn on \"USB debugging\", then \"Network debugging\" if it's listed",
    ),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuideCard(modifier: Modifier = Modifier) {
    var brand by rememberSaveable { mutableStateOf(guides.keys.first()) }
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Turn on debugging on your TV", style = MaterialTheme.typography.titleLarge)
            Text(
                "Pick your TV and follow the steps on it. This screen finds it on its own when you're done.",
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                guides.keys.forEach { b ->
                    FilterChip(selected = b == brand, onClick = { brand = b }, label = { Text(b) })
                }
            }
            guides.getValue(brand).forEachIndexed { i, step ->
                Row {
                    Text("${i + 1}", style = SmallNumber, color = MaterialTheme.colorScheme.primary)
                    androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                    Text(step, style = MaterialTheme.typography.bodyLarge)
                }
            }
            HorizontalDivider()
            Text(
                "Newer TVs (Android 11+) may show \"Wireless debugging\" instead. Turn that on too; you'll type a 6-digit code when you connect.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (brand == "Fire TV") {
                Text(
                    "No Developer options after 7 presses? Newer Fire TVs run Vega OS, which isn't Android, and Tellyscope can't reach them.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                "Debugging switched itself off? Some TVs reset it after a restart. Turn it back on and this screen reconnects.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
