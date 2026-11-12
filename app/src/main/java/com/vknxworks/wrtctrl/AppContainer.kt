package com.vknxworks.wrtctrl

import android.content.Context
import com.vknxworks.wrtctrl.data.lock.AppLock
import com.vknxworks.wrtctrl.data.lock.LockPrefs
import com.vknxworks.wrtctrl.data.lock.LockVault
import com.vknxworks.wrtctrl.data.router.CleartextPolicy
import com.vknxworks.wrtctrl.data.router.RouterStore
import com.vknxworks.wrtctrl.data.router.Sessions
import com.vknxworks.wrtctrl.data.router.Changes
import com.vknxworks.wrtctrl.data.router.ClientNotes
import com.vknxworks.wrtctrl.data.notify.CheckStore
import com.vknxworks.wrtctrl.data.notify.Notifier
import com.vknxworks.wrtctrl.data.notify.NotifySettingsRepository
import com.vknxworks.wrtctrl.data.widgets.WidgetData
import com.vknxworks.wrtctrl.data.widgets.WidgetStore
import com.vknxworks.wrtctrl.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency container; one per process. */
class AppContainer(val context: Context) {
    /**
     * Work that outlives a screen (saving settings, refreshes, widgets). A failure in one of these
     * (a router that went away, storage that can't be read right now) must never take the app down, also
     * not while it's closing: the next refresh tries again.
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })

    val settings = SettingsRepository(context, appScope)

    /**
     * Shared HTTP client. Each router gets a copy with its own certificate pin, so a pinned certificate
     * is only ever accepted for the router it belongs to. Redirects are not followed: ubus never
     * redirects, and following one could send the session to another host. Plain HTTP only goes to
     * router addresses the user accepted it for ([CleartextPolicy]), checked on every request.
     */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(CleartextPolicy.interceptor)
        .addNetworkInterceptor(CleartextPolicy.interceptor)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "WrtCtrl/${BuildConfig.VERSION_NAME} (Android)")
                    .build(),
            )
        }
        .build()

    /** The app lock's PIN, fingerprint key and the key that seals router logins while locked. */
    val vault = LockVault(context, "wrtctrl")
    val lockPrefs = LockPrefs(context)

    /** Router logins are sealed with the app PIN: the lock is on and background checks are off. */
    fun sealing(): Boolean = vault.enabled && !lockPrefs.backgroundChecks

    /** Background work (notifications, widgets, tile) can't read the logins right now. */
    fun backgroundBlocked(): Boolean = sealing() && !vault.isOpen

    val routerStore = RouterStore(context, vault, ::sealing)
    val sessions = Sessions(routerStore, http, appScope, initiallyLocked = sealing())

    /** Every configuration change goes through here: uci apply with rollback, then confirm. */
    val changes = Changes()

    val notifySettings = NotifySettingsRepository(context, appScope)

    /** Removes a router and everything WrtCtrl keeps about it on the phone. */
    fun removeRouter(id: String) {
        sessions.remove(id)
        CheckStore.forget(context, id)
        ClientNotes.forget(context, id)
        Notifier.cancelRouter(context, id)
        if (WidgetStore.read(context)?.routerId == id) WidgetStore.clear(context)
        appScope.launch { runCatching { WidgetData.redraw(context) } }
    }

    /** Turns the app lock on with [pin]. [backgroundChecks]: keep notifications and widgets working while locked. */
    suspend fun enableLock(pin: String, backgroundChecks: Boolean) {
        // Unlocked first, so the lock screen doesn't flash up while the PIN is being saved.
        AppLock.markUnlocked()
        withContext(Dispatchers.Default) {
            lockPrefs.backgroundChecks = backgroundChecks
            vault.setUp(pin)
            routerStore.resealAll(sealing())
        }
        settings.update { it.copy(lockOnOpen = false) }
    }

    /** Turns the app lock off (after the PIN was confirmed): logins go back to Keystore-only encryption. */
    suspend fun disableLock() {
        withContext(Dispatchers.Default) { routerStore.resealAll(false) }
        settings.update { it.copy(lockOnOpen = false, lockChanges = false) }
        vault.disable()
        lockPrefs.backgroundChecks = false
        sessions.setLocked(false)
    }

    /** Switches "Background checks while locked" (only while unlocked, so every login can be re-encrypted). */
    suspend fun setBackgroundChecks(on: Boolean) {
        withContext(Dispatchers.Default) {
            lockPrefs.backgroundChecks = on
            routerStore.resealAll(sealing())
        }
    }

    /** "Forgot PIN?": sealed logins can't be decrypted any more, so remove every router and start over. */
    suspend fun forgetEverything() {
        withContext(Dispatchers.Default) {
            routerStore.routers.value.forEach { CheckStore.forget(context, it.id); ClientNotes.forget(context, it.id) }
            routerStore.removeAll()
        }
        WidgetStore.clear(context)
        runCatching { Notifier.cancelAll(context) }
        runCatching { WidgetData.redraw(context) }
        AppLock.markUnlocked()
        settings.update { it.copy(lockOnOpen = false, lockChanges = false) }
        vault.disable()
        lockPrefs.backgroundChecks = false
        sessions.setLocked(false)
    }

    /** Locked: wipe the key from memory and drop the client that holds the login. */
    fun onLocked() {
        vault.close()
        if (sealing()) {
            sessions.setLocked(true)
            // The widget and the tile now only say that WrtCtrl is locked.
            appScope.launch { runCatching { WidgetData.redraw(context) } }
        }
    }

    fun onUnlocked() {
        AppLock.markUnlocked()
        sessions.setLocked(false)
        // Seal any login left unsealed (WrtCtrl stopped between setting up the lock and sealing them).
        if (vault.enabled) appScope.launch(Dispatchers.Default) { runCatching { routerStore.resealAll(sealing()) } }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as WrtCtrlApp).container
