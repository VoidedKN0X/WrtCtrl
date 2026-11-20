package com.vknxworks.wrtctrl.data.router

import com.vknxworks.wrtctrl.data.crypto.DataKeyStore
import com.vknxworks.wrtctrl.data.crypto.SealedDataException
import com.vknxworks.wrtctrl.data.crypto.SealedFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * Where the nicknames and notes were kept before encryption: one JSON text per router in the
 * `client_notes` preferences. Still used when there is no data key, exactly as before.
 */
interface LegacyNotes {
    fun ids(): Set<String>
    fun get(routerId: String): String?

    /** Saves and waits until it's on disk; false when that failed. */
    fun put(routerId: String, json: String): Boolean

    /** Removes and waits until that's on disk; false when that failed. */
    fun remove(routerId: String): Boolean

    /** Removes without waiting (a router was removed). */
    fun forget(routerId: String)
}

/**
 * The nicknames and notes of each router's devices as JSON text (`{"<mac>": {"nickname": "", "note": ""}}`),
 * encrypted at rest: one sealed file per router, `<dir>/<router id>.json` ([SealedFile], under the data key).
 * The user typed these in and they exist nowhere else, so nothing here may lose them:
 *
 * - notes still in the old preferences move into a sealed file only after that file was written in full,
 *   read back from disk, decrypted and compared; only then is the old copy removed. If the app stops in
 *   between, both copies exist and say the same;
 * - when both exist and differ (an older version of the app was installed again and saved notes), they are
 *   merged, and the old preferences win for a device both mention, being the newer ones;
 * - a sealed file that can't be opened (the key is gone, or the file is damaged) is never deleted or
 *   overwritten: [read] returns null and [write] refuses, until [setAside] renames it out of the way;
 * - without a usable data key and without a sealed file, notes are read from and saved to the old
 *   preferences as before.
 *
 * Everything here can ask the phone's secure hardware for the key: call it off the main thread.
 */
class NotesStore(private val dir: File, private val keys: DataKeyStore, private val legacy: LegacyNotes) {

    private fun file(routerId: String) = File(dir, "$routerId.json")
    private fun tmp(routerId: String) = File(dir, "$routerId.json.tmp")
    private fun aside(routerId: String) = File(dir, "$routerId.json.unreadable")

    /** Only ids that are safe as a file name get a file (router ids are UUIDs); anything else stays in the old preferences. */
    private fun usable(routerId: String) =
        routerId.isNotEmpty() && routerId.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }

    /** The notes of [routerId] as JSON text (`{}` when there are none), or null when they can't be opened. */
    @Synchronized
    fun read(routerId: String): String? {
        val old = legacy.get(routerId)
        if (!usable(routerId)) return old ?: EMPTY
        val file = file(routerId)
        if (!file.exists()) {
            if (old == null) return EMPTY
            // From before encryption: seal a copy, check it, and only then drop the old one.
            val key = keys.get(create = true)
            if (key != null && writeSealed(key, routerId, old)) legacy.remove(routerId)
            return old
        }
        val text = open(file) ?: return null
        if (old == null) return text
        // Both exist. The same notes twice: a move that stopped before its last step. Different notes: an
        // older version of the app saved some after this one had sealed the file.
        val fileNotes = parse(text)
        val oldNotes = parse(old)
        if (fileNotes == null || oldNotes == null) return text
        val merged = JsonObject(fileNotes + oldNotes)
        if (merged == fileNotes) {
            legacy.remove(routerId)
            return text
        }
        val mergedText = merged.toString()
        val key = keys.get(create = false)
        if (key != null && writeSealed(key, routerId, mergedText)) legacy.remove(routerId)
        return mergedText
    }

    /**
     * Saves [json] as the notes of [routerId]. False when nothing was saved: the notes there are can't be
     * opened (they are left alone), or writing failed (what was saved before is still there).
     */
    @Synchronized
    fun write(routerId: String, json: String): Boolean {
        if (!usable(routerId)) return legacy.put(routerId, json)
        val file = file(routerId)
        if (file.exists()) {
            // Only a file that can be opened may be replaced.
            if (open(file) == null) return false
            val key = keys.get(create = true) ?: return false
            if (!writeSealed(key, routerId, json)) return false
            // An old copy still around (its removal failed earlier) must not come back as "newer" notes.
            if (legacy.get(routerId) != null) legacy.remove(routerId)
            return true
        }
        val key = keys.get(create = true)
        if (key != null && writeSealed(key, routerId, json)) {
            legacy.remove(routerId)
            return true
        }
        return legacy.put(routerId, json)
    }

    /** Moves every router's notes that are still in the old preferences into sealed files. */
    @Synchronized
    fun migrateAll() {
        legacy.ids().forEach { id ->
            try {
                read(id)
            } catch (ignored: Exception) {
            }
        }
    }

    /** True when [routerId] has a sealed file that can't be opened right now. */
    @Synchronized
    fun unreadable(routerId: String): Boolean {
        if (!usable(routerId)) return false
        val file = file(routerId)
        return file.exists() && open(file) == null
    }

    /**
     * "Start empty": renames notes that can't be opened to `<router id>.json.unreadable` (kept, not deleted)
     * so new ones can be saved. Does nothing, and returns false, when the notes can be opened after all.
     */
    @Synchronized
    fun setAside(routerId: String): Boolean {
        if (!unreadable(routerId)) return false
        return try {
            val aside = aside(routerId)
            aside.delete()
            file(routerId).renameTo(aside)
        } catch (e: Exception) {
            false
        }
    }

    /** Asks the secure hardware for the key again at the next read ("Try again"). */
    fun retry() = keys.reset()

    /** A router was removed: everything kept about its devices goes. */
    fun forget(routerId: String) {
        legacy.forget(routerId)
        if (!usable(routerId)) return
        try {
            file(routerId).delete()
            tmp(routerId).delete()
            aside(routerId).delete()
        } catch (ignored: Exception) {
        }
    }

    /** The text in [file], decrypted when it's sealed; null when it can't be read or opened. */
    private fun open(file: File): String? {
        val bytes = try {
            file.readBytes()
        } catch (e: Exception) {
            return null
        }
        if (!SealedFile.isSealed(bytes)) return bytes.toString(Charsets.UTF_8)
        val key = keys.get(create = false) ?: return null
        return try {
            SealedFile.open(key, bytes).toString(Charsets.UTF_8)
        } catch (e: SealedDataException) {
            null
        }
    }

    private fun writeSealed(key: ByteArray, routerId: String, json: String): Boolean = try {
        dir.mkdirs()
        SealedFile.writeSealedChecked(key, file(routerId), tmp(routerId), json.toByteArray(Charsets.UTF_8))
    } catch (e: Exception) {
        false
    }

    private fun parse(text: String): JsonObject? = try {
        Json.parseToJsonElement(text) as? JsonObject
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val EMPTY = "{}"
    }
}
