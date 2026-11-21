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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.wrtctrl.container
import com.vknxworks.wrtctrl.data.api.UciSection
import com.vknxworks.wrtctrl.data.api.isDnsName
import com.vknxworks.wrtctrl.data.api.isIpv6
import com.vknxworks.wrtctrl.data.api.uciAdd
import com.vknxworks.wrtctrl.data.api.uciDelete
import com.vknxworks.wrtctrl.data.api.uciGet
import com.vknxworks.wrtctrl.data.api.uciSet
import com.vknxworks.wrtctrl.ui.clients.isIpv4
import com.vknxworks.wrtctrl.ui.components.ConfirmDialog
import com.vknxworks.wrtctrl.ui.components.CtrlDialog
import com.vknxworks.wrtctrl.ui.components.CtrlTextField
import com.vknxworks.wrtctrl.ui.components.EmptyState
import kotlinx.coroutines.launch

/** A local hostname being added (null section) or edited. */
private data class NameEdit(val section: UciSection?, val name: String, val ip: String)

/** Local hostnames: the names the router's DNS answers itself (uci `dhcp` `domain` sections), with add, edit and delete. */
@Composable
fun HostnamesScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val active = activeRouter()
    val router = active?.router
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val canEdit = router?.readOnly == false && access.acl.canApply && access.acl.canWriteUci("dhcp")
    var editing by remember { mutableStateOf<NameEdit?>(null) }
    var deleting by remember { mutableStateOf<UciSection?>(null) }

    val data = rememberRouterData { client -> client.uciGet("dhcp", "domain") }
    val list = data.value

    fun applyChange(what: String, stage: suspend com.vknxworks.wrtctrl.data.api.UbusClient.() -> Unit) {
        val client = active?.client ?: return
        scope.launch {
            c.changes.apply(client, what, stage = stage)
            data.refresh()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.net_hostnames)) {
            if (canEdit) IconButton(onClick = { editing = NameEdit(null, "", "") }) { Icon(Icons.Outlined.Add, stringResource(R.string.hostname_add)) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            data.error?.let { ErrorCard(it, router?.host.orEmpty(), onRetry = { data.refresh() }) }
            if (list == null) return@Column
            if (list.isEmpty()) {
                EmptyState(Icons.Outlined.Dns, stringResource(R.string.hostnames_empty_title), stringResource(R.string.hostnames_empty_body))
            } else {
                Group(stringResource(R.string.hostnames_count, list.size)) {
                    list.forEach { h ->
                        GroupRow(
                            h.opt("name").ifBlank { h.name },
                            h.opt("ip").ifBlank { "—" },
                            icon = Icons.Outlined.Dns,
                            onClick = if (canEdit) ({ editing = NameEdit(h, h.opt("name"), h.opt("ip")) }) else null,
                            trailing = if (canEdit) ({ IconButton(onClick = { deleting = h }) { Icon(Icons.Outlined.Delete, stringResource(R.string.remove), tint = CtrlColors.Error) } }) else null,
                        )
                    }
                }
            }
            Text(
                stringResource(if (canEdit) R.string.hostnames_footer else R.string.not_allowed_here),
                style = MaterialTheme.typography.bodySmall,
                color = CtrlColors.Muted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }

    editing?.let { e ->
        var name by remember(e) { mutableStateOf(e.name) }
        var ip by remember(e) { mutableStateOf(e.ip) }
        val nameOk = isDnsName(name.trim())
        val ipOk = isIpv4(ip.trim()) || isIpv6(ip.trim())
        CtrlDialog(
            onDismissRequest = { editing = null },
            icon = { Icon(Icons.Outlined.Dns, null) },
            title = { Text(stringResource(if (e.section == null) R.string.hostname_add else R.string.hostname_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CtrlTextField(value = name, onValueChange = { name = it.trim() }, label = { Text(stringResource(R.string.field_hostname)) }, supportingText = { Text(stringResource(R.string.hostname_name_hint)) }, isError = name.isNotEmpty() && !nameOk, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
                    CtrlTextField(value = ip, onValueChange = { ip = it.trim() }, label = { Text(stringResource(R.string.field_ip)) }, isError = ip.isNotEmpty() && !ipOk, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editing = null
                        val values = mapOf("name" to name.trim(), "ip" to ip.trim())
                        val section = e.section
                        applyChange(context.getString(if (section == null) R.string.change_hostname_added else R.string.change_hostname_changed, name.trim())) {
                            if (section == null) uciAdd("dhcp", "domain", values) else uciSet("dhcp", section.name, values)
                        }
                    },
                    enabled = nameOk && ipOk,
                ) { Text(stringResource(R.string.save_changes)) }
            },
            dismissButton = { TextButton({ editing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    deleting?.let { h ->
        val name = h.opt("name").ifBlank { h.name }
        ConfirmDialog(
            title = stringResource(R.string.hostname_delete),
            body = stringResource(R.string.hostname_delete_body, name),
            confirmLabel = stringResource(R.string.remove),
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                applyChange(context.getString(R.string.change_hostname_removed, name)) { uciDelete("dhcp", h.name) }
            },
        )
    }
}
