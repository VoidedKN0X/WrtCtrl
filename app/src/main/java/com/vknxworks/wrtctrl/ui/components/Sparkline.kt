package com.vknxworks.wrtctrl.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * A small trend line without axes: a title with the latest value(s) as text, and the line below.
 * Two series get the two chart colours and a coloured key in front of their values, so they're
 * never told apart by colour alone. [top] fixes the scale (e.g. 1.0 for fractions).
 */
@Composable
fun Sparkline(
    title: String,
    series: List<ChartSeries>,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    top: Double? = null,
) {
    val colors = ChartColors.series()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = (top ?: series.flatMap { it.values }.filterNotNull().maxOrNull() ?: 0.0).let { if (it <= 0) 1.0 else it }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            series.forEachIndexed { i, s ->
                if (series.size > 1) {
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(width = 10.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(colors[i % colors.size]))
                    Spacer(Modifier.width(4.dp))
                    Text(s.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                }
                Text(s.values.lastOrNull { it != null }?.let(format) ?: "—", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(4.dp))
        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
            series.forEachIndexed { si, s ->
                val n = s.values.size
                if (n < 2) return@forEachIndexed
                val path = Path()
                var drawing = false
                s.values.forEachIndexed { i, v ->
                    val x = size.width * i / (n - 1)
                    if (v == null) {
                        drawing = false
                    } else {
                        val y = (size.height - (v / max).coerceIn(0.0, 1.0) * size.height).toFloat()
                        if (drawing) path.lineTo(x, y) else path.moveTo(x, y)
                        drawing = true
                    }
                }
                drawPath(path, colors[si % colors.size], style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
