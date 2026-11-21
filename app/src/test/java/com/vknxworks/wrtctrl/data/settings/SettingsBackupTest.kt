package com.vknxworks.wrtctrl.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SettingsBackupTest {
    private val sample = SettingsBackup(
        appVersion = "1.0.0",
        createdAt = "2026-11-21T10:00:00+01:00",
        settings = BackupSettings(theme = "DARK", hideScreenshots = true, notifyEvents = listOf("A", "B"), notifyIntervalMinutes = 30),
        routers = listOf(
            BackupRouter("Home", "http://192.168.1.1", "root", readOnly = false),
            BackupRouter("Remote", "https://router.example.org", "admin", readOnly = true),
        ),
    )

    @Test
    fun aFileIsReadBackAsItWasWritten() {
        assertEquals(sample, SettingsBackups.parse(SettingsBackups.encode(sample)))
    }

    @Test
    fun theFileHasNoFieldForAnythingSecret() {
        val text = SettingsBackups.encode(sample).lowercase()
        for (word in listOf("password", "session", "token", "pin", "secret", "certpin", "allowhttp", "lock")) {
            assertFalse("the file mentions $word", text.contains("\"$word"))
            assertFalse("the file has a key containing $word", Regex("\"[a-z]*$word[a-z]*\"\\s*:").containsMatchIn(text))
        }
    }

    @Test
    fun secretsInAFileAreIgnored() {
        val text = """{"format":"wrtctrl-settings","version":1,"settings":{"lockChanges":false,"lockTimeout":"RESTART"},
            "routers":[{"name":"X","url":"https://x.example","username":"u","password":"hunter2","certPin":"AA:BB","allowHttp":true}]}"""
        val b = SettingsBackups.parse(text)
        assertEquals(listOf(BackupRouter("X", "https://x.example", "u", false)), b.routers)
        assertFalse(SettingsBackups.encode(b).contains("hunter2"))
    }

    @Test
    fun otherFilesAreRefused() {
        for (text in listOf("", "not json", "[]", """{"format":"other-settings","version":1}""", """{"format":"wrtctrl-settings","version":1,"routers":[{"url":"ftp://x"}]}""", """{"format":"wrtctrl-settings","version":1,"routers":[{"url":"https://a b"}]}""")) {
            try {
                SettingsBackups.parse(text)
                fail("accepted: $text")
            } catch (e: BackupFormatException) {
                assertFalse(e.newer)
            }
        }
        try {
            SettingsBackups.parse("""{"format":"wrtctrl-settings","version":99}""")
            fail("accepted a newer file")
        } catch (e: BackupFormatException) {
            assertTrue(e.newer)
        }
    }

    @Test
    fun thePlanOnlyAddsWhatIsNotHereYet() {
        val plan = SettingsBackups.plan(sample, existing = listOf("https://router.example.org/" to "admin"))
        assertEquals(listOf("Home"), plan.toAdd.map { it.name })
        assertEquals(listOf("Remote"), plan.alreadyHere.map { it.name })
        assertEquals(listOf("Home"), plan.plainHttp.map { it.name })
        // The same address with another username is another router; a router listed twice is added once.
        val twice = sample.copy(routers = sample.routers + sample.routers[0] + BackupRouter("Other login", "https://router.example.org", "guest"))
        assertEquals(listOf("Home", "Other login"), SettingsBackups.plan(twice, listOf("https://router.example.org" to "admin")).toAdd.map { it.name })
    }

    @Test
    fun hidingIsOnlyEverSwitchedOn() {
        assertTrue(SettingsBackups.keepHidden(current = true, fromFile = false))
        assertTrue(SettingsBackups.keepHidden(current = false, fromFile = true))
        assertTrue(SettingsBackups.keepHidden(current = true, fromFile = null))
        assertFalse(SettingsBackups.keepHidden(current = false, fromFile = null))
        assertFalse(SettingsBackups.keepHidden(current = false, fromFile = false))
    }
}
