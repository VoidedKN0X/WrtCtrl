package com.vknxworks.wrtctrl.data.lock

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App lock choices that must be known synchronously at start-up, before the settings DataStore is read:
 * whether the router logins stay usable in the background while the app is locked.
 */
class LockPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)

    private var _background by mutableStateOf(prefs.getBoolean(KEY_BACKGROUND, false))

    /**
     * On: notifications, widgets and the tile keep checking your routers while WrtCtrl is locked, so the
     * logins stay encrypted with the Keystore only. Off: the logins are also sealed with the app PIN and
     * background checks pause until you unlock.
     */
    var backgroundChecks: Boolean
        get() = _background
        set(v) {
            _background = v
            prefs.edit().putBoolean(KEY_BACKGROUND, v).commit()
        }

    private companion object {
        const val KEY_BACKGROUND = "background_checks"
    }
}
