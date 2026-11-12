package com.vknxworks.wrtctrl.ui.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.container
import com.vknxworks.wrtctrl.data.api.NetInterface
import com.vknxworks.wrtctrl.data.api.SystemInfo
import com.vknxworks.wrtctrl.data.api.wan
import com.vknxworks.wrtctrl.data.api.wan6
import com.vknxworks.wrtctrl.data.router.loadClients
import com.vknxworks.wrtctrl.ui.components.ChartSeries
import com.vknxworks.wrtctrl.ui.components.ErrorCard
import com.vknxworks.wrtctrl.ui.components.LineChart
import com.vknxworks.wrtctrl.ui.components.LocalBottomClearance
import com.vknxworks.wrtctrl.ui.components.CtrlCard
import com.vknxworks.wrtctrl.ui.components.CtrlHeroCard
import com.vknxworks.wrtctrl.ui.components.CtrlSectionLabel
import com.vknxworks.wrtctrl.ui.components.TabTitle
import com.vknxworks.wrtctrl.ui.components.UsageBar
import com.vknxworks.wrtctrl.ui.components.bandLabel
import com.vknxworks.wrtctrl.ui.components.formatBytes
import com.vknxworks.wrtctrl.ui.components.formatRate
import com.vknxworks.wrtctrl.ui.components.formatUptime
import com.vknxworks.wrtctrl.ui.components.percent
import com.vknxworks.wrtctrl.ui.components.rememberRouterData
import com.vknxworks.wrtctrl.ui.components.usageColor
import com.vknxworks.wrtctrl.ui.nav.ClientsRoute
import com.vknxworks.wrtctrl.ui.nav.InterfacesRoute
import com.vknxworks.wrtctrl.ui.nav.LocalNavigator
import com.vknxworks.wrtctrl.ui.nav.NetworkRoute
import com.vknxworks.wrtctrl.ui.nav.PackagesRoute
import com.vknxworks.wrtctrl.ui.nav.WifiRoute
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import com.vknxworks.wrtctrl.ui.theme.CtrlType
import com.vknxworks.wrtctrl.ui.theme.Gutter
import com.vknxworks.wrtctrl.ui.theme.Health
import kotlinx.coroutines.launch
import java.util.Locale

/** How often the Overview refreshes while it's open (the throughput graph samples at the same pace). */
private const val POLL_MS = 2_000L

@Composable
fun OverviewScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val router = active?.router
    val routerId = router?.id.orEmpty()
    var rates by remember(routerId) { mutableStateOf(LiveStats.points(routerId)) }
    var refreshing by remember { mutableStateOf(false) }

    val data = rememberRouterData(POLL_MS) { client ->
        val snap = loadOverview(routerId, client)
        val wan = snap.interfaces.wan()
        if (wan != null && client.acl?.canCall("network.device", "status") != false) {
            rates = runCatching { LiveStats.sampleWan(routerId, client, wan.statsDevice) }.getOrDefault(rates)
        }
        snap
    }
    val clients = rememberRouterData(15_000) { client -> loadClients(c.context, client, routerId) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_overview), showTitle = false)
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                scope.launch {
                    data.refresh()
                    clients.refresh()
                    refreshing = false
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            val snap = data.value
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomClearance.current + 16.dp)) {
                item("header") {
                    Header(
                        name = access.board?.hostname?.ifBlank { null } ?: router?.name.orEmpty(),
                        online = data.error == null && snap != null,
                        version = access.board?.release?.version?.ifBlank { null } ?: router?.openWrtVersion.orEmpty(),
                        uptime = snap?.info?.uptime,
                    )
                }
                val err = access.error ?: data.error
                if (err != null) item("error") { ErrorCard(err, router?.host.orEmpty(), onRetry = { c.sessions.refreshAccess(); data.refresh() }) }
                if (snap != null) {
                    val wan = snap.interfaces.wan()
                    item("throughput") { ThroughputCard(rates, wan) }
                    item("stats") { StatCards(snap, settings.memWarnPercent, settings.overlayWarnPercent) }
                    val items = attention(snap, settings.memWarnPercent, settings.overlayWarnPercent, UpdateCounts.get(routerId))
                    item("attention") {
                        AttentionCard(items) { a ->
                            when (a) {
                                Attention.WanDown -> nav.navigate(InterfacesRoute)
                                is Attention.RadioOff -> nav.navigate(WifiRoute)
                                is Attention.Updates -> nav.navigate(PackagesRoute)
                                is Attention.PendingChanges, is Attention.LowMemory, is Attention.StorageFull -> Unit
                            }
                        }
                    }
                    item("wan") { WanCard(wan, snap.interfaces.wan6()) { nav.navigate(InterfacesRoute) } }
                    if (snap.radios.isNotEmpty()) item("wifi") { RadiosCard(snap) { nav.navigate(WifiRoute) } }
                    item("router") { RouterCard(access.board?.model ?: router?.model.orEmpty(), access.board?.kernel.orEmpty(), access.board?.release?.target.orEmpty(), snap.info, snap.temperature) }
                    item("clients") {
                        val list = clients.value
                        SummaryRow(
                            stringResource(R.string.tab_clients),
                            if (list == null) "…" else stringResource(R.string.clients_online_of, list.count { it.online }, list.size),
                        ) { nav.tab(ClientsRoute) }
                        SummaryRow(stringResource(R.string.tab_network), stringResource(R.string.overview_network_hint)) { nav.tab(NetworkRoute) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(name: String, online: Boolean, version: String, uptime: Long?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CtrlSectionLabel(
            stringResource(R.string.overview_label, stringResource(if (online) R.string.state_online else R.string.state_offline)),
            color = if (online) CtrlColors.Muted else CtrlColors.Error,
        )
        Text(name, style = CtrlType.Display, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(
                version.ifBlank { null }?.let { stringResource(R.string.openwrt_version, it) },
                uptime?.let { stringResource(R.string.uptime_value, formatUptime(it)) },
            ).joinToString(" · ").lowercase(Locale.getDefault()),
            style = CtrlType.Mono,
            color = CtrlColors.Muted,
        )
    }
}

@Composable
private fun ThroughputCard(points: List<RatePoint>, wan: NetInterface?) {
    val last = points.lastOrNull()
    Column(Modifier.padding(horizontal = Gutter, vertical = 6.dp)) {
        CtrlHeroCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.wan_throughput).uppercase(), style = CtrlType.Label.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                if (wan != null) Text(wan.statsDevice.uppercase(), style = CtrlType.Label.copy(fontWeight = FontWeight.SemiBold))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.rate_down).uppercase(), style = CtrlType.Label)
                    Text(last?.let { formatRate(it.down) } ?: "—", style = CtrlType.Readout)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.rate_up).uppercase(), style = CtrlType.Label)
                    Text(last?.let { formatRate(it.up) } ?: "—", style = CtrlType.Readout)
                }
            }
        }
        if (points.size >= 2) {
            CtrlCard(Modifier.padding(top = 10.dp)) {
                LineChart(
                    times = points.map { it.time / 1000 },
                    series = listOf(
                        ChartSeries(stringResource(R.string.rate_down), points.map { it.down }),
                        ChartSeries(stringResource(R.string.rate_up), points.map { it.up }),
                    ),
                    format = ::formatRate,
                )
            }
        }
    }
}

