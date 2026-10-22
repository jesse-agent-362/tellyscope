// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tellyscope.data.Action
import app.tellyscope.data.AppInfo
import app.tellyscope.data.ConnectResult
import app.tellyscope.data.Device
import app.tellyscope.data.DeviceSource
import app.tellyscope.data.DeviceState
import app.tellyscope.data.HistoryEntry
import app.tellyscope.data.NetworkInfo
import app.tellyscope.data.ScanEvent
import app.tellyscope.data.ScanHit
import app.tellyscope.data.ScanProgress
import app.tellyscope.data.Snapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Per-row connect state on the scan screen. */
sealed interface RowState {
    data object Found : RowState
    data class Waiting(val since: Long) : RowState
    data class Code(val input: String = "", val busy: Boolean = false, val error: String? = null) : RowState
    data object NotAllowed : RowState
}

data class ScanUi(
    val scanning: Boolean = false,
    val progress: ScanProgress? = null,
    /** Not on Wi-Fi or Ethernet, so there was no sweep. */
    val noLan: Boolean = false,
    val hits: List<ScanHit> = emptyList(),
    val rows: Map<String, RowState> = emptyMap(),
)

data class DeviceUi(
    val snapshot: Snapshot? = null,
    val apps: List<AppInfo> = emptyList(),
    val network: NetworkInfo? = null,
    val history: List<HistoryEntry> = emptyList(),
    /** Package whose action row is open, and the action awaiting confirmation. */
    val pending: Pair<String, Action>? = null,
    val busy: String? = null,
)

data class UiState(
    val scan: ScanUi = ScanUi(),
    val devices: List<Device> = emptyList(),
    val selectedId: String? = null,
    val showOffline: Boolean = false,
    val device: DeviceUi = DeviceUi(),
    val query: String = "",
    val now: Long = System.currentTimeMillis(),
) {
    val selected: Device? get() = devices.firstOrNull { it.id == selectedId }
}

