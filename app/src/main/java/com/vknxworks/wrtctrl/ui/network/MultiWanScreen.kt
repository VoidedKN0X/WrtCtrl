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
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.NetworkCheck
import com.vknxworks.wrtctrl.data.api.MwanInterface
import com.vknxworks.wrtctrl.data.api.mwanStatus
import com.vknxworks.wrtctrl.ui.components.EmptyState
import com.vknxworks.wrtctrl.ui.components.formatUptime

@Composable
private fun mwanState(i: MwanInterface): String = when (i.status) {
    "online" -> if (i.onlineSeconds > 0) stringResource(R.string.mwan_online_for, formatUptime(i.onlineSeconds)) else stringResource(R.string.mwan_online)
    "offline" -> if (i.offlineSeconds > 0) stringResource(R.string.mwan_offline_for, formatUptime(i.offlineSeconds)) else stringResource(R.string.mwan_offline)
    "connecting" -> stringResource(R.string.mwan_connecting)
    "disconnecting" -> stringResource(R.string.mwan_disconnecting)
    "disabled" -> stringResource(R.string.mwan_disabled)
    "error" -> stringResource(R.string.mwan_error)
    else -> stringResource(R.string.unknown)
}

/** Multi-WAN (mwan3): every WAN it tracks with its state and test addresses, and how each policy shares the traffic. */
@Composable
fun MultiWanScreen() {
    val router = activeRouter()?.router
    val data = rememberRouterData(10_000) { client -> client.mwanStatus() }
    val st = data.value

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.net_mwan))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            data.error?.let { ErrorCard(it, router?.host.orEmpty(), onRetry = { data.refresh() }) }
            if (st == null) return@Column
            if (st.interfaces.isEmpty()) {
                EmptyState(Icons.Outlined.AltRoute, stringResource(R.string.mwan_empty_title), stringResource(R.string.mwan_empty_body))
                return@Column
            }
            st.interfaces.forEach { i ->
                Group(i.name) {
                    GroupRow(
                        stringResource(R.string.mwan_state),
                        mwanState(i) + if (!i.enabled) " · " + stringResource(R.string.mwan_not_enabled) else "",
                        icon = Icons.Outlined.NetworkCheck,
                        iconTint = when (i.status) {
                            "online" -> CtrlColors.AccentText
                            "offline", "error" -> CtrlColors.Error
                            else -> CtrlColors.Muted
                        },
                        titleColor = if (i.status == "offline" || i.status == "error") CtrlColors.Error else CtrlColors.Text,
                    )
                    i.trackIps.forEach { t ->
                        GroupRow(
                            t.ip,
                            if (t.status == "up") stringResource(R.string.mwan_track_up, t.latencyMs, t.lossPercent) else stringResource(R.string.mwan_track_down),
                        )
                    }
                    if (i.trackIps.isEmpty()) GroupRow(stringResource(R.string.mwan_no_tracking))
                }
            }
            if (st.policies.isNotEmpty()) {
                Group(stringResource(R.string.mwan_policies), footer = stringResource(R.string.mwan_policies_footer)) {
                    st.policies.forEach { (name, members) ->
                        GroupRow(
                            name,
                            if (members.isEmpty()) stringResource(R.string.mwan_policy_empty) else members.joinToString(" · ") { "${it.iface} ${it.percent}%" },
                            icon = Icons.Outlined.AltRoute,
                        )
                    }
                }
            }
        }
    }
}
