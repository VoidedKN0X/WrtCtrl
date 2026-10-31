package com.vknxworks.wrtctrl.ui.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material3.FilterChip
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
import com.vknxworks.wrtctrl.data.api.UciSection
import com.vknxworks.wrtctrl.data.api.uciAdd
import com.vknxworks.wrtctrl.data.api.uciDelete
import com.vknxworks.wrtctrl.data.api.uciGet
import com.vknxworks.wrtctrl.data.api.uciSet
import com.vknxworks.wrtctrl.ui.clients.isIpv4
import com.vknxworks.wrtctrl.ui.components.ConfirmDialog
import com.vknxworks.wrtctrl.ui.components.CtrlDialog
import com.vknxworks.wrtctrl.ui.components.CtrlTextField
import com.vknxworks.wrtctrl.ui.components.EmptyState
import com.vknxworks.wrtctrl.ui.components.ErrorCard
import com.vknxworks.wrtctrl.ui.components.Group
import com.vknxworks.wrtctrl.ui.components.GroupRow
import com.vknxworks.wrtctrl.ui.components.GroupSwitch
import com.vknxworks.wrtctrl.ui.components.ScreenTopBar
import com.vknxworks.wrtctrl.ui.components.activeRouter
import com.vknxworks.wrtctrl.ui.components.rememberRouterData
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import kotlinx.coroutines.launch

/** A port forward (firewall redirect) as the form edits it. */
data class Forward(
    val section: UciSection?,
    val name: String,
    val proto: String,
    val externalPort: String,
    val internalIp: String,
    val internalPort: String,
)

/** A port (1–65535) or a range like 8000-8010. */
fun isPortSpec(s: String): Boolean {
    val parts = s.trim().split('-')
    if (parts.size !in 1..2) return false
    val nums = parts.map { it.trim().toIntOrNull() ?: return false }
    if (nums.any { it !in 1..65535 }) return false
    return nums.size == 1 || nums[0] <= nums[1]
}

private val PROTOS = listOf("tcp udp", "tcp", "udp")

@Composable
private fun protoLabel(p: String): String = when (p) {
    "tcp udp", "tcpudp" -> stringResource(R.string.proto_tcp_udp)
    "tcp" -> "TCP"
    "udp" -> "UDP"
    else -> p.uppercase()
}

