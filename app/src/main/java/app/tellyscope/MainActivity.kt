// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.tellyscope.data.FakeDeviceSource
import app.tellyscope.ui.Copy
import app.tellyscope.ui.DeviceActions
import app.tellyscope.ui.DeviceScreen
import app.tellyscope.ui.ScanScreen
import app.tellyscope.ui.TellyscopeViewModel
import app.tellyscope.ui.theme.TellyscopeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Until the :adb module lands, the app runs on the fake source. Launch with
        // `--es scenario empty|noallow` to see the guide or the timeout.
        val scenario = when (intent.getStringExtra("scenario")) {
            "empty" -> FakeDeviceSource.Scenario.Empty
            "noallow" -> FakeDeviceSource.Scenario.NoAllow
            else -> FakeDeviceSource.Scenario.Normal
        }
        setContent {
            TellyscopeTheme {
                val vm: TellyscopeViewModel = viewModel(
                    factory = viewModelFactory { initializer { TellyscopeViewModel(FakeDeviceSource(scenario)) } },
                )
                TellyscopeApp(vm)
            }
        }
    }
}

@Composable
fun TellyscopeApp(vm: TellyscopeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var adding by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(done) {
        val entry = done ?: return@LaunchedEffect
        vm.doneShown()
        val result = snackbar.showSnackbar(
            message = Copy.done(entry.action, entry.label),
            actionLabel = entry.undo?.let(Copy::undo),
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) vm.undo(entry)
    }

    // A newly connected TV becomes the selection; leave the add flow then, not on tap.
    LaunchedEffect(state.selectedId) { adding = false }

    if (state.selected == null || adding) {
        BackHandler(enabled = adding) { adding = false }
        ScanScreen(
            scan = state.scan,
            now = state.now,
            onRescan = vm::rescan,
            onConnect = vm::connect,
            onCode = vm::codeChanged,
            onPair = vm::pair,
            onAddByIp = vm::addByIp,
            onBack = if (adding) ({ adding = false }) else null,
        )
    } else {
        DeviceScreen(
            state = state,
            snackbar = snackbar,
            actions = DeviceActions(
                select = vm::select,
                addDevice = { adding = true; vm.rescan() },
                showOffline = vm::showOffline,
                query = vm::query,
                propose = vm::propose,
                confirm = vm::confirm,
                cancel = vm::cancel,
                undo = vm::undo,
                reconnect = { state.selectedId?.let(vm::select) },
            ),
        )
    }
}
