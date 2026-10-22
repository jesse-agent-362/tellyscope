// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import app.tellyscope.data.Action
import app.tellyscope.data.AppInfo
import app.tellyscope.data.DeviceList
import app.tellyscope.data.DeviceLists
import app.tellyscope.data.KEEP_REASON
import app.tellyscope.data.LISTS_BASE
import app.tellyscope.data.ListGroup
import app.tellyscope.data.ListPackage
import app.tellyscope.data.keepRefuses
import app.tellyscope.data.matches
import app.tellyscope.data.parseIndex
import app.tellyscope.data.suggest
import app.tellyscope.data.withKeep
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.security.MessageDigest

/** org.json is Android's, so this runs under Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeviceListsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val device = """
        {"schema": 1, "id": "acme-55", "name": "Acme 55", "tested": {"os": "Android TV 11", "sdk": 30, "build": null},
         "launcher": null, "keep": [{"pkg": "com.acme.hdmi"}],
         "groups": [{"title": "Junk", "packages": [
           {"pkg": "com.acme.reporter", "note": "usage reporter"},
           {"pkg": "com.acme.updater", "optional": true},
           {"pkg": "com.acme.gone"},
           {"pkg": "com.acme.bad;reboot"}]}],
         "source": {"repo": "Gathin23/cleanyourtv", "path": "examples/acme-55/debloat.txt", "commit": "abc"}}
    """.trimIndent().toByteArray()

    private fun index(sha: String = sha256(device), schema: Int = 1) = """
        {"schema": $schema, "source": {"repo": "Gathin23/cleanyourtv", "commit": "30626a8e4714"},
         "devices": [
           {"id": "acme-55", "name": "Acme 55", "match": {"manufacturer": ["Acme"], "model": ["A55", "A55X"]},
            "sdk": 30, "file": "devices/acme-55.json", "sha256": "$sha"},
           {"id": "other", "name": "Other TV", "match": null, "sdk": null, "file": "devices/other.json", "sha256": "${"0".repeat(64)}"}]}
    """.trimIndent().toByteArray()

    private fun app(pkg: String, enabled: Boolean = true, protectedReason: String? = null) =
        AppInfo(pkg, pkg.substringAfterLast('.'), null, true, enabled, protectedReason, "")

    private fun served(vararg files: Pair<String, ByteArray>): suspend (String) -> ByteArray {
        val map = files.associate { (path, bytes) -> LISTS_BASE + path to bytes }
        return { url -> map[url] ?: throw IOException("404 $url") }
    }

    @Test fun everyKeyInTheRuleMustMatchOneOfItsValues() {
        val rule = mapOf("manufacturer" to listOf("Acme"), "model" to listOf("A55", "A55X"))
        assertTrue(matches(rule, mapOf("manufacturer" to "ACME", "model" to "a55x", "device" to "whatever")))
        assertFalse(matches(rule, mapOf("manufacturer" to "Acme", "model" to "A65")))
        assertFalse(matches(rule, mapOf("manufacturer" to "Acme"))) // model unknown
        assertFalse(matches(rule + ("device" to listOf("acmeone")), mapOf("manufacturer" to "Acme", "model" to "A55", "device" to "acmetwo")))
        assertFalse(matches(null, mapOf("manufacturer" to "Acme")))
        assertFalse(matches(emptyMap(), mapOf("manufacturer" to "Acme")))
    }

    @Test fun onlySchemaOneIsAccepted() {
        assertNotNull(parseIndex(index().decodeToString()))
        assertNull(parseIndex(index(schema = 2).decodeToString()))
        assertNull(parseIndex("not json"))
    }

    @Test fun aDeviceFileWithTheWrongSha256IsRejectedAndNotCached() = runTest {
        val lists = DeviceLists(tmp.root, served("index.json" to index(sha = "f".repeat(64)), "devices/acme-55.json" to device))
        val entry = lists.index()!!.devices.first()
        assertNull(lists.list(entry))
        assertFalse(tmp.root.resolve("acme-55.json").exists())

        val good = DeviceLists(tmp.newFolder(), served("index.json" to index(), "devices/acme-55.json" to device))
        assertEquals("Acme 55", good.list(good.index()!!.devices.first())?.name)
    }

    @Test fun offlineFallsBackToTheCachedIndexAndLists() = runTest {
        DeviceLists(tmp.root, served("index.json" to index(), "devices/acme-55.json" to device)).let {
            it.list(it.index()!!.devices.first())
        }
        val offline = DeviceLists(tmp.root) { throw IOException("offline") }
        val index = offline.index()
        assertEquals("30626a8e4714", index?.commit)
        assertNotNull(offline.list(index!!.devices.first()))
        assertNull(DeviceLists(tmp.newFolder()) { throw IOException("offline") }.index())
    }

    @Test fun aPickIsRememberedPerTvAndBeatsAMatch() = runTest {
        val index = parseIndex(index().decodeToString())!!
        val props = mapOf("manufacturer" to "Acme", "model" to "A55")
        DeviceLists(tmp.root).pick("sn:one", "other")
        val lists = DeviceLists(tmp.root)
        assertEquals("other", lists.entryFor(index, "sn:one", props)?.id)
        assertEquals("acme-55", lists.entryFor(index, "sn:two", props)?.id)
        assertNull(lists.entryFor(index, "sn:two", mapOf("manufacturer" to "Zeta", "model" to "Z1")))
    }

    @Test fun onlyPackagesTheTvHasAndStillHasOnAreSuggested() = runTest {
        val lists = DeviceLists(tmp.root, served("index.json" to index(), "devices/acme-55.json" to device))
        val list = lists.list(lists.index()!!.devices.first())!!
        // The shell-metacharacter name never made it past parsing.
        assertEquals(listOf("com.acme.reporter", "com.acme.updater", "com.acme.gone"), list.groups.single().packages.map { it.pkg })

        val apps = listOf(app("com.acme.reporter"), app("com.acme.updater", enabled = false), app("com.netflix.ninja"))
        val shown = suggest(list, apps, sdk = 30).flatMap { it.packages }.map { it.pkg }
        assertEquals(listOf("com.acme.reporter"), shown)
    }

    @Test fun ticksOnlyNonOptionalEntriesAndOnlyOnTheTestedSdk() {
        val list = DeviceList(
            "acme-55", "Acme 55", "Android TV 11", 30, emptySet(),
            listOf(ListGroup("Junk", listOf(ListPackage("com.a.one", null, false), ListPackage("com.a.two", null, true)))),
        )
        val apps = listOf(app("com.a.one"), app("com.a.two"))
        fun ticks(sdk: Int?) = suggest(list, apps, sdk).flatMap { it.packages }.associate { it.pkg to it.ticked }
        assertEquals(mapOf("com.a.one" to true, "com.a.two" to false), ticks(30))
        assertEquals(mapOf("com.a.one" to false, "com.a.two" to false), ticks(31))
        assertEquals(mapOf("com.a.one" to false, "com.a.two" to false), ticks(null))
        assertEquals(mapOf("com.a.one" to false, "com.a.two" to false), suggest(list.copy(sdk = null), apps, null).flatMap { it.packages }.associate { it.pkg to it.ticked })
    }

    @Test fun keepOnlyAddsProtection() {
        val keep = setOf("com.acme.hdmi")
        assertEquals(KEEP_REASON, withKeep(null, "com.acme.hdmi", enabled = true, keep))
        // A built-in reason always survives, list or no list.
        assertEquals("this is your home screen", withKeep("this is your home screen", "com.acme.launcher", enabled = true, keep))
        assertEquals("this is your home screen", withKeep("this is your home screen", "com.acme.launcher", enabled = true, emptySet()))
        // Already off: Enable must still be offered.
        assertNull(withKeep(null, "com.acme.hdmi", enabled = false, keep))

        assertTrue(keepRefuses(Action.Disable, "com.acme.hdmi", keep))
        assertTrue(keepRefuses(Action.Stop, "com.acme.hdmi", keep))
        assertFalse(keepRefuses(Action.Enable, "com.acme.hdmi", keep))
        assertFalse(keepRefuses(Action.Disable, "com.acme.reporter", keep))

        // A list that names a package in both keep and a group never suggests it.
        val list = DeviceList("x", "X", null, 30, keep, listOf(ListGroup("Junk", listOf(ListPackage("com.acme.hdmi", null, false)))))
        assertTrue(suggest(list, listOf(app("com.acme.hdmi")), 30).isEmpty())
        // Nor one the app already protects.
        val builtIn = list.copy(keep = emptySet())
        assertTrue(suggest(builtIn, listOf(app("com.acme.hdmi", protectedReason = "your remote or HDMI inputs could stop working")), 30).isEmpty())
    }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}
