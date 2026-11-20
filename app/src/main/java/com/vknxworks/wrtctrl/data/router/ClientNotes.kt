package com.vknxworks.wrtctrl.data.router

import android.content.Context
import android.content.SharedPreferences
import com.vknxworks.wrtctrl.data.crypto.DataKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

// ---------- Nicknames and notes (on the phone only) ----------

@Serializable
data class ClientNote(val nickname: String = "", val note: String = "")

/**
 * Nicknames and notes for devices, per router. Stored on the phone only, never sent to the router, and
 * encrypted at rest: one sealed file per router in `filesDir/client_notes` (see [NotesStore] for the rules
 * that keep them from being lost). [all], [set], [migrate], [retry] and [startEmpty] can ask the phone's
 * secure hardware for the key, so they must be called off the main thread.
 */
object ClientNotes {
    private val json = Json { ignoreUnknownKeys = true }
    private val ser = MapSerializer(String.serializer(), ClientNote.serializer())

    @Volatile
    private var notesStore: NotesStore? = null

    private val _unreadable = MutableStateFlow<Set<String>>(emptySet())

    /** The routers whose saved nicknames and notes can't be opened right now; the Clients screens say so. */
    val unreadable: StateFlow<Set<String>> = _unreadable.asStateFlow()

    private fun store(context: Context): NotesStore = notesStore ?: synchronized(this) {
        notesStore ?: NotesStore(
            File(context.applicationContext.filesDir, "client_notes"),
            DataKeys.keys(context),
            PrefsNotes(context.applicationContext.getSharedPreferences("client_notes", Context.MODE_PRIVATE)),
        ).also { notesStore = it }
    }

    private fun mark(routerId: String, cantOpen: Boolean) {
        synchronized(_unreadable) {
            val now = _unreadable.value
            if (cantOpen != (routerId in now)) _unreadable.value = if (cantOpen) now + routerId else now - routerId
        }
    }

    /** Every nickname and note of [routerId]'s devices, by MAC address. Empty when they can't be opened (see [unreadable]). */
    fun all(context: Context, routerId: String): Map<String, ClientNote> {
        val text = store(context).read(routerId)
        mark(routerId, text == null)
        if (text == null) return emptyMap()
        return runCatching { json.decodeFromString(ser, text) }.getOrDefault(emptyMap())
    }

    /**
     * Saves the nickname and note of [mac]. False when nothing was saved: the saved notes can't be opened
     * (they are never overwritten), or writing failed.
     */
    fun set(context: Context, routerId: String, mac: String, note: ClientNote): Boolean {
        val store = store(context)
        // Reading and writing as one step, so two saves can't undo each other.
        synchronized(store) {
            val text = store.read(routerId)
            mark(routerId, text == null)
            if (text == null) return false
            val map = runCatching { json.decodeFromString(ser, text) }.getOrDefault(emptyMap()).toMutableMap()
            if (note.nickname.isBlank() && note.note.isBlank()) map.remove(mac) else map[mac] = note
            return store.write(routerId, json.encodeToString(ser, map))
        }
    }

    /** Moves notes saved by an older version (in the preferences) into sealed files. Safe to call at every start. */
    fun migrate(context: Context) = store(context).migrateAll()

    /** "Try again": asks the secure hardware for the key again and looks whether [routerId]'s notes open now. */
    fun retry(context: Context, routerId: String) {
        val store = store(context)
        store.retry()
        mark(routerId, store.unreadable(routerId))
    }

    /**
     * "Start empty": sets [routerId]'s notes that can't be opened aside (renamed, not deleted), so new ones
     * can be saved.
     */
    fun startEmpty(context: Context, routerId: String) {
        val store = store(context)
        store.setAside(routerId)
        mark(routerId, store.unreadable(routerId))
    }

    fun forget(context: Context, routerId: String) {
        store(context).forget(routerId)
        mark(routerId, false)
    }
}

/** The `client_notes` preferences, where the notes were kept before encryption. */
private class PrefsNotes(private val prefs: SharedPreferences) : LegacyNotes {
    override fun ids(): Set<String> = prefs.all.keys.toSet()

    override fun get(routerId: String): String? = try {
        prefs.getString(routerId, null)
    } catch (e: ClassCastException) {
        null
    }

    override fun put(routerId: String, json: String): Boolean = prefs.edit().putString(routerId, json).commit()

    override fun remove(routerId: String): Boolean = prefs.edit().remove(routerId).commit()

    override fun forget(routerId: String) {
        prefs.edit().remove(routerId).apply()
    }
}
