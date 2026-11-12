package com.vknxworks.wrtctrl.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * Runs [refresh] right away and then every [intervalMs] while the screen is visible, and stops
 * completely when the app goes to the background. After a failed refresh ([refresh] returns false)
 * the interval doubles up to [maxIntervalMs], so an unreachable server isn't hammered.
 */
@Composable
fun PollWhileVisible(key: Any?, intervalMs: Long, maxIntervalMs: Long = 60_000L, refresh: suspend () -> Boolean) {
    val owner = LocalLifecycleOwner.current
    val current by rememberUpdatedState(refresh)
    LaunchedEffect(owner, key) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var wait = intervalMs
            while (true) {
                val ok = current()
                wait = if (ok) intervalMs else (wait * 2).coerceAtMost(maxIntervalMs)
                delay(wait)
            }
        }
    }
}
