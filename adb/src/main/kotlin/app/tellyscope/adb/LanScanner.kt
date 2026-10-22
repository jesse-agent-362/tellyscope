// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import com.flyfishxu.kadb.mdns.KadbMdns
import com.flyfishxu.kadb.mdns.MdnsServiceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** A device answering on the LAN, before any ADB handshake. */
data class ScanHit(
    val host: String,
    val port: Int,
    val kind: Kind,
    /** mDNS service name, else reverse DNS; null when neither answers. */
    val name: String?,
) {
    enum class Kind {
        /** `adb tcpip` / network debugging on a fixed port (usually 5555). */
        CLASSIC,
        /** Android 11+ wireless debugging, already paired: connect port from `_adb-tls-connect`. */
        WIRELESS,
        /** Wireless debugging with the "pair with code" box open: needs the 6-digit code. */
        PAIRING,
    }
}

/**
 * Finds ADB on the local /24s: a TCP sweep of [port] plus, if given, mDNS for wireless debugging.
 * MAC/maker lookup is left out: Android 10+ apps can't read the ARP table.
 */
class LanScanner(
    private val mdns: KadbMdns? = null,
    private val port: Int = 5555,
    private val probeTimeout: Duration = 400.milliseconds,
    private val mdnsWindow: Duration = 3.seconds,
    private val parallelism: Int = 64,
) {
    /** Emits each hit as soon as it answers; completes when the sweep and the mDNS window end. */
    fun scan(hosts: List<String> = localSubnetHosts()): Flow<ScanHit> = channelFlow {
        launch { sweep(hosts) }
        mdns?.let { launch { browse(it) } }
    }

    private suspend fun ProducerScope<ScanHit>.sweep(hosts: List<String>) {
        val gate = Semaphore(parallelism)
        hosts.forEach { host ->
            launch(Dispatchers.IO) {
                gate.withPermit {
                    if (isOpen(host)) send(ScanHit(host, port, ScanHit.Kind.CLASSIC, reverseName(host)))
                }
            }
        }
    }

    private suspend fun ProducerScope<ScanHit>.browse(mdns: KadbMdns) {
        withContext(Dispatchers.IO) { mdns.start() } // JmDNS setup blocks
        try {
            val seen = mutableSetOf<Pair<String, Int>>()
            withTimeoutOrNull(mdnsWindow) {
                mdns.state.collect { state ->
                    for (e in state.allDevices) {
                        if (!seen.add(e.host to e.port)) continue
                        val kind = when (e.serviceType) {
                            MdnsServiceType.TLS_PAIRING -> ScanHit.Kind.PAIRING
                            MdnsServiceType.TLS_CONNECT -> ScanHit.Kind.WIRELESS
                            else -> ScanHit.Kind.CLASSIC
                        }
                        send(ScanHit(e.host, e.port, kind, e.name))
                    }
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { mdns.stop() }
        }
    }

    private fun isOpen(host: String): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), probeTimeout.inWholeMilliseconds.toInt()); true }
    } catch (_: Exception) {
        false
    }

    private fun reverseName(host: String): String? =
        runCatching { InetAddress.getByName(host).canonicalHostName }.getOrNull()?.takeIf { it != host }

    companion object {
        /** Every other address in the /24 of each up, non-loopback IPv4 interface. */
        fun localSubnetHosts(): List<String> =
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .flatMap { addr ->
                    val b = addr.address
                    val prefix = "${b[0].toUByte()}.${b[1].toUByte()}.${b[2].toUByte()}"
                    (1..254).map { "$prefix.$it" }.filter { it != addr.hostAddress }
                }
                .distinct()
    }
}
