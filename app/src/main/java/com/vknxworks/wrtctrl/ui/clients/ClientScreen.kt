package com.vknxworks.wrtctrl.ui.clients

import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.vknxworks.wrtctrl.data.api.OptionalPackage
import com.vknxworks.wrtctrl.data.api.deauth
import com.vknxworks.wrtctrl.data.api.installedOptional
import com.vknxworks.wrtctrl.data.api.usageByMac
import com.vknxworks.wrtctrl.data.api.wake
import com.vknxworks.wrtctrl.data.lock.AppLock
import com.vknxworks.wrtctrl.data.router.ClientNote
import com.vknxworks.wrtctrl.data.router.ClientNotes
import com.vknxworks.wrtctrl.data.router.Link
import com.vknxworks.wrtctrl.data.router.NetClient
import com.vknxworks.wrtctrl.data.router.loadClients
import com.vknxworks.wrtctrl.data.router.stageBlock
import com.vknxworks.wrtctrl.data.router.stageRemoveStaticLease
import com.vknxworks.wrtctrl.data.router.stageStaticLease
import com.vknxworks.wrtctrl.data.router.stageUnblock
import com.vknxworks.wrtctrl.ui.components.ChartSeries
import com.vknxworks.wrtctrl.ui.components.ConfirmDialog
import com.vknxworks.wrtctrl.ui.components.CtrlCard
import com.vknxworks.wrtctrl.ui.components.CtrlDialog
import com.vknxworks.wrtctrl.ui.components.CtrlSectionLabel
import com.vknxworks.wrtctrl.ui.components.CtrlTextField
import com.vknxworks.wrtctrl.ui.components.ErrorCard
import com.vknxworks.wrtctrl.ui.components.Group
import com.vknxworks.wrtctrl.ui.components.GroupRow
import com.vknxworks.wrtctrl.ui.components.ScreenTopBar
import com.vknxworks.wrtctrl.ui.components.Sparkline
import com.vknxworks.wrtctrl.ui.components.activeRouter
import com.vknxworks.wrtctrl.ui.components.bandLabel
import com.vknxworks.wrtctrl.ui.components.errorMessage
import com.vknxworks.wrtctrl.ui.components.formatBytes
import com.vknxworks.wrtctrl.ui.components.formatRate
import com.vknxworks.wrtctrl.ui.components.formatUptime
import com.vknxworks.wrtctrl.ui.components.rememberRouterData
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import com.vknxworks.wrtctrl.ui.theme.CtrlType
import com.vknxworks.wrtctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

/** AdCtrl's package: "Open in AdCtrl" shows when it's installed. */
private const val ADCTRL = "com.vknxworks.adctrl"

private data class SignalPoint(val time: Long, val signal: Double, val rate: Double)

