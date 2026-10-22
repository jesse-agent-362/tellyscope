// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

import java.io.File

/** What Tellyscope remembers about a TV between launches. */
data class SavedDevice(
    val host: String,
    val port: Int,
    val kind: ScanKind,
    val name: String?,
    val model: String?,
    val lastSeen: Long?,
)

/**
 * Remembered TVs, one tab-separated line each. Plain text so it needs no library and a damaged
 * line costs one device, not the list. Writes go to a temp file and are renamed into place.
 */
class DeviceStore(private val file: File) {

    @Synchronized
    fun load(): List<SavedDevice> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull(::parse)
    }

    @Synchronized
    fun save(devices: List<SavedDevice>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(devices.joinToString("") { format(it) + "\n" })
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun format(d: SavedDevice) = listOf(
        VERSION, d.host, d.port.toString(), d.kind.name, clean(d.name), clean(d.model), d.lastSeen?.toString().orEmpty(),
    ).joinToString("\t")

    private fun parse(line: String): SavedDevice? {
        val f = line.split('\t')
        if (f.size != 7 || f[0] != VERSION) return null
        return SavedDevice(
            host = f[1].ifEmpty { return null },
            port = f[2].toIntOrNull() ?: return null,
            kind = ScanKind.entries.firstOrNull { it.name == f[3] } ?: return null,
            name = f[4].ifEmpty { null },
            model = f[5].ifEmpty { null },
            lastSeen = f[6].toLongOrNull(),
        )
    }

    /** Names come from the network (mDNS, reverse DNS): keep them on one field. */
    private fun clean(s: String?) = s.orEmpty().replace(Regex("[\\t\\r\\n]"), " ")

    private companion object {
        const val VERSION = "v1"
    }
}
