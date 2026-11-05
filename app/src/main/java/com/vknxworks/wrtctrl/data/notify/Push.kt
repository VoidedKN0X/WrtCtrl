package com.vknxworks.wrtctrl.data.notify

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.container
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where WrtCtrl's registration with ntfy stands. */
sealed interface PushStatus {
    data object Off : PushStatus
    /** Push is on, but ntfy isn't installed (any more). */
    data object NoNtfy : PushStatus
    data object Registering : PushStatus
    data class Registered(val endpoint: String) : PushStatus
    /** ntfy's server is down for now; the address stays the same and ntfy says when it's back. */
    data class Unavailable(val endpoint: String) : PushStatus
    data class Failed(val reason: String) : PushStatus
}

/**
 * Push through ntfy only. A push is only a wake-up call (what the message says is ignored):
 * whatever posts to WrtCtrl's push address (a cron job on the router, for example), WrtCtrl runs its
 * background check right away and notifies about what it finds. What the message says is ignored.
 * The periodic background check keeps running as the fallback.
 */
object Push {
    private const val PREFS = "push"
    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_LAST_WAKE = "last_wake"

    /** A push starts a check at most this often, however often something posts to the address. */
    const val MIN_GAP_MS = 60_000L

    private val _status = MutableStateFlow<PushStatus>(PushStatus.Off)
    val status: StateFlow<PushStatus> = _status.asStateFlow()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context, enabled: Boolean) {
        val endpoint = prefs(context).getString(KEY_ENDPOINT, null)
        val unavailable = UnifiedPush.unavailable.value
        _status.value = when {
            !enabled -> PushStatus.Off
            !UnifiedPush.isNtfyInstalled(context) -> PushStatus.NoNtfy
            endpoint != null && unavailable -> PushStatus.Unavailable(endpoint)
            endpoint != null -> PushStatus.Registered(endpoint)
            _status.value is PushStatus.Failed -> _status.value
            else -> PushStatus.Registering
        }
    }

    /** At start-up: registers with ntfy again, which keeps the same address and recovers from a reinstalled ntfy. */
    fun start(context: Context, enabled: Boolean) {
        load(context, enabled)
        if (enabled && UnifiedPush.isNtfyInstalled(context)) UnifiedPush.register(context, context.getString(R.string.app_name))
    }

    /** Asks ntfy for an address. Returns false (status [PushStatus.NoNtfy]) when ntfy isn't installed. */
    fun register(context: Context): Boolean {
        if (!UnifiedPush.register(context, context.getString(R.string.app_name))) {
            _status.value = PushStatus.NoNtfy
            return false
        }
        if (_status.value !is PushStatus.Registered) _status.value = PushStatus.Registering
        return true
    }

    fun unregister(context: Context) {
        runCatching { UnifiedPush.unregister(context) }
        prefs(context).edit().remove(KEY_ENDPOINT).apply()
        _status.value = PushStatus.Off
    }

    internal fun onEndpoint(context: Context, url: String) {
        prefs(context).edit().putString(KEY_ENDPOINT, url).apply()
        UnifiedPush.onEndpoint(context)
        _status.value = PushStatus.Registered(url)
    }

    internal fun onFailed(context: Context, reason: String) {
        UnifiedPush.onLost(context, reason)
        _status.value = PushStatus.Failed(reason)
    }

    internal fun onUnregistered(context: Context) {
        UnifiedPush.onLost(context, null)
        prefs(context).edit().remove(KEY_ENDPOINT).apply()
        _status.value = PushStatus.Failed("UNREGISTERED")
    }

    internal fun onTempUnavailable(context: Context) {
        UnifiedPush.onTempUnavailable()
        prefs(context).getString(KEY_ENDPOINT, null)?.let { _status.value = PushStatus.Unavailable(it) }
    }

    internal fun onMessage() {
        UnifiedPush.onMessage()
        val st = _status.value
        if (st is PushStatus.Unavailable) _status.value = PushStatus.Registered(st.endpoint)
    }

    /**
     * Whether this push may start a check: at most one a minute, so a publisher that posts too often
     * (or someone who learned the address) can't keep the phone busy. The clock going backwards counts as a gap.
     */
    internal fun takeWake(context: Context): Boolean {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val last = p.getLong(KEY_LAST_WAKE, 0)
        if (last in 1..now && now - last < MIN_GAP_MS) return false
        p.edit().putLong(KEY_LAST_WAKE, now).apply()
        return true
    }
}

/**
 * Receives what ntfy hands over: the push address, registration errors and wake-up messages. Exported
 * because ntfy is another app, so every broadcast must carry the secret token of WrtCtrl's registration or
 * it's ignored: the token is what keeps other apps out. A broadcast from a sender that
 * identifies itself as an app other than ntfy is dropped too. A message's content is never shown or
 * used: it only starts the background check, which reads from your own routers.
 */
class PushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getStringExtra(UnifiedPush.EXTRA_TOKEN) ?: return
        if (token != UnifiedPush.token(context)) return
        // Only rejects senders that identify as another app; anonymous ones get through to the token check.
        val sender = sentFromPackage
        if (sender != null && sender != UnifiedPush.NTFY) return
        val app = context.applicationContext
        when (intent.action) {
            UnifiedPush.ACTION_NEW_ENDPOINT -> {
                val endpoint = intent.getStringExtra(UnifiedPush.EXTRA_ENDPOINT) ?: return
                UnifiedPush.ack(app, token, intent.getStringExtra(UnifiedPush.EXTRA_ID))
                Push.onEndpoint(app, endpoint)
            }
            UnifiedPush.ACTION_REGISTRATION_FAILED -> Push.onFailed(app, intent.getStringExtra(UnifiedPush.EXTRA_REASON) ?: "UNKNOWN")
            UnifiedPush.ACTION_UNREGISTERED -> Push.onUnregistered(app)
            UnifiedPush.ACTION_TEMP_UNAVAILABLE -> Push.onTempUnavailable(app)
            UnifiedPush.ACTION_MESSAGE -> {
                UnifiedPush.ack(app, token, intent.getStringExtra(UnifiedPush.EXTRA_ID))
                Push.onMessage()
                val c = app.container
                val pending = goAsync()
                c.appScope.launch {
                    try {
                        val settings = c.notifySettings.current()
                        // Push only wakes the background checks; with them off there's nothing to run.
                        if (!settings.polling || !settings.push) return@launch
                        if (!Push.takeWake(app)) return@launch
                        NotifyWorker.runFromPush(app)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}

/**
 * ntfy binds to this for a few seconds before it delivers a message. That lifts WrtCtrl to foreground
 * importance, so the check can start straight away even while the phone is in deep sleep.
 */
class RaiseToForegroundService : Service() {
    private val binder = Binder()
    override fun onBind(intent: Intent?): IBinder = binder
}
