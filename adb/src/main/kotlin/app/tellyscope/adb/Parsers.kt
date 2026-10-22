// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

/** Parsers for raw shell output. Pure functions; every one returns null rather than guessing. */
object Parsers {

    /** Aggregate `cpu` line of /proc/stat. */
    data class CpuTimes(val busy: Long, val total: Long)

    fun cpuTimes(procStat: String): CpuTimes? {
        val line = procStat.lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return null
        val f = line.trim().split(Regex("\\s+")).drop(1).mapNotNull(String::toLongOrNull)
        if (f.size < 4) return null
        val total = f.take(8).sum() // user nice system idle iowait irq softirq steal; guest is already in user
        val idle = f[3] + f.getOrElse(4) { 0 }
        return CpuTimes(total - idle, total)
    }

    /** CPU use between two samples, 0..100, or null if the counters didn't move. */
    fun cpuPercent(prev: CpuTimes, now: CpuTimes): Double? {
        val dt = now.total - prev.total
        if (dt <= 0) return null
        return ((now.busy - prev.busy).toDouble() / dt * 100).coerceIn(0.0, 100.0)
    }

    data class Memory(val totalKb: Long, val availableKb: Long)

    fun memory(meminfo: String): Memory? {
        val kv = meminfo.lineSequence().mapNotNull { line ->
            val name = line.substringBefore(':', "").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            line.substringAfter(':').trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull()?.let { name to it }
        }.toMap()
        val total = kv["MemTotal"] ?: return null
        // MemAvailable exists since kernel 3.14; older TVs get a rough free+cached.
        val avail = kv["MemAvailable"] ?: ((kv["MemFree"] ?: return null) + (kv["Cached"] ?: 0))
        return Memory(total, avail)
    }

    data class Storage(val totalKb: Long, val usedKb: Long)

    /** `df -k /data`. Toybox may wrap a long filesystem name onto its own line. */
    fun storage(df: String): Storage? {
        val nums = df.lineSequence().drop(1).joinToString(" ").trim().split(Regex("\\s+"))
            .mapNotNull(String::toLongOrNull)
        if (nums.size < 2) return null
        return Storage(totalKb = nums[0], usedKb = nums[1])
    }

    fun uptimeSeconds(procUptime: String): Long? =
        procUptime.trim().substringBefore(' ').toDoubleOrNull()?.toLong()

    /**
     * Output of [Commands.THERMAL]: `type=millidegrees` lines. Prefers a CPU/SoC zone, otherwise
     * the hottest plausible one. Null when nothing readable, so the UI shows a dash, never 0.
     */
    fun temperatureC(thermal: String): Double? {
        val zones = thermal.lineSequence().mapNotNull { line ->
            val type = line.substringBefore('=', "").trim().lowercase()
            val raw = line.substringAfter('=', "").trim().toDoubleOrNull() ?: return@mapNotNull null
            val c = if (raw > 1000) raw / 1000 else raw // some kernels report whole degrees
            if (c in 1.0..150.0) type to c else null
        }.toList()
        if (zones.isEmpty()) return null
        val cpu = zones.filter { (t, _) -> listOf("cpu", "soc", "tsens", "core").any { t.contains(it) } }
        return (cpu.ifEmpty { zones }).maxOf { it.second }
    }

    data class Package(val name: String, val uid: Int?, val versionCode: Long? = null)

    /**
     * `pm list packages [--show-versioncode] [-U]`: `package:com.foo versionCode:42 uid:10023`
     * (uid list may be comma-separated).
     */
    fun packages(pmList: String): List<Package> = pmList.lineSequence().mapNotNull { line ->
        val l = line.trim()
        if (!l.startsWith("package:")) return@mapNotNull null
        val name = l.removePrefix("package:").substringBefore(' ')
        val uid = l.substringAfter(" uid:", "").substringBefore(',').toIntOrNull()
        val version = l.substringAfter(" versionCode:", "").substringBefore(' ').toLongOrNull()
        Package(name, uid, version).takeIf { name.isNotEmpty() }
    }.toList()

    data class Process(val pid: Int, val user: String, val rssKb: Long, val cpuPercent: Double, val name: String)

    /**
     * `top -b -n 1 -o PID,USER,RES,%CPU,NAME`. Header and summary lines are skipped by requiring a
     * numeric first column. RES carries K/M/G suffixes.
     */
    fun processes(top: String): List<Process> = top.lineSequence().mapNotNull { line ->
        val f = line.trim().split(Regex("\\s+"), limit = 5)
        if (f.size < 5) return@mapNotNull null
        val pid = f[0].toIntOrNull() ?: return@mapNotNull null
        val rss = sizeKb(f[2]) ?: return@mapNotNull null
        val cpu = f[3].toDoubleOrNull() ?: return@mapNotNull null
        Process(pid, f[1], rss, cpu, f[4])
    }.toList()

    /** `u0_a123` → 10123, `system` etc. → null. */
    fun appUid(user: String): Int? {
        val m = Regex("u(\\d+)_a(\\d+)").matchEntire(user) ?: return null
        return m.groupValues[1].toInt() * 100_000 + 10_000 + m.groupValues[2].toInt()
    }

    internal fun sizeKb(s: String): Long? {
        val n = s.dropLast(1).toDoubleOrNull()
        return when (s.lastOrNull()?.uppercaseChar()) {
            'K' -> n?.toLong()
            'M' -> n?.let { (it * 1024).toLong() }
            'G' -> n?.let { (it * 1024 * 1024).toLong() }
            else -> s.toLongOrNull()?.let { it / 1024 } // bare bytes
        }
    }

    /** `getprop` lines: `[ro.product.model]: [XK03H]`. */
    fun props(getprop: String): Map<String, String> = getprop.lineSequence().mapNotNull { line ->
        val m = Regex("""\[(.+?)]: \[(.*)]""").matchEntire(line.trim()) ?: return@mapNotNull null
        m.groupValues[1] to m.groupValues[2]
    }.toMap()
}
