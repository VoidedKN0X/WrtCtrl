package com.vknxworks.wrtctrl.data.notify

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.data.settings.appDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Things WrtCtrl notifies about, from its own background checks (woken right away by ntfy pushes). */
enum class NotifyEvent(@StringRes val label: Int, @StringRes val summary: Int) {
    UNREACHABLE(R.string.ev_unreachable, R.string.ev_unreachable_sub),
    WAN_DOWN(R.string.ev_wan_down, R.string.ev_wan_down_sub),
    WAN_UP(R.string.ev_wan_up, R.string.ev_wan_up_sub),
    NEW_DEVICE(R.string.ev_new_device, R.string.ev_new_device_sub),
    UPDATES(R.string.ev_updates, R.string.ev_updates_sub),
}

data class NotifySettings(
    /** Check the routers in the background with WorkManager. Off until turned on. */
    val polling: Boolean = false,
    val intervalMinutes: Int = 15,
    /** Run the check right away whenever ntfy delivers something to WrtCtrl's push address. */
    val push: Boolean = false,
    /** The events that are switched on (all by default). */
    val events: Set<NotifyEvent> = NotifyEvent.entries.toSet(),
) {
    fun on(e: NotifyEvent): Boolean = e in events

    companion object {
        val INTERVALS = listOf(15, 30, 60, 120)
    }
}

class NotifySettingsRepository(private val context: Context, scope: CoroutineScope) {
    private object K {
        val polling = booleanPreferencesKey("notify_polling")
        val interval = intPreferencesKey("notify_interval")
        val push = booleanPreferencesKey("notify_push")
        val events = stringSetPreferencesKey("notify_events")
    }

    val settings: StateFlow<NotifySettings> = context.appDataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, NotifySettings())

    /** The saved settings, read directly (for the background worker, before the flow has a value). */
    suspend fun current(): NotifySettings = context.appDataStore.data.first().toSettings()

    private fun Preferences.toSettings() = NotifySettings(
        polling = this[K.polling] ?: false,
        intervalMinutes = (this[K.interval] ?: 15).let { if (it in NotifySettings.INTERVALS) it else 15 },
        push = this[K.push] ?: false,
        events = this[K.events]?.mapNotNull { n -> NotifyEvent.entries.firstOrNull { it.name == n } }?.toSet()
            ?: NotifyEvent.entries.toSet(),
    )

    suspend fun update(transform: (NotifySettings) -> NotifySettings) {
        context.appDataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.polling] = s.polling
            p[K.interval] = s.intervalMinutes
            p[K.push] = s.push
            p[K.events] = s.events.map { it.name }.toSet()
        }
    }
}
