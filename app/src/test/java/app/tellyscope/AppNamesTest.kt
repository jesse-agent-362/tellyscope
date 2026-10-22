// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import app.tellyscope.adb.AppLabel
import app.tellyscope.adb.Parsers
import app.tellyscope.data.AppNames
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppNamesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val icon = byteArrayOf(1, 2, 3)
    private val netflix = Parsers.Package("com.netflix.ninja", null, 10)
    private val odd = Parsers.Package("com.example.weatherbox", null, 3)

    /** Answers like the helper would, from the TV's current versions, and records each run. */
    private class Helper(val fail: Boolean = false) {
        val runs = mutableListOf<List<String>>()
        var versions = mutableMapOf<String, Long>()
        suspend fun describe(packages: List<String>): List<AppLabel>? {
            runs += packages
            if (fail) return null
            return packages.map { AppLabel(it, "Real ${it.substringAfterLast('.')}", versions[it] ?: 0, byteArrayOf(1, 2, 3)) }
        }
    }

    @Test fun helperNamesAndIcons() = runBlocking {
        val helper = Helper()
        val names = AppNames(tmp.root).names("SN1", listOf(netflix, odd), helper::describe)
        assertEquals("Real ninja", names.getValue("com.netflix.ninja").label)
        assertArrayEquals(icon, names.getValue("com.netflix.ninja").icon)
    }

    @Test fun fallbackWhenTheHelperFails() = runBlocking {
        val helper = Helper(fail = true)
        val appNames = AppNames(tmp.root)
        val names = appNames.names("SN1", listOf(netflix, odd), helper::describe)
        assertEquals("Netflix", names.getValue("com.netflix.ninja").label) // bundled table
        assertEquals("Weatherbox", names.getValue("com.example.weatherbox").label) // humanised id
        assertNull(names.getValue("com.netflix.ninja").icon) // letter avatar
        // A refusal isn't retried on every open; the next app start tries again.
        appNames.names("SN1", listOf(netflix, odd), helper::describe)
        assertEquals(1, helper.runs.size)
    }

    @Test fun helperRunsAgainOnlyForNewOrUpdatedApps() = runBlocking {
        val helper = Helper().apply { versions = mutableMapOf(netflix.name to 10, odd.name to 3) }
        AppNames(tmp.root).names("SN1", listOf(netflix, odd), helper::describe)
        // A fresh instance reads the cache from disk, as after an app restart.
        val later = AppNames(tmp.root)
        later.names("SN1", listOf(netflix, odd), helper::describe)
        assertEquals(1, helper.runs.size)

        val youtube = Parsers.Package("com.google.android.youtube.tv", null, 5)
        helper.versions[netflix.name] = 11
        later.names("SN1", listOf(netflix.copy(versionCode = 11), odd, youtube), helper::describe)
        assertEquals(listOf("com.netflix.ninja", "com.google.android.youtube.tv"), helper.runs.last())
    }

    @Test fun cacheIsPerTv() = runBlocking {
        val helper = Helper()
        val appNames = AppNames(tmp.root)
        appNames.names("SN1", listOf(netflix), helper::describe)
        appNames.names("SN2", listOf(netflix), helper::describe)
        assertEquals(2, helper.runs.size)
    }

    @Test fun appsTheHelperSkipsArentAskedForAgain() = runBlocking {
        var runs = 0
        val appNames = AppNames(tmp.root)
        repeat(2) {
            appNames.names("SN1", listOf(netflix, odd), { runs++; listOf(AppLabel("com.netflix.ninja", "Netflix", 10, null)) })
        }
        assertEquals(1, runs)
    }

    @Test fun damagedCacheLinesCostOnlyThatApp() = runBlocking {
        File(tmp.root, "SN1.tsv").writeText("com.netflix.ninja\t10\tNetflix\t\ngarbage line\ncom.example.weatherbox\tx\tW\t\n")
        val helper = Helper()
        val names = AppNames(tmp.root).names("SN1", listOf(netflix, odd), helper::describe)
        assertEquals("Netflix", names.getValue("com.netflix.ninja").label)
        assertEquals(listOf("com.example.weatherbox"), helper.runs.single())
    }
}