/** Port forwards (firewall redirects from the WAN): list, add, edit, delete, enable or disable. */
@Composable
fun PortForwardsScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val active = activeRouter()
    val router = active?.router
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val canEdit = router?.readOnly == false && access.acl.canApply && access.acl.canWriteUci("firewall")
    var editing by remember { mutableStateOf<Forward?>(null) }
    var deleting by remember { mutableStateOf<UciSection?>(null) }

    val data = rememberRouterData { client -> client.uciGet("firewall", "redirect") }
    val list = data.value.orEmpty()

    fun applyChange(what: String, stage: suspend com.vknxworks.wrtctrl.data.api.UbusClient.() -> Unit) {
        val client = active?.client ?: return
        scope.launch {
            c.changes.apply(client, what, stage = stage)
            data.refresh()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.net_forwards)) {
            if (canEdit) IconButton(onClick = { editing = Forward(null, "", "tcp udp", "", "", "") }) { Icon(Icons.Outlined.Add, stringResource(R.string.forward_add)) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            data.error?.let { ErrorCard(it, router?.host.orEmpty(), onRetry = { data.refresh() }) }
            if (data.value != null && list.isEmpty()) {
                EmptyState(Icons.Outlined.Router, stringResource(R.string.forwards_empty_title), stringResource(R.string.forwards_empty_body))
            }
            list.forEach { r ->
                val name = r.opt("name").ifBlank { r.name }
                val enabled = r.flag("enabled", true)
                val target = r.opt("dest_ip").ifBlank { stringResource(R.string.forward_router_itself) } + r.opt("dest_port").ifBlank { r.opt("src_dport") }.let { if (it.isBlank()) "" else ":$it" }
                Group(name) {
                    GroupSwitch(
                        stringResource(R.string.forward_summary, r.opt("src_dport").ifBlank { "*" }, target),
                        listOf(protoLabel(r.opt("proto", "tcp udp")), stringResource(R.string.forward_zones, r.opt("src", "wan"), r.opt("dest", "lan"))).joinToString(" · "),
                        checked = enabled,
                    ) { on ->
                        if (canEdit) applyChange(context.getString(if (on) R.string.change_forward_on else R.string.change_forward_off, name)) {
                            uciSet("firewall", r.name, mapOf("enabled" to if (on) "1" else "0"))
                        }
                    }
                    if (canEdit) {
                        GroupRow(stringResource(R.string.forward_edit), icon = Icons.Outlined.Edit, showChevron = true, onClick = {
                            editing = Forward(r, r.opt("name"), r.opt("proto", "tcp udp"), r.opt("src_dport"), r.opt("dest_ip"), r.opt("dest_port"))
                        })
                        GroupRow(stringResource(R.string.forward_delete), icon = Icons.Outlined.Delete, iconTint = CtrlColors.Error, titleColor = CtrlColors.Error, onClick = { deleting = r })
                    }
                }
            }
            Text(
                stringResource(if (canEdit) R.string.forwards_footer else R.string.not_allowed_here),
                style = MaterialTheme.typography.bodySmall,
                color = CtrlColors.Muted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }

    editing?.let { f ->
        ForwardDialog(f, onDismiss = { editing = null }) { out ->
            editing = null
            val values = mapOf(
                "name" to out.name,
                "src" to "wan",
                "src_dport" to out.externalPort,
                "dest" to "lan",
                "dest_ip" to out.internalIp,
                "dest_port" to out.internalPort.ifBlank { out.externalPort },
                "proto" to out.proto,
                "target" to "DNAT",
            )
            val section = f.section
            applyChange(context.getString(if (section == null) R.string.change_forward_added else R.string.change_forward_changed, out.name)) {
                if (section == null) uciAdd("firewall", "redirect", values + ("enabled" to "1")) else uciSet("firewall", section.name, values)
            }
        }
    }
    deleting?.let { r ->
        val name = r.opt("name").ifBlank { r.name }
        ConfirmDialog(
            title = stringResource(R.string.forward_delete),
            body = stringResource(R.string.forward_delete_body, name),
            confirmLabel = stringResource(R.string.remove),
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                applyChange(context.getString(R.string.change_forward_removed, name)) { uciDelete("firewall", r.name) }
            },
        )
    }
}

@Composable
private fun ForwardDialog(f: Forward, onDismiss: () -> Unit, onSave: (Forward) -> Unit) {
    var name by remember { mutableStateOf(f.name) }
    var proto by remember { mutableStateOf(if (f.proto == "tcpudp") "tcp udp" else f.proto) }
    var ext by remember { mutableStateOf(f.externalPort) }
    var ip by remember { mutableStateOf(f.internalIp) }
    var port by remember { mutableStateOf(f.internalPort) }
    val nameOk = name.isNotBlank() && name.length <= 64
    val extOk = isPortSpec(ext)
    val ipOk = isIpv4(ip)
    val portOk = port.isBlank() || isPortSpec(port)
    CtrlDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Router, null) },
        title = { Text(stringResource(if (f.section == null) R.string.forward_add else R.string.forward_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CtrlTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.field_name_plain)) }, isError = name.isNotEmpty() && !nameOk, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PROTOS.forEach { p -> FilterChip(selected = proto == p, onClick = { proto = p }, label = { Text(protoLabel(p)) }) }
                }
                CtrlTextField(value = ext, onValueChange = { ext = it.trim() }, label = { Text(stringResource(R.string.field_external_port)) }, isError = ext.isNotEmpty() && !extOk, supportingText = { Text(stringResource(R.string.port_hint)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.fillMaxWidth())
                CtrlTextField(value = ip, onValueChange = { ip = it.trim() }, label = { Text(stringResource(R.string.field_internal_ip)) }, isError = ip.isNotEmpty() && !ipOk, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                CtrlTextField(value = port, onValueChange = { port = it.trim() }, label = { Text(stringResource(R.string.field_internal_port)) }, isError = !portOk, supportingText = { Text(stringResource(R.string.internal_port_hint)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(Forward(f.section, name.trim(), proto, ext.trim(), ip.trim(), port.trim())) }, enabled = nameOk && extOk && ipOk && portOk) {
                Text(stringResource(R.string.save_changes))
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
