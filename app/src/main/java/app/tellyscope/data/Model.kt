// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

/** How a TV was found: legacy network ADB on 5555, or Android 11+ wireless debugging (TLS + pairing code). */
enum class ScanKind { Classic, Wireless }

data class ScanHit(
    val ip: String,
    val port: Int,
    val kind: ScanKind,
    /** mDNS name, else reverse DNS, else null. */
    val hostname: String?,
    /** From the MAC's OUI; null when the MAC is randomised. */
    val maker: String?,
)

data class ScanProgress(val probed: Int, val total: Int, val subnet: String)

sealed interface ScanEvent {
    data class Found(val hit: ScanHit) : ScanEvent
    data class Progress(val progress: ScanProgress) : ScanEvent
    data object Done : ScanEvent
}

/**
 * ADB reports `unauthorized` both before the user answers and after they press
 * Deny, so there is no "denied" state: [NotAllowed] means "unauthorized past the
 * timeout", and only on a first connect. A TV that was online before and stalls
 * stays [Offline] and keeps retrying. [NeedsPairing] is a wireless-debugging TV
 * that doesn't know our key yet: show the 6-digit code field.
 */
enum class DeviceState { Found, Waiting, NeedsPairing, Online, Offline, NotAllowed }

data class Device(
    val id: String,
    val hit: ScanHit,
    val state: DeviceState,
    val model: String?,
    val manufacturer: String?,
    val android: String?,
    /** Epoch millis of the last successful poll. */
    val lastSeen: Long?,
)

sealed interface ConnectResult {
    data class Online(val device: Device) : ConnectResult
    data object NotAllowed : ConnectResult
    /** Wireless-debugging TV that doesn't know our key; ask for the pairing code. */
    data object NeedsPairing : ConnectResult
    data class WrongCode(val message: String) : ConnectResult
    data class Unreachable(val message: String) : ConnectResult
}

data class Proc(
    val pid: Int,
    val name: String,
    /** Owning package; null for native daemons (shown as "android system"). */
    val packageName: String?,
    val cpuPercent: Double,
    val ramMb: Int,
)

/** One poll of the fast tier. Null fields are stats the device doesn't report; the UI shows "—", never 0. */
data class Snapshot(
    val at: Long,
    val cpuPercent: Int,
    val ramUsedMb: Int,
    val ramTotalMb: Int,
    val storageUsedGb: Double?,
    val storageTotalGb: Double?,
    val tempC: Int?,
    val uptimeSeconds: Long,
    val screenOn: Boolean?,
    val procs: List<Proc>,
)

data class AppInfo(
    val packageName: String,
    /** Human label from the APK. Never the package id; the source falls back to a humanised id. */
    val label: String,
    /** ARGB of the icon tile until real icons are wired; null = neutral tile. */
    val iconColor: Long?,
    val system: Boolean,
    val enabled: Boolean,
    /** Why this app can't be touched, in user words; null when actions are allowed. */
    val protectedReason: String?,
    val version: String,
)

data class Socket(val app: String, val remote: String, val state: String)
data class AppTraffic(val app: String, val rxMb: Int, val txMb: Int)

data class NetworkInfo(
    val iface: String,
    val ip: String,
    val mac: String?,
    val ssid: String?,
    val rssi: Int?,
    val linkMbps: Int?,
    val sockets: List<Socket>,
    val traffic: List<AppTraffic>,
)

enum class Action { Stop, Disable, Enable, Open }

data class HistoryEntry(
    val id: Long,
    val deviceId: String,
    val at: Long,
    val action: Action,
    val packageName: String,
    val label: String,
    /** What the undo button runs; null when there is nothing to undo. */
    val undo: Action?,
    val undoneAt: Long?,
)