class TellyscopeViewModel(
    private val source: DeviceSource,
    private val allowTimeoutMs: Long = 60_000,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState(now = clock()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** One-shot "done, undo?" messages for the snackbar. */
    private val _done = MutableStateFlow<HistoryEntry?>(null)
    val done: StateFlow<HistoryEntry?> = _done.asStateFlow()

    /** One-shot failures for the snackbar: the TV refused, or went away mid-action. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var scanJob: Job? = null
    private var liveJob: Job? = null
    private var mediumJob: Job? = null

    init {
        viewModelScope.launch {
            // Remembered TVs open straight to their page (offline until they answer).
            val known = source.known()
            _state.update { it.copy(devices = known) }
            if (_state.value.selectedId == null) known.firstOrNull()?.let { select(it.id) }
        }
        viewModelScope.launch {
            var tick = 0
            while (true) {
                delay(1_000)
                _state.update { it.copy(now = clock()) }
                if (++tick % 3 == 0) refreshDevices()
            }
        }
        rescan()
    }

    fun rescan() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            _state.update { it.copy(scan = it.scan.copy(scanning = true, noLan = false)) }
            source.scan().collect { e ->
                when (e) {
                    is ScanEvent.Progress -> _state.update { it.copy(scan = it.scan.copy(progress = e.progress)) }
                    is ScanEvent.Found -> _state.update { s ->
                        val known = s.devices.any { it.hit.ip == e.hit.ip }
                        if (known || s.scan.hits.any { it.ip == e.hit.ip }) s
                        else s.copy(scan = s.scan.copy(
                            hits = (s.scan.hits + e.hit).sortedWith(compareBy({ it.maker == null }, { ipKey(it.ip) })),
                            rows = s.scan.rows + (e.hit.ip to RowState.Found),
                        ))
                    }
                    ScanEvent.NoLan -> _state.update { it.copy(scan = it.scan.copy(noLan = true)) }
                    ScanEvent.Done -> Unit
                }
            }
            _state.update { it.copy(scan = it.scan.copy(scanning = false)) }
            // Empty scan: the guide is on screen; keep looking so the user never has to press anything.
            if (_state.value.scan.hits.isEmpty()) {
                delay(15_000)
                rescan()
            }
        }
    }

    fun connect(hit: ScanHit) {
        // Wireless-debugging TVs that already know our key connect straight away;
        // the rest answer NeedsPairing and get the code field.
        setRow(hit.ip, RowState.Waiting(clock()))
        viewModelScope.launch {
            when (val r = withTimeoutOrNull(allowTimeoutMs) { source.connect(hit) }) {
                is ConnectResult.Online -> online(r.device)
                ConnectResult.NeedsPairing -> setRow(hit.ip, RowState.Code())
                null, ConnectResult.NotAllowed, is ConnectResult.Unreachable, is ConnectResult.WrongCode ->
                    setRow(hit.ip, RowState.NotAllowed)
            }
        }
    }

    fun codeChanged(hit: ScanHit, input: String) {
        setRow(hit.ip, RowState.Code(input.filter(Char::isDigit).take(6)))
    }

    fun pair(hit: ScanHit) {
        val row = _state.value.scan.rows[hit.ip] as? RowState.Code ?: return
        setRow(hit.ip, row.copy(busy = true, error = null))
        viewModelScope.launch {
            when (val r = source.pair(hit, row.input)) {
                is ConnectResult.Online -> online(r.device)
                is ConnectResult.WrongCode -> setRow(hit.ip, row.copy(busy = false, error = r.message))
                is ConnectResult.Unreachable -> setRow(hit.ip, row.copy(busy = false, error = r.message))
                ConnectResult.NotAllowed -> setRow(hit.ip, RowState.NotAllowed)
                ConnectResult.NeedsPairing -> setRow(hit.ip, row.copy(busy = false, error = "The TV still wants a code. Check the number on screen and try again."))
            }
        }
    }

    /** "Add by IP": `ip` or `ip:port`. Returns an error line, or null when the connect started. */
    fun addByIp(text: String): String? {
        val m = Regex("""^(\d{1,3}(?:\.\d{1,3}){3})(?::(\d{1,5}))?$""").find(text.trim())
            ?: return "That doesn't look like an IP address. It should look like four numbers with dots, like the one in your TV's network settings."
        val port = m.groupValues[2].toIntOrNull() ?: 5555
        val kind = if (port == 5555) app.tellyscope.data.ScanKind.Classic else app.tellyscope.data.ScanKind.Wireless
        val hit = ScanHit(m.groupValues[1], port, kind, null, null)
        _state.update { s ->
            if (s.scan.hits.any { it.ip == hit.ip }) s
            else s.copy(scan = s.scan.copy(hits = listOf(hit) + s.scan.hits))
        }
        connect(hit)
        return null
    }

    private fun online(device: Device) {
        _state.update { s ->
            s.copy(
                devices = s.devices.filterNot { it.id == device.id } + device,
                scan = s.scan.copy(hits = s.scan.hits.filterNot { it.ip == device.hit.ip }),
            )
        }
        select(device.id)
    }

    fun select(id: String?) {
        liveJob?.cancel()
        mediumJob?.cancel()
        _state.update { it.copy(selectedId = id, device = DeviceUi(), query = "") }
        val device = _state.value.selected ?: return
        if (device.state != DeviceState.Online) return
        liveJob = viewModelScope.launch {
            source.live(id!!).collect { snap -> _state.update { it.copy(device = it.device.copy(snapshot = snap)) } }
        }
        mediumJob = viewModelScope.launch {
            // A TV that drops mid-read throws; the live flow ends and the offline card takes over.
            try {
                _state.update { it.copy(device = it.device.copy(apps = source.apps(id!!))) }
                while (true) {
                    val net = source.network(id!!)
                    _state.update { it.copy(device = it.device.copy(network = net)) }
                    delay(10_000)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Nothing to show: the stats stop and the offline card explains.
            }
        }
    }

    /** Remembered TVs reconnect on their own; pick up their state and restart stats when one comes back. */
    private suspend fun refreshDevices() {
        val known = source.known()
        val before = _state.value.selected?.state
        _state.update { it.copy(devices = known) }
        val now = _state.value.selected ?: return
        if (now.state == DeviceState.Online && before != DeviceState.Online) select(now.id)
    }

    /** Reconnect button: a TV stopped at "not allowed" asks again; an offline one restarts its stats. */
    fun reconnect(id: String) {
        viewModelScope.launch {
            source.reconnect(id)
            refreshDevices()
            select(id)
        }
    }

    fun forget(id: String) {
        viewModelScope.launch {
            source.forget(id)
            val known = source.known()
            _state.update { it.copy(devices = known) }
            // Next TV, online ones first; none left means back to the scan screen.
            select((known.firstOrNull { it.state == DeviceState.Online } ?: known.firstOrNull())?.id)
        }
    }

    fun showOffline(show: Boolean) = _state.update { it.copy(showOffline = show) }

    fun query(q: String) = _state.update { it.copy(query = q) }

    /** First tap on an action button: show what it will do. */
    fun propose(pkg: String, action: Action) =
        _state.update { it.copy(device = it.device.copy(pending = pkg to action)) }

    fun cancel() = _state.update { it.copy(device = it.device.copy(pending = null)) }

    fun confirm() {
        val (pkg, action) = _state.value.device.pending ?: return
        run(pkg, action)
    }

    fun undo(entry: HistoryEntry) {
        val undo = entry.undo ?: return
        run(entry.packageName, undo, undoing = entry.id)
    }

    private fun run(pkg: String, action: Action, undoing: Long? = null) {
        val id = _state.value.selectedId ?: return
        _state.update { it.copy(device = it.device.copy(pending = null, busy = pkg)) }
        viewModelScope.launch {
            // The source throws with the user-facing reason when the TV refuses.
            val (entry, apps) = try {
                source.act(id, action, pkg) to source.apps(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Anything else (a dropped socket) still must not crash the app.
                _state.update { it.copy(device = it.device.copy(busy = null)) }
                _error.value = e.message?.replaceFirstChar(Char::uppercase) ?: "That didn't work."
                return@launch
            }
            _state.update { s ->
                val history = s.device.history.map { if (it.id == undoing) it.copy(undoneAt = entry.at) else it }
                s.copy(device = s.device.copy(apps = apps, busy = null, history = listOf(entry) + history))
            }
            if (undoing == null) _done.value = entry
        }
    }

    fun doneShown() {
        _done.value = null
    }

    fun errorShown() {
        _error.value = null
    }

    private fun setRow(ip: String, row: RowState) =
        _state.update { it.copy(scan = it.scan.copy(rows = it.scan.rows + (ip to row))) }

    private fun ipKey(ip: String) = ip.split('.').fold(0L) { acc, o -> acc * 256 + (o.toLongOrNull() ?: 0) }
}
