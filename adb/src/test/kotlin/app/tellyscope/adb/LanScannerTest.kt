// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals

class LanScannerTest {
    @Test fun findsOnlyOpenPorts() = runBlocking {
        ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { server ->
            val hits = LanScanner(port = server.localPort).scan(listOf("127.0.0.1", "127.0.0.2")).toList()
            assertEquals(listOf("127.0.0.1"), hits.map { it.host })
            assertEquals(ScanHit.Kind.CLASSIC, hits.single().kind)
        }
    }
}
