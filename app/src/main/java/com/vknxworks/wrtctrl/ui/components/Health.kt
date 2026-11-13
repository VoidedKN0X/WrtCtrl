package com.vknxworks.wrtctrl.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import com.vknxworks.wrtctrl.ui.theme.Health

/**
 * Colour for a usage level: normal below [warnAt], warning from there, critical from 95 % (or 10
 * points above [warnAt] when that's lower). [normal] is used below the warning level.
 */
@Composable
@ReadOnlyComposable
fun usageColor(fraction: Double?, warnAt: Int, normal: Color): Color {
    if (fraction == null) return normal
    val pct = fraction * 100
    val critical = minOf(95, warnAt + 10)
    return when {
        pct >= critical -> Health.critical
        pct >= warnAt -> Health.warn
        else -> normal
    }
}

/** A Signal usage bar (6dp, fully rounded ends, dark track). Null [fraction] draws an empty track (unknown). */
@Composable
fun UsageBar(fraction: Double?, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val track = CtrlColors.ProgressTrack
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, Offset.Zero, size, r)
        val f = (fraction ?: 0.0).coerceIn(0.0, 1.0).toFloat()
        if (f > 0f) drawRoundRect(color, Offset.Zero, Size(size.width * f, size.height), r)
    }
}

/** A guest's or node's status as a small coloured square (accent when running, grey when stopped). */
@Composable
fun StatusDot(status: String, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val color = when (status) {
        "running", "online" -> Health.ok
        "paused", "suspended" -> Health.warn
        "stopped", "offline" -> Health.idle
        else -> Health.critical
    }
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.25f)).background(color))
}
