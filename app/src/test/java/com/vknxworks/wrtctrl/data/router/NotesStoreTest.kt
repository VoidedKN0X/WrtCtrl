package com.vknxworks.wrtctrl.data.router

import com.vknxworks.wrtctrl.data.crypto.DataKeyStore
import com.vknxworks.wrtctrl.data.crypto.KeyWrapper
import com.vknxworks.wrtctrl.data.crypto.SealedFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The device nicknames and notes, encrypted at rest: the move out of the old preferences, key loss, and merging. */
class NotesStoreTest {
    private val id = "0b6f6a7e-3c1d-4a55-9a61-5d1f2c3b4a59"
    private val notes = """{"AA:BB:CC:00:11:22":{"nickname":"TV","note":"living room"}}"""
    private val other = """{"AA:BB:CC:00:11:33":{"nickname":"Printer"}}"""

    private fun tempDir(): File = Files.createTempDirectory("wrtctrl-notes").toFile()

    /** Stands in for the Android Keystore: a wrap only its own [secret] can undo. */
    private class FakeWrapper(var secret: Byte = 7, var broken: Boolean = false) : KeyWrapper {
        override fun wrap(plain: ByteArray): ByteArray {
            if (broken) throw IllegalStateException("keystore unavailable")
            return byteArrayOf(secret) + plain.map { (it + secret).toByte() }
        }

        override fun unwrap(wrapped: ByteArray): ByteArray {
            if (broken) throw IllegalStateException("keystore unavailable")
            if (wrapped[0] != secret) throw IllegalStateException("wrong key")
            return wrapped.drop(1).map { (it - secret).toByte() }.toByteArray()
        }
    }

    /** Stands in for the `client_notes` preferences. */
    private class FakeLegacy(val map: MutableMap<String, String> = mutableMapOf(), var failRemove: Boolean = false) : LegacyNotes {
        override fun ids(): Set<String> = map.keys.toSet()
        override fun get(routerId: String): String? = map[routerId]
        override fun put(routerId: String, json: String): Boolean {
            map[routerId] = json
            return true
        }

        override fun remove(routerId: String): Boolean {
            if (failRemove) return false
            map.remove(routerId)
            return true
        }

        override fun forget(routerId: String) {
            map.remove(routerId)
        }
    }

    private class Setup(val keyDir: File, val dir: File, val legacy: FakeLegacy, val wrapper: FakeWrapper) {
        fun store() = NotesStore(dir, DataKeyStore(keyDir, wrapper), legacy)
    }

    private fun setup(legacy: FakeLegacy = FakeLegacy(), wrapper: FakeWrapper = FakeWrapper()) =
        Setup(tempDir(), File(tempDir(), "client_notes"), legacy, wrapper)

    @Test
    fun nothingSavedReadsAsEmptyAndMakesNoKeyOrFile() {
        val s = setup()
        assertEquals("{}", s.store().read(id))
        s.store().migrateAll()
        assertFalse(File(s.keyDir, DataKeyStore.FILE).exists())
        assertFalse(s.dir.exists())
    }

    @Test
    fun oldNotesMoveIntoASealedFileAndTheOldCopyGoesLast() {
        val s = setup(FakeLegacy(mutableMapOf(id to notes)))
        assertEquals(notes, s.store().read(id))
        val file = File(s.dir, "$id.json")
        assertTrue(SealedFile.isSealed(file))
        assertFalse(notes in String(file.readBytes(), Charsets.ISO_8859_1))
        assertNull(s.legacy.get(id))
        assertEquals(notes, s.store().read(id))
        assertEquals(setOf("$id.json"), s.dir.list()!!.toSet())
    }

    @Test
    fun migrateAllMovesEveryRouter() {
        val id2 = "second-router_2"
        val s = setup(FakeLegacy(mutableMapOf(id to notes, id2 to other)))
        s.store().migrateAll()
        assertTrue(s.legacy.map.isEmpty())
        assertEquals(notes, s.store().read(id))
        assertEquals(other, s.store().read(id2))
    }

    @Test
    fun aMoveThatStoppedBeforeItsLastStepIsFinishedLater() {
        val s = setup(FakeLegacy(mutableMapOf(id to notes), failRemove = true))
        assertEquals(notes, s.store().read(id))
        // Both copies exist and say the same.
        assertEquals(notes, s.legacy.get(id))
        assertTrue(SealedFile.isSealed(File(s.dir, "$id.json")))
        val sealed = File(s.dir, "$id.json").readBytes()
        s.legacy.failRemove = false
        assertEquals(notes, s.store().read(id))
        assertNull(s.legacy.get(id))
        assertArrayEquals(sealed, File(s.dir, "$id.json").readBytes())
    }

