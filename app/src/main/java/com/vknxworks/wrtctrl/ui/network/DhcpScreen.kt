package com.vknxworks.wrtctrl.ui.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.container
import com.vknxworks.wrtctrl.data.api.DhcpLease
import com.vknxworks.wrtctrl.data.api.UciSection
import com.vknxworks.wrtctrl.data.api.dhcpLeases
import com.vknxworks.wrtctrl.data.api.normMac
import com.vknxworks.wrtctrl.data.api.uciGet
import com.vknxworks.wrtctrl.data.router.Oui
import com.vknxworks.wrtctrl.data.router.stageRemoveStaticLease
import com.vknxworks.wrtctrl.data.router.stageStaticLease
import com.vknxworks.wrtctrl.ui.clients.isHostname
import com.vknxworks.wrtctrl.ui.clients.isIpv4
import com.vknxworks.wrtctrl.ui.components.ConfirmDialog
import com.vknxworks.wrtctrl.ui.components.CtrlDialog
import com.vknxworks.wrtctrl.ui.components.CtrlTextField
import com.vknxworks.wrtctrl.ui.components.ErrorCard
import com.vknxworks.wrtctrl.ui.components.Group
import com.vknxworks.wrtctrl.ui.components.GroupRow
import com.vknxworks.wrtctrl.ui.components.ScreenTopBar
import com.vknxworks.wrtctrl.ui.components.activeRouter
import com.vknxworks.wrtctrl.ui.components.formatUptime
import com.vknxworks.wrtctrl.ui.components.rememberRouterData
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import kotlinx.coroutines.launch

private data class DhcpData(val leases: List<DhcpLease>, val hosts: List<UciSection>, val pools: List<UciSection>)

/** A static lease being added (null section) or edited. */
private data class HostEdit(val section: UciSection?, val mac: String, val ip: String, val name: String)

