package com.vknxworks.wrtctrl.ui.components

import android.text.format.DateUtils
import java.util.Locale
import kotlin.math.roundToInt

/** Binary units like the Proxmox web UI: 1.5 GiB, 512 MiB. */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KiB", "MiB", "GiB", "TiB", "PiB")
    var value = bytes / 1024.0
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024
        i++
    }
    return if (value >= 100) "${value.roundToInt()} ${units[i]}" else String.format(Locale.getDefault(), "%.1f %s", value, units[i])
}

/** Compact uptime: 45s, 12m, 5h 3m, 14d 2h. */
fun formatUptime(seconds: Long): String {
    if (seconds <= 0) return "0s"
    val d = seconds / 86_400
    val h = (seconds % 86_400) / 3_600
    val m = (seconds % 3_600) / 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "${seconds}s"
    }
}

fun percent(fraction: Double): Int = (fraction * 100).roundToInt().coerceIn(0, 100)

/** "5 minutes ago", "at 14:02", using the phone's locale and clock format. */
fun relativeTime(epochMs: Long): String =
    DateUtils.getRelativeTimeSpanString(epochMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

/** Cores without a pointless ".0": 4, 0.5. */
fun formatCores(cores: Double): String =
    if (cores == cores.toLong().toDouble()) cores.toLong().toString() else String.format(Locale.getDefault(), "%.1f", cores)
