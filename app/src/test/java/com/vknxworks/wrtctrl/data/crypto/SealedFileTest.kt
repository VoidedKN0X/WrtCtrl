package com.vknxworks.wrtctrl.data.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Encryption at rest: the sealed file format, the loss-proof move from an ordinary file, the data key's rules and the sealed cache. */
class SealedFileTest {
    private val key = ByteArray(32) { (it * 7 + 1).toByte() }
    private val json = "{\"vehicles\":[]}".toByteArray()

    private fun tempDir(): File = Files.createTempDirectory("wrtctrl-test").toFile()

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

    @Test
    fun sealedBytesOpenAgainAndAreRecognised() {
        val sealed = SealedFile.seal(key, json)
        assertTrue(SealedFile.isSealed(sealed))
        assertFalse(SealedFile.isSealed(json))
        assertFalse(SealedFile.isSealed(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertArrayEquals(json, SealedFile.open(key, sealed))
        assertArrayEquals(ByteArray(0), SealedFile.open(key, SealedFile.seal(key, ByteArray(0))))
    }

    @Test
    fun wrongKeyOrDamageIsRefused() {
        val sealed = SealedFile.seal(key, json)
        assertTrue(runCatching { SealedFile.open(ByteArray(32), sealed) }.exceptionOrNull() is SealedDataException)
        val damaged = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertTrue(runCatching { SealedFile.open(key, damaged) }.exceptionOrNull() is SealedDataException)
        assertTrue(runCatching { SealedFile.open(key, json) }.exceptionOrNull() is SealedDataException)
    }

    @Test
    fun sealInPlaceReplacesTheOrdinaryFileWithACheckedCopy() {
        val dir = tempDir()
        val file = File(dir, "data.json").apply { writeBytes(json) }
        val tmp = File(dir, "data.json.tmp")
        assertTrue(SealedFile.sealInPlace(key, file, tmp))
        assertTrue(SealedFile.isSealed(file))
        assertFalse(tmp.exists())
        assertArrayEquals(json, SealedFile.open(key, file.readBytes()))
        // Already sealed: nothing to do.
        val before = file.readBytes()
        assertTrue(SealedFile.sealInPlace(key, file, tmp))
        assertArrayEquals(before, file.readBytes())
    }

    @Test
    fun aFailedSealLeavesTheOrdinaryFileAsItWas() {
        val dir = tempDir()
        val file = File(dir, "photo.jpg").apply { writeBytes(json) }
        // The temporary file can't be written (it's a folder with something in it).
        val blocked = File(dir, "photo.jpg.seal.tmp").apply { mkdirs(); File(this, "x").writeText("x") }
        assertFalse(SealedFile.sealInPlace(key, file, blocked))
        assertArrayEquals(json, file.readBytes())
        // A key of the wrong size can't seal anything.
        val tmp = File(dir, "t")
        assertFalse(SealedFile.sealInPlace(ByteArray(5), file, tmp))
        assertArrayEquals(json, file.readBytes())
        assertFalse(tmp.exists())
        // A file that isn't there.
        assertFalse(SealedFile.sealInPlace(key, File(dir, "missing"), tmp))
        assertFalse(File(dir, "missing").exists())
    }

    @Test
    fun dataKeyIsMadeOnceAndReadBack() {
        val dir = tempDir()
        val wrapper = FakeWrapper()
        val store = DataKeyStore(dir, wrapper)
        assertNull(store.get(create = false))
        val made = store.get(create = true)
        assertNotNull(made)
        assertArrayEquals(made, DataKeyStore(dir, wrapper).get(create = false))
    }

    @Test
    fun aKeyFileThatCantBeUnwrappedIsNeverReplaced() {
        val dir = tempDir()
        val made = DataKeyStore(dir, FakeWrapper()).get(create = true)
        val onDisk = File(dir, DataKeyStore.FILE).readBytes()
        // The Keystore key is gone (a different one answers): no key, and the key file stays.
        assertNull(DataKeyStore(dir, FakeWrapper(secret = 9)).get(create = true))
        assertArrayEquals(onDisk, File(dir, DataKeyStore.FILE).readBytes())
        assertArrayEquals(made, DataKeyStore(dir, FakeWrapper()).get(create = false))
    }

    @Test
    fun withoutAWorkingKeystoreThereIsNoKeyAndNoKeyFile() {
        val dir = tempDir()
        val wrapper = FakeWrapper(broken = true)
        val store = DataKeyStore(dir, wrapper)
        assertNull(store.get(create = true))
        assertFalse(File(dir, DataKeyStore.FILE).exists())
        // Not asked again in the same run until reset.
        wrapper.broken = false
        assertNull(store.get(create = true))
        store.reset()
        assertNotNull(store.get(create = true))
    }

    @Test
    fun discardSetsTheOldKeyFileAsideAndANewKeyIsMade() {
        val dir = tempDir()
        val store = DataKeyStore(dir, FakeWrapper())
        val first = store.get(create = true)!!
        store.discard(keep = true)
        assertFalse(File(dir, DataKeyStore.FILE).exists())
        assertTrue(File(dir, DataKeyStore.FILE + ".unreadable").exists())
        assertFalse(first.contentEquals(store.get(create = true)!!))
    }

    @Test
    fun cacheSealsWhatItWritesAndReadsBothKinds() {
        val dir = tempDir()
        val cache = SealedCache(DataKeyStore(tempDir(), FakeWrapper()))
        val file = File(dir, "a.json")
        assertNull(cache.read(file))
        cache.write(file, json)
        assertTrue(SealedFile.isSealed(file))
        assertFalse(File(dir, "a.json.tmp").exists())
        assertArrayEquals(json, cache.read(file))
        // A file from before encryption is read as it is.
        val old = File(dir, "old.json").apply { writeBytes(json) }
        assertArrayEquals(json, cache.read(old))
        assertArrayEquals(json, old.readBytes())
    }

    @Test
    fun cacheWithoutAKeyWritesOrdinaryFilesAsBefore() {
        val keyDir = tempDir()
        val cache = SealedCache(DataKeyStore(keyDir, FakeWrapper(broken = true)))
        val file = File(tempDir(), "a.json")
        cache.write(file, json)
        assertArrayEquals(json, file.readBytes())
        assertArrayEquals(json, cache.read(file))
        assertFalse(File(keyDir, DataKeyStore.FILE).exists())
    }

    @Test
    fun aSealedCacheFileThatCantBeOpenedIsDroppedAndMadeAgain() {
        val keyDir = tempDir()
        val file = File(tempDir(), "a.json")
        SealedCache(DataKeyStore(keyDir, FakeWrapper())).write(file, json)
        val keyFile = File(keyDir, DataKeyStore.FILE).readBytes()
        // The Keystore key is gone: nothing saved, the file goes, the key file stays as it is.
        val lost = SealedCache(DataKeyStore(keyDir, FakeWrapper(secret = 9)))
        assertNull(lost.read(file))
        assertFalse(file.exists())
        assertArrayEquals(keyFile, File(keyDir, DataKeyStore.FILE).readBytes())
        // Written again as an ordinary file, because there is no usable key.
        lost.write(file, json)
        assertArrayEquals(json, file.readBytes())
        // A damaged sealed file is dropped too.
        val cache = SealedCache(DataKeyStore(keyDir, FakeWrapper()))
        cache.write(file, json)
        val sealed = file.readBytes()
        file.writeBytes(sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() })
        assertNull(cache.read(file))
        assertFalse(file.exists())
    }

    @Test
    fun sealAllSealsOrdinaryFilesOnly() {
        val keyDir = tempDir()
        val dir = tempDir()
        val cache = SealedCache(DataKeyStore(keyDir, FakeWrapper()))
        // Nothing to seal: no key is made, and a folder that isn't there is fine.
        cache.sealAll(dir)
        cache.sealAll(File(dir, "missing"))
        assertFalse(File(keyDir, DataKeyStore.FILE).exists())
        val a = File(dir, "a.json").apply { writeBytes(json) }
        val b = File(dir, "b.json")
        val stray = File(dir, "c.json.tmp").apply { writeBytes(json) }
        cache.sealAll(dir)
        assertTrue(SealedFile.isSealed(a))
        assertArrayEquals(json, cache.read(a))
        assertFalse(stray.exists())
        cache.write(b, json)
        val before = b.readBytes()
        cache.sealAll(dir)
        assertArrayEquals(before, b.readBytes())
        assertEquals(setOf("a.json", "b.json"), dir.list()!!.toSet())
    }

    @Test
    fun sealAllWithoutAKeyLeavesOrdinaryFilesAlone() {
        val dir = tempDir()
        val a = File(dir, "a.json").apply { writeBytes(json) }
        SealedCache(DataKeyStore(tempDir(), FakeWrapper(broken = true))).sealAll(dir)
        assertArrayEquals(json, a.readBytes())
        SealedCache(DataKeyStore(tempDir(), FakeWrapper())).delete(a)
        assertFalse(a.exists())
    }
}
