package com.vknxworks.wrtctrl.data.lock

import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.vknxworks.wrtctrl.AppContainer
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.data.api.WriteGuard
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import javax.crypto.Cipher

/**
 * The app lock (the shared Ctrl series lock): an app PIN with optional fingerprint unlock. The secrets
 * live in [LockVault]; this object decides when the app is locked.
 *
 * - The app locks on a cold start and after the chosen time away ("Lock again"). Trips the app starts
 *   itself (file picker, browser, settings pages) don't count, see [expectReturn].
 * - "Confirm changes": every request that could change something on a router asks for the fingerprint
 *   or PIN first, through [WriteGuard]. A confirmation (or unlocking) covers the next minute.
 * - Locking wipes the vault key, so router logins sealed with it can't be read until the next unlock.
 */
object AppLock {
    enum class State { CHECKING, LOCKED, UNLOCKED }

    private const val WRITE_GRACE_MS = 60_000L

    private val _state = MutableStateFlow(State.CHECKING)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Goes up every time the app locks, so the lock screen offers the fingerprint once per lock. */
    var lockCount by mutableIntStateOf(0)
        private set

    /**
     * Goes up every time you come back to the app after really leaving it (not for the fingerprint prompt
     * itself), so the lock screen offers the fingerprint again on every return, not just once per lock.
     */
    var returns by mutableIntStateOf(0)
        private set

    /** A PIN screen shown on top of the app: turning the lock on or off, or changing the PIN. */
    var request by mutableStateOf<PinRequest?>(null)

    /** A change waiting for "Confirm changes"; the overlay completes it. */
    var confirm by mutableStateOf<CompletableDeferred<Boolean>?>(null)
        private set

    /** The old app lock's settings (phone's screen lock) are on. */
    private var oldLock by mutableStateOf(false)

    /** Old app lock still on from before the PIN: confirm once with it, then choose a PIN. */
    val legacy: Boolean get() = oldLock && container?.vault?.enabled == false

    @Volatile private var container: AppContainer? = null
    @Volatile private var lockTimeoutMs = 0L
    @Volatile private var lockChanges = false
    @Volatile private var settingsLoaded = false
    @Volatile private var foreground: WeakReference<FragmentActivity>? = null
    @Volatile private var startedActivities = 0
    /** The fingerprint (or old screen lock) prompt that is open right now, if any. */
    @Volatile private var openPrompt: BiometricPrompt? = null
    @Volatile private var openGeneration = 0
    @Volatile private var promptStartedAt = 0L
    private var promptGeneration = 0
    @Volatile private var backgroundedAt = 0L
    @Volatile private var expectReturnAt = 0L
    @Volatile private var lastConfirmAt = 0L
    private val promptLock = Mutex()

    /** Set by an open form: keeps what was typed before locking hides it. */
    var saveBeforeLock: (() -> Unit)? = null

    private fun configured(c: AppContainer) = c.vault.enabled || legacy

    private const val PROMPT_STALE_MS = 60_000L

    /**
     * A prompt is open. Some phones drop a prompt without ever answering (for example one asked for while
     * the app was still coming back on screen); such a prompt stops counting after a minute, and asking
     * again always replaces it, so it can never block fingerprint unlock until the app is closed.
     */
    private val authenticating: Boolean
        get() = openGeneration != 0 && SystemClock.elapsedRealtime() - promptStartedAt < PROMPT_STALE_MS

    /** Starts tracking a new prompt, replacing one that is still marked as open. Returns its generation. */
    private fun beginPrompt(): Int {
        openPrompt?.let { runCatching { it.cancelAuthentication() } }
        openPrompt = null
        val generation = ++promptGeneration
        openGeneration = generation
        promptStartedAt = SystemClock.elapsedRealtime()
        return generation
    }

    /** True once, and only for the newest prompt: a late answer from a replaced prompt is ignored. */
    private fun endPrompt(generation: Int): Boolean {
        if (generation != openGeneration) return false
        openGeneration = 0
        openPrompt = null
        return true
    }

