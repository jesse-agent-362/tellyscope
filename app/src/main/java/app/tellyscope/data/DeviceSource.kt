// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

import kotlinx.coroutines.flow.Flow

/**
 * Everything the screens need from a TV. The UI only talks to this; an
 * adapter over the `:adb` module implements it for real devices, and
 * [FakeDeviceSource] stands in for screenshots and until that lands. Refresh tiers follow the slice-1 data list:
 * fast 2 s ([live]), medium 10 s ([network]), slow / on open ([apps]).
 */
interface DeviceSource {
    /** LAN sweep for 5555 plus mDNS for wireless debugging. Emits hits as they answer, then [ScanEvent.Done]. */
    fun scan(): Flow<ScanEvent>

    /** Classic connect. Suspends while the TV shows the Allow prompt; the caller owns the timeout. */
    suspend fun connect(hit: ScanHit): ConnectResult

    /** Wireless-debugging pairing with the 6-digit code shown on the TV, then connect. */
    suspend fun pair(hit: ScanHit, code: String): ConnectResult

    /** Previously paired devices, including offline ones. */
    suspend fun known(): List<Device>

    /** Fast-tier stats. Completes when the device goes offline. */
    fun live(deviceId: String): Flow<Snapshot>

    suspend fun apps(deviceId: String): List<AppInfo>

    suspend fun network(deviceId: String): NetworkInfo

    /** Runs an action. Never uninstalls: there is no uninstall in [Action] on purpose. */
    suspend fun act(deviceId: String, action: Action, packageName: String): HistoryEntry
}
