// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Where the per-TV debloat lists live: the tellyscope-devices repo, built from cleanyourtv. */
const val LISTS_BASE = "https://raw.githubusercontent.com/man15h/tellyscope-devices/main/"

/** One row of `index.json`. [match] is null for lists nobody has matched to a TV yet. */
data class ListEntry(
    val id: String,
    val name: String,
    val match: Map<String, List<String>>?,
    val sdk: Int?,
    val file: String,
    val sha256: String,
)

data class ListIndex(val commit: String?, val devices: List<ListEntry>)

data class ListPackage(val pkg: String, val note: String?, val optional: Boolean)

data class ListGroup(val title: String, val packages: List<ListPackage>)

data class DeviceList(
    val id: String,
    val name: String,
    val testedOs: String?,
    val sdk: Int?,
    /** Packages the list says must stay on. They can only add protection. */
    val keep: Set<String>,
    val groups: List<ListGroup>,
)

data class Suggested(val pkg: String, val label: String, val note: String?, val optional: Boolean, val ticked: Boolean)

data class SuggestedGroup(val title: String, val packages: List<Suggested>)

/** What the "Suggested for this TV" card shows. */
sealed interface Suggestions {
    /** No index (offline and nothing cached), or the chosen list failed its checksum. */
    data object Unavailable : Suggestions

    /** No list matches this TV and none was picked: ask which TV it is. */
    data class Pick(val commit: String?, val devices: List<ListEntry>) : Suggestions

    data class Ready(
        val commit: String?,
        val list: DeviceList,
        /** The TV's SDK; ticks only when it equals [DeviceList.sdk]. */
        val sdk: Int?,
        val groups: List<SuggestedGroup>,
        /** Every list, for "Not this TV?". */
        val devices: List<ListEntry>,
    ) : Suggestions {
        val sdkMatches get() = sdk != null && sdk == list.sdk
    }
}

/** Same rule tellyscope-devices checks; a name goes into a `pm` shell line. */
private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
private val FILE = Regex("devices/[A-Za-z0-9][A-Za-z0-9._-]*\\.json")
private val SHA256 = Regex("[0-9a-f]{64}")
private const val SCHEMA = 1

/** The props a match rule may name, read from `ro.product.*`. */
val MATCH_KEYS = listOf("manufacturer", "model", "device")

/** A rule matches when every key in it equals one of its values, ignoring case. An empty rule never does. */
fun matches(rule: Map<String, List<String>>?, props: Map<String, String?>): Boolean =
    !rule.isNullOrEmpty() && rule.all { (key, values) ->
        val actual = props[key] ?: return@all false
        values.any { it.equals(actual, ignoreCase = true) }
    }

/** Null unless it is a schema 1 index. Entries with a bad file name or checksum are dropped. */
fun parseIndex(text: String): ListIndex? = try {
    val o = JSONObject(text)
    if (o.optInt("schema") != SCHEMA) null else ListIndex(
        commit = o.optJSONObject("source")?.optStringOrNull("commit"),
        devices = o.getJSONArray("devices").objects().mapNotNull { d ->
            ListEntry(
                id = d.getString("id"),
                name = d.getString("name"),
                match = d.optJSONObject("match")?.let { m ->
                    m.keys().asSequence().associateWith { k -> m.getJSONArray(k).strings() }
                },
                sdk = d.optIntOrNull("sdk"),
                file = d.getString("file"),
                sha256 = d.getString("sha256"),
            ).takeIf { FILE.matches(it.file) && SHA256.matches(it.sha256) }
        },
    )
} catch (_: org.json.JSONException) {
    null
}

/** Null unless it is a schema 1 device file for [id]. Names that aren't package names are dropped. */
fun parseList(text: String, id: String): DeviceList? = try {
    val o = JSONObject(text)
    val tested = o.optJSONObject("tested")
    fun packages(a: JSONArray?) = a?.objects().orEmpty()
        .map { ListPackage(it.getString("pkg"), it.optStringOrNull("note"), it.optBoolean("optional")) }
        .filter { PACKAGE.matches(it.pkg) }
    if (o.optInt("schema") != SCHEMA || o.optString("id") != id) null else DeviceList(
        id = id,
        name = o.getString("name"),
        testedOs = tested?.optStringOrNull("os"),
        sdk = tested?.optIntOrNull("sdk"),
        keep = packages(o.optJSONArray("keep")).map { it.pkg }.toSet(),
        groups = o.getJSONArray("groups").objects().map { g ->
            ListGroup(g.optString("title"), packages(g.optJSONArray("packages")))
        }.filter { it.packages.isNotEmpty() },
    )
} catch (_: org.json.JSONException) {
    null
}

/**
 * The list cut down to this TV: only apps it actually has, still on, and not protected (built in
 * or by the list's keep). Pre-ticked only for non-optional entries on a TV with the SDK the list
 * was tested on.
 */
