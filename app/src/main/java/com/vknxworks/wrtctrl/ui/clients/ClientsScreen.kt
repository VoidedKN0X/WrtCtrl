package com.vknxworks.wrtctrl.ui.clients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.container
import com.vknxworks.wrtctrl.data.router.Link
import com.vknxworks.wrtctrl.data.router.NetClient
import com.vknxworks.wrtctrl.data.router.loadClients
import com.vknxworks.wrtctrl.ui.components.ChoiceSheet
import com.vknxworks.wrtctrl.ui.components.CtrlSquareButton
import com.vknxworks.wrtctrl.ui.components.CtrlTextField
import com.vknxworks.wrtctrl.ui.components.EmptyState
import com.vknxworks.wrtctrl.ui.components.ErrorCard
import com.vknxworks.wrtctrl.ui.components.LocalBottomClearance
import com.vknxworks.wrtctrl.ui.components.TabTitle
import com.vknxworks.wrtctrl.ui.components.activeRouter
import com.vknxworks.wrtctrl.ui.components.bandLabel
import com.vknxworks.wrtctrl.ui.components.rememberRouterData
import com.vknxworks.wrtctrl.ui.nav.ClientRoute
import com.vknxworks.wrtctrl.ui.nav.LocalNavigator
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import com.vknxworks.wrtctrl.ui.theme.CtrlType
import com.vknxworks.wrtctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

enum class ClientSort(val label: Int) {
    NAME(R.string.sort_name), IP(R.string.sort_ip), ONLINE(R.string.sort_online), SIGNAL(R.string.sort_signal)
}

enum class ClientFilter(val label: Int) {
    ALL(R.string.filter_all), ONLINE(R.string.filter_online), WIRED(R.string.filter_wired),
    BAND_2G(R.string.filter_2g), BAND_5G(R.string.filter_5g), BAND_6G(R.string.filter_6g),
}

/** Sorts IPv4 addresses numerically (192.168.1.9 before 192.168.1.10), anything else after. */
private fun ipKey(ip: String): Long =
    ip.split('.').takeIf { it.size == 4 }?.fold(0L) { acc, p -> acc * 256 + (p.toIntOrNull() ?: 0) } ?: Long.MAX_VALUE

fun sortClients(list: List<NetClient>, sort: ClientSort): List<NetClient> = when (sort) {
    ClientSort.NAME -> list.sortedWith(compareBy({ it.name.isBlank() }, { it.name.lowercase() }, { it.mac }))
    ClientSort.IP -> list.sortedBy { ipKey(it.ipv4.firstOrNull().orEmpty()) }
    ClientSort.ONLINE -> list.sortedWith(compareBy({ !it.online }, { it.name.isBlank() }, { it.name.lowercase() }))
    ClientSort.SIGNAL -> list.sortedWith(compareBy({ it.signal == null }, { -(it.signal ?: -200) }))
}

private fun matches(c: NetClient, f: ClientFilter): Boolean = when (f) {
    ClientFilter.ALL -> true
    ClientFilter.ONLINE -> c.online
    ClientFilter.WIRED -> c.link is Link.Wired
    ClientFilter.BAND_2G -> c.band == "2g"
    ClientFilter.BAND_5G -> c.band == "5g"
    ClientFilter.BAND_6G -> c.band == "6g"
}