@Composable
private fun StatCards(s: OverviewSnapshot, memWarn: Int, storageWarn: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(
            label = stringResource(R.string.label_cpu),
            value = s.cpu?.let { "${percent(it)}%" } ?: "—",
            detail = s.info?.load?.takeIf { it.isNotEmpty() }?.joinToString(" ") { String.format(Locale.getDefault(), "%.2f", it) },
            fraction = s.cpu,
            color = CtrlColors.Text,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(R.string.label_memory),
            value = s.info?.let { "${percent(it.memFraction)}%" } ?: "—",
            detail = s.info?.let { formatBytes(it.memUsed) + " / " + formatBytes(it.memTotal) },
            fraction = s.info?.memFraction,
            color = usageColor(s.info?.memFraction, memWarn, CtrlColors.Text),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(R.string.label_storage),
            value = s.storage?.let { "${percent(it.fraction)}%" } ?: "—",
            detail = s.storage?.let { formatBytes(it.used) + " / " + formatBytes(it.size) },
            fraction = s.storage?.fraction,
            color = usageColor(s.storage?.fraction, storageWarn, CtrlColors.Text),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, detail: String?, fraction: Double?, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    CtrlCard(modifier, contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CtrlSectionLabel(label)
            Text(value, style = CtrlType.Readout, color = color, maxLines = 1)
            UsageBar(fraction, color)
            if (detail != null) Text(detail, style = CtrlType.Mono, color = CtrlColors.Muted, maxLines = 2)
        }
    }
}

