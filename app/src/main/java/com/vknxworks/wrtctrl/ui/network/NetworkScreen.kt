package com.vknxworks.wrtctrl.ui.network

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NetworkPing
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.container
import com.vknxworks.wrtctrl.data.api.UbusClient
import com.vknxworks.wrtctrl.data.api.WifiIface
import com.vknxworks.wrtctrl.data.api.features
import com.vknxworks.wrtctrl.data.api.uciSet
import com.vknxworks.wrtctrl.data.api.wireless
import com.vknxworks.wrtctrl.ui.components.ADCTRL
import com.vknxworks.wrtctrl.ui.components.ErrorCard
import com.vknxworks.wrtctrl.ui.components.isInstalled
import com.vknxworks.wrtctrl.ui.components.openAdCtrl
import com.vknxworks.wrtctrl.ui.components.Group
import com.vknxworks.wrtctrl.ui.components.GroupRow
import com.vknxworks.wrtctrl.ui.components.GroupSwitch
import com.vknxworks.wrtctrl.ui.components.LocalBottomClearance
import com.vknxworks.wrtctrl.ui.components.TabTitle
import com.vknxworks.wrtctrl.ui.components.activeRouter
import com.vknxworks.wrtctrl.ui.components.rememberRouterData
import com.vknxworks.wrtctrl.ui.nav.DdnsRoute
import com.vknxworks.wrtctrl.ui.nav.DhcpRoute
import com.vknxworks.wrtctrl.ui.nav.DiagnosticsRoute
import com.vknxworks.wrtctrl.ui.nav.FirewallRoute
import com.vknxworks.wrtctrl.ui.nav.HostnamesRoute
import com.vknxworks.wrtctrl.ui.nav.MultiWanRoute
import com.vknxworks.wrtctrl.ui.nav.TailscaleRoute
import com.vknxworks.wrtctrl.ui.nav.WireGuardRoute
import com.vknxworks.wrtctrl.ui.nav.InterfacesRoute
import com.vknxworks.wrtctrl.ui.nav.LocalNavigator
import com.vknxworks.wrtctrl.ui.nav.PortForwardsRoute
import com.vknxworks.wrtctrl.ui.nav.WifiRoute
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import com.vknxworks.wrtctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

/** The guest Wi-Fi networks on a router (an interface called "guest", or "guest" in the SSID). */
suspend fun UbusClient.guestNetworks(): List<WifiIface> =
    runCatching { wireless() }.getOrDefault(emptyList()).flatMap { it.ifaces }.filter { it.isGuest }

/** Switches every guest Wi-Fi network on or off, applied with rollback. Returns true when kept. */
suspend fun switchGuest(context: Context, client: UbusClient, guests: List<WifiIface>, on: Boolean): Boolean {
    val what = context.getString(if (on) R.string.change_guest_on else R.string.change_guest_off)
    return context.container.changes.apply(client, what) {
        guests.forEach { uciSet("wireless", it.section, mapOf("disabled" to if (on) "0" else "1")) }
    }
}

