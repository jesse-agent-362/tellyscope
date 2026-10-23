// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.concurrent.thread
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ConnectorTest {
    @BeforeTest fun key() = Connector.init(File(Files.createTempDirectory("adbkey").toFile(), "adbkey"))

    @Test fun portOutOfRangeIsUnreachable() = runBlocking<Unit> {
        // InetSocketAddress threw IllegalArgumentException here, which nothing caught.
        assertIs<ConnectResult.Unreachable>(Connector.connect("127.0.0.1", 70000))
        assertIs<ConnectResult.Unreachable>(Connector.connect("127.0.0.1", 0))
    }

    @Test fun pairingThatDropsMidHandshakeIsUnreachable() = runBlocking<Unit> {
        // A pairing port that hangs up straight away, as one does when the Wi-Fi drops.
        ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { server ->
            thread(isDaemon = true) { runCatching { while (true) server.accept().close() } }
            val r = withTimeout(30_000) { Connector.pair("127.0.0.1", server.localPort, "123456") }
            assertIs<PairResult.Unreachable>(r)
        }
    }

    @Test fun pairingWithNothingListeningIsUnreachable() = runBlocking<Unit> {
        val port = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        assertIs<PairResult.Unreachable>(withTimeout(30_000) { Connector.pair("127.0.0.1", port, "123456") })
    }

    @Test fun badCodeIsWrongCode() = runBlocking<Unit> {
        assertEquals(PairResult.WrongCode("pairing code must be 6 digits"), Connector.pair("127.0.0.1", 1, "12a456"))
    }
}