/** Every device the router knows: search, sort (name, IP, online, signal) and filter (online, wired, band). */
@Composable
fun ClientsScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val router = activeRouter()?.router
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(ClientSort.ONLINE) }
    var filter by rememberSaveable { mutableStateOf(ClientFilter.ALL) }
    var sortSheet by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    val data = rememberRouterData(10_000) { client -> loadClients(c.context, client, router?.id.orEmpty()) }
    val all = data.value.orEmpty()
    val bands = all.mapNotNull { it.band }.toSet()
    val filters = ClientFilter.entries.filter {
        when (it) {
            ClientFilter.BAND_2G -> "2g" in bands
            ClientFilter.BAND_5G -> "5g" in bands
            ClientFilter.BAND_6G -> "6g" in bands
            ClientFilter.WIRED -> bands.isNotEmpty()
            else -> true
        }
    }
    val q = query.trim().lowercase()
    val shown = sortClients(
        all.filter { matches(it, filter) }.filter { cl ->
            q.isEmpty() || cl.name.lowercase().contains(q) || cl.mac.lowercase().contains(q) ||
                cl.ipv4.any { it.contains(q) } || cl.vendor?.lowercase()?.contains(q) == true
        },
        sort,
    )

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_clients), trailing = {
            CtrlSquareButton(Icons.Outlined.Search, stringResource(R.string.search), onClick = { searching = !searching; if (!searching) query = "" })
            CtrlSquareButton(Icons.Outlined.Sort, stringResource(R.string.sort), onClick = { sortSheet = true }, modifier = Modifier.padding(start = 6.dp))
        })
        if (searching) {
            CtrlTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.clients_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp),
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(stringResource(f.label)) })
            }
        }
        data.error?.let { ErrorCard(it, router?.host.orEmpty(), onRetry = { data.refresh() }) }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                scope.launch {
                    data.refresh()
                    refreshing = false
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            if (data.value != null && shown.isEmpty()) {
                EmptyState(Icons.Outlined.Devices, stringResource(R.string.clients_empty_title), stringResource(R.string.clients_empty_body))
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomClearance.current + 16.dp)) {
                    item("count") {
                        Text(
                            stringResource(R.string.clients_online_of, all.count { it.online }, all.size).uppercase(),
                            style = CtrlType.MonoStatus,
                            color = CtrlColors.Muted,
                            modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
                        )
                    }
                    val blocked = all.count { it.blockRule != null }
                    if (blocked > 0) {
                        item("blocked") {
                            Row(
                                Modifier.fillMaxWidth().clickable { nav.navigate(com.vknxworks.wrtctrl.ui.nav.BlockedRoute) }.padding(horizontal = Gutter, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Icon(Icons.Outlined.Block, null, tint = CtrlColors.Error, modifier = Modifier.size(24.dp))
                                Text(pluralStringResource(R.plurals.blocked_devices_row, blocked, blocked), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = CtrlColors.Muted)
                            }
                        }
                    }
                    items(shown, key = { it.mac }) { cl -> ClientRow(cl) { nav.navigate(ClientRoute(cl.mac)) } }
                }
            }
        }
    }

    if (sortSheet) {
        ChoiceSheet(stringResource(R.string.sort), ClientSort.entries.map { it to stringResource(it.label) }, sort, onDismiss = { sortSheet = false }) {
            sort = it
            sortSheet = false
        }
    }
}

@Composable
fun ClientRow(cl: NetClient, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(
                when {
                    cl.blocked -> Icons.Outlined.Block
                    cl.link is Link.Wifi -> Icons.Outlined.Wifi
                    cl.link is Link.Wired -> Icons.Outlined.Cable
                    else -> Icons.Outlined.Devices
                },
                null,
                tint = when {
                    cl.blocked -> CtrlColors.Error
                    cl.online -> CtrlColors.AccentText
                    else -> CtrlColors.Stopped
                },
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    cl.name.ifBlank { cl.vendor ?: stringResource(R.string.client_unnamed) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (cl.online) CtrlColors.Text else CtrlColors.Muted,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (cl.isStatic) Icon(Icons.Outlined.PushPin, stringResource(R.string.client_static), Modifier.size(14.dp), tint = CtrlColors.Muted)
            }
            Text(
                listOfNotNull(cl.ipv4.firstOrNull() ?: cl.ipv6.firstOrNull(), cl.vendor, if (cl.randomisedMac) stringResource(R.string.client_private_mac) else null)
                    .joinToString(" · ")
                    .ifBlank { cl.mac },
                style = CtrlType.Mono,
                color = CtrlColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            when (val l = cl.link) {
                is Link.Wifi -> "${bandLabel(l.band)} · ${l.signal} dBm"
                Link.Wired -> stringResource(R.string.link_wired)
                Link.Offline -> stringResource(R.string.state_offline)
            }.uppercase(),
            style = CtrlType.MonoStatus,
            color = if (cl.online) CtrlColors.AccentText else CtrlColors.Stopped,
        )
    }
}
