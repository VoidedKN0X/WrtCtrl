package com.vknxworks.wrtctrl.ui.components

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * True while the app is covered by the lock screen, or its activity is closing. Dialogs, bottom sheets
 * and menus are windows of their own, drawn above everything in the activity, so while this is true they
 * aren't shown: one left open would sit on top of the lock screen (and still take taps), and a new window
 * can't be added to an activity that is closing.
 */
val LocalAppCovered = compositionLocalOf { false }

/**
 * Screens that always get FLAG_SECURE, whatever "Hide in screenshots" says: server sign-in
 * (token secret, password, two-factor code) and consoles (a root shell and whatever it prints).
 * A count rather than a flag, so two such screens overlapping during a transition can't clear it early.
 */
object ScreenPrivacy {
    var forced by mutableIntStateOf(0)
        private set

    internal fun acquire() { forced++ }
    internal fun release() { forced = (forced - 1).coerceAtLeast(0) }

    /**
     * Screens that hide other apps' floating windows while they show (PIN entry and change, passwords,
     * keys, "can't be undone" confirmations). Counted, so nested screens can't clear it early.
     */
    var shielded by mutableIntStateOf(0)
        private set

    /** Views marked as sensitive for accessibility services, with how many screens asked for it. */
    private val sensitiveViews = java.util.WeakHashMap<android.view.View, Int>()

    internal fun shield() { shielded++ }
    internal fun unshield() { shielded = (shielded - 1).coerceAtLeast(0) }

    /**
     * Only accessibility services that are real accessibility tools (screen readers, switch access) can
     * read [view] and everything in it while it's marked. Android 14+.
     */
    internal fun markSensitive(view: android.view.View) {
        if (android.os.Build.VERSION.SDK_INT < 34) return
        val n = sensitiveViews[view] ?: 0
        sensitiveViews[view] = n + 1
        if (n == 0) runCatching { view.setAccessibilityDataSensitive(android.view.View.ACCESSIBILITY_DATA_SENSITIVE_YES) }
    }

    internal fun unmarkSensitive(view: android.view.View) {
        if (android.os.Build.VERSION.SDK_INT < 34) return
        val n = sensitiveViews[view] ?: return
        if (n > 1) {
            sensitiveViews[view] = n - 1
        } else {
            sensitiveViews.remove(view)
            runCatching { view.setAccessibilityDataSensitive(android.view.View.ACCESSIBILITY_DATA_SENSITIVE_AUTO) }
        }
    }
}

/** Keeps FLAG_SECURE on for as long as the calling screen is shown. */
@Composable
fun SecureScreen() {
    DisposableEffect(Unit) {
        ScreenPrivacy.acquire()
        onDispose { ScreenPrivacy.release() }
    }
}

/**
 * Applies "Hide in screenshots" ([secure]: FLAG_SECURE, which also blanks the recent-apps preview) and
 * "Hide in recent apps" ([hideRecents]: a blank card in Recents, screenshots still work) to [activity]'s
 * window, and again whenever they change, so a switch works at once in every open screen.
 */
@Composable
fun WindowPrivacy(activity: Activity, secure: Boolean, hideRecents: Boolean) {
    LaunchedEffect(secure) {
        if (secure) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    LaunchedEffect(hideRecents) { activity.setRecentsScreenshotEnabled(!hideRecents) }
}

/**
 * Copies [text] marked as sensitive, so Android hides it from the clipboard preview and keyboards
 * don't keep it in their clipboard history.
 */
fun copySensitive(context: Context, label: String, text: String) {
    val clip = ClipData.newPlainText(label, text).apply {
        description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}

/**
 * While the calling screen is shown: other apps' floating windows are hidden (so none can sit over a PIN
 * pad, a key field or a confirm button), and with [sensitive] the window it's in is hidden from
 * accessibility services that aren't real accessibility tools. Call it inside a dialog's content to
 * cover that dialog's window.
 */
@Composable
fun ShieldScreen(sensitive: Boolean = true) {
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view, sensitive) {
        ScreenPrivacy.shield()
        if (sensitive) ScreenPrivacy.markSensitive(view)
        // Inside a dialog: its own window hides floating windows too, not only the activity's behind it.
        val dialogWindow = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        dialogWindow?.let { runCatching { it.setHideOverlayWindows(true) } }
        onDispose {
            ScreenPrivacy.unshield()
            if (sensitive) ScreenPrivacy.unmarkSensitive(view)
            dialogWindow?.let { runCatching { it.setHideOverlayWindows(false) } }
        }
    }
}

/**
 * Hides other apps' floating windows (chat heads, overlays) over [activity] while [hide] is true; the rest
 * of the time they work as usual. Needs HIDE_OVERLAY_WINDOWS, which Android grants at install.
 */
@Composable
fun OverlayShield(activity: android.app.Activity, hide: Boolean) {
    DisposableEffect(hide) {
        runCatching { activity.window.setHideOverlayWindows(hide) }
        onDispose { }
    }
}
