// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import app.tellyscope.data.Action
import app.tellyscope.data.DeviceState
import app.tellyscope.data.Snapshot

/**
 * Every user-facing sentence about actions and time lives here so wording is
 * reviewed in one place: every button says what it does before it runs.
 */
object Copy {
    fun button(action: Action) = when (action) {
        Action.Stop -> "Stop"
        Action.Disable -> "Disable"
        Action.Enable -> "Turn on"
        Action.Open -> "Open"
    }

    fun confirm(action: Action) = when (action) {
        Action.Stop -> "Yes, stop it"
        Action.Disable -> "Yes, disable it"
        Action.Enable -> "Yes, turn it on"
        Action.Open -> "Yes, open it"
    }

    /**
     * Names the package next to the label: the label comes from the app itself, and any app can
     * call itself "Settings". The package id is what actually gets changed.
     */
    fun explain(action: Action, app: String, pkg: String) = "$app ($pkg)".let { who ->
        when (action) {
            Action.Stop -> "Stops $who until you open it again."
            Action.Disable -> "Hides $who and stops it running. Nothing is deleted; you can turn it back on here."
            Action.Enable -> "Turns $who back on."
            Action.Open -> "Opens $who on the TV."
        }
    }

    fun done(action: Action, app: String) = when (action) {
        Action.Stop -> "Stopped $app"
        Action.Disable -> "Disabled $app"
        Action.Enable -> "Turned on $app"
        Action.Open -> "Opened $app"
    }

    /** Force-stop has no true undo; the button says what it actually does. */
    fun undo(undo: Action) = if (undo == Action.Open) "Open again" else "Undo"

    fun ago(at: Long, now: Long): String {
        val s = ((now - at) / 1000).coerceAtLeast(0)
        return when {
            s < 10 -> "just now"
            s < 60 -> "$s s ago"
            s < 3600 -> "${s / 60} min ago"
            s < 86_400 -> "${s / 3600} h ago"
            else -> "${s / 86_400} d ago"
        }
    }

    fun uptime(s: Long): String {
        val d = s / 86_400
        val h = (s % 86_400) / 3600
        val m = (s % 3600) / 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    fun mb(v: Int) = if (v >= 1024) "%.1f GB".format(v / 1024.0) else "$v MB"

    const val ANDROID_SYSTEM = "Android system"

    /** The word next to the status dot, so the state never rests on colour alone. */
    fun state(s: DeviceState) = when (s) {
        DeviceState.Online -> "Connected"
        DeviceState.Offline -> "Offline"
        DeviceState.NotAllowed -> "Not allowed"
        DeviceState.Waiting -> "Connecting"
        DeviceState.NeedsPairing -> "Needs the pairing code"
        DeviceState.Found -> "Not connected"
    }

    /** Under 10 GB a whole number hides too much: a 1.5 GB TV isn't a 2 GB one. */
    fun gbOf(used: Double, total: Double) =
        if (total < 10) "%.1f of %.1f GB".format(used, total) else "%.1f of %.0f GB".format(used, total)

    /** An offline TV that has never sent stats; a refusing TV is on, it just hasn't allowed us. */
    fun noStats(s: DeviceState) =
        if (s == DeviceState.NotAllowed) "Stats show up once the TV allows this phone."
        else "No stats yet. They show up once the TV is back on."

    const val WRONG_CODE = "That code didn't work. Check the 6 digits on the TV and try again."
    const val UNREACHABLE = "The TV didn't answer. Check it's on and on the same Wi-Fi as this phone, then try again."

    data class Health(val ok: Boolean, val text: String)

    /**
     * The one line at the top of a TV's page: the most pressing problem, or that all is well.
     * [heaviest] are app names by memory use, largest first; there is no per-app storage size yet.
     */
    fun health(s: Snapshot, heaviest: List<String>): Health {
        val storage = s.storageUsedGb?.let { u -> s.storageTotalGb?.takeIf { it > 0 }?.let { u / it } }
        val ram = if (s.ramTotalMb > 0) s.ramUsedMb.toDouble() / s.ramTotalMb else null
        val most = heaviest.take(2).takeIf { it.isNotEmpty() }?.let { ": ${it.joinToString(" and ")} use the most" }.orEmpty()
        return when {
            storage != null && storage >= FULL -> Health(false, "Storage almost full")
            ram != null && ram >= FULL -> Health(false, "Memory almost full$most")
            (s.tempC ?: 0) >= HOT_C -> Health(false, "Running hot")
            else -> Health(true, "Running fine")
        }
    }

    const val FULL = 0.9
    private const val HOT_C = 80
}
