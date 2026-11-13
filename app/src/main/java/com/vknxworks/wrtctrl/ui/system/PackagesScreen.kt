package com.vknxworks.wrtctrl.ui.system

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.container
import com.vknxworks.wrtctrl.data.api.PackageManager
import com.vknxworks.wrtctrl.data.api.PackageResult
import com.vknxworks.wrtctrl.data.api.packageManager
import com.vknxworks.wrtctrl.data.api.updatePackageLists
import com.vknxworks.wrtctrl.data.api.upgradable
import com.vknxworks.wrtctrl.data.api.upgradePackages
import com.vknxworks.wrtctrl.ui.components.ConfirmDialog
import com.vknxworks.wrtctrl.ui.components.CtrlCard
import com.vknxworks.wrtctrl.ui.components.CtrlProgressBar
import com.vknxworks.wrtctrl.ui.components.EmptyState
import com.vknxworks.wrtctrl.ui.components.ErrorCard
import com.vknxworks.wrtctrl.ui.components.PillButton
import com.vknxworks.wrtctrl.ui.components.ScreenTopBar
import com.vknxworks.wrtctrl.ui.components.activeRouter
import com.vknxworks.wrtctrl.ui.components.errorMessage
import com.vknxworks.wrtctrl.ui.components.rememberRouterData
import com.vknxworks.wrtctrl.ui.overview.UpdateCounts
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import com.vknxworks.wrtctrl.ui.theme.CtrlType
import com.vknxworks.wrtctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

/**
 * Packages with updates, through whichever package manager the router has (apk on OpenWrt 25.12 and
 * newer, opkg before). Refresh the lists, pick packages (or all) and upgrade after a warning; the
 * package manager's output is shown.
 */
@Composable
fun PackagesScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val active = activeRouter()
    val router = active?.router
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val canUpgrade = router?.readOnly == false && access.acl.canUpgradePackages
    val canUpdate = router?.readOnly == false && access.acl.canExec("/usr/libexec/package-manager-call update")
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirm by remember { mutableStateOf<List<String>?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var output by remember { mutableStateOf<PackageResult?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    val data = rememberRouterData { client ->
        val pm = client.packageManager()
        val list = client.upgradable(pm)
        UpdateCounts.set(router?.id.orEmpty(), list.size)
        pm to list
    }
    val pm = data.value?.first
    val list = data.value?.second.orEmpty()

    fun refreshLists() {
        val client = active?.client ?: return
        busy = context.getString(R.string.pkg_updating_lists)
        actionError = null
        scope.launch {
            runCatching { client.updatePackageLists() }
                .onSuccess { output = it }
                .onFailure { actionError = errorMessage(context, it, router?.host.orEmpty()) }
            busy = null
            data.refresh()
        }
    }

    fun upgrade(names: List<String>) {
        val client = active?.client ?: return
        busy = context.getString(R.string.pkg_upgrading, names.size)
        actionError = null
        scope.launch {
            runCatching { client.upgradePackages(names) }
                .onSuccess { output = it; selected = emptySet() }
                .onFailure { actionError = errorMessage(context, it, router?.host.orEmpty()) }
            busy = null
            data.refresh()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.sys_packages)) {
            if (canUpdate) IconButton(onClick = ::refreshLists, enabled = busy == null) { Icon(Icons.Outlined.Refresh, stringResource(R.string.pkg_refresh_lists)) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            data.error?.let { ErrorCard(it, router?.host.orEmpty(), onRetry = { data.refresh() }) }
            pm?.let {
                Text(
                    stringResource(R.string.pkg_manager, if (it == PackageManager.APK) "apk" else "opkg").uppercase(),
                    style = CtrlType.MonoStatus,
                    color = CtrlColors.Muted,
                    modifier = Modifier.padding(horizontal = Gutter, vertical = 6.dp),
                )
            }
            CtrlCard(Modifier.padding(horizontal = Gutter, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Warning, null, tint = CtrlColors.Warn, modifier = Modifier.padding(end = 10.dp))
                    Text(stringResource(R.string.pkg_warning), style = MaterialTheme.typography.bodySmall)
                }
            }
            busy?.let {
                Column(Modifier.padding(horizontal = Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                    CtrlProgressBar(Modifier.fillMaxWidth())
                }
            }
            actionError?.let { Text(it, color = CtrlColors.Error, modifier = Modifier.padding(horizontal = Gutter, vertical = 6.dp)) }

            if (data.value != null && list.isEmpty()) {
                EmptyState(Icons.Outlined.Inventory2, stringResource(R.string.pkg_none_title), stringResource(R.string.pkg_none_body), modifier = Modifier.padding(vertical = 24.dp))
            }
            list.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(horizontal = Gutter - 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (canUpgrade) {
                        Checkbox(checked = p.name in selected, onCheckedChange = { on -> selected = if (on) selected + p.name else selected - p.name })
                    }
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text("${p.installed} → ${p.available}", style = CtrlType.Mono, color = CtrlColors.Muted)
                    }
                }
            }
            if (canUpgrade && list.isNotEmpty()) {
                Column(Modifier.padding(horizontal = Gutter, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(stringResource(R.string.pkg_upgrade_selected, selected.size), onClick = { confirm = selected.toList() }, enabled = selected.isNotEmpty() && busy == null, modifier = Modifier.fillMaxWidth())
                    PillButton(stringResource(R.string.pkg_upgrade_all, list.size), onClick = { confirm = list.map { it.name } }, enabled = busy == null, modifier = Modifier.fillMaxWidth())
                }
            } else if (!canUpgrade && list.isNotEmpty()) {
                Text(stringResource(R.string.not_allowed_here), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp))
            }

            output?.let { o ->
                Text(
                    stringResource(if (o.code == 0) R.string.pkg_output_ok else R.string.pkg_output_failed, o.code),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (o.code == 0) CtrlColors.AccentText else CtrlColors.Error,
                    modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
                )
                Text(
                    o.output.ifBlank { "—" },
                    style = CtrlType.Mono,
                    softWrap = false,
                    modifier = Modifier
                        .padding(horizontal = Gutter)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CtrlColors.Raised)
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                )
            }
        }
    }

    confirm?.let { names ->
        val core = names.filter { it in CORE_PACKAGES || it.startsWith("kmod-") || it.startsWith("libc") }
        ConfirmDialog(
            title = stringResource(R.string.pkg_confirm_title, names.size),
            body = stringResource(R.string.pkg_confirm_body, names.joinToString(", ")) +
                if (core.isNotEmpty()) "\n\n" + stringResource(R.string.pkg_confirm_core, core.joinToString(", ")) else "",
            confirmLabel = stringResource(R.string.pkg_upgrade),
            onDismiss = { confirm = null },
            onConfirm = {
                confirm = null
                upgrade(names)
            },
        )
    }
}

/** Packages whose upgrade on a running router is known to be risky (OpenWrt recommends a sysupgrade instead). */
private val CORE_PACKAGES = setOf(
    "base-files", "busybox", "kernel", "procd", "ubus", "ubusd", "libubox", "rpcd", "uhttpd", "netifd", "fstools",
    "apk-mbedtls", "apk-openssl", "opkg", "dropbear", "firewall4", "nftables", "dnsmasq", "odhcpd", "musl", "libc", "libgcc",
)
