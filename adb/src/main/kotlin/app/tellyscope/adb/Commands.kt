// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

/**
 * Shell snippets, batched so one tier costs one ADB round trip. Sections are split on [SEP].
 */
internal object Commands {
    const val SEP = "@@tellyscope@@"

    const val THERMAL =
        "for z in /sys/class/thermal/thermal_zone*; do echo \"$(cat \$z/type 2>/dev/null)=$(cat \$z/temp 2>/dev/null)\"; done"

    /** Every 2 s: CPU counters and memory. */
    val FAST = listOf("cat /proc/stat", "cat /proc/meminfo").batch()

    /** Every 60 s and on open: identity, storage, uptime, temperature. */
    val SLOW = listOf(
        "getprop",
        "df -k /data",
        "cat /proc/uptime",
        THERMAL,
    ).batch()

    const val PROCESSES = "top -b -n 1 -o PID,USER,RES,%CPU,NAME"
    const val PACKAGES = "pm list packages -U"
    const val HOME_ACTIVITY = "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
    const val HOME_ACTIVITIES = "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME"

    private fun List<String>.batch() = joinToString("; echo $SEP; ") { "$it 2>/dev/null" }

    fun split(output: String, expected: Int): List<String> =
        output.split("$SEP\n", SEP).let { parts -> List(expected) { parts.getOrElse(it) { "" } } }
}
