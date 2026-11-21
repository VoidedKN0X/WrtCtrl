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
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.vknxworks.wrtctrl.data.api.ddnsServices
import com.vknxworks.wrtctrl.ui.components.EmptyState

/** Dynamic DNS (ddns-scripts): every service with the address it registered and when it last and next updates. */
@Composable
fun DdnsScreen() {
    val router = activeRouter()?.router
    val data = rememberRouterData(30_000) { client -> client.ddnsServices() }
    val list = data.value

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.net_ddns))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            data.error?.let { ErrorCard(it, router?.host.orEmpty(), onRetry = { data.refresh() }) }
            if (list == null) return@Column
            if (list.isEmpty()) {
                EmptyState(Icons.Outlined.Language, stringResource(R.string.ddns_empty_title), stringResource(R.string.ddns_empty_body))
                return@Column
            }
            list.forEach { s ->
                Group(s.section) {
                    GroupRow(
                        stringResource(R.string.ddns_state),
                        stringResource(
                            when {
                                !s.enabled -> R.string.ddns_disabled
                                s.running -> R.string.ddns_running
                                else -> R.string.ddns_stopped
                            },
                        ),
                        icon = Icons.Outlined.Language,
                        iconTint = if (s.enabled && s.running) CtrlColors.AccentText else CtrlColors.Muted,
                        titleColor = if (s.enabled && !s.running) CtrlColors.Warn else CtrlColors.Text,
                    )
                    if (s.lookupHost.isNotBlank()) GroupRow(stringResource(R.string.ddns_hostname), s.lookupHost + if (s.ipv6) " · IPv6" else "")
                    if (s.provider.isNotBlank()) GroupRow(stringResource(R.string.ddns_provider), s.provider)
                    GroupRow(stringResource(R.string.ddns_ip), s.ip.ifBlank { stringResource(R.string.unknown) })
                    GroupRow(stringResource(R.string.ddns_last_update), s.lastUpdate.ifBlank { stringResource(R.string.ddns_never) })
                    if (s.nextUpdate.isNotBlank()) {
                        GroupRow(
                            stringResource(R.string.ddns_next_update),
                            // ddns-scripts' own words for a service without a next update.
                            when (s.nextUpdate) {
                                "Disabled" -> stringResource(R.string.ddns_disabled)
                                "Stopped" -> stringResource(R.string.ddns_stopped)
                                "Verify" -> stringResource(R.string.ddns_verifying)
                                "Run once" -> stringResource(R.string.ddns_run_once)
                                else -> s.nextUpdate
                            },
                        )
                    }
                }
            }
            Text(stringResource(R.string.ddns_footer), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }
    }
}