/** DHCP: active leases, static leases (add, edit, delete) and the address pools. */
@Composable
fun DhcpScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val active = activeRouter()
    val router = active?.router
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val canEdit = router?.readOnly == false && access.acl.canApply && access.acl.canWriteUci("dhcp")
    var editing by remember { mutableStateOf<HostEdit?>(null) }
    var deleting by remember { mutableStateOf<UciSection?>(null) }

    val data = rememberRouterData(15_000) { client ->
        DhcpData(
            leases = runCatching { client.dhcpLeases() }.getOrDefault(emptyList()),
            hosts = client.uciGet("dhcp", "host"),
            pools = client.uciGet("dhcp", "dhcp"),
        )
    }
    val d = data.value

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.net_dhcp)) {
            if (canEdit) IconButton(onClick = { editing = HostEdit(null, "", "", "") }) { Icon(Icons.Outlined.Add, stringResource(R.string.dhcp_add_static)) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            data.error?.let { ErrorCard(it, router?.host.orEmpty(), onRetry = { data.refresh() }) }
            if (d == null) return@Column

            Group(stringResource(R.string.dhcp_static), footer = stringResource(if (canEdit) R.string.dhcp_static_footer else R.string.not_allowed_here)) {
                if (d.hosts.isEmpty()) GroupRow(stringResource(R.string.dhcp_static_none))
                d.hosts.forEach { h ->
                    val mac = h.list("mac").joinToString(", ")
                    GroupRow(
                        h.opt("name").ifBlank { mac },
                        listOf(h.opt("ip").ifBlank { "—" }, mac).joinToString(" · "),
                        icon = Icons.Outlined.PushPin,
                        onClick = if (canEdit) ({ editing = HostEdit(h, h.list("mac").firstOrNull().orEmpty(), h.opt("ip"), h.opt("name")) }) else null,
                        trailing = if (canEdit) ({ IconButton(onClick = { deleting = h }) { Icon(Icons.Outlined.Delete, stringResource(R.string.remove), tint = CtrlColors.Error) } }) else null,
                    )
                }
            }

            Group(stringResource(R.string.dhcp_leases, d.leases.size)) {
                if (d.leases.isEmpty()) GroupRow(stringResource(R.string.dhcp_leases_none))
                d.leases.sortedBy { it.hostname.lowercase() }.forEach { l ->
                    val static = d.hosts.any { h -> h.list("mac").any { normMac(it) == l.mac } }
                    GroupRow(
                        l.hostname.ifBlank { Oui.vendor(context, l.mac) ?: l.mac },
                        listOfNotNull(
                            l.ip,
                            l.mac,
                            when {
                                static -> stringResource(R.string.client_lease_static)
                                l.expires <= 0 -> stringResource(R.string.client_lease_infinite)
                                else -> stringResource(R.string.client_lease_expires, formatUptime(l.expires))
                            },
                        ).joinToString(" · "),
                        onClick = if (canEdit && !static && '.' in l.ip) ({ editing = HostEdit(null, l.mac, l.ip, l.hostname) }) else null,
                    )
                }
            }

            d.pools.forEach { p ->
                Group(stringResource(R.string.dhcp_pool, p.opt("interface").ifBlank { p.name })) {
                    if (p.flag("ignore")) {
                        GroupRow(stringResource(R.string.dhcp_pool_off))
                    } else {
                        GroupRow(stringResource(R.string.dhcp_pool_start), p.opt("start", "100"))
                        GroupRow(stringResource(R.string.dhcp_pool_limit), p.opt("limit", "150"))
                        GroupRow(stringResource(R.string.dhcp_pool_lease), p.opt("leasetime", "12h"))
                    }
                    p.opt("dhcpv6").ifBlank { null }?.let { GroupRow(stringResource(R.string.dhcp_pool_v6), it) }
                    p.opt("ra").ifBlank { null }?.let { GroupRow(stringResource(R.string.dhcp_pool_ra), it) }
                }
            }
            Text(stringResource(R.string.dhcp_pool_note), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, modifier = Modifier.padding(horizontal = 24.dp))
        }
    }

    val client = active?.client
    editing?.let { e ->
        HostDialog(e, onDismiss = { editing = null }) { mac, ip, name ->
            editing = null
            if (client == null) return@HostDialog
            scope.launch {
                c.changes.apply(client, context.getString(R.string.change_static_lease, name.ifBlank { mac })) { stageStaticLease(mac, ip, name, e.section) }
                data.refresh()
            }
        }
    }
    deleting?.let { h ->
        ConfirmDialog(
            title = stringResource(R.string.client_static_remove),
            body = stringResource(R.string.client_static_remove_body, h.opt("name").ifBlank { h.list("mac").joinToString() }),
            confirmLabel = stringResource(R.string.remove),
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                if (client == null) return@ConfirmDialog
                scope.launch {
                    c.changes.apply(client, context.getString(R.string.change_static_lease_removed, h.opt("name").ifBlank { h.name })) { stageRemoveStaticLease(h) }
                    data.refresh()
                }
            },
        )
    }
}

@Composable
private fun HostDialog(e: HostEdit, onDismiss: () -> Unit, onSave: (mac: String, ip: String, name: String) -> Unit) {
    var mac by remember { mutableStateOf(e.mac) }
    var ip by remember { mutableStateOf(e.ip) }
    var name by remember { mutableStateOf(e.name.replace(Regex("[^A-Za-z0-9-]"), "-").trim('-')) }
    val macOk = normMac(mac).isNotEmpty()
    val ipOk = isIpv4(ip)
    val nameOk = isHostname(name.trim())
    CtrlDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.PushPin, null) },
        title = { Text(stringResource(if (e.section == null) R.string.dhcp_add_static else R.string.client_static_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CtrlTextField(value = mac, onValueChange = { mac = it.trim() }, label = { Text(stringResource(R.string.field_mac)) }, isError = mac.isNotEmpty() && !macOk, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
                CtrlTextField(value = ip, onValueChange = { ip = it.trim() }, label = { Text(stringResource(R.string.field_ip)) }, isError = ip.isNotEmpty() && !ipOk, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                CtrlTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.field_hostname)) }, isError = !nameOk, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(normMac(mac), ip.trim(), name.trim()) }, enabled = macOk && ipOk && nameOk) { Text(stringResource(R.string.save_changes)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
