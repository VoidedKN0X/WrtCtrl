package com.vknxworks.wrtctrl.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Date

/**
 * Fixed chart colours, validated for colour-blind separation and contrast against the card surface
 * in each mode (they don't follow dynamic colour, so two series always stay distinguishable).
 * A single-series chart uses the first one.
 */
object ChartColors {
    // Signal: the accent (as text colour, so it reads on white in light mode) and the text colour,
    // so two series are always told apart.
    @Composable
    fun series(): List<Color> = listOf(com.vknxworks.wrtctrl.ui.theme.CtrlColors.AccentText, com.vknxworks.wrtctrl.ui.theme.CtrlColors.Text)
}

data class ChartSeries(val label: String, val values: List<Double?>)

/**
 * A line chart for RRD data: thin 2dp lines, a recessive grid, a legend with each series' latest
 * value, and a crosshair with the values at that time while you touch or drag across it.
 * Gaps (null values) break the line. [yMax] fixes the top (e.g. 100 % or total memory); otherwise
 * it's the highest value. [format] turns a value into text in the series' unit.
 */
@Composable
fun LineChart(
    times: List<Long>,
    series: List<ChartSeries>,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    yMax: Double? = null,
) {
    val colors = ChartColors.series()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val context = LocalContext.current
    var touchX by remember { mutableStateOf<Float?>(null) }
    var width by remember { mutableStateOf(1f) }

    val top = (yMax ?: series.flatMap { it.values }.filterNotNull().maxOrNull() ?: 0.0).let { if (it <= 0.0) 1.0 else it }
    val n = times.size
    val index: Int? = touchX?.let { x -> if (n < 2) null else ((x / width) * (n - 1)).toInt().coerceIn(0, n - 1) }
    val timeFormat = remember { DateFormat.getTimeFormat(context) }
    val dateFormat = remember { DateFormat.getMediumDateFormat(context) }
    fun label(t: Long): String {
        val d = Date(t * 1000)
        val span = if (n >= 2) times.last() - times.first() else 0
        return if (span > 86_400) "${dateFormat.format(d)} ${timeFormat.format(d)}" else timeFormat.format(d)
    }

    Column(modifier) {
        // Legend: always present for two series, and it carries the values as text (identity isn't colour alone).
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            series.forEachIndexed { i, s ->
                val v = if (index != null) s.values.getOrNull(index) else s.values.lastOrNull { it != null }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (series.size > 1) {
                        Box(Modifier.size(width = 12.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(colors[i % colors.size]))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(s.label, style = MaterialTheme.typography.labelMedium, color = muted)
                    Spacer(Modifier.width(6.dp))
                    Text(v?.let(format) ?: "—", style = MaterialTheme.typography.labelLarge, color = ink)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(n) {
                    detectTapGestures(onPress = { o -> touchX = o.x; tryAwaitRelease(); touchX = null })
                }
                .pointerInput(n) {
                    detectDragGestures(
                        onDragStart = { o -> touchX = o.x },
                        onDragEnd = { touchX = null },
                        onDragCancel = { touchX = null },
                        onDrag = { change, _ -> change.consume(); touchX = change.position.x.coerceIn(0f, size.width.toFloat()) },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                val h = size.height
                // Recessive grid: baseline and two dashed guides.
                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                listOf(0f, 0.5f, 1f).forEach { f ->
                    val y = h - f * h
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f, pathEffect = if (f == 0f) null else dash)
                }
                if (n < 2) return@Canvas
                fun xOf(i: Int) = size.width * i / (n - 1)
                fun yOf(v: Double) = (h - (v / top).coerceIn(0.0, 1.0) * h).toFloat()
                series.forEachIndexed { si, s ->
                    val color = colors[si % colors.size]
                    val line = Path()
                    var drawing = false
                    s.values.forEachIndexed { i, v ->
                        if (v == null) {
                            drawing = false
                        } else if (!drawing) {
                            line.moveTo(xOf(i), yOf(v)); drawing = true
                        } else {
                            line.lineTo(xOf(i), yOf(v))
                        }
                    }
                    if (series.size == 1) {
                        // A soft area under a single series; with two series the lines stay unfilled.
                        val area = Path().apply {
                            addPath(line)
                            val lastIdx = s.values.indexOfLast { it != null }
                            val firstIdx = s.values.indexOfFirst { it != null }
                            if (lastIdx >= 0 && firstIdx >= 0) {
                                lineTo(xOf(lastIdx), h); lineTo(xOf(firstIdx), h); close()
                            }
                        }
                        drawPath(area, color.copy(alpha = 0.14f))
                    }
                    drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                index?.let { i ->
                    val x = xOf(i)
                    drawLine(muted, Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx())
                    series.forEachIndexed { si, s ->
                        s.values.getOrNull(i)?.let { v ->
                            drawCircle(colors[si % colors.size], 4.dp.toPx(), Offset(x, yOf(v)))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            val leftLabel = if (index != null) label(times[index]) else times.firstOrNull()?.let(::label).orEmpty()
            Text(leftLabel, style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.weight(1f))
            if (index == null) {
                Text(times.lastOrNull()?.let(::label).orEmpty(), style = MaterialTheme.typography.labelSmall, color = muted)
            } else {
                Text(format(top), style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
    }
}
