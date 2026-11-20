package com.vknxworks.wrtctrl.ui.nav

import android.content.Intent
import com.vknxworks.wrtctrl.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App shortcuts (long-press the icon) open WrtCtrl with one of these actions;
 * [MainActivity] hands them here and the main screen goes to the right place. They only open WrtCtrl:
 * the app lock asks first, and nothing changes on the router without a confirmation on the phone.
 */
object Shortcuts {
    const val ACTION_CLIENTS = "com.vknxworks.wrtctrl.CLIENTS"
    const val ACTION_GUEST = "com.vknxworks.wrtctrl.GUEST_WIFI"
    const val ACTION_REBOOT = "com.vknxworks.wrtctrl.REBOOT"

    enum class Target { CLIENTS, GUEST, REBOOT }

    private val _pending = MutableStateFlow<Target?>(null)
    val pending: StateFlow<Target?> = _pending.asStateFlow()

    fun handle(intent: Intent?) {
        _pending.value = when (intent?.action) {
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
