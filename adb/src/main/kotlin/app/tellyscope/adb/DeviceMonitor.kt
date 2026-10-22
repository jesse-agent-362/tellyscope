// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** One reading for the stat strip. Null fields mean "this TV doesn't report it". */
data class Stats(
    val cpuPercent: Double?,
    val memory: Parsers.Memory?,
    val storage: Parsers.Storage? = null,
    val uptimeSeconds: Long? = null,
    val temperatureC: Double? = null,
    val model: String? = null,
    val manufacturer: String? = null,
    val androidVersion: String? = null,
    val sdk: Int? = null,
)

/** Where [DeviceLink] gets readings from; [DeviceMonitor] in the app, a fake in tests. */
fun interface MonitorSource {
    fun stats(): Flow<Stats>
}

/**
 * Live stats on two tiers: CPU/RAM every [fast], identity/storage/uptime/temperature every [slow].
 * The first emission comes after one fast interval, since CPU % needs two samples.
 * Shell failures and reads slower than [readTimeout] throw [IOException], so the caller can mark
 * the device offline instead of waiting out the socket timeout.
 */
class DeviceMonitor(
    private val shell: AdbShell,
    private val fast: Duration = 2.seconds,
    private val slow: Duration = 60.seconds,
    private val readTimeout: Duration = 10.seconds,
) : MonitorSource {
    override fun stats(): Flow<Stats> = flow {
        var slowStats = readSlow()
        var lastSlow = TimeSource.Monotonic.markNow()
        var prevCpu = readFast().first

        while (true) {
            delay(fast)
            if (lastSlow.elapsedNow() >= slow) {
                slowStats = readSlow()
                lastSlow = TimeSource.Monotonic.markNow()
            }
            val (cpu, mem) = readFast()
            val pct = if (prevCpu != null && cpu != null) Parsers.cpuPercent(prevCpu, cpu) else null
            prevCpu = cpu
            emit(slowStats.copy(cpuPercent = pct, memory = mem))
        }
    }

    private suspend fun run(command: String): String =
        withTimeoutOrNull(readTimeout) { shell.shell(command).output }
            ?: throw IOException("no answer in $readTimeout")

    private suspend fun readFast(): Pair<Parsers.CpuTimes?, Parsers.Memory?> {
        val (stat, meminfo) = Commands.split(run(Commands.FAST), 2)
        return Parsers.cpuTimes(stat) to Parsers.memory(meminfo)
    }

    private suspend fun readSlow(): Stats {
        val (getprop, df, uptime, thermal) = Commands.split(run(Commands.SLOW), 4)
        val props = Parsers.props(getprop)
        return Stats(
            cpuPercent = null,
            memory = null,
            storage = Parsers.storage(df),
            uptimeSeconds = Parsers.uptimeSeconds(uptime),
            temperatureC = Parsers.temperatureC(thermal),
            model = props["ro.product.model"],
            manufacturer = props["ro.product.manufacturer"],
            androidVersion = props["ro.build.version.release"],
            sdk = props["ro.build.version.sdk"]?.toIntOrNull(),
        )
    }
}
