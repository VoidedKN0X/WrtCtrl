package com.vknxworks.wrtctrl.data.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vknxworks.wrtctrl.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** App-wide preferences (not per router). Router settings and secrets live elsewhere, encrypted. */
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "wrtctrl")

enum class ThemeChoice(@StringRes val label: Int) {
    SYSTEM(R.string.theme_system),
    DARK(R.string.theme_dark),
    LIGHT(R.string.theme_light),
    BLACK(R.string.theme_black),
}

/** When the app lock asks again after you leave the app. */
enum class LockTimeout(@StringRes val label: Int, val millis: Long) {
    IMMEDIATELY(R.string.lock_again_immediately, 0L),
    MINUTE_1(R.string.lock_again_1_min, 60_000L),
    MINUTES_5(R.string.lock_again_5_min, 5 * 60_000L),
    MINUTES_15(R.string.lock_again_15_min, 15 * 60_000L),
    HOUR_1(R.string.lock_again_1_hour, 60 * 60_000L),
    RESTART(R.string.lock_again_restart, -1L),
}

data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val dynamicColor: Boolean = false,
    /** Overlay (storage for settings and installed packages) use, in percent, from which it's flagged. */
    val overlayWarnPercent: Int = 85,
    /** Router memory use (percent) from which it's flagged. */
    val memWarnPercent: Int = 85,
    /** "Hide in screenshots": FLAG_SECURE on every window (also blanks the recent-apps preview). */
    val hideScreenshots: Boolean = false,
    /** "Hide in recent apps": the recent-apps view shows a blank card. Screenshots still work. */
    val hideRecents: Boolean = false,
    /** The old app lock (phone's screen lock). Only read to move people to the app PIN. */
    val lockOnOpen: Boolean = false,
    /** App lock: ask for the fingerprint or PIN before anything that changes something on a router. */
    val lockChanges: Boolean = false,
    /** How long the app may be in the background before the app lock asks again. */
    val lockTimeout: LockTimeout = LockTimeout.IMMEDIATELY,
)

class SettingsRepository(private val context: Context, scope: CoroutineScope) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val overlayWarn = intPreferencesKey("overlay_warn_percent")
        val memWarn = intPreferencesKey("mem_warn_percent")
        val hideScreenshots = booleanPreferencesKey("hide_screenshots")
        val hideRecents = booleanPreferencesKey("hide_recents")
        val lockOnOpen = booleanPreferencesKey("lock_on_open")
        val lockChanges = booleanPreferencesKey("lock_changes")
        val lockTimeout = stringPreferencesKey("lock_timeout")
    }

    val settings: StateFlow<AppSettings> = context.appDataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    /** The stored settings as they're read and changed, without [settings]' placeholder defaults at start-up. */
    val stored: Flow<AppSettings> = context.appDataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        theme = enumOr(this[K.theme], ThemeChoice.SYSTEM),
        dynamicColor = this[K.dynamic] ?: false,
        overlayWarnPercent = (this[K.overlayWarn] ?: 85).coerceIn(50, 99),
        memWarnPercent = (this[K.memWarn] ?: 85).coerceIn(50, 99),
        hideScreenshots = this[K.hideScreenshots] ?: false,
        hideRecents = this[K.hideRecents] ?: false,
        lockOnOpen = this[K.lockOnOpen] ?: false,
        lockChanges = this[K.lockChanges] ?: false,
        lockTimeout = enumOr(this[K.lockTimeout], LockTimeout.IMMEDIATELY),
    )

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.appDataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.theme] = s.theme.name
            p[K.dynamic] = s.dynamicColor
            p[K.overlayWarn] = s.overlayWarnPercent
            p[K.memWarn] = s.memWarnPercent
            p[K.hideScreenshots] = s.hideScreenshots
            p[K.hideRecents] = s.hideRecents
            p[K.lockOnOpen] = s.lockOnOpen
            p[K.lockChanges] = s.lockChanges
            p[K.lockTimeout] = s.lockTimeout.name
        }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
