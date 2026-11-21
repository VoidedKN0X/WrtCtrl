package com.vknxworks.wrtctrl.ui.system

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.container
import com.vknxworks.wrtctrl.data.api.FIRMWARE_CHECK_ARGS
import com.vknxworks.wrtctrl.data.api.FirmwareCheck
import com.vknxworks.wrtctrl.data.api.FirmwareCheckFailed
import com.vknxworks.wrtctrl.data.api.OptionalPackage
import com.vknxworks.wrtctrl.data.api.UCLIENT_FETCH
import com.vknxworks.wrtctrl.data.api.firmwareCheck
import com.vknxworks.wrtctrl.data.api.downloadBackup
import com.vknxworks.wrtctrl.data.api.installPackage
import com.vknxworks.wrtctrl.data.api.installedOptional
import com.vknxworks.wrtctrl.data.api.reboot
import com.vknxworks.wrtctrl.data.lock.AppLock
import com.vknxworks.wrtctrl.ui.components.ConfirmDialog
import com.vknxworks.wrtctrl.ui.components.Group
import com.vknxworks.wrtctrl.ui.components.GroupRow
import com.vknxworks.wrtctrl.ui.components.LocalBottomClearance
import com.vknxworks.wrtctrl.ui.components.TabTitle
import com.vknxworks.wrtctrl.ui.components.TypedConfirmDialog
import com.vknxworks.wrtctrl.ui.components.activeRouter
import com.vknxworks.wrtctrl.ui.components.errorMessage
import com.vknxworks.wrtctrl.ui.components.formatBytes
import com.vknxworks.wrtctrl.ui.components.rememberRouterData
import com.vknxworks.wrtctrl.ui.nav.LocalNavigator
import com.vknxworks.wrtctrl.ui.nav.LogsRoute
import com.vknxworks.wrtctrl.ui.nav.PackagesRoute
import com.vknxworks.wrtctrl.ui.nav.ServicesRoute
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import com.vknxworks.wrtctrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One optional package: what it unlocks in WrtCtrl. */
private fun OptionalPackage.unlocks(): Int = when (this) {
    OptionalPackage.NLBWMON -> R.string.opt_nlbwmon
    OptionalPackage.ETHERWAKE -> R.string.opt_etherwake
    OptionalPackage.COLLECTD -> R.string.opt_collectd
}

