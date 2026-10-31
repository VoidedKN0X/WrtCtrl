package com.vknxworks.wrtctrl.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.container
import com.vknxworks.wrtctrl.data.api.UbusClient
import com.vknxworks.wrtctrl.data.router.ActiveRouter
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import com.vknxworks.wrtctrl.ui.theme.Gutter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

/** Data a screen loads from the active router: the last good value, the last error, and whether it's loading. */
@Stable
class RouterData<T> {
    var value by mutableStateOf<T?>(null)
        internal set
    var error by mutableStateOf<Throwable?>(null)
        internal set
    var loading by mutableStateOf(true)
        internal set
    var updatedAt by mutableStateOf(0L)
        internal set
    internal var reload: suspend () -> Unit = {}

    /** Loads again now (pull to refresh, after a change). */
    suspend fun refresh() = reload()
}

/** The active router, or null while WrtCtrl is locked or none is selected. */
@Composable
fun activeRouter(): ActiveRouter? {
    val c = LocalContext.current.container
    val active by c.sessions.active.collectAsStateWithLifecycle()
    return active
}

/**
 * Loads [load] from the active router: once, or every [intervalMs] while the screen is visible.
 * A failed load keeps the last good value and backs off, so an unreachable router isn't hammered.
 */
@Composable
fun <T> rememberRouterData(intervalMs: Long = 0, vararg keys: Any?, load: suspend (UbusClient) -> T): RouterData<T> {
    val active = activeRouter()
    val data = remember(active?.router?.id, *keys) { RouterData<T>() }
    val current by rememberUpdatedState(load)
    val client = active?.client
    data.reload = {
        if (client != null) {
            data.loading = true
            try {
                data.value = current(client)
                data.error = null
                data.updatedAt = System.currentTimeMillis()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                data.error = e
            } finally {
                data.loading = false
            }
        } else {
            data.loading = false
        }
    }
    if (intervalMs > 0) {
        PollWhileVisible(listOf(active?.router?.id, *keys), intervalMs) {
            data.reload()
            data.error == null
        }
    } else {
        LaunchedEffect(active?.router?.id, *keys) { data.reload() }
    }
    return data
}

/** A card saying why data couldn't be loaded, with Retry. */
@Composable
fun ErrorCard(error: Throwable, host: String, modifier: Modifier = Modifier, onRetry: (suspend () -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    CtrlCard(modifier.padding(horizontal = Gutter, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.CloudOff, null, tint = CtrlColors.Error)
            Column(Modifier.weight(1f)) {
                Text(errorMessage(context, error, host), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (onRetry != null) {
            TextButton(onClick = { scope.launch { onRetry() } }) { Text(stringResource(R.string.retry)) }
        }
    }
}

/** Bits per second as 950 kbit/s, 12.4 Mbit/s, 1.02 Gbit/s. */
fun formatRate(bitsPerSecond: Double): String {
    val units = listOf("bit/s", "kbit/s", "Mbit/s", "Gbit/s")
    var v = bitsPerSecond.coerceAtLeast(0.0)
    var i = 0
    while (v >= 1000 && i < units.lastIndex) {
        v /= 1000
        i++
    }
    return when {
        i == 0 -> "${v.toInt()} ${units[i]}"
        v >= 100 -> "${v.toInt()} ${units[i]}"
        v >= 10 -> String.format(Locale.getDefault(), "%.1f %s", v, units[i])
        else -> String.format(Locale.getDefault(), "%.2f %s", v, units[i])
    }
}

/** A Wi-Fi band from uci ("2g", "5g", "6g", or an old hwmode) as "2.4 GHz", "5 GHz", "6 GHz". */
fun bandLabel(band: String): String = when (band.lowercase()) {
    "2g", "11g", "11b", "11n" -> "2.4 GHz"
    "5g", "11a" -> "5 GHz"
    "6g" -> "6 GHz"
    "60g" -> "60 GHz"
    else -> band
}
