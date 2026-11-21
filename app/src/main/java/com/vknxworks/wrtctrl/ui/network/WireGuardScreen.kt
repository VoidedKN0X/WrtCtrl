package com.vknxworks.wrtctrl.ui.network

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.ui.components.ErrorCard
import com.vknxworks.wrtctrl.ui.components.Group
import com.vknxworks.wrtctrl.ui.components.GroupRow
import com.vknxworks.wrtctrl.ui.components.ScreenTopBar
import com.vknxworks.wrtctrl.ui.components.activeRouter
import com.vknxworks.wrtctrl.ui.components.rememberRouterData
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.wrtctrl.container
import com.vknxworks.wrtctrl.data.api.WgInstance
import com.vknxworks.wrtctrl.data.api.WgPeerConfig
import com.vknxworks.wrtctrl.data.api.interfaces
import com.vknxworks.wrtctrl.data.api.uciSet
import com.vknxworks.wrtctrl.data.api.wireguardInstances
import com.vknxworks.wrtctrl.data.api.wireguardPeers
import com.vknxworks.wrtctrl.ui.components.EmptyState
import com.vknxworks.wrtctrl.ui.components.GroupSwitch
import com.vknxworks.wrtctrl.ui.components.formatBytes
import com.vknxworks.wrtctrl.ui.components.relativeTime
import kotlinx.coroutines.launch

private data class WgData(val ifaces: List<String>, val running: Map<String, WgInstance>, val peers: Map<String, List<WgPeerConfig>>)

/** WireGuard: every interface with its peers, their last handshake and traffic, and a switch per peer. */
@Composable
fun WireGuardScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val active = activeRouter()
    val router = active?.router
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val canEdit = router?.readOnly == false && access.acl.canApply && access.acl.canWriteUci("network")

    val data = rememberRouterData(15_000) { client ->
        val ifaces = client.interfaces().filter { it.proto == "wireguard" }.map { it.name }
        // The live state needs the wg tool and LuCI's WireGuard support; without them the peers still show.
        val running = runCatching { client.wireguardInstances() }.getOrDefault(emptyList()).associateBy { it.name }
        WgData(ifaces, running, ifaces.associateWith { client.wireguardPeers(it) })
    }
    val d = data.value

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.net_wireguard))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            data.error?.let { ErrorCard(it, router?.host.orEmpty(), onRetry = { data.refresh() }) }
            if (d == null) return@Column
            if (d.ifaces.isEmpty()) {
                EmptyState(Icons.Outlined.VpnKey, stringResource(R.string.wg_empty_title), stringResource(R.string.wg_empty_body))
                return@Column
            }
            d.ifaces.forEach { iface ->
                val live = d.running[iface]
                val peers = d.peers[iface].orEmpty()
                Group(iface) {
                    GroupRow(
                        stringResource(R.string.wg_interface),
                        if (live == null) stringResource(R.string.wg_not_running) else stringResource(R.string.wg_listen_port, live.listenPort.ifBlank { "—" }),
                        icon = Icons.Outlined.VpnKey,
                        iconTint = if (live != null) CtrlColors.AccentText else CtrlColors.Muted,
                    )
                    if (peers.isEmpty()) GroupRow(stringResource(R.string.wg_no_peers))
                    peers.forEach { p ->
                        val st = live?.peers?.firstOrNull { it.publicKey == p.publicKey }
                        val name = p.description.ifBlank { p.publicKey.take(12) + "…" }
                        val lines = buildList {
                            if (p.allowedIps.isNotEmpty()) add(p.allowedIps.joinToString(", "))
                            when {
                                p.disabled -> add(stringResource(R.string.wg_peer_off))
                                st == null -> add(stringResource(R.string.wg_peer_no_state))
                                st.latestHandshake <= 0 -> add(stringResource(R.string.wg_never_connected))
                                else -> add(stringResource(R.string.wg_handshake, relativeTime(st.latestHandshake * 1000)))
                            }
                            if (st != null && !p.disabled) {
                                if (st.endpoint.isNotBlank()) add(st.endpoint)
                                add(stringResource(R.string.wg_traffic, formatBytes(st.rxBytes), formatBytes(st.txBytes)))
                            }
                        }
                        GroupSwitch(name, lines.joinToString("\n"), checked = !p.disabled) { on ->
                            val client = active?.client ?: return@GroupSwitch
                            if (!canEdit) return@GroupSwitch
                            scope.launch {
                                c.changes.apply(client, context.getString(if (on) R.string.change_wg_peer_on else R.string.change_wg_peer_off, name)) {
                                    uciSet("network", p.section, mapOf("disabled" to if (on) "0" else "1"))
                                }
                                data.refresh()
                            }
                        }
                    }
                }
            }
            Text(
                stringResource(if (canEdit) R.string.wg_footer else R.string.not_allowed_here),
                style = MaterialTheme.typography.bodySmall,
                color = CtrlColors.Muted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }
}
