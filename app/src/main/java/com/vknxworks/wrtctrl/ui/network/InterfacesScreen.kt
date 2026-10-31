package com.vknxworks.wrtctrl.ui.network

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.container
import com.vknxworks.wrtctrl.data.api.DeviceStats
import com.vknxworks.wrtctrl.data.api.NetInterface
import com.vknxworks.wrtctrl.data.api.allDeviceStats
import com.vknxworks.wrtctrl.data.api.interfaces
import com.vknxworks.wrtctrl.data.api.restartInterface
import com.vknxworks.wrtctrl.ui.components.ConfirmDialog
import com.vknxworks.wrtctrl.ui.components.ErrorCard
import com.vknxworks.wrtctrl.ui.components.Group
import com.vknxworks.wrtctrl.ui.components.GroupRow
import com.vknxworks.wrtctrl.ui.components.ScreenTopBar
import com.vknxworks.wrtctrl.ui.components.TypedConfirmDialog
import com.vknxworks.wrtctrl.ui.components.activeRouter
import com.vknxworks.wrtctrl.ui.components.errorMessage
import com.vknxworks.wrtctrl.ui.components.formatBytes
import com.vknxworks.wrtctrl.ui.components.formatUptime
import com.vknxworks.wrtctrl.ui.components.rememberRouterData
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import com.vknxworks.wrtctrl.ui.theme.CtrlType
import kotlinx.coroutines.launch

private data class IfaceData(val interfaces: List<NetInterface>, val stats: Map<String, DeviceStats>)

/**
 * Interfaces: state, addresses and traffic counters, and restarting one. Restarting isn't a config
 * change, so there's no rollback: restarting the interface the phone is connected through (LAN) cuts
 * it off until the interface is back, and needs the name typed to confirm.
 */
@Composable
fun InterfacesScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val active = activeRouter()
    val router = active?.router
    val access by c.sessions.access.collectAsStateWithLifecycle()
    var restarting by remember { mutableStateOf<NetInterface?>(null) }

    val data = rememberRouterData(5_000) { client ->
        IfaceData(client.interfaces(), runCatching { client.allDeviceStats() }.getOrDefault(emptyMap()))
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.net_interfaces))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            data.error?.let { ErrorCard(it, router?.host.orEmpty(), onRetry = { data.refresh() }) }
            data.value?.interfaces?.filter { it.name != "loopback" }?.forEach { i ->
                val st = data.value?.stats?.get(i.statsDevice)
                val canRestart = router?.readOnly == false && access.acl.canCall("network.interface.${i.name}", "down") && access.acl.canCall("network.interface.${i.name}", "up")
                Group(i.name.uppercase()) {
                    GroupRow(
                        stringResource(if (i.up) R.string.state_up else if (i.pending) R.string.iface_pending else R.string.state_down),
                        listOfNotNull(
                            stringResource(R.string.wan_proto, i.proto.ifBlank { "—" }, i.statsDevice.ifBlank { "—" }),
                            if (i.up) stringResource(R.string.uptime_value, formatUptime(i.uptime)) else null,
                        ).joinToString(" · "),
                        titleColor = if (i.up) CtrlColors.AccentText else CtrlColors.Error,
                    )
                    val addrs = (i.ipv4 + i.ipv6 + i.ipv6Prefixes).map { it.toString() }
                    if (addrs.isNotEmpty()) {
                        Text(addrs.joinToString("\n"), style = CtrlType.Mono, color = CtrlColors.Muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    if (st != null) {
                        GroupRow(stringResource(R.string.iface_traffic), stringResource(R.string.iface_traffic_value, formatBytes(st.rxBytes), formatBytes(st.txBytes)))
                    }
                    if (i.errors.isNotEmpty()) GroupRow(stringResource(R.string.iface_errors), i.errors.joinToString(", "), titleColor = CtrlColors.Error)
                    if (canRestart) {
                        GroupRow(stringResource(R.string.iface_restart), icon = Icons.Outlined.RestartAlt, onClick = { restarting = i })
                    }
                }
            }
            Text(stringResource(R.string.iface_note), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }
    }

    restarting?.let { i ->
        val client = active?.client
        fun go() {
            restarting = null
            if (client == null) return
            scope.launch {
                runCatching { client.restartInterface(i.name) }
                    .onSuccess { Toast.makeText(context, context.getString(R.string.iface_restarted, i.name), Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(context, errorMessage(context, it, router?.host.orEmpty()), Toast.LENGTH_LONG).show() }
                data.refresh()
            }
        }
        // The LAN (and anything else WrtCtrl may be reaching the router through) needs the name typed.
        val risky = i.name == "lan" || i.name.startsWith("wan")
        if (risky) {
            TypedConfirmDialog(
                title = stringResource(R.string.iface_restart_title, i.name),
                body = stringResource(if (i.name == "lan") R.string.iface_restart_lan_body else R.string.iface_restart_wan_body, i.name),
                expected = i.name,
                confirmLabel = stringResource(R.string.iface_restart),
                onDismiss = { restarting = null },
                onConfirm = ::go,
            )
        } else {
            ConfirmDialog(
                title = stringResource(R.string.iface_restart_title, i.name),
                body = stringResource(R.string.iface_restart_body, i.name),
                confirmLabel = stringResource(R.string.iface_restart),
                onDismiss = { restarting = null },
                onConfirm = ::go,
            )
        }
    }
}
