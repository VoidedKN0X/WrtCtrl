package com.vknxworks.wrtctrl.ui.setup

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.data.api.RouterAcl
import com.vknxworks.wrtctrl.ui.components.Group
import com.vknxworks.wrtctrl.ui.components.GroupRow
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import com.vknxworks.wrtctrl.ui.theme.Health

/** One thing the app can do, whether this login's rpcd ACL allows it, and what it needs (shown as a hint). */
private class Capability(@StringRes val label: Int, val needs: String, val allowed: (RouterAcl) -> Boolean)

/** What WrtCtrl looks for in the login's ACL (see the README's least-privilege setup). */
private val capabilities = listOf(
    Capability(R.string.cap_status, "system.info") { it.canCall("system", "info") && it.canCall("network.interface", "dump") },
    Capability(R.string.cap_clients, "luci-rpc.getHostHints") { it.canCall("luci-rpc", "getHostHints") && it.canCall("luci-rpc", "getDHCPLeases") },
    Capability(R.string.cap_dhcp, "uci dhcp: write") { it.canApply && it.canWriteUci("dhcp") },
    Capability(R.string.cap_firewall, "uci firewall: write") { it.canApply && it.canWriteUci("firewall") },
    Capability(R.string.cap_interfaces, "network.interface.*: up, down") { it.canCall("network.interface.wan", "up") && it.canCall("network.interface.wan", "down") },
    Capability(R.string.cap_services, "rc.init") { it.canCall("rc", "init") },
    Capability(R.string.cap_logs, "log.read") { it.canCall("log", "read") },
    Capability(R.string.cap_packages, "package-manager-call") { it.canUpgradePackages },
    Capability(R.string.cap_reboot, "system.reboot") { it.canCall("system", "reboot") },
)

/** Lists what the signed-in login is allowed to do, with what each item needs. */
@Composable
fun AccessSummary(acl: RouterAcl) {
    val nothing = capabilities.none { it.allowed(acl) }
    Group(stringResource(R.string.access_title), footer = stringResource(if (nothing) R.string.access_none_warning else R.string.access_footer)) {
        capabilities.forEach { cap ->
            val ok = cap.allowed(acl)
            GroupRow(
                title = stringResource(cap.label),
                subtitle = "${stringResource(if (ok) R.string.allowed else R.string.not_allowed)}  ·  ${cap.needs}",
                titleColor = if (ok) CtrlColors.Text else CtrlColors.Muted,
                trailing = {
                    if (ok) Icon(Icons.Outlined.CheckCircle, null, tint = Health.ok)
                    else Icon(Icons.Outlined.Block, null, tint = CtrlColors.Muted)
                },
            )
        }
    }
}
