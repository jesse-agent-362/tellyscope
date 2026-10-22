// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.cli

import app.tellyscope.adb.Connector
import app.tellyscope.adb.DeviceLink
import app.tellyscope.adb.LanScanner
import com.flyfishxu.kadb.mdns.KadbMdnsJvm
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Smoke test for :adb from a desktop on the TV's LAN.
 *   scan                 list ADB devices
 *   stats <host> [port]  connect (press Allow on the TV) and print live stats
 *   pair <host> <port> <code>
 */
fun main(args: Array<String>) = runBlocking {
    Connector.init(File(System.getProperty("user.home"), ".tellyscope/adbkey.pem"))
    when (args.firstOrNull()) {
        "scan" -> LanScanner(KadbMdnsJvm()).scan().collect { println("${it.host}:${it.port}  ${it.kind}  ${it.name ?: "-"}") }
        "pair" -> Connector.pair(args[1], args[2].toInt(), args[3]).also { println("paired") }
        "stats" -> {
            val link = DeviceLink(args[1], args.getOrNull(2)?.toInt() ?: 5555, this)
            launch { link.state.collect { println("state: $it") } }
            launch { link.stats.filterNotNull().onEach { println(it) }.collect() }
            link.connect()
        }
        else -> println("usage: scan | stats <host> [port] | pair <host> <port> <code>")
    }
}
