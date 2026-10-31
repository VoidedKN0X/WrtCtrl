package com.vknxworks.wrtctrl.ui.widgets

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.vknxworks.wrtctrl.MainActivity
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.container
import com.vknxworks.wrtctrl.data.widgets.WidgetData
import com.vknxworks.wrtctrl.data.widgets.WidgetStore
import com.vknxworks.wrtctrl.ui.network.guestNetworks
import com.vknxworks.wrtctrl.ui.network.switchGuest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App shortcuts (long-press the icon), the widget and the tile open WrtCtrl with one of these actions;
 * [MainActivity] hands them here and the main screen goes to the right place. They only open WrtCtrl:
 * the app lock asks first, and nothing changes on the router without a confirmation on the phone.
 */
object Shortcuts {
    const val ACTION_OVERVIEW = "com.vknxworks.wrtctrl.OVERVIEW"
    const val ACTION_CLIENTS = "com.vknxworks.wrtctrl.CLIENTS"
    const val ACTION_GUEST = "com.vknxworks.wrtctrl.GUEST_WIFI"
    const val ACTION_REBOOT = "com.vknxworks.wrtctrl.REBOOT"

    enum class Target { OVERVIEW, CLIENTS, GUEST, REBOOT }

    private val _pending = MutableStateFlow<Target?>(null)
    val pending: StateFlow<Target?> = _pending.asStateFlow()

    fun handle(intent: Intent?) {
        _pending.value = when (intent?.action) {
            ACTION_OVERVIEW -> Target.OVERVIEW
            ACTION_CLIENTS -> Target.CLIENTS
            ACTION_GUEST -> Target.GUEST
            ACTION_REBOOT -> Target.REBOOT
            else -> return
        }
    }

    fun consumed() {
        _pending.value = null
    }
}

/**
 * Quick Settings tile: guest Wi-Fi on or off for the active router. The change is applied with rollback
 * and confirmed, like in the app. It opens WrtCtrl instead of switching when WrtCtrl is locked with the
 * login sealed, when "Confirm changes" is on, in read-only mode, or when the router has no guest network.
 */
class GuestWifiTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        draw()
        // Fresh state in the background; the tile redraws when it's in.
        val c = applicationContext.container
        if (!c.backgroundBlocked()) c.appScope.launch { WidgetData.refresh(applicationContext) }
    }

    private fun draw() {
        val tile = qsTile ?: return
        val c = applicationContext.container
        val snap = WidgetStore.read(applicationContext)?.takeIf { it.routerId == c.routerStore.activeId.value }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_notification)
        tile.label = getString(R.string.tile_guest_label)
        when {
            c.backgroundBlocked() -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = getString(R.string.tile_locked)
            }
            snap == null -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = getString(R.string.tile_unknown)
            }
            !snap.hasGuest -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.subtitle = getString(R.string.tile_no_guest)
            }
            else -> {
                tile.state = if (snap.guestOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.subtitle = getString(if (snap.guestOn) R.string.state_on else R.string.state_off)
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val c = applicationContext.container
        val router = c.sessions.active.value?.router
        val mustOpen = c.backgroundBlocked() || router == null || router.readOnly || c.settings.settings.value.lockChanges
        if (mustOpen) {
            openApp()
            return
        }
        val client = c.sessions.active.value?.client ?: return openApp()
        qsTile?.let {
            it.state = Tile.STATE_UNAVAILABLE
            it.subtitle = getString(R.string.tile_switching)
            it.updateTile()
        }
        c.appScope.launch {
            val guests = client.guestNetworks()
            if (guests.isNotEmpty()) {
                val on = guests.none { !it.disabled }
                switchGuest(applicationContext, client, guests, on)
            }
            WidgetData.refresh(applicationContext)
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(Shortcuts.ACTION_GUEST)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            runCatching { startActivityAndCollapse(pi) }
        } else {
            @Suppress("DEPRECATION")
            runCatching { startActivityAndCollapse(intent) }
        }
    }
}