/** The System tab: services, packages, the system log, the firmware check, a config backup, optional packages and reboot. */
@Composable
fun SystemScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val active = activeRouter()
    val router = active?.router
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val acl = access.acl
    val writable = router?.readOnly == false
    var confirmReboot by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf<OptionalPackage?>(null) }
    var fwChecking by remember(router?.id) { mutableStateOf(false) }
    var fwResult by remember(router?.id) { mutableStateOf<FirmwareCheck?>(null) }
    var fwError by remember(router?.id) { mutableStateOf<String?>(null) }
    val optional = rememberRouterData { client -> runCatching { client.installedOptional() }.getOrDefault(emptySet()) }
    val hostname = access.board?.hostname?.ifBlank { null } ?: router?.hostname?.ifBlank { null } ?: router?.name.orEmpty()

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri ->
        val client = active?.client
        if (uri == null || client == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out -> client.downloadBackup(out) } ?: 0L
                }
                toast(context.getString(R.string.backup_saved, formatBytes(bytes)))
            } catch (e: Exception) {
                runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }
                toast(errorMessage(context, e, router?.host.orEmpty()))
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_system))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomClearance.current + 16.dp)) {
            item("main") {
                Group {
                    GroupRow(stringResource(R.string.sys_services), stringResource(R.string.sys_services_sub), icon = Icons.Outlined.Settings, showChevron = true, onClick = { nav.navigate(ServicesRoute) })
                    GroupRow(stringResource(R.string.sys_packages), stringResource(R.string.sys_packages_sub), icon = Icons.Outlined.Inventory2, showChevron = true, onClick = { nav.navigate(PackagesRoute) })
                    GroupRow(stringResource(R.string.sys_logs), stringResource(R.string.sys_logs_sub), icon = Icons.Outlined.ListAlt, showChevron = true, onClick = { nav.navigate(LogsRoute) })
                }
            }
            item("firmware") {
                val version = access.board?.release?.version.orEmpty().ifBlank { router?.openWrtVersion.orEmpty() }
                val canCheck = acl.canExec(UCLIENT_FETCH + " " + FIRMWARE_CHECK_ARGS.joinToString(" "))
                val result = fwResult
                Group(stringResource(R.string.sys_firmware), footer = stringResource(R.string.sys_firmware_footer)) {
                    GroupRow(stringResource(R.string.sys_firmware_installed), version.ifBlank { stringResource(R.string.unknown) }, icon = Icons.Outlined.SystemUpdate)
                    GroupRow(
                        stringResource(R.string.sys_firmware_check),
                        when {
                            !canCheck -> stringResource(R.string.sys_firmware_not_allowed)
                            fwChecking -> stringResource(R.string.sys_firmware_checking)
                            fwError != null -> fwError
                            result == null -> stringResource(R.string.sys_firmware_check_sub)
                            result.notARelease -> stringResource(R.string.sys_firmware_not_release)
                            result.newer.isEmpty() -> stringResource(R.string.sys_firmware_latest, result.current)
                            else -> stringResource(R.string.sys_firmware_newer, result.newer.joinToString(", "))
                        },
                        icon = Icons.Outlined.Refresh,
                        titleColor = when {
                            !canCheck -> CtrlColors.Muted
                            result != null && result.newer.isNotEmpty() && fwError == null && !fwChecking -> CtrlColors.AccentText
                            else -> CtrlColors.Text
                        },
                        onClick = if (canCheck && !fwChecking && version.isNotBlank()) ({
                            val client = active?.client
                            if (client != null) {
                                fwChecking = true
                                fwError = null
                                scope.launch {
                                    try {
                                        fwResult = client.firmwareCheck(version)
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        throw e
                                    } catch (e: FirmwareCheckFailed) {
                                        fwError = context.getString(R.string.sys_firmware_failed)
                                    } catch (e: Exception) {
                                        fwError = errorMessage(context, e, router?.host.orEmpty())
                                    } finally {
                                        fwChecking = false
                                    }
                                }
                            }
                        }) else null,
                    )
                }
            }
            item("backup") {
                val canBackup = "backup" in acl.cgiIo || acl.cgiIo.contains("*")
                Group(stringResource(R.string.sys_backup), footer = stringResource(R.string.sys_backup_footer)) {
                    GroupRow(
                        stringResource(R.string.sys_backup_download),
                        stringResource(if (canBackup) R.string.sys_backup_download_sub else R.string.not_allowed_here),
                        icon = Icons.Outlined.Archive,
                        titleColor = if (canBackup) CtrlColors.Text else CtrlColors.Muted,
                        onClick = if (canBackup) ({
                            val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                            AppLock.expectReturn()
                            saveBackup.launch("backup-${hostname.ifBlank { "openwrt" }}-$stamp.tar.gz")
                        }) else null,
                    )
                }
            }
            item("optional") {
                val installed = optional.value
                Group(stringResource(R.string.sys_optional), footer = stringResource(R.string.sys_optional_footer)) {
                    OptionalPackage.entries.forEach { p ->
                        val has = installed?.contains(p) == true
                        val canInstall = writable && acl.canExec("/usr/libexec/package-manager-call install ${p.pkg}")
                        GroupRow(
                            p.pkg,
                            stringResource(p.unlocks()) + "\n" + stringResource(
                                when {
                                    installed == null -> R.string.opt_checking
                                    has -> R.string.opt_installed
                                    canInstall -> R.string.opt_tap_install
                                    else -> R.string.opt_not_installed
                                },
                            ),
                            icon = Icons.Outlined.Extension,
                            titleColor = if (has) CtrlColors.AccentText else CtrlColors.Text,
                            onClick = if (!has && installed != null && canInstall) ({ installing = p }) else null,
                        )
                    }
                }
            }
            item("reboot") {
                val canReboot = writable && acl.canCall("system", "reboot")
                Group {
                    GroupRow(
                        stringResource(R.string.sys_reboot),
                        stringResource(if (canReboot) R.string.sys_reboot_sub else R.string.not_allowed_here),
                        icon = Icons.Outlined.PowerSettingsNew,
                        iconTint = if (canReboot) CtrlColors.Error else CtrlColors.Muted,
                        titleColor = if (canReboot) CtrlColors.Error else CtrlColors.Muted,
                        onClick = if (canReboot) ({ confirmReboot = true }) else null,
                    )
                }
            }
            if (!writable) {
                item("ro") {
                    Text(stringResource(R.string.read_only_actions_hidden), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp))
                }
            }
        }
    }

    // Reboot asks for the router's hostname, typed exactly. Also opened by the "Reboot" app shortcut.
    val shortcutReboot by RebootRequest.pending.collectAsStateWithLifecycle()
    if (confirmReboot || shortcutReboot) {
        RebootDialog(hostname, onDismiss = { confirmReboot = false; RebootRequest.consume() }) {
            confirmReboot = false
            RebootRequest.consume()
            val client = active?.client ?: return@RebootDialog
            scope.launch {
                runCatching { client.reboot() }
                    .onSuccess { toast(context.getString(R.string.reboot_started, hostname)) }
                    .onFailure { toast(errorMessage(context, it, router?.host.orEmpty())) }
            }
        }
    }
    installing?.let { p ->
        ConfirmDialog(
            title = stringResource(R.string.opt_install_title, p.pkg),
            body = stringResource(R.string.opt_install_body, p.pkg, stringResource(p.unlocks())),
            confirmLabel = stringResource(R.string.opt_install),
            danger = false,
            onDismiss = { installing = null },
            onConfirm = {
                installing = null
                val client = active?.client ?: return@ConfirmDialog
                scope.launch {
                    toast(context.getString(R.string.opt_installing, p.pkg))
                    runCatching { client.installPackage(p.pkg) }
                        .onSuccess { r -> toast(context.getString(if (r.code == 0) R.string.opt_install_done else R.string.opt_install_failed, p.pkg)) }
                        .onFailure { toast(errorMessage(context, it, router?.host.orEmpty())) }
                    optional.refresh()
                }
            },
        )
    }
}

/** The reboot confirmation: the hostname must be typed exactly. */
@Composable
fun RebootDialog(hostname: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    TypedConfirmDialog(
        title = stringResource(R.string.reboot_title, hostname),
        body = stringResource(R.string.reboot_body),
        expected = hostname,
        confirmLabel = stringResource(R.string.sys_reboot),
        icon = Icons.Outlined.PowerSettingsNew,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

/** A reboot asked for from the app shortcut: the System tab shows the confirmation. */
object RebootRequest {
    private val _pending = kotlinx.coroutines.flow.MutableStateFlow(false)
    val pending: kotlinx.coroutines.flow.StateFlow<Boolean> = _pending

    fun request() {
        _pending.value = true
    }

    fun consume() {
        _pending.value = false
    }
}