fun suggest(list: DeviceList, apps: List<AppInfo>, sdk: Int?): List<SuggestedGroup> {
    val installed = apps.associateBy { it.packageName }
    val tick = sdk != null && sdk == list.sdk
    return list.groups.map { g ->
        SuggestedGroup(g.title, g.packages.mapNotNull { p ->
            val app = installed[p.pkg]?.takeIf { it.enabled && it.protectedReason == null && p.pkg !in list.keep }
            app?.let { Suggested(p.pkg, it.label, p.note, p.optional, ticked = tick && !p.optional) }
        })
    }.filter { it.packages.isNotEmpty() }
}

const val KEEP_REASON = "this TV's list says it must stay on"

/**
 * The list's keep entries add a reason; they never clear a built-in one. A keep entry that is
 * already off stays unprotected, so Enable still shows.
 */
fun withKeep(builtIn: String?, pkg: String, enabled: Boolean, keep: Set<String>): String? =
    builtIn ?: KEEP_REASON.takeIf { enabled && pkg in keep }

/** Stop and Disable are refused for keep entries; Enable and Open always run. */
fun keepRefuses(action: Action, pkg: String, keep: Set<String>) =
    (action == Action.Stop || action == Action.Disable) && pkg in keep

/**
 * Fetches and caches the index and device files under [dir]. A failed fetch falls back to the
 * cache; a device file is only used (and cached) when its sha256 matches the index. Hand picks
 * are remembered per [historyKey].
 */
class DeviceLists(
    private val dir: File,
    private val fetch: suspend (String) -> ByteArray = ::download,
) {
    private val lock = Mutex()
    private var index: ListIndex? = null
    private val picks = File(dir, "picks.tsv")

    /**
     * Fetched once per process; later calls reuse it. Offline, the cached copy is returned and the
     * next call tries the network again. Null when offline with no cache.
     */
    suspend fun index(): ListIndex? = lock.withLock {
        index ?: withContext(Dispatchers.IO) {
            dir.mkdirs()
            val cache = File(dir, "index.json")
            val text = attempt { fetch(LISTS_BASE + "index.json") }?.decodeToString()
            text?.let(::parseIndex)?.also { cache.writeText(text); index = it }
                ?: cache.takeIf { it.exists() }?.readText()?.let(::parseIndex)
        }
    }

    /** The device file for [entry], from cache or the network. Null when the bytes don't match its sha256. */
    suspend fun list(entry: ListEntry): DeviceList? = withContext(Dispatchers.IO) {
        val cache = File(dir, entry.file.substringAfter('/'))
        val bytes = cache.takeIf { it.exists() }?.readBytes()?.takeIf { sha256(it) == entry.sha256 }
            ?: attempt { fetch(LISTS_BASE + entry.file) }?.takeIf { sha256(it) == entry.sha256 }
                ?.also { dir.mkdirs(); cache.writeBytes(it) }
        bytes?.let { parseList(it.decodeToString(), entry.id) }
    }

    /**
     * The list for a TV: the one picked by hand for [key], else the first whose rule matches
     * [props], else null for "Pick your TV".
     */
    fun entryFor(index: ListIndex, key: String, props: Map<String, String?>): ListEntry? =
        picked(key)?.let { id -> index.devices.firstOrNull { it.id == id } }
            ?: index.devices.firstOrNull { matches(it.match, props) }

    @Synchronized
    fun picked(key: String): String? = readPicks()[key]

    @Synchronized
    fun pick(key: String, id: String) {
        dir.mkdirs()
        val all = readPicks() + (key to id)
        val tmp = File(dir, "picks.tsv.tmp")
        tmp.writeText(all.entries.joinToString("") { (k, v) -> "$k\t$v\n" })
        tmp.renameTo(picks)
    }

    private fun readPicks(): Map<String, String> = if (!picks.exists()) emptyMap() else
        picks.readLines().mapNotNull { l -> l.split('\t').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

private const val MAX_BYTES = 2 shl 20 // 2 MiB; the index is ~60 KB

/** Plain GET with short timeouts, so a slow network falls back to the cache rather than hanging. */
private suspend fun download(url: String): ByteArray = withContext(Dispatchers.IO) {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 10_000
    c.readTimeout = 10_000
    try {
        check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
        c.inputStream.use { s ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val n = s.read(buf).takeIf { it >= 0 } ?: break
                out.write(buf, 0, n)
                check(out.size() <= MAX_BYTES) { "too large" }
            }
            out.toByteArray()
        }
    } finally {
        c.disconnect()
    }
}

private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
private fun JSONArray.strings() = (0 until length()).map { getString(it) }
private fun JSONObject.optStringOrNull(k: String) = if (isNull(k)) null else optString(k)
private fun JSONObject.optIntOrNull(k: String) = if (isNull(k)) null else optInt(k)
