// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.tellyscope.data.Action
import app.tellyscope.data.Device
import app.tellyscope.data.DeviceState
import app.tellyscope.data.FakeDeviceSource
import app.tellyscope.data.HistoryEntry
import app.tellyscope.data.ScanHit
import app.tellyscope.data.ScanKind
import app.tellyscope.data.ScanProgress
import app.tellyscope.ui.DeviceActions
import app.tellyscope.ui.DeviceScreen
import app.tellyscope.ui.DeviceUi
import app.tellyscope.ui.ForgetDialog
import app.tellyscope.ui.RestoreDialog
import app.tellyscope.ui.PermissionScreen
import app.tellyscope.ui.RowState
import app.tellyscope.ui.ScanScreen
import app.tellyscope.ui.ScanUi
import app.tellyscope.ui.UiState
import app.tellyscope.ui.theme.TellyscopeTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders each screen on fixed fake data. `./gradlew :app:recordRoborazziDebug`
 * writes PNGs to app/build/outputs/roborazzi; `verifyRoborazziDebug` diffs them.
 * Dynamic color is off so the branded fallback scheme is what gets reviewed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private val now = 1_760_000_000_000L
    private val fake = FakeDeviceSource(clock = { now }, random = Random(7))

    private val xgimi = ScanHit("192.168.1.23", 5555, ScanKind.Classic, "xgimi-projector", "XGIMI")
    private val box = ScanHit("192.168.1.41", 5555, ScanKind.Classic, "android-7f3a", "Amlogic")
    private val chromecast = ScanHit("192.168.1.57", 37211, ScanKind.Wireless, "chromecast", "Google")
    private val unknown = ScanHit("192.168.1.88", 5555, ScanKind.Classic, null, null)

    private fun shot(name: String, dark: Boolean = false, before: () -> Unit = {}, content: @Composable () -> Unit) {
        // Expressive indicators animate forever, so the clock never idles on its own:
        // drive it by hand and capture a fixed frame.
        compose.mainClock.autoAdvance = false
        compose.setContent { TellyscopeTheme(dark = dark, dynamic = false) { content() } }
        compose.mainClock.advanceTimeBy(1_000)
        before()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${name}${if (dark) "_dark" else ""}.png")
    }

    private fun scan(ui: ScanUi, dark: Boolean = false, name: String) = shot(name, dark) {
        ScanScreen(ui, now, {}, {}, { _, _ -> }, {}, { null })
    }

    @Test fun scan_inProgress() = scan(
        ScanUi(
            scanning = true,
            progress = ScanProgress(115, 254, "192.168.1.0/24"),
            hits = listOf(xgimi, box),
            rows = mapOf(xgimi.ip to RowState.Found, box.ip to RowState.Found),
        ),
        name = "01_scan_in_progress",
    )

    private val connectStates get() = ScanUi(
        scanning = false,
        progress = ScanProgress(254, 254, "192.168.1.0/24"),
        hits = listOf(xgimi, chromecast, unknown),
        rows = mapOf(
            xgimi.ip to RowState.Waiting(now - 12_000),
            chromecast.ip to RowState.Code("4821"),
            unknown.ip to RowState.NotAllowed,
        ),
    )

    @Test fun scan_connectStates() = scan(connectStates, name = "02_scan_connect_states")

    @Test fun scan_connectStates_dark() = scan(connectStates, dark = true, name = "02_scan_connect_states")

    @Test fun scan_empty() = scan(
        ScanUi(scanning = false, progress = ScanProgress(254, 254, "192.168.1.0/24")),
        name = "03_scan_empty_guide",
    )

    private fun deviceState(device: DeviceUi.() -> DeviceUi = { this }): UiState = runBlocking {
        val d = Device(xgimi.ip, xgimi, DeviceState.Online, "XGIMI Horizon Pro", "XGIMI", "11", now)
        UiState(
            devices = listOf(d) + fake.known(),
            selectedId = d.id,
            device = DeviceUi(
                snapshot = fake.snapshot(d.id),
                apps = fake.apps(d.id),
                network = fake.network(d.id),
            ).device(),
            now = now,
        )
    }

    @Test fun device_overview() = shot("04_device") { DeviceScreen(deviceState(), DeviceActions()) }

    @Test fun device_overview_dark() = shot("04_device", dark = true) { DeviceScreen(deviceState(), DeviceActions()) }

    @Config(qualifiers = "w411dp-h1500dp-xxhdpi")
    @Test fun device_confirmDisable() {
        val state = deviceState { copy(pending = "com.xgimi.assistant" to Action.Disable) }
        shot("05_device_confirm_disable") { DeviceScreen(state, DeviceActions()) }
    }

    @Test fun device_offline() {
        val base = deviceState()
        val off = base.devices.last()
        shot("06_device_offline") {
            DeviceScreen(base.copy(selectedId = off.id, device = DeviceUi(), showOffline = true), DeviceActions())
        }
    }

    @Config(qualifiers = "w411dp-h1500dp-xxhdpi")
    @Test fun device_history() {
        val history = listOf(
            HistoryEntry(2, xgimi.ip, now - 40_000, Action.Disable, "com.xgimi.ota", "XGIMI Updates", Action.Enable, null),
            HistoryEntry(1, xgimi.ip, now - 5 * 60_000, Action.Stop, "com.netflix.ninja", "Netflix", Action.Open, now - 4 * 60_000),
        )
        val openHistory = {
            compose.onNodeWithText("Apps").performClick() // fold the long list away
            compose.onNodeWithText("History").performScrollTo().performClick()
            Unit
        }
        shot("07_device_history", before = openHistory) { DeviceScreen(deviceState { copy(history = history) }, DeviceActions()) }
    }

    @Test fun permission() = shot("08_permission") { PermissionScreen(denied = false, canAsk = true, {}, {}) }

    @Test fun permission_denied() = shot("08_permission_denied") { PermissionScreen(denied = true, canAsk = true, {}, {}) }

    @Test fun forget() = shot("09_forget") { ForgetDialog("xgimi-projector", {}, {}) }

    @Test fun scan_offWifi() = scan(
        ScanUi(scanning = false, noLan = true, hits = listOf(chromecast), rows = mapOf(chromecast.ip to RowState.Found)),
        name = "10_scan_off_wifi",
    )

    @Test fun device_notAllowed() {
        val base = deviceState()
        val off = base.devices.last().copy(state = DeviceState.NotAllowed)
        shot("12_device_not_allowed") {
            DeviceScreen(base.copy(devices = base.devices.dropLast(1) + off, selectedId = off.id, device = DeviceUi()), DeviceActions())
        }
    }

    @Test fun restore() = shot("11_restore") { RestoreDialog(2, {}, {}) }
}
