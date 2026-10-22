// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DeviceMonitorTest {
    private val sep = "\n@@tellyscope@@\n"

    /** Each FAST call advances the CPU counters by busy +25 / total +100. */
    private fun device(): AdbShell {
        var tick = 0L
        return AdbShell { cmd ->
            if (cmd.startsWith("cat /proc/stat")) {
                tick++
                ok("cpu  ${tick * 25} 0 0 ${tick * 75} 0 0 0 0\n${sep}MemTotal: 2000 kB\nMemAvailable: 500 kB\n")
            } else {
                ok("[ro.product.model]: [XK03H]\n[ro.build.version.sdk]: [30]\n$sep" +
                    "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/x 16000 9800 6200 62% /data\n$sep" +
                    "300.5 100.0\n$sep" + "cpu-thermal=52000\n")
            }
        }
    }

    @Test fun firstReadingHasEverything() = runTest {
        val s = DeviceMonitor(device()).stats().first()
        assertEquals(25.0, s.cpuPercent)
        assertEquals(Parsers.Memory(2000, 500), s.memory)
        assertEquals(Parsers.Storage(16000, 9800), s.storage)
        assertEquals(300L, s.uptimeSeconds)
        assertEquals(52.0, s.temperatureC)
        assertEquals("XK03H", s.model)
        assertEquals(30, s.sdk)
    }

    @Test fun keepsEmitting() = runTest {
        assertEquals(3, DeviceMonitor(device()).stats().take(3).toList().size)
    }

    @Test fun silentDeviceThrowsIo() = runTest {
        val hung = AdbShell { kotlinx.coroutines.awaitCancellation() }
        assertFailsWith<IOException> { DeviceMonitor(hung).stats().first() }
    }
}
