package com.vknxworks.wrtctrl.ui.clients

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.data.api.RouterAcl
import com.vknxworks.wrtctrl.data.router.Bandwidth
import com.vknxworks.wrtctrl.data.router.BandwidthSource
import com.vknxworks.wrtctrl.data.router.ConnEntry
import com.vknxworks.wrtctrl.data.router.NetClient
import com.vknxworks.wrtctrl.data.router.PeriodLength
import com.vknxworks.wrtctrl.data.router.PeriodUsage
import com.vknxworks.wrtctrl.data.router.conntrack
import com.vknxworks.wrtctrl.data.router.nlbwHistory
import com.vknxworks.wrtctrl.ui.components.ChartColors
import com.vknxworks.wrtctrl.ui.components.ChartSeries
import com.vknxworks.wrtctrl.ui.components.CtrlCard
import com.vknxworks.wrtctrl.ui.components.CtrlSectionLabel
import com.vknxworks.wrtctrl.ui.components.Sparkline
import com.vknxworks.wrtctrl.ui.components.formatBytes
import com.vknxworks.wrtctrl.ui.components.formatRate
import com.vknxworks.wrtctrl.ui.components.rememberRouterData
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import com.vknxworks.wrtctrl.ui.theme.CtrlType
import com.vknxworks.wrtctrl.ui.theme.Gutter
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A device's bandwidth: per accounting period from nlbwmon when it's installed (and the login may read it),
 * otherwise a live rate from the connection tracking table, otherwise what's needed for either.
 */
@Composable
fun ClientBandwidth(cl: NetClient, acl: RouterAcl, nlbwInstalled: Boolean) {
    when (Bandwidth.source(acl, nlbwInstalled)) {
        BandwidthSource.NLBWMON -> NlbwHistory(cl)
        BandwidthSource.CONNTRACK -> LiveRate(cl, nlbwInstalled)
        BandwidthSource.NONE -> NeedsSetup(nlbwInstalled)
    }
}

@Composable
private fun NlbwHistory(cl: NetClient) {
    val data = rememberRouterData(0, cl.mac) { client -> client.nlbwHistory(cl.mac) }
    val periods = data.value ?: return
    CtrlCard(Modifier.padding(horizontal = Gutter, vertical = 6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val length = Bandwidth.periodLength(periods.map { it.start })
            CtrlSectionLabel(
                stringResource(
                    when (length) {
                        PeriodLength.DAY -> R.string.bw_title_daily
                        PeriodLength.WEEK -> R.string.bw_title_weekly
                        PeriodLength.MONTH -> R.string.bw_title_monthly
                        PeriodLength.OTHER -> R.string.bw_title_periods
                    },
                ),
            )
            if (periods.isEmpty() || periods.all { it.total == 0L }) {
                Text(stringResource(R.string.bw_none_yet), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
            } else {
                UsageBars(periods)
                val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                val last = periods.last()
                Text(
                    stringResource(R.string.bw_current_period, last.start.format(fmt), formatBytes(last.rxBytes), formatBytes(last.txBytes)),
                    style = CtrlType.Mono,
                    color = CtrlColors.Muted,
                )
            }
            Text(stringResource(R.string.bw_nlbw_footer), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
        }
    }
}

/** One bar per period, download (accent) stacked under upload; the tallest period fills the height. */
@Composable
private fun UsageBars(periods: List<PeriodUsage>) {
    val colors = ChartColors.series()
    val down = colors[0]
    val up = colors[1]
    val max = periods.maxOf { it.total }.coerceAtLeast(1L).toFloat()
    val desc = stringResource(R.string.bw_chart_desc, periods.size, formatBytes(periods.maxOf { it.total }))
    Column {
        Canvas(Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = desc }) {
            val n = periods.size
            val slot = size.width / n
            val barW = (slot * 0.6f).coerceAtMost(28.dp.toPx())
            periods.forEachIndexed { i, p ->
                val x = i * slot + (slot - barW) / 2
                val hDown = size.height * (p.rxBytes / max)
                val hUp = size.height * (p.txBytes / max)
                val r = CornerRadius(3.dp.toPx())
                if (hDown > 0) drawRoundRect(down, Offset(x, size.height - hDown), Size(barW, hDown), r)
                if (hUp > 0) drawRoundRect(up, Offset(x, size.height - hDown - hUp), Size(barW, hUp), r)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val f = DateTimeFormatter.ofPattern("d MMM")
            Text(periods.first().start.format(f), style = MaterialTheme.typography.labelSmall, color = CtrlColors.Muted)
            if (periods.size > 1) Text(periods.last().start.format(f), style = MaterialTheme.typography.labelSmall, color = CtrlColors.Muted)
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.bw_legend_down), style = MaterialTheme.typography.labelSmall, color = down)
            Text(stringResource(R.string.bw_legend_up), style = MaterialTheme.typography.labelSmall, color = up)
        }
    }
}

private class Sample(val atMs: Long, val entries: List<ConnEntry>)

@Composable
private fun LiveRate(cl: NetClient, nlbwInstalled: Boolean) {
    val rates = remember(cl.mac) { mutableStateListOf<Double>() }
    val last = remember(cl.mac) { arrayOfNulls<Sample>(1) }
    val ips = cl.ipv4 + cl.ipv6
    val data = rememberRouterData(3_000, cl.mac) { client ->
        val now = Sample(System.currentTimeMillis(), client.conntrack())
        val prev = last[0]
        last[0] = now
        if (prev != null) {
            rates += Bandwidth.rate(prev.entries, now.entries, ips, (now.atMs - prev.atMs) / 1000.0) * 8
            if (rates.size > 60) rates.removeAt(0)
        }
        Bandwidth.countsBytes(now.entries)
    }
    CtrlCard(Modifier.padding(horizontal = Gutter, vertical = 6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rates.size >= 2) {
                Sparkline(stringResource(R.string.bw_live), listOf(ChartSeries(stringResource(R.string.bw_live), rates.toList())), format = ::formatRate)
            } else {
                CtrlSectionLabel(stringResource(R.string.bw_live))
                Text(stringResource(R.string.bw_live_waiting), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
            }
            if (data.value == false) {
                Text(stringResource(R.string.bw_no_acct), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Warn)
            }
            Text(
                stringResource(if (nlbwInstalled) R.string.bw_live_footer_nlbw_acl else R.string.bw_live_footer),
                style = MaterialTheme.typography.bodySmall,
                color = CtrlColors.Muted,
            )
        }
    }
}

@Composable
private fun NeedsSetup(nlbwInstalled: Boolean) {
    CtrlCard(Modifier.padding(horizontal = Gutter, vertical = 6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CtrlSectionLabel(stringResource(R.string.bw_title))
            Text(stringResource(R.string.bw_needs_title), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(if (nlbwInstalled) R.string.bw_needs_acl else R.string.bw_needs_body),
                style = MaterialTheme.typography.bodySmall,
                color = CtrlColors.Muted,
            )
        }
    }
}
