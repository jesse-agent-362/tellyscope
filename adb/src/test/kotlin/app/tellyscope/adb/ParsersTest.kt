// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParsersTest {
    @Test fun cpuPercentFromTwoSamples() {
        val a = Parsers.cpuTimes("cpu  100 0 100 700 100 0 0 0 0 0\ncpu0 1 2 3 4\n")!!
        val b = Parsers.cpuTimes("cpu  150 0 150 750 150 0 0 0 0 0\n")!!
        // busy +100 of total +200
        assertEquals(50.0, Parsers.cpuPercent(a, b))
        assertNull(Parsers.cpuPercent(b, b))
        assertNull(Parsers.cpuTimes("cpu0 1 2 3 4"))
    }

    @Test fun memoryPrefersMemAvailable() {
        val m = Parsers.memory("MemTotal:        2048000 kB\nMemFree:  100000 kB\nMemAvailable:    900000 kB\nCached: 5 kB\n")
        assertEquals(Parsers.Memory(2048000, 900000), m)
        assertEquals(Parsers.Memory(10, 7), Parsers.memory("MemTotal: 10 kB\nMemFree: 4 kB\nCached: 3 kB\n"))
        assertNull(Parsers.memory(""))
    }

    @Test fun storageHandlesWrappedFilesystemName() {
        val plain = "Filesystem     1K-blocks    Used Available Use% Mounted on\n/dev/block/dm-5  16000000 9800000   6200000  62% /data\n"
        val wrapped = "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/block/bootdevice/by-name/userdata\n 16000000 9800000 6200000 62% /data\n"
        assertEquals(Parsers.Storage(16000000, 9800000), Parsers.storage(plain))
        assertEquals(Parsers.Storage(16000000, 9800000), Parsers.storage(wrapped))
        assertNull(Parsers.storage("df: /data: Permission denied\n"))
    }

    @Test fun temperaturePrefersCpuZoneAndScales() {
        assertEquals(52.0, Parsers.temperatureC("battery=30000\ncpu-thermal=52000\ngpu=61000\n"))
        assertEquals(61.0, Parsers.temperatureC("battery=30000\ngpu=61000\n"))
        assertEquals(45.0, Parsers.temperatureC("soc_thermal=45\n"))
        assertNull(Parsers.temperatureC("=\ncpu=\nx=-40000\n"))
    }

    @Test fun packagesWithUid() {
        val out = "package:com.google.android.youtube.tv uid:10087\npackage:android uid:1000\npackage:com.shared uid:10050,10051\njunk\n"
        assertEquals(
            listOf(
                Parsers.Package("com.google.android.youtube.tv", 10087),
                Parsers.Package("android", 1000),
                Parsers.Package("com.shared", 10050),
            ),
            Parsers.packages(out),
        )
    }

    @Test fun processesSkipHeaders() {
        val top = """
            Tasks: 210 total,   1 running, 209 sleeping
              Mem:  2000000K total
            PID USER          RES  %CPU NAME
            512 system       120M   9.0 system_server
            2201 u0_a87      312M   3.5 com.google.android.youtube.tv
            33 root          2.5K   0.0 kworker/0:1 extra
        """.trimIndent()
        val p = Parsers.processes(top)
        assertEquals(3, p.size)
        assertEquals(Parsers.Process(2201, "u0_a87", 312 * 1024, 3.5, "com.google.android.youtube.tv"), p[1])
        assertEquals("kworker/0:1 extra", p[2].name)
        assertEquals(10087, Parsers.appUid("u0_a87"))
        assertNull(Parsers.appUid("system"))
    }

    @Test fun propsAndUptime() {
        val p = Parsers.props("[ro.product.model]: [XK03H]\n[ro.build.version.sdk]: [30]\n[empty]: []\n")
        assertEquals("XK03H", p["ro.product.model"])
        assertEquals("", p["empty"])
        assertEquals(273600L, Parsers.uptimeSeconds("273600.52 1000000.00\n"))
    }
}