@Composable
private fun AttentionCard(items: List<Attention>, onOpen: (Attention) -> Unit) {
    CtrlCard(Modifier.padding(horizontal = Gutter, vertical = 6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CtrlSectionLabel(stringResource(R.string.attention_title))
            if (items.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = Health.ok, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.attention_none), style = MaterialTheme.typography.bodyMedium)
                }
            }
            items.forEach { a ->
                val (icon, text, critical) = when (a) {
                    Attention.WanDown -> Triple(Icons.Outlined.Public, stringResource(R.string.att_wan_down), true)
                    is Attention.RadioOff -> Triple(Icons.Outlined.WifiOff, stringResource(R.string.att_radio_off, a.radio.name, bandLabel(a.radio.band)), false)
                    is Attention.LowMemory -> Triple(Icons.Outlined.Memory, stringResource(R.string.att_low_memory, percent(a.fraction)), a.fraction > 0.95)
                    is Attention.StorageFull -> Triple(Icons.Outlined.SdStorage, stringResource(R.string.att_storage_full, percent(a.fraction)), a.fraction > 0.95)
                    is Attention.Updates -> Triple(Icons.Outlined.Inventory2, stringResource(R.string.att_updates, a.count), false)
                    is Attention.PendingChanges -> Triple(Icons.Outlined.PendingActions, stringResource(R.string.att_pending, a.count), false)
                }
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(a) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(icon, null, tint = if (critical) Health.critical else Health.warn, modifier = Modifier.size(20.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WanCard(wan: NetInterface?, wan6: NetInterface?, onClick: () -> Unit) {
    CtrlCard(Modifier.padding(horizontal = Gutter, vertical = 6.dp), onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CtrlSectionLabel(stringResource(R.string.wan_title), Modifier.weight(1f))
                if (wan != null) {
                    Text(
                        stringResource(if (wan.up) R.string.state_up else R.string.state_down).uppercase(),
                        style = CtrlType.MonoStatus,
                        color = if (wan.up) CtrlColors.AccentText else CtrlColors.Error,
                    )
                }
            }
            if (wan == null) {
                Text(stringResource(R.string.wan_none), color = CtrlColors.Muted, style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            Text(wan.ipv4.firstOrNull()?.address ?: "—", style = CtrlType.Readout)
            val lines = listOfNotNull(
                stringResource(R.string.wan_proto, wan.proto.ifBlank { "—" }, wan.statsDevice.ifBlank { "—" }),
                if (wan.up) stringResource(R.string.uptime_value, formatUptime(wan.uptime)) else null,
                wan.gateway?.let { stringResource(R.string.wan_gateway, it) },
                wan.dns.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.wan_dns, it.joinToString(", ")) },
                stringResource(R.string.wan_ipv6, ipv6Summary(wan, wan6)),
            )
            Text(lines.joinToString("\n"), style = CtrlType.Mono, color = CtrlColors.Muted)
            wan.errors.takeIf { it.isNotEmpty() }?.let { errs ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = CtrlColors.Error, modifier = Modifier.size(18.dp))
                    Text(errs.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Error)
                }
            }
        }
    }
}

@Composable
private fun ipv6Summary(wan: NetInterface, wan6: NetInterface?): String {
    val addr = (wan6?.ipv6 ?: emptyList()) + wan.ipv6
    val prefix = wan6?.ipv6Prefixes.orEmpty()
    return when {
        wan6 != null && wan6.up && addr.isNotEmpty() -> addr.first().toString()
        wan6 != null && wan6.up -> stringResource(R.string.ipv6_up_no_address)
        wan6 != null -> stringResource(R.string.ipv6_down)
        addr.isNotEmpty() -> addr.first().toString()
        prefix.isNotEmpty() -> prefix.first().toString()
        else -> stringResource(R.string.ipv6_none)
    }
}

@Composable
private fun RadiosCard(s: OverviewSnapshot, onClick: () -> Unit) {
    CtrlCard(Modifier.padding(horizontal = Gutter, vertical = 6.dp), onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CtrlSectionLabel(stringResource(R.string.wifi_radios))
            s.radios.forEach { r ->
                val on = r.up && !r.disabled
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(bandLabel(r.band).ifBlank { r.name }, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(
                                r.channel.ifBlank { null }?.let { stringResource(R.string.wifi_channel, it) },
                                r.htmode.ifBlank { null },
                                r.txpower.ifBlank { null }?.let { stringResource(R.string.wifi_txpower, it) },
                                r.ifaces.count { !it.disabled }.let { stringResource(R.string.wifi_networks, it) },
                            ).joinToString(" · "),
                            style = CtrlType.Mono,
                            color = CtrlColors.Muted,
                        )
                    }
                    Text(
                        stringResource(if (on) R.string.state_on else R.string.state_off).uppercase(),
                        style = CtrlType.MonoStatus,
                        color = if (on) CtrlColors.AccentText else CtrlColors.Stopped,
                    )
                }
            }
        }
    }
}

@Composable
private fun RouterCard(model: String, kernel: String, target: String, info: SystemInfo?, temperature: Double?) {
    CtrlCard(Modifier.padding(horizontal = Gutter, vertical = 6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CtrlSectionLabel(stringResource(R.string.router_details))
            Text(
                listOfNotNull(
                    model.ifBlank { null },
                    target.ifBlank { null }?.let { stringResource(R.string.router_target, it) },
                    kernel.ifBlank { null }?.let { stringResource(R.string.router_kernel, it) },
                    info?.load?.takeIf { it.size >= 3 }?.let { l -> stringResource(R.string.router_load, l.joinToString("  ") { String.format(Locale.getDefault(), "%.2f", it) }) },
                    temperature?.let { stringResource(R.string.router_temperature, String.format(Locale.getDefault(), "%.0f", it)) },
                    info?.takeIf { it.swapTotal > 0 }?.let { stringResource(R.string.router_swap, formatBytes(it.swapTotal - it.swapFree), formatBytes(it.swapTotal)) },
                ).joinToString("\n"),
                style = CtrlType.Mono,
                color = CtrlColors.Muted,
            )
        }
    }
}

@Composable
private fun SummaryRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Gutter + 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(value.uppercase(), style = CtrlType.MonoStatus, color = CtrlColors.Muted)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = CtrlColors.Muted)
    }
}
