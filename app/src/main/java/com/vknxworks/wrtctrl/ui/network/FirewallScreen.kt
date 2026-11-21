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
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.wrtctrl.container
import com.vknxworks.wrtctrl.data.api.UciSection
import com.vknxworks.wrtctrl.data.api.uciGet
import com.vknxworks.wrtctrl.data.api.uciSet
import com.vknxworks.wrtctrl.ui.components.GroupSwitch
import kotlinx.coroutines.launch

/** "tcp udp" → "TCP + UDP"; nothing set → null (any protocol, or fw4's default for a rule with ports). */
internal fun ruleProto(raw: List<String>): String? {
    val parts = raw.flatMap { it.split(' ', ',') }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    if (parts.isEmpty() || parts == listOf("all") || parts == listOf("any")) return null
    if (parts == listOf("tcpudp")) return "TCP + UDP"
    return parts.joinToString(" + ") { it.uppercase() }
}

/** One line for a firewall rule: where from, where to, protocol, ports and what happens. */
@Composable
private fun ruleSummary(r: UciSection): String {
    val src = r.opt("src")
    val dest = r.opt("dest")
    val from = when {
        src.isBlank() -> stringResource(R.string.fw_this_router)
        src == "*" -> stringResource(R.string.fw_any_zone)
        else -> src
    }
    val to = when {
        dest.isBlank() -> stringResource(R.string.fw_this_router)
        dest == "*" -> stringResource(R.string.fw_any_zone)
        else -> dest
    }
    val ports = r.list("dest_port").joinToString(", ")
    return listOfNotNull(
        stringResource(R.string.fw_from_to, from, to),
        ruleProto(r.list("proto")),
        if (ports.isNotBlank()) stringResource(R.string.fw_port, ports) else null,
        r.opt("target", "DROP").uppercase(),
    ).joinToString(" · ")
}

/** Firewall: the default policies, the zones with what they forward to, and the traffic rules with a switch each. */
@Composable
fun FirewallScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val active = activeRouter()
    val router = active?.router
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val canEdit = router?.readOnly == false && access.acl.canApply && access.acl.canWriteUci("firewall")

    val data = rememberRouterData { client -> client.uciGet("firewall") }
    val all = data.value

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.net_firewall))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            data.error?.let { ErrorCard(it, router?.host.orEmpty(), onRetry = { data.refresh() }) }
            if (all == null) return@Column
            val defaults = all.firstOrNull { it.type == "defaults" }
            val zones = all.filter { it.type == "zone" }
            val forwardings = all.filter { it.type == "forwarding" }
            val rules = all.filter { it.type == "rule" }

            if (defaults != null) {
                Group(stringResource(R.string.fw_defaults)) {
                    GroupRow(
                        stringResource(R.string.fw_policies),
                        stringResource(R.string.fw_policy_line, defaults.opt("input", "REJECT").uppercase(), defaults.opt("output", "REJECT").uppercase(), defaults.opt("forward", "REJECT").uppercase()),
                        icon = Icons.Outlined.Security,
                    )
                }
            }

            Group(stringResource(R.string.fw_zones, zones.size)) {
                if (zones.isEmpty()) GroupRow(stringResource(R.string.fw_zones_none))
                zones.forEach { z ->
                    val name = z.opt("name").ifBlank { z.name }
                    val to = forwardings.filter { it.opt("src") == name }.map { it.opt("dest") }.filter { it.isNotBlank() }
                    val networks = z.list("network").flatMap { it.split(' ') }.filter { it.isNotBlank() }
                    val lines = buildList {
                        add(stringResource(R.string.fw_policy_line, z.opt("input", "DROP").uppercase(), z.opt("output", "DROP").uppercase(), z.opt("forward", "DROP").uppercase()))
                        if (networks.isNotEmpty()) add(stringResource(R.string.fw_zone_networks, networks.joinToString(", ")))
                        add(if (to.isEmpty()) stringResource(R.string.fw_zone_forwards_none) else stringResource(R.string.fw_zone_forwards, to.joinToString(", ")))
                        if (z.flag("masq")) add(stringResource(R.string.fw_zone_masq))
                    }
                    GroupRow(name, lines.joinToString("\n"), icon = Icons.Outlined.Shield)
                }
            }

            Group(stringResource(R.string.fw_rules, rules.size)) {
                if (rules.isEmpty()) GroupRow(stringResource(R.string.fw_rules_none))
                rules.forEach { r ->
                    val name = r.opt("name").ifBlank { r.name }
                    GroupSwitch(name, ruleSummary(r), checked = r.flag("enabled", true)) { on ->
                        val client = active?.client ?: return@GroupSwitch
                        if (!canEdit) return@GroupSwitch
                        scope.launch {
                            c.changes.apply(client, context.getString(if (on) R.string.change_rule_on else R.string.change_rule_off, name)) {
                                uciSet("firewall", r.name, mapOf("enabled" to if (on) "1" else "0"))
                            }
                            data.refresh()
                        }
                    }
                }
            }
            Text(
                stringResource(if (canEdit) R.string.fw_footer else R.string.not_allowed_here),
                style = MaterialTheme.typography.bodySmall,
                color = CtrlColors.Muted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }
}
