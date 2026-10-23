// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import app.tellyscope.data.UpdateCheck
import app.tellyscope.data.compareVersions
import app.tellyscope.data.newest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/** org.json is Android's, so this runs under Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdateCheckTest {
    @get:Rule val tmp = TemporaryFolder()

    private val day = 24 * 60 * 60 * 1000L

    private fun release(tag: String, pre: Boolean = tag.contains("-alpha"), notes: String = "Notes for $tag") =
        """{"tag_name":"$tag","prerelease":$pre,"draft":false,"body":"$notes"}"""

    private fun releases(vararg r: String) = "[" + r.joinToString(",") + "]"

    @Test fun `alpha sorts before its release, which sorts before the next alpha`() {
        assertTrue(compareVersions("0.2.0-alpha.3", "0.2.0") < 0)
        assertTrue(compareVersions("0.2.0", "0.2.1-alpha.1") < 0)
        assertTrue(compareVersions("0.2.0-alpha.3", "0.2.1-alpha.1") < 0)
        assertTrue(compareVersions("0.2.0-alpha.2", "0.2.0-alpha.10") < 0)
        assertTrue(compareVersions("0.10.0", "0.9.9") > 0)
        assertEquals(0, compareVersions("0.2.0", "0.2.0"))
    }

    @Test fun `release builds see full releases only, alpha builds see alphas too`() {
        val list = releases(release("v0.2.1-alpha.1"), release("v0.2.0"), release("v0.2.0-alpha.3"), release("v0.1.0"))
        assertEquals("0.2.0", newest(list, alpha = false)?.version)
        assertEquals("0.2.1-alpha.1", newest(list, alpha = true)?.version)
    }

    @Test fun `odd tags, drafts and pre-releases without an alpha tag are ignored`() {
        val list = releases(
            release("v9.0.0-beta"),
            release("nightly"),
            release("v3.0.0", pre = true),
            """{"tag_name":"v4.0.0","prerelease":false,"draft":true,"body":""}""",
            release("v0.2.0"),
        )
        assertEquals("0.2.0", newest(list, alpha = true)?.version)
        assertNull(newest("not json", alpha = false))
    }

    @Test fun `offers a newer release with its notes and page`() = runTest {
        val u = UpdateCheck(tmp.newFile(), "0.2.0-alpha.1", alpha = true, fetch = { releases(release("v0.2.0")).toByteArray() }).check()
        assertEquals("0.2.0", u?.version)
        assertEquals("Notes for v0.2.0", u?.notes)
        assertTrue(u!!.url.endsWith("/tellyscope/releases/tag/v0.2.0"))
    }

    @Test fun `nothing when the running version is the newest`() = runTest {
        val check = UpdateCheck(tmp.newFile(), "0.2.0", alpha = false, fetch = { releases(release("v0.2.0"), release("v0.1.0")).toByteArray() })
        assertNull(check.check())
    }

    @Test fun `asks at most once a day`() = runTest {
        var now = 1_000_000L
        var calls = 0
        val check = UpdateCheck(tmp.newFile(), "0.1.0", alpha = false, clock = { now }) { calls++; releases(release("v0.2.0")).toByteArray() }
        assertEquals("0.2.0", check.check()?.version)
        now += day - 1
        assertNull(check.check())
        assertEquals(1, calls)
        now += 1
        assertEquals("0.2.0", check.check()?.version)
        assertEquals(2, calls)
    }

    @Test fun `a failed request is silent and tried again next time`() = runTest {
        var fail = true
        val check = UpdateCheck(tmp.newFile(), "0.1.0", alpha = false) {
            if (fail) throw IOException("offline")
            releases(release("v0.2.0")).toByteArray()
        }
        assertNull(check.check())
        fail = false
        assertEquals("0.2.0", check.check()?.version)
    }

    @Test fun `a skipped version stays skipped, a later one is offered`() = runTest {
        val file = tmp.newFile()
        var now = 0L
        var latest = "v0.2.0"
        val check = UpdateCheck(file, "0.1.0", alpha = false, clock = { now }) { releases(release(latest)).toByteArray() }
        check.skip("0.2.0")
        assertNull(check.check())
        now += day
        latest = "v0.2.1"
        assertEquals("0.2.1", UpdateCheck(file, "0.1.0", alpha = false, clock = { now }) { releases(release(latest)).toByteArray() }.check()?.version)
    }

    @Test fun `on by default, and off means no request`() = runTest {
        val file = tmp.newFile()
        var calls = 0
        val check = UpdateCheck(file, "0.1.0", alpha = false) { calls++; releases(release("v0.2.0")).toByteArray() }
        assertTrue(check.enabled)
        check.enabled = false
        assertFalse(UpdateCheck(file, "0.1.0", alpha = false).enabled)
        assertNull(check.check())
        assertEquals(0, calls)
    }
}
