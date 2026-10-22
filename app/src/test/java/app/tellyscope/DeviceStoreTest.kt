// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import app.tellyscope.data.DeviceStore
import app.tellyscope.data.SavedDevice
import app.tellyscope.data.ScanKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeviceStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val tv = SavedDevice("192.168.1.23", 5555, ScanKind.Classic, "xgimi-projector", "XGIMI Horizon Pro", 1_760_000_000_000)
    private val stick = SavedDevice("192.168.1.57", 37211, ScanKind.Wireless, null, null, null)

    @Test fun missingFileIsEmpty() {
        assertEquals(emptyList<SavedDevice>(), DeviceStore(tmp.root.resolve("devices.tsv")).load())
    }

    @Test fun survivesARestart() {
        val file = tmp.root.resolve("devices.tsv")
        DeviceStore(file).save(listOf(tv, stick))
        // A new store on the same file is what the next launch sees.
        assertEquals(listOf(tv, stick), DeviceStore(file).load())
    }

    @Test fun saveReplacesTheList() {
        val store = DeviceStore(tmp.root.resolve("devices.tsv"))
        store.save(listOf(tv, stick))
        store.save(listOf(stick))
        assertEquals(listOf(stick), store.load())
    }

    @Test fun networkNamesCannotBreakTheFormat() {
        val store = DeviceStore(tmp.root.resolve("devices.tsv"))
        store.save(listOf(tv.copy(name = "living\troom\nTV")))
        assertEquals(listOf(tv.copy(name = "living room TV")), store.load())
    }

    @Test fun damagedLinesCostOnlyThemselves() {
        val file = tmp.root.resolve("devices.tsv")
        DeviceStore(file).save(listOf(tv))
        file.appendText("v1\tbroken\n")
        file.appendText("v9\t192.168.1.88\t5555\tClassic\t\t\t\n")
        file.appendText("v1\t192.168.1.88\tnot-a-port\tClassic\t\t\t\n")
        assertEquals(listOf(tv), DeviceStore(file).load())
    }
}