    /**
     * Hooks the lock into [activity] (call from onCreate). The first call also starts following the
     * settings and installs the write check.
     */
    fun attach(activity: FragmentActivity, c: AppContainer) {
        if (container == null) {
            container = c
            WriteGuard.check = { checkWrite() }
            c.appScope.launch {
                c.settings.stored.collect { s ->
                    lockTimeoutMs = s.lockTimeout.millis
                    lockChanges = s.lockChanges
                    oldLock = s.lockOnOpen || s.lockChanges
                    if (!settingsLoaded) {
                        settingsLoaded = true
                        _state.value = if (configured(c)) State.LOCKED else State.UNLOCKED
                    } else if (!configured(c) && _state.value != State.UNLOCKED) {
                        _state.value = State.UNLOCKED
                    }
                }
            }
        }
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                startedActivities++
                foreground = WeakReference(activity)
                if (startedActivities == 1) onForeground()
            }

            override fun onStop(owner: LifecycleOwner) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (foreground?.get() === activity) foreground = null
                if (startedActivities == 0) onBackground()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                if (foreground?.get() === activity) foreground = null
            }
        })
    }

    /**
     * The app itself is opening another app (file picker, browser, settings). That trip doesn't count
     * as leaving, so you don't have to unlock again when you come back.
     */
    fun expectReturn() {
        expectReturnAt = SystemClock.elapsedRealtime()
    }

    private fun onBackground() {
        val c = container ?: return
        if (authenticating) return
        backgroundedAt = SystemClock.elapsedRealtime()
        // Only right after the app opened something itself; leaving later still counts.
        exemptTrip = expectReturnAt > 0 && backgroundedAt - expectReturnAt < 5_000
        expectReturnAt = 0L
        // "As soon as you leave" locks right away, so the key is gone while the app is in the background.
        if (configured(c) && _state.value == State.UNLOCKED && lockTimeoutMs == 0L && !exemptTrip) lock()
    }

    /** The current trip away was started by the app itself (file picker, browser, settings). */
    @Volatile private var exemptTrip = false

    private fun onForeground() {
        val c = container ?: return
        val since = backgroundedAt
        val exempt = exemptTrip
        backgroundedAt = 0L
        expectReturnAt = 0L
        exemptTrip = false
        if (since > 0) returns++
        if (!settingsLoaded || !configured(c) || _state.value != State.UNLOCKED || since <= 0 || lockTimeoutMs < 0) return
        // A trip the app started still locks if you stay away long: after 5 minutes, or the chosen time if longer.
        val limit = if (exempt) maxOf(lockTimeoutMs, 5 * 60_000L) else lockTimeoutMs
        if (SystemClock.elapsedRealtime() - since >= limit) lock()
    }

    /**
     * Locked, for callers outside the app's screens (the tile): also true once the "Lock again" time has run
     * out while WrtCtrl sat in the background, which [state] only notices when the app comes back on screen.
     */
    fun lockedNow(): Boolean {
        if (_state.value != State.UNLOCKED) return true
        val since = backgroundedAt
        if (since <= 0 || lockTimeoutMs < 0) return false
        val limit = if (exemptTrip) maxOf(lockTimeoutMs, 5 * 60_000L) else lockTimeoutMs
        return SystemClock.elapsedRealtime() - since >= limit
    }

    /**
     * Locks now when WrtCtrl has been away for longer than the "Lock again" time. Leaving the app only notes
     * the time and [onForeground] locks when it comes back on screen, so the widget, the tile and the
     * background checks, which work without WrtCtrl on screen, call this first. Main thread only.
     */
    fun lockIfExpired() {
        val c = container ?: return
        if (!settingsLoaded || !configured(c) || _state.value != State.UNLOCKED || startedActivities > 0) return
        if (lockedNow()) lock()
    }

    fun lock() {
        val c = container ?: return
        _state.value = State.LOCKED
        lockCount++
        request = null
        confirm?.complete(false)
        confirm = null
        saveBeforeLock?.let { runCatching(it) }
        c.onLocked()
    }

    fun markUnlocked() {
        backgroundedAt = 0L
        lastConfirmAt = SystemClock.elapsedRealtime()
        _state.value = State.UNLOCKED
    }

    /** Only class 3 ("strong") biometrics: camera-only face unlock can be fooled by a photo. */
    fun biometricAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** The phone's own screen lock or biometrics, only used once by people updating from the old lock. */
    fun legacyAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows the fingerprint prompt for [cipher], a Keystore key that only works after a strong match.
     * [onSuccess] gets the unlocked cipher; [onError] gets a message, or null when cancelled.
     */
    fun biometricPrompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        cipher: Cipher,
        onError: (String?) -> Unit = {},
        onSuccess: (Cipher) -> Unit,
    ) {
        // Leaving or closing the app: no prompt (the system would refuse it, or it would outlive the screen).
        if (activity.isFinishing || activity.isDestroyed) return
        // Replaces a prompt that is still marked as open; its late answer (if any) is ignored below.
        val generation = beginPrompt()
        val prompt = runCatching {
            BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (!endPrompt(generation)) return
                    val c = result.cryptoObject?.cipher
                    if (c != null) onSuccess(c) else onError(activity.getString(R.string.pin_fingerprint_failed))
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!endPrompt(generation)) return
                    val cancelled = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    onError(if (cancelled) null else errString.toString())
                }

                // A finger that didn't match: the prompt stays open for another try.
                override fun onAuthenticationFailed() = Unit
            },
            )
        }.getOrElse {
            if (endPrompt(generation)) onError(it.message)
            return
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(activity.getString(R.string.pin_use_pin))
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()
        openPrompt = prompt
        runCatching { prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher)) }.onFailure {
            if (endPrompt(generation)) onError(it.message)
        }
    }

    /** The old unlock with the phone's screen lock, to prove it's you before choosing a PIN. */
    fun legacyAuthenticate(activity: FragmentActivity, onResult: (String?) -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return
        val generation = beginPrompt()
        val prompt = runCatching {
            BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (!endPrompt(generation)) return
                    markUnlocked()
                    onResult(null)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!endPrompt(generation)) return
                    onResult(errString.toString())
                }

                override fun onAuthenticationFailed() = Unit
            },
            )
        }.getOrElse {
            if (endPrompt(generation)) onResult(it.message)
            return
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.lock_prompt_title))
            .setSubtitle(activity.getString(R.string.pin_legacy_prompt_subtitle))
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .setConfirmationRequired(false)
            .build()
        openPrompt = prompt
        runCatching { prompt.authenticate(info) }.onFailure {
            if (endPrompt(generation)) onResult(it.message)
        }
    }

    /** The [WriteGuard] check: asks for the fingerprint or PIN before a change when "Confirm changes" is on. */
    private suspend fun checkWrite(): Boolean {
        val c = container ?: return true
        if (!lockChanges || !c.vault.enabled) return true
        return promptLock.withLock {
            if (lastConfirmAt != 0L && SystemClock.elapsedRealtime() - lastConfirmAt < WRITE_GRACE_MS) return@withLock true
            val activity = foreground?.get() ?: return@withLock false
            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@withLock false
            val pending = CompletableDeferred<Boolean>()
            withContext(Dispatchers.Main) { confirm = pending }
            val ok = try {
                pending.await()
            } finally {
                // Also when the screen that asked is closed meanwhile: don't leave a dead question on screen.
                withContext(NonCancellable + Dispatchers.Main) { if (confirm === pending) confirm = null }
            }
            if (ok) lastConfirmAt = SystemClock.elapsedRealtime()
            ok
        }
    }
}

/** What a PIN screen on top of the app is for. */
enum class PinRequest { ENABLE, CHANGE, DISABLE }
