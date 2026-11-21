package com.vknxworks.wrtctrl.ui.components

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
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

/**
 * Compact uptime with the phone's own short units (ICU's narrow format): 45s, 12m, 5h 3m, 14d 2h in
 * English, 14j 2h in French and so on.
 */
fun formatUptime(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val d = s / 86_400
    val h = (s % 86_400) / 3_600
    val m = (s % 3_600) / 60
    return when {
        d > 0 -> narrowUnits(Measure(d, MeasureUnit.DAY), Measure(h, MeasureUnit.HOUR))
        h > 0 -> narrowUnits(Measure(h, MeasureUnit.HOUR), Measure(m, MeasureUnit.MINUTE))
        m > 0 -> narrowUnits(Measure(m, MeasureUnit.MINUTE))
        else -> narrowUnits(Measure(s, MeasureUnit.SECOND))
    }
}

/** Amounts with short units in the phone's language, like "5h 3m". */
fun narrowUnits(vararg measures: Measure): String =
    MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.NARROW).formatMeasures(*measures)

fun percent(fraction: Double): Int = (fraction * 100).roundToInt().coerceIn(0, 100)

/** "5 minutes ago", "at 14:02", using the phone's locale and clock format. */
fun relativeTime(epochMs: Long): String =
    DateUtils.getRelativeTimeSpanString(epochMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

/** Cores without a pointless ".0": 4, 0.5. */
fun formatCores(cores: Double): String =
    if (cores == cores.toLong().toDouble()) cores.toLong().toString() else String.format(Locale.getDefault(), "%.1f", cores)