/** The Network tab: guest Wi-Fi switch, then Wi-Fi, DHCP, port forwards, interfaces, firewall, hostnames and diagnostics, and whatever else the router has (Tailscale, WireGuard, dynamic DNS, mwan3, AdGuard Home). */
@Composable
fun NetworkScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val active = activeRouter()
    val router = active?.router
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val acl = access.acl
    val wifi = rememberRouterData { client -> if (client.acl?.canCall("luci-rpc", "getWirelessDevices") != false) client.wireless() else emptyList() }
    val radios = wifi.value.orEmpty()
    val features = rememberRouterData { client -> client.features() }
    val adCtrlInstalled = remember { isInstalled(context, ADCTRL) }
    val guests = radios.flatMap { it.ifaces }.filter { it.isGuest }
    val canWifi = router?.readOnly == false && acl.canApply && acl.canWriteUci("wireless")

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_network))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomClearance.current + 16.dp)) {
            wifi.error?.let { e -> item("err") { ErrorCard(e, router?.host.orEmpty(), onRetry = { wifi.refresh() }) } }
            if (guests.isNotEmpty()) {
                item("guest") {
                    val on = guests.any { !it.disabled }
                    Group(stringResource(R.string.guest_wifi), footer = stringResource(R.string.guest_wifi_footer)) {
                        GroupSwitch(
                            stringResource(R.string.guest_wifi),
                            guests.joinToString(", ") { it.ssid.ifBlank { it.section } } + if (!canWifi) "\n" + stringResource(R.string.not_allowed_here) else "",
                            checked = on,
                            icon = Icons.Outlined.WifiTethering,
                        ) { want ->
                            val client = active?.client ?: return@GroupSwitch
                            if (!canWifi) return@GroupSwitch
                            scope.launch {
                                switchGuest(context, client, guests, want)
                                wifi.refresh()
                            }
                        }
                    }
                }
            }
            item("sections") {
                Group {
                    GroupRow(
                        stringResource(R.string.net_wifi),
                        if (radios.isEmpty()) stringResource(R.string.net_wifi_none) else stringResource(R.string.net_wifi_sub, radios.size, radios.sumOf { it.ifaces.size }),
                        icon = Icons.Outlined.Wifi,
                        showChevron = true,
                        onClick = { nav.navigate(WifiRoute) },
                    )
                    GroupRow(stringResource(R.string.net_dhcp), stringResource(R.string.net_dhcp_sub), icon = Icons.Outlined.Dns, showChevron = true, onClick = { nav.navigate(DhcpRoute) })
                    GroupRow(stringResource(R.string.net_forwards), stringResource(R.string.net_forwards_sub), icon = Icons.Outlined.Router, showChevron = true, onClick = { nav.navigate(PortForwardsRoute) })
                    GroupRow(stringResource(R.string.net_interfaces), stringResource(R.string.net_interfaces_sub), icon = Icons.Outlined.Lan, showChevron = true, onClick = { nav.navigate(InterfacesRoute) })
                    GroupRow(stringResource(R.string.net_firewall), stringResource(R.string.net_firewall_sub), icon = Icons.Outlined.Security, showChevron = true, onClick = { nav.navigate(FirewallRoute) })
                    GroupRow(stringResource(R.string.net_hostnames), stringResource(R.string.net_hostnames_sub), icon = Icons.Outlined.Label, showChevron = true, onClick = { nav.navigate(HostnamesRoute) })
                    GroupRow(stringResource(R.string.net_diag), stringResource(R.string.net_diag_sub), icon = Icons.Outlined.NetworkPing, showChevron = true, onClick = { nav.navigate(DiagnosticsRoute) })
                }
            }
            // Only what the router has: Tailscale, WireGuard, dynamic DNS and mwan3 each get a row when found.
            val found = features.value
            if (found != null && (found.tailscale || found.wireguard.isNotEmpty() || found.ddns || found.mwan3)) {
                item("extras") {
                    Group(stringResource(R.string.net_extras)) {
                        if (found.tailscale) GroupRow(stringResource(R.string.net_tailscale), stringResource(R.string.net_tailscale_sub), icon = Icons.Outlined.Public, showChevron = true, onClick = { nav.navigate(TailscaleRoute) })
                        if (found.wireguard.isNotEmpty()) GroupRow(stringResource(R.string.net_wireguard), found.wireguard.joinToString(", "), icon = Icons.Outlined.VpnKey, showChevron = true, onClick = { nav.navigate(WireGuardRoute) })
                        if (found.ddns) GroupRow(stringResource(R.string.net_ddns), stringResource(R.string.net_ddns_sub), icon = Icons.Outlined.Language, showChevron = true, onClick = { nav.navigate(DdnsRoute) })
                        if (found.mwan3) GroupRow(stringResource(R.string.net_mwan), stringResource(R.string.net_mwan_sub), icon = Icons.Outlined.AltRoute, showChevron = true, onClick = { nav.navigate(MultiWanRoute) })
                    }
                }
            }
            // AdGuard Home on this router, and AdCtrl on this phone: DNS filtering is AdCtrl's job.
            if (found?.adguardHome == true && adCtrlInstalled) {
                item("dns") {
                    Group(stringResource(R.string.net_dns), footer = stringResource(R.string.net_dns_footer)) {
                        GroupRow(
                            stringResource(R.string.client_open_adctrl),
                            stringResource(R.string.net_open_adctrl_sub),
                            icon = Icons.Outlined.Shield,
                            trailing = { Icon(Icons.Outlined.OpenInNew, null, tint = CtrlColors.Muted) },
                            onClick = { openAdCtrl(context) },
                        )
                    }
                }
            }
            item("note") {
                Text(
                    stringResource(R.string.net_rollback_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = CtrlColors.Muted,
                    modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp),
                )
            }
        }
    }
}
