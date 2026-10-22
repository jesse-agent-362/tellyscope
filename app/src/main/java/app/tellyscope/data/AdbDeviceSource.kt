// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.tellyscope.adb.ActionResult
import app.tellyscope.adb.Actions
import app.tellyscope.adb.Connector
import app.tellyscope.adb.DeviceLink
import app.tellyscope.adb.LanScanner
import app.tellyscope.adb.Parsers
import app.tellyscope.adb.Protected
import app.tellyscope.adb.ProtectedReason
import com.flyfishxu.kadb.exception.AdbPairAuthException
import com.flyfishxu.kadb.mdns.KadbMdnsAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import java.io.File
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap
import app.tellyscope.adb.Action as AdbAction
import app.tellyscope.adb.ConnectResult as AdbConnectResult
import app.tellyscope.adb.DeviceState as AdbState
import app.tellyscope.adb.ScanHit as AdbHit

/**
 * [DeviceSource] on real TVs through `:adb`. Device id is the TV's IP. Known devices live for the
 * app process only (persisting them is a later slice), and `procs` / network details beyond the
 * IP are empty for now: slice 1 is scan → connect → live CPU/RAM.
 */
class AdbDeviceSource(context: Context) : DeviceSource {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob())
    private val links = ConcurrentHashMap<String, DeviceLink>()
    private val hits = ConcurrentHashMap<String, ScanHit>()
    /** Wireless debugging advertises pairing and connect on different ports. */
    private val pairingPorts = ConcurrentHashMap<String, Int>()
    private var historyId = 0L

    init {
        Connector.init(File(appContext.filesDir, "adbkey.pem"))
    }

    /**
     * The subnet of the active network if it's Wi-Fi or Ethernet. On mobile data or a VPN there is
     * no sweep, so we never probe a carrier's or VPN's neighbours; mDNS and add-by-IP still work.
     */
    private fun lanHosts(): List<String> {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val network = cm.activeNetwork ?: return emptyList()
        val caps = cm.getNetworkCapabilities(network) ?: return emptyList()
        val local = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        if (!local || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return emptyList()
        return cm.getLinkProperties(network)?.linkAddresses.orEmpty()
            .filter { it.address is Inet4Address }
            .flatMap { LanScanner.subnetHosts(it.address as Inet4Address, it.prefixLength) }
            .distinct()
    }

    override fun scan(): Flow<ScanEvent> = flow {
        LanScanner(KadbMdnsAndroid(appContext)).scan(lanHosts()).collect { h ->
            if (h.kind == AdbHit.Kind.PAIRING) {
                pairingPorts[h.host] = h.port
                if (hits.containsKey(h.host)) return@collect
            }
            val hit = ScanHit(
                ip = h.host,
                port = h.port,
                kind = if (h.kind == AdbHit.Kind.CLASSIC) ScanKind.Classic else ScanKind.Wireless,
                hostname = h.name,
                maker = null, // Android 10+ apps can't read the ARP table
            )
            hits[h.host] = hit
            emit(ScanEvent.Found(hit))
        }
        emit(ScanEvent.Done)
    }

    override suspend fun connect(hit: ScanHit): ConnectResult = when (val r = Connector.connect(hit.ip, hit.port)) {
        is AdbConnectResult.Online -> {
            hits[hit.ip] = hit
            // Hand the open session to a link, which reconnects on its own from here on.
            var first: AdbConnectResult? = r
            val link = DeviceLink(hit.ip, hit.port, scope, open = { h, p ->
                first?.also { first = null } ?: Connector.connect(h, p)
            })
            links.put(hit.ip, link)?.disconnect()
            link.connect()
            ConnectResult.Online(device(hit.ip))
        }
        AdbConnectResult.NotAllowed -> ConnectResult.NotAllowed
        AdbConnectResult.NeedsPairing -> ConnectResult.NeedsPairing
        is AdbConnectResult.Unreachable -> ConnectResult.Unreachable(r.reason)
    }

    override suspend fun pair(hit: ScanHit, code: String): ConnectResult {
        try {
            Connector.pair(hit.ip, pairingPorts[hit.ip] ?: hit.port, code)
        } catch (e: AdbPairAuthException) {
            return ConnectResult.WrongCode(e.message ?: "pairing failed")
        } catch (e: IllegalArgumentException) {
            return ConnectResult.WrongCode(e.message ?: "pairing code must be 6 digits")
        }
        // After pairing, the connect port is the one mDNS advertised, not the pairing port.
        val connectHit = hits[hit.ip]?.takeIf { it.port != pairingPorts[hit.ip] } ?: hit
        return connect(connectHit)
    }

    override suspend fun known(): List<Device> = links.keys.map(::device)

    override fun live(deviceId: String): Flow<Snapshot> {
        val link = links[deviceId] ?: return flow {}
        // Completes once the link drops (offline, not allowed, needs pairing), as the interface promises.
        return combine(link.state, link.stats) { state, stats -> state to stats }
            .transformWhile { (state, stats) ->
                if (state == AdbState.ONLINE && stats != null) emit(stats)
                state in setOf(AdbState.FOUND, AdbState.WAITING, AdbState.ONLINE)
            }
            .map { s ->
                val mem = s.memory
                Snapshot(
                    at = System.currentTimeMillis(),
                    cpuPercent = s.cpuPercent?.toInt() ?: 0,
                    ramUsedMb = mem?.let { ((it.totalKb - it.availableKb) / 1024).toInt() } ?: 0,
                    ramTotalMb = mem?.let { (it.totalKb / 1024).toInt() } ?: 0,
                    storageUsedGb = s.storage?.let { it.usedKb / 1_048_576.0 },
                    storageTotalGb = s.storage?.let { it.totalKb / 1_048_576.0 },
                    tempC = s.temperatureC?.toInt(),
                    uptimeSeconds = s.uptimeSeconds ?: 0,
                    screenOn = null,
                    procs = emptyList(),
                )
            }
    }

    override suspend fun apps(deviceId: String): List<AppInfo> {
        val shell = session(deviceId)
        val home = Actions(shell).homePackages()
        val system = Parsers.packages(shell.shell("pm list packages -s").output).map { it.name }.toSet()
        val disabled = Parsers.packages(shell.shell("pm list packages -d").output).map { it.name }.toSet()
        return Parsers.packages(shell.shell("pm list packages").output).map { p ->
            AppInfo(
                packageName = p.name,
                label = humanise(p.name),
                iconColor = null,
                system = p.name in system,
                enabled = p.name !in disabled,
                // Same rule as Actions.run: no known launcher, nothing can be stopped or disabled.
                // A disabled app stays unprotected so Enable (always allowed) still shows.
                protectedReason = when {
                    home.isNotEmpty() -> Protected.reason(p.name, home)
                    p.name in disabled -> null
                    else -> ProtectedReason.UNKNOWN_HOME
                }?.let(::reasonText),
                version = "",
            )
        }.sortedBy { it.label.lowercase() }
    }

    override suspend fun network(deviceId: String): NetworkInfo {
        val out = session(deviceId).shell("ip -o -4 addr show").output
        val line = out.lineSequence().firstOrNull { "inet " in it && " lo " !in it }
        val f = line?.trim()?.split(Regex("\\s+")).orEmpty()
        return NetworkInfo(
            iface = f.getOrElse(1) { "" },
            ip = f.getOrNull(3)?.substringBefore('/') ?: deviceId,
            mac = null, ssid = null, rssi = null, linkMbps = null,
            sockets = emptyList(), traffic = emptyList(),
        )
    }

    override suspend fun act(deviceId: String, action: Action, packageName: String): HistoryEntry {
        val r = Actions(session(deviceId)).run(AdbAction.valueOf(action.name.uppercase()), packageName)
        return when (r) {
            is ActionResult.Done -> HistoryEntry(
                id = ++historyId,
                deviceId = deviceId,
                at = System.currentTimeMillis(),
                action = action,
                packageName = packageName,
                label = humanise(packageName),
                undo = r.undo?.let { Action.valueOf(it.name.lowercase().replaceFirstChar(Char::uppercase)) },
                undoneAt = null,
            )
            is ActionResult.Refused -> throw IllegalStateException(reasonText(r.reason))
            is ActionResult.Failed -> throw IllegalStateException(r.output.ifEmpty { "the TV refused" })
        }
    }

    private fun session(deviceId: String) =
        links[deviceId]?.session ?: throw IllegalStateException("$deviceId is offline")

    private fun device(id: String): Device {
        val link = links[id]
        val stats = link?.stats?.value
        return Device(
            id = id,
            hit = hits.getValue(id),
            state = when (link?.state?.value) {
                AdbState.WAITING -> DeviceState.Waiting
                AdbState.ONLINE -> DeviceState.Online
                AdbState.OFFLINE -> DeviceState.Offline
                AdbState.NOT_ALLOWED -> DeviceState.NotAllowed
                AdbState.NEEDS_PAIRING -> DeviceState.NeedsPairing
                AdbState.FOUND, null -> DeviceState.Found
            },
            model = stats?.model,
            manufacturer = stats?.manufacturer,
            android = stats?.androidVersion,
            lastSeen = link?.lastSeenMillis,
        )
    }

    /** `com.google.android.youtube.tv` → "Youtube". Real APK labels come with the apps slice. */
    private fun humanise(pkg: String): String {
        val parts = pkg.split('.').filter { it !in setOf("com", "android", "google", "tv", "app", "apps", "org") }
        return (parts.lastOrNull() ?: pkg).replaceFirstChar(Char::uppercase)
    }

    /** Copy from the protected-apps table in the UX copy doc. */
    private fun reasonText(r: ProtectedReason) = when (r) {
        ProtectedReason.HOME_SCREEN -> "this is your home screen"
        ProtectedReason.SYSTEM -> "the TV needs this to work"
        ProtectedReason.SETTINGS -> "you need this to change TV settings"
        ProtectedReason.PLAY_SERVICES -> "most apps stop working without it"
        ProtectedReason.KEYBOARD -> "you'd lose the on-screen keyboard"
        ProtectedReason.TV_INPUTS_REMOTE -> "your remote or HDMI inputs could stop working"
        ProtectedReason.SETUP_WIZARD -> "the TV may get stuck on restart without it"
        ProtectedReason.UNKNOWN_HOME -> "this TV didn't say which app is its home screen, so Tellyscope won't change anything"
    }
}
