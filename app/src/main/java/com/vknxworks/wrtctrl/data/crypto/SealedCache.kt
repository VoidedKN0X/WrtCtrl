package com.vknxworks.wrtctrl.data.crypto

import java.io.File

/**
 * Small files the app can fetch or work out again, kept sealed ([SealedFile]) under the data key
 * ([DataKeyStore]). Because they can be made again, the rules are simpler than for data that can't:
 *
 * - [read] takes sealed and ordinary files alike. A sealed file that can't be opened (the key is gone or the
 *   file is damaged) counts as "nothing saved": it is deleted and the app makes it again;
 * - [write] seals when there is a data key (one is made the first time) and writes an ordinary file, as before
 *   encryption, when there is none;
 * - [sealAll] turns the ordinary files left by an older version into sealed ones, each through
 *   [SealedFile.sealInPlace], so a file is always either the old one or the sealed one.
 *
 * Reading, writing and sealing take turns, so a file is never sealed while it's being replaced. Everything
 * here can ask the phone's secure hardware for the key: call it off the main thread.
 */
class SealedCache(private val keys: DataKeyStore) {

    /** What [file] holds, decrypted when it's sealed; null when there is nothing (readable) saved. */
    @Synchronized
    fun read(file: File): ByteArray? {
        val bytes = try {
            if (!file.isFile) return null
            file.readBytes()
        } catch (e: Exception) {
            return null
        }
        if (!SealedFile.isSealed(bytes)) return bytes
        val key = keys.get(create = false)
        val plain = if (key == null) {
            null
        } else {
            try {
                SealedFile.open(key, bytes)
            } catch (e: SealedDataException) {
                null
            }
        }
        if (plain == null) delete(file)
        return plain
    }

    /** Saves [plain] in [file], sealed when there is a data key. Throws when the file can't be written. */
    @Synchronized
    fun write(file: File, plain: ByteArray) {
        val key = keys.get(create = true)
        val sealed = if (key == null) null else SealedFile.sealChecked(key, plain)
        SealedFile.writeAtomic(file, File(file.path + TMP), sealed ?: plain)
    }

    /**
     * Seals every ordinary file in [dir] and removes temporary files a stopped write left behind. Does
     * nothing (and makes no key) when there is nothing to seal; a file that can't be sealed now stays an
     * ordinary file and is tried again at the next start.
     */
    @Synchronized
    fun sealAll(dir: File) {
        val files = try {
            dir.listFiles()?.filter { it.isFile }
        } catch (e: Exception) {
            null
        } ?: return
        files.filter { it.name.endsWith(TMP) }.forEach {
            try {
                it.delete()
            } catch (ignored: Exception) {
            }
        }
        val ordinary = files.filter { !it.name.endsWith(TMP) && !SealedFile.isSealed(it) }
        if (ordinary.isEmpty()) return
        val key = keys.get(create = true) ?: return
        ordinary.forEach { SealedFile.sealInPlace(key, it, File(it.path + SEAL_TMP)) }
    }

    /** Deletes [file] and its temporary files. Doesn't wait for a read or write that's going on. */
    fun delete(file: File) {
        try {
            file.delete()
            File(file.path + TMP).delete()
            File(file.path + SEAL_TMP).delete()
        } catch (ignored: Exception) {
        }
    }

    private companion object {
        const val TMP = ".tmp"
        const val SEAL_TMP = ".seal.tmp"
    }
}
