package com.vknxworks.wrtctrl.ui.network

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
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
import com.vknxworks.wrtctrl.data.api.TAILSCALE
import com.vknxworks.wrtctrl.data.api.TailscalePeer
import com.vknxworks.wrtctrl.data.api.tailscaleExitNode
import com.vknxworks.wrtctrl.data.api.tailscaleStatus
import com.vknxworks.wrtctrl.ui.components.ConfirmDialog
import com.vknxworks.wrtctrl.ui.components.EmptyState
import com.vknxworks.wrtctrl.ui.components.ErrorCard
import com.vknxworks.wrtctrl.ui.components.Group
import com.vknxworks.wrtctrl.ui.components.GroupRow
import com.vknxworks.wrtctrl.ui.components.ScreenTopBar
import com.vknxworks.wrtctrl.ui.components.activeRouter
import com.vknxworks.wrtctrl.ui.components.errorMessage
import com.vknxworks.wrtctrl.ui.components.rememberRouterData
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import kotlinx.coroutines.launch

/** What the user picked in the exit node list: a peer, or "none" (null peer). */
private data class ExitChoice(val peer: TailscalePeer?)

/** Tailscale on the router: its state, the exit node it uses (with a switch) and the devices of the tailnet. */
@Composable
fun TailscaleScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val active = activeRouter()
    val router = active?.router
    val access by c.sessions.access.collectAsStateWithLifecycle()
    // The permission file allows "tailscale set --exit-node=…": checked with an example of what is sent.
    val canSwitch = router?.readOnly == false && access.acl.canExec("$TAILSCALE set --exit-node=100.64.0.1 --exit-node-allow-lan-access=true")
    var choice by remember { mutableStateOf<ExitChoice?>(null) }
    var busy by remember { mutableStateOf(false) }

    val data = rememberRouterData(15_000) { client -> client.tailscaleStatus() }
    val st = data.value

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.net_tailscale))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            data.error?.let { ErrorCard(it, router?.host.orEmpty(), onRetry = { data.refresh() }) }
            if (st == null) {
                if (!data.loading && data.error == null) {
                    EmptyState(Icons.Outlined.Public, stringResource(R.string.ts_no_status_title), stringResource(R.string.ts_no_status_body))
                }
                return@Column
            }

            Group(stringResource(R.string.ts_status)) {
                GroupRow(
                    stringResource(R.string.ts_state),
                    when (st.state) {
                        "Running" -> stringResource(R.string.ts_state_running)
                        "Stopped" -> stringResource(R.string.ts_state_stopped)
                        "NeedsLogin" -> stringResource(R.string.ts_state_login)
                        "Starting" -> stringResource(R.string.ts_state_starting)
                        else -> st.state.ifBlank { stringResource(R.string.unknown) }
                    },
                    titleColor = if (st.running) CtrlColors.AccentText else CtrlColors.Text,
                )
                if (st.name.isNotBlank() || st.ips.isNotEmpty()) GroupRow(stringResource(R.string.ts_this_router), (listOf(st.name) + st.ips).filter { it.isNotBlank() }.joinToString(" · "))
                if (st.tailnet.isNotBlank()) GroupRow(stringResource(R.string.ts_tailnet), st.tailnet)
                if (st.version.isNotBlank()) GroupRow(stringResource(R.string.ts_version), st.version)
                st.health.forEach { h -> GroupRow(stringResource(R.string.ts_health), h, icon = Icons.Outlined.Warning, iconTint = CtrlColors.Warn) }
            }

            if (st.running) {
                val options = st.peers.filter { it.exitNodeOption || it.exitNode }
                val current = st.exitNode
                Group(
                    stringResource(R.string.ts_exit_node),
                    footer = stringResource(
                        when {
                            !canSwitch -> R.string.not_allowed_here
                            options.isEmpty() -> R.string.ts_exit_none_offered
                            else -> R.string.ts_exit_footer
                        },
                    ),
                ) {
                    GroupRow(
                        stringResource(R.string.ts_exit_none),
                        stringResource(R.string.ts_exit_none_sub),
                        onClick = if (canSwitch && current != null && !busy) ({ choice = ExitChoice(null) }) else null,
                        trailing = if (current == null) ({ Icon(Icons.Outlined.Check, stringResource(R.string.selected), tint = CtrlColors.AccentText) }) else null,
                    )
                    options.forEach { p ->
                        val ip = p.ips.firstOrNull().orEmpty()
                        GroupRow(
                            p.name.ifBlank { ip },
                            listOf(ip, stringResource(if (p.online) R.string.ts_online else R.string.ts_offline)).filter { it.isNotBlank() }.joinToString(" · "),
                            icon = Icons.Outlined.Public,
                            onClick = if (canSwitch && !p.exitNode && ip.isNotBlank() && !busy) ({ choice = ExitChoice(p) }) else null,
                            trailing = if (p.exitNode) ({ Icon(Icons.Outlined.Check, stringResource(R.string.selected), tint = CtrlColors.AccentText) }) else null,
                        )
                    }
                }
            }

            Group(stringResource(R.string.ts_devices, st.peers.size)) {
                if (st.peers.isEmpty()) GroupRow(stringResource(R.string.ts_devices_none))
                st.peers.forEach { p ->
                    GroupRow(
                        p.name.ifBlank { p.ips.firstOrNull().orEmpty() },
                        (p.ips.take(1) + listOf(p.os, stringResource(if (p.online) R.string.ts_online else R.string.ts_offline))).filter { it.isNotBlank() }.joinToString(" · "),
                        icon = Icons.Outlined.Devices,
                        iconTint = if (p.online) CtrlColors.AccentText else CtrlColors.Muted,
                    )
                }
            }
        }
    }

    choice?.let { ch ->
        val peer = ch.peer
        val name = peer?.name?.ifBlank { peer.ips.firstOrNull().orEmpty() }.orEmpty()
        ConfirmDialog(
            title = stringResource(if (peer == null) R.string.ts_exit_off_title else R.string.ts_exit_on_title),
            body = if (peer == null) stringResource(R.string.ts_exit_off_body) else stringResource(R.string.ts_exit_on_body, name),
            confirmLabel = stringResource(if (peer == null) R.string.ts_exit_off_confirm else R.string.ts_exit_on_confirm),
            danger = peer != null,
            onDismiss = { choice = null },
            onConfirm = {
                choice = null
                val client = active?.client ?: return@ConfirmDialog
                busy = true
                scope.launch {
                    runCatching { client.tailscaleExitNode(peer?.ips?.firstOrNull()) }
                        .onSuccess { r ->
                            if (r.code == 0) {
                                toast(if (peer == null) context.getString(R.string.ts_exit_off_done) else context.getString(R.string.ts_exit_on_done, name))
                            } else {
                                toast(context.getString(R.string.ts_exit_failed, (r.stderr.ifBlank { r.stdout }).trim().lineSequence().firstOrNull().orEmpty()))
                            }
                        }
                        .onFailure { toast(errorMessage(context, it, router?.host.orEmpty())) }
                    busy = false
                    data.refresh()
                }
            },
        )
    }
}