/** One device: addresses, connection, data use, and the actions the login allows. */
@Composable
fun ClientScreen(mac: String) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val active = activeRouter()
    val router = active?.router
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val acl = access.acl
    val readOnly = router?.readOnly != false
    val history = remember(mac) { mutableStateListOf<SignalPoint>() }

    val optional = rememberRouterData { client -> runCatching { client.installedOptional() }.getOrDefault(emptySet()) }
    val hasNlbw = optional.value?.contains(OptionalPackage.NLBWMON) == true
    val data = rememberRouterData(3_000, hasNlbw) { client ->
        val usage = if (hasNlbw) runCatching { client.usageByMac() }.getOrDefault(emptyMap()) else emptyMap()
        val list = loadClients(context, client, router?.id.orEmpty(), usage)
        val me = list.firstOrNull { it.mac == mac }
        (me?.link as? Link.Wifi)?.let { w ->
            history.add(SignalPoint(System.currentTimeMillis() / 1000, w.signal.toDouble(), (w.rxRate * 1000).toDouble()))
            if (history.size > 120) history.removeAt(0)
        }
        me
    }
    val cl = data.value

    var staticDialog by remember { mutableStateOf(false) }
    var notesDialog by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var confirmDeauth by remember { mutableStateOf(false) }
    var confirmUnstatic by remember { mutableStateOf(false) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(cl?.name?.ifBlank { null } ?: cl?.vendor ?: mac)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            data.error?.let { ErrorCard(it, router?.host.orEmpty(), onRetry = { data.refresh() }) }
            if (cl == null) {
                if (data.value == null && !data.loading && data.error == null) {
                    Text(stringResource(R.string.client_gone), color = CtrlColors.Muted, modifier = Modifier.padding(Gutter))
                }
                return@Column
            }
            ClientInfo(cl)

            if (history.size >= 2) {
                CtrlCard(Modifier.padding(horizontal = Gutter, vertical = 6.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Sparkline(stringResource(R.string.client_signal), listOf(ChartSeries(stringResource(R.string.client_signal), history.map { it.signal })), format = { "${it.toInt()} dBm" })
                        Sparkline(stringResource(R.string.client_rate), listOf(ChartSeries(stringResource(R.string.client_rate), history.map { it.rate })), format = ::formatRate)
                    }
                }
            }
            cl.usage?.let { u ->
                Group(stringResource(R.string.client_usage), footer = stringResource(R.string.client_usage_footer)) {
                    GroupRow(stringResource(R.string.rate_down), formatBytes(u.rxBytes))
                    GroupRow(stringResource(R.string.rate_up), formatBytes(u.txBytes))
                    GroupRow(stringResource(R.string.client_connections), u.connections.toString())
                }
            }

            Group(stringResource(R.string.client_actions)) {
                val canDhcp = !readOnly && acl.canApply && acl.canWriteUci("dhcp")
                val canFw = !readOnly && acl.canApply && acl.canWriteUci("firewall")
                if (cl.isStatic) {
                    GroupRow(stringResource(R.string.client_static_edit), cl.staticLease?.opt("ip"), icon = Icons.Outlined.PushPin, showChevron = canDhcp, onClick = if (canDhcp) ({ staticDialog = true }) else null, titleColor = if (canDhcp) CtrlColors.Text else CtrlColors.Muted)
                    GroupRow(stringResource(R.string.client_static_remove), icon = Icons.Outlined.PushPin, onClick = if (canDhcp) ({ confirmUnstatic = true }) else null, titleColor = if (canDhcp) CtrlColors.Text else CtrlColors.Muted)
                } else {
                    GroupRow(stringResource(R.string.client_make_static), stringResource(R.string.client_make_static_sub), icon = Icons.Outlined.PushPin, showChevron = canDhcp, onClick = if (canDhcp) ({ staticDialog = true }) else null, titleColor = if (canDhcp) CtrlColors.Text else CtrlColors.Muted)
                }
                val wifi = cl.link as? Link.Wifi
                if (wifi != null) {
                    val canDeauth = !readOnly && acl.canCall("hostapd.${wifi.ifname}", "del_client")
                    GroupRow(stringResource(R.string.client_deauth), stringResource(R.string.client_deauth_sub), icon = Icons.Outlined.LinkOff, onClick = if (canDeauth) ({ confirmDeauth = true }) else null, titleColor = if (canDeauth) CtrlColors.Text else CtrlColors.Muted)
                }
                GroupRow(
                    stringResource(if (cl.blocked) R.string.client_unblock else R.string.client_block),
                    stringResource(if (cl.blocked) R.string.client_unblock_sub else R.string.client_block_sub),
                    icon = Icons.Outlined.Block,
                    iconTint = if (cl.blocked) CtrlColors.Muted else CtrlColors.Error,
                    titleColor = if (!canFw) CtrlColors.Muted else if (cl.blocked) CtrlColors.Text else CtrlColors.Error,
                    onClick = if (canFw) ({ confirmBlock = true }) else null,
                )
                if (optional.value?.contains(OptionalPackage.ETHERWAKE) == true) {
                    val canWake = !readOnly && acl.canExec("/usr/bin/etherwake -D -i br-lan ${cl.mac}")
                    GroupRow(stringResource(R.string.client_wake), stringResource(R.string.client_wake_sub), icon = Icons.Outlined.PowerSettingsNew, titleColor = if (canWake) CtrlColors.Text else CtrlColors.Muted, onClick = if (canWake) ({
                        active?.client?.let { client ->
                            scope.launch {
                                runCatching { client.wake(cl.mac, cl.dev.ifBlank { "br-lan" }) }
                                    .onSuccess { toast(context.getString(R.string.client_wake_sent)) }
                                    .onFailure { toast(errorMessage(context, it, router?.host.orEmpty())) }
                            }
                        }
                    }) else null)
                }
                GroupRow(stringResource(R.string.client_notes), cl.note.ifBlank { null } ?: stringResource(R.string.client_notes_sub), icon = Icons.Outlined.Edit, showChevron = true, onClick = { notesDialog = true })
                if (isInstalled(context, ADCTRL)) {
                    GroupRow(stringResource(R.string.client_open_adctrl), stringResource(R.string.client_open_adctrl_sub), icon = Icons.Outlined.Shield, trailing = { androidx.compose.material3.Icon(Icons.Outlined.OpenInNew, null, tint = CtrlColors.Muted) }, onClick = {
                        context.packageManager.getLaunchIntentForPackage(ADCTRL)?.let { intent ->
                            AppLock.expectReturn()
                            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                    })
                }
            }
            if (readOnly) {
                Text(stringResource(R.string.read_only_actions_hidden), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, modifier = Modifier.padding(horizontal = Gutter + 4.dp))
            }
        }
    }

    val client = active?.client
    if (cl != null && client != null) {
        val label = cl.name.ifBlank { cl.vendor ?: cl.mac }
        if (staticDialog) {
            StaticLeaseDialog(
                initialIp = cl.staticLease?.opt("ip")?.ifBlank { null } ?: cl.ipv4.firstOrNull().orEmpty(),
                initialName = cl.staticLease?.opt("name")?.ifBlank { null } ?: cl.hostname,
                onDismiss = { staticDialog = false },
            ) { ip, name ->
                staticDialog = false
                scope.launch {
                    c.changes.apply(client, context.getString(R.string.change_static_lease, label)) { stageStaticLease(cl.mac, ip, name, cl.staticLease) }
                    data.refresh()
                }
            }
        }
        if (confirmUnstatic) {
            ConfirmDialog(
                title = stringResource(R.string.client_static_remove),
                body = stringResource(R.string.client_static_remove_body, label),
                confirmLabel = stringResource(R.string.remove),
                onDismiss = { confirmUnstatic = false },
                onConfirm = {
                    confirmUnstatic = false
                    val lease = cl.staticLease ?: return@ConfirmDialog
                    scope.launch {
                        c.changes.apply(client, context.getString(R.string.change_static_lease_removed, label)) { stageRemoveStaticLease(lease) }
                        data.refresh()
                    }
                },
            )
        }
        if (confirmBlock) {
            ConfirmDialog(
                title = stringResource(if (cl.blocked) R.string.client_unblock else R.string.client_block),
                body = stringResource(if (cl.blocked) R.string.client_unblock_body else R.string.client_block_body, label),
                confirmLabel = stringResource(if (cl.blocked) R.string.client_unblock else R.string.client_block),
                danger = !cl.blocked,
                onDismiss = { confirmBlock = false },
                onConfirm = {
                    confirmBlock = false
                    scope.launch {
                        val rule = cl.blockRule
                        if (rule != null) {
                            c.changes.apply(client, context.getString(R.string.change_unblock, label)) { stageUnblock(rule) }
                        } else {
                            c.changes.apply(client, context.getString(R.string.change_block, label)) { stageBlock(cl.mac, label) }
                        }
                        data.refresh()
                    }
                },
            )
        }
        if (confirmDeauth) {
            val wifi = cl.link as? Link.Wifi
            ConfirmDialog(
                title = stringResource(R.string.client_deauth),
                body = stringResource(R.string.client_deauth_body, label),
                confirmLabel = stringResource(R.string.client_deauth_confirm),
                onDismiss = { confirmDeauth = false },
                onConfirm = {
                    confirmDeauth = false
                    if (wifi == null) return@ConfirmDialog
                    scope.launch {
                        runCatching { client.deauth(wifi.ifname, cl.mac) }
                            .onSuccess { toast(context.getString(R.string.client_deauth_done, label)) }
                            .onFailure { toast(errorMessage(context, it, router?.host.orEmpty())) }
                        data.refresh()
                    }
                },
            )
        }
        if (notesDialog) {
            NotesDialog(cl, onDismiss = { notesDialog = false }) { note ->
                notesDialog = false
                ClientNotes.set(context, router?.id.orEmpty(), cl.mac, note)
                scope.launch { data.refresh() }
            }
        }
    }
}