    @Test
    fun withoutAKeyTheOldPreferencesAreUsedAsBefore() {
        val s = setup(FakeLegacy(mutableMapOf(id to notes)), FakeWrapper(broken = true))
        val store = s.store()
        assertEquals(notes, store.read(id))
        assertTrue(store.write(id, other))
        assertEquals(other, s.legacy.get(id))
        assertEquals(other, store.read(id))
        assertFalse(File(s.dir, "$id.json").exists())
        assertFalse(store.unreadable(id))
        // The key works at a later start: the notes move then.
        s.wrapper.broken = false
        assertEquals(other, s.store().read(id))
        assertTrue(SealedFile.isSealed(File(s.dir, "$id.json")))
        assertNull(s.legacy.get(id))
    }

    @Test
    fun writesAreSealedAndReadBack() {
        val s = setup()
        val store = s.store()
        assertTrue(store.write(id, notes))
        assertTrue(SealedFile.isSealed(File(s.dir, "$id.json")))
        assertTrue(s.legacy.map.isEmpty())
        assertEquals(notes, s.store().read(id))
        assertTrue(store.write(id, other))
        assertEquals(other, s.store().read(id))
        assertEquals(setOf("$id.json"), s.dir.list()!!.toSet())
    }

    @Test
    fun notesThatCantBeOpenedAreNeverOverwrittenOrDeleted() {
        val s = setup()
        assertTrue(s.store().write(id, notes))
        val file = File(s.dir, "$id.json")
        val sealed = file.readBytes()
        val keyFile = File(s.keyDir, DataKeyStore.FILE).readBytes()
        // The Keystore key is gone.
        s.wrapper.secret = 9
        val lost = s.store()
        assertNull(lost.read(id))
        assertTrue(lost.unreadable(id))
        assertFalse(lost.write(id, other))
        lost.migrateAll()
        assertArrayEquals(sealed, file.readBytes())
        assertArrayEquals(keyFile, File(s.keyDir, DataKeyStore.FILE).readBytes())
        assertTrue(s.legacy.map.isEmpty())
        // The key is back: "Try again" opens them.
        s.wrapper.secret = 7
        assertNull(lost.read(id))
        lost.retry()
        assertEquals(notes, lost.read(id))
        assertFalse(lost.unreadable(id))
        assertFalse(lost.setAside(id))
        assertArrayEquals(sealed, file.readBytes())
    }

    @Test
    fun aDamagedFileIsKeptToo() {
        val s = setup()
        val store = s.store()
        assertTrue(store.write(id, notes))
        val file = File(s.dir, "$id.json")
        val damaged = file.readBytes().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        file.writeBytes(damaged)
        assertNull(store.read(id))
        assertFalse(store.write(id, other))
        assertArrayEquals(damaged, file.readBytes())
    }

    @Test
    fun startEmptySetsTheUnreadableNotesAsideAndNewOnesCanBeSaved() {
        val s = setup()
        assertTrue(s.store().write(id, notes))
        val sealed = File(s.dir, "$id.json").readBytes()
        s.wrapper.secret = 9
        val lost = s.store()
        assertTrue(lost.setAside(id))
        assertFalse(File(s.dir, "$id.json").exists())
        assertArrayEquals(sealed, File(s.dir, "$id.json.unreadable").readBytes())
        assertEquals("{}", lost.read(id))
        // The key file is never replaced, so there is no usable key: new notes go to the old preferences.
        assertTrue(lost.write(id, other))
        assertEquals(other, s.legacy.get(id))
        assertEquals(other, lost.read(id))
        assertArrayEquals(sealed, File(s.dir, "$id.json.unreadable").readBytes())
    }

    @Test
    fun notesSavedByAnOlderVersionAfterSealingAreMergedIn() {
        val s = setup()
        assertTrue(s.store().write(id, notes))
        // An older version was installed again and saved a note of its own, and changed the TV's.
        s.legacy.map[id] = """{"AA:BB:CC:00:11:33":{"nickname":"Printer"},"AA:BB:CC:00:11:22":{"nickname":"Television"}}"""
        val merged = s.store().read(id)!!
        assertTrue("Printer" in merged)
        assertTrue("Television" in merged)
        assertFalse("living room" in merged)
        assertNull(s.legacy.get(id))
        assertEquals(merged, s.store().read(id))
        assertTrue(SealedFile.isSealed(File(s.dir, "$id.json")))
    }

    @Test
    fun anIdThatIsNoFileNameStaysInTheOldPreferences() {
        val s = setup()
        val store = s.store()
        for (odd in listOf("", "../x", "a/b", "a.b")) {
            assertEquals("{}", store.read(odd))
            assertTrue(store.write(odd, notes))
            assertEquals(notes, s.legacy.get(odd))
            assertEquals(notes, store.read(odd))
            assertFalse(store.unreadable(odd))
            store.forget(odd)
            assertNull(s.legacy.get(odd))
        }
        assertFalse(s.dir.exists())
    }

    @Test
    fun forgetRemovesEverythingKeptForARouter() {
        val s = setup()
        assertTrue(s.store().write(id, notes))
        s.wrapper.secret = 9
        val lost = s.store()
        assertTrue(lost.setAside(id))
        assertTrue(lost.write(id, other))
        lost.forget(id)
        assertNull(s.legacy.get(id))
        assertTrue(s.dir.list()!!.isEmpty())
    }
}
