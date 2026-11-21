package com.vknxworks.wrtctrl.data.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * WrtCtrl's settings as a JSON file the user keeps: the app settings and the saved routers (name,
 * address, username, read-only), and nothing secret. Passwords, sessions, the app lock's PIN, pinned
 * certificate fingerprints, device nicknames and notes and the push address are never written, and none are read
 * from a file: after an import a new router's password is typed in again and its certificate
 * confirmed again. The app lock's own settings are left out too, so a file can't weaken or switch off
 * the lock.
 */
@Serializable
data class SettingsBackup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    /** WrtCtrl version that wrote the file, for information. */
    val appVersion: String? = null,
    /** ISO time the file was written. */
    val createdAt: String? = null,
    val settings: BackupSettings = BackupSettings(),
    val routers: List<BackupRouter> = emptyList(),
) {
    companion object {
        const val FORMAT = "wrtctrl-settings"
        const val VERSION = 1
    }
}

/** App settings; every field is optional so a file from an older or newer version still imports what it has. */
@Serializable
data class BackupSettings(
    val theme: String? = null,
    val dynamicColor: Boolean? = null,
    val hideScreenshots: Boolean? = null,
    val hideRecents: Boolean? = null,
    val overlayWarnPercent: Int? = null,
    val memWarnPercent: Int? = null,
    /** Which notifications are switched on (their names), and how often the background check runs. */
    val notifyEvents: List<String>? = null,
    val notifyIntervalMinutes: Int? = null,
)

/** A saved router without anything secret. */
@Serializable
data class BackupRouter(
    val name: String = "",
    val url: String = "",
    val username: String = "",
    val readOnly: Boolean = false,
)

/** What an import would do, worked out before anything changes, to show and confirm. */
data class ImportPlan(
    /** Routers in the file that aren't set up here yet (by address and username): they'd be added, without a password. */
    val toAdd: List<BackupRouter>,
    /** Routers in the file that are already here: left exactly as they are. */
    val alreadyHere: List<BackupRouter>,
    /** The ones of [toAdd] with a plain http:// address. */
    val plainHttp: List<BackupRouter>,
)

/** Thrown for a file that isn't a WrtCtrl settings file, or one from a newer, incompatible version. */
class BackupFormatException(val newer: Boolean) : Exception()

object SettingsBackups {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** A settings file is a few kilobytes; anything far bigger isn't one. */
    const val MAX_BYTES = 256 * 1024

    fun encode(backup: SettingsBackup): String = json.encodeToString(SettingsBackup.serializer(), backup)

    /** Reads and checks a settings file. */
    fun parse(text: String): SettingsBackup {
        val b = runCatching { json.decodeFromString(SettingsBackup.serializer(), text) }.getOrNull()
            ?: throw BackupFormatException(newer = false)
        if (b.format != SettingsBackup.FORMAT) throw BackupFormatException(newer = false)
        if (b.version > SettingsBackup.VERSION) throw BackupFormatException(newer = true)
        if (b.routers.size > 50 || b.routers.any { s -> !isAddress(s.url) || s.name.length > 200 || s.username.length > 200 }) {
            throw BackupFormatException(newer = false)
        }
        return b
    }

    private fun isAddress(url: String): Boolean =
        url.length <= 300 && (url.startsWith("http://") || url.startsWith("https://")) && url.none { it.isWhitespace() }

    private fun key(url: String, username: String) = url.trimEnd('/').lowercase() + "\n" + username

    /**
     * Sorts the file's routers into new ones and ones already set up here. [existing] holds the
     * address and username of every saved router.
     */
    fun plan(backup: SettingsBackup, existing: List<Pair<String, String>>): ImportPlan {
        val have = existing.map { (url, user) -> key(url, user) }.toSet()
        val seen = mutableSetOf<String>()
        val unique = backup.routers.filter { seen.add(key(it.url, it.username)) }
        val (here, new) = unique.partition { key(it.url, it.username) in have }
        return ImportPlan(toAdd = new, alreadyHere = here, plainHttp = new.filter { it.url.startsWith("http://") })
    }

    /** "Hide in screenshots" and "Hide in recent apps" can be switched on by a file, never off. */
    fun keepHidden(current: Boolean, fromFile: Boolean?): Boolean = current || fromFile == true
}