private fun isInstalled(context: Context, pkg: String): Boolean =
    runCatching { context.packageManager.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)

@Composable
private fun ClientInfo(cl: NetClient) {
    CtrlCard(Modifier.padding(horizontal = Gutter, vertical = 6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CtrlSectionLabel(
                when (val l = cl.link) {
                    is Link.Wifi -> stringResource(R.string.client_on_wifi, bandLabel(l.band), l.ssid)
                    Link.Wired -> stringResource(R.string.client_on_wired)
                    Link.Offline -> stringResource(R.string.state_offline)
                },
                color = if (cl.online) CtrlColors.AccentText else CtrlColors.Muted,
            )
            Text(cl.ipv4.firstOrNull() ?: cl.ipv6.firstOrNull() ?: "—", style = CtrlType.Readout)
            val lines = listOfNotNull(
                stringResource(R.string.client_mac, cl.mac),
                cl.vendor?.let { stringResource(R.string.client_vendor, it) } ?: if (cl.randomisedMac) stringResource(R.string.client_private_mac_long) else null,
                cl.hostname.ifBlank { null }?.let { stringResource(R.string.client_hostname, it) },
                cl.ipv4.drop(1).takeIf { it.isNotEmpty() }?.let { stringResource(R.string.client_more_ipv4, it.joinToString(", ")) },
                cl.ipv6.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.client_ipv6, it.joinToString("\n")) },
                (cl.link as? Link.Wifi)?.let { stringResource(R.string.client_wifi_detail, it.signal, formatRate(it.rxRate * 1000.0), formatRate(it.txRate * 1000.0)) },
                when {
                    cl.isStatic -> stringResource(R.string.client_lease_static)
                    cl.leaseExpires == null -> null
                    cl.leaseExpires <= 0 -> stringResource(R.string.client_lease_infinite)
                    else -> stringResource(R.string.client_lease_expires, formatUptime(cl.leaseExpires))
                },
                if (cl.blocked) stringResource(R.string.client_is_blocked) else null,
            )
            Text(lines.joinToString("\n"), style = CtrlType.Mono, color = CtrlColors.Muted)
            if (cl.note.isNotBlank()) Text(cl.note, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Valid dotted IPv4 address. */
fun isIpv4(s: String): Boolean {
    val p = s.trim().split('.')
    return p.size == 4 && p.all { part -> part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) && part.toInt() in 0..255 }
}

/** A hostname dnsmasq accepts: letters, digits and dashes, up to 63 characters. */
fun isHostname(s: String): Boolean = s.isEmpty() || Regex("^[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?$").matches(s)

@Composable
fun StaticLeaseDialog(initialIp: String, initialName: String, onDismiss: () -> Unit, onSave: (ip: String, name: String) -> Unit) {
    var ip by remember { mutableStateOf(initialIp) }
    var name by remember { mutableStateOf(initialName.replace(Regex("[^A-Za-z0-9-]"), "-").trim('-')) }
    val ipOk = isIpv4(ip)
    val nameOk = isHostname(name.trim())
    CtrlDialog(
        onDismissRequest = onDismiss,
        icon = { androidx.compose.material3.Icon(Icons.Outlined.PushPin, null) },
        title = { Text(stringResource(R.string.static_lease_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.static_lease_body), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
                CtrlTextField(
                    value = ip,
                    onValueChange = { ip = it.trim() },
                    label = { Text(stringResource(R.string.field_ip)) },
                    isError = ip.isNotEmpty() && !ipOk,
                    supportingText = if (ip.isNotEmpty() && !ipOk) ({ Text(stringResource(R.string.invalid_ip)) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                CtrlTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_hostname)) },
                    isError = !nameOk,
                    supportingText = if (!nameOk) ({ Text(stringResource(R.string.invalid_hostname)) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(ip.trim(), name.trim()) }, enabled = ipOk && nameOk) { Text(stringResource(R.string.save_changes)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun NotesDialog(cl: NetClient, onDismiss: () -> Unit, onSave: (ClientNote) -> Unit) {
    var nickname by remember { mutableStateOf(cl.nickname) }
    var note by remember { mutableStateOf(cl.note) }
    CtrlDialog(
        onDismissRequest = onDismiss,
        icon = { androidx.compose.material3.Icon(Icons.Outlined.Edit, null) },
        title = { Text(stringResource(R.string.client_notes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.client_notes_body), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
                CtrlTextField(value = nickname, onValueChange = { nickname = it }, label = { Text(stringResource(R.string.field_nickname)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                CtrlTextField(value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.field_note)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(ClientNote(nickname.trim(), note.trim())) }) { Text(stringResource(R.string.save_changes)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
