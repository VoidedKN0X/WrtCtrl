package com.vknxworks.wrtctrl.ui.network

import com.vknxworks.wrtctrl.ui.components.ShieldScreen
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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.container
import com.vknxworks.wrtctrl.data.api.UciSection
import com.vknxworks.wrtctrl.data.api.WifiIface
import com.vknxworks.wrtctrl.data.api.WifiRadio
import com.vknxworks.wrtctrl.data.api.uciGet
import com.vknxworks.wrtctrl.data.api.uciSet
import com.vknxworks.wrtctrl.data.api.wireless
import com.vknxworks.wrtctrl.ui.components.CtrlDialog
import com.vknxworks.wrtctrl.ui.components.CtrlTextField
import com.vknxworks.wrtctrl.ui.components.EmptyState
import com.vknxworks.wrtctrl.ui.components.ErrorCard
import com.vknxworks.wrtctrl.ui.components.Group
import com.vknxworks.wrtctrl.ui.components.GroupRow
import com.vknxworks.wrtctrl.ui.components.GroupSwitch
import com.vknxworks.wrtctrl.ui.components.ScreenTopBar
import com.vknxworks.wrtctrl.ui.components.SecureScreen
import com.vknxworks.wrtctrl.ui.components.activeRouter
import com.vknxworks.wrtctrl.ui.components.bandLabel
import com.vknxworks.wrtctrl.ui.components.rememberRouterData
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import kotlinx.coroutines.launch

private data class WifiData(val radios: List<WifiRadio>, val sections: List<UciSection>)

/** Readable name for uci's encryption value. */
@Composable
fun encryptionLabel(enc: String): String = when {
    enc.isBlank() || enc == "none" -> stringResource(R.string.enc_open)
    enc.startsWith("sae-mixed") -> "WPA2/WPA3"
    enc.startsWith("sae") -> "WPA3"
    enc.startsWith("psk2") -> "WPA2"
    enc.startsWith("psk-mixed") -> "WPA/WPA2"
    enc.startsWith("psk") -> "WPA"
    enc.startsWith("owe") -> "OWE"
    enc.startsWith("wpa") -> stringResource(R.string.enc_enterprise)
    else -> enc
}

/** Radios and their Wi-Fi networks: switch each on or off, and change a network's name or password. */
@Composable
fun WifiScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val active = activeRouter()
    val router = active?.router
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val canEdit = router?.readOnly == false && access.acl.canApply && access.acl.canWriteUci("wireless")
    var editing by remember { mutableStateOf<WifiIface?>(null) }

    val data = rememberRouterData { client ->
        WifiData(client.wireless(), runCatching { client.uciGet("wireless", "wifi-iface") }.getOrDefault(emptyList()))
    }
    val radios = data.value?.radios.orEmpty()

    fun toggle(what: String, section: String, on: Boolean) {
        val client = active?.client ?: return
        scope.launch {
            c.changes.apply(client, what) { uciSet("wireless", section, mapOf("disabled" to if (on) "0" else "1")) }
            data.refresh()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.net_wifi))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            data.error?.let { ErrorCard(it, router?.host.orEmpty(), onRetry = { data.refresh() }) }
            if (data.value != null && radios.isEmpty()) {
                EmptyState(Icons.Outlined.WifiOff, stringResource(R.string.wifi_none_title), stringResource(R.string.wifi_none_body))
            }
            radios.forEach { r ->
                Group(
                    "${r.name} · ${bandLabel(r.band)}",
                    footer = listOfNotNull(
                        r.channel.ifBlank { null }?.let { stringResource(R.string.wifi_channel, it) },
                        r.htmode.ifBlank { null },
                        r.txpower.ifBlank { null }?.let { stringResource(R.string.wifi_txpower, it) },
                    ).joinToString(" · ").ifBlank { null },
                ) {
                    GroupSwitch(
                        stringResource(R.string.wifi_radio_switch),
                        stringResource(if (r.up && !r.disabled) R.string.state_on else R.string.state_off),
                        checked = !r.disabled,
                        icon = Icons.Outlined.Wifi,
                    ) { on -> if (canEdit) toggle(context.getString(if (on) R.string.change_radio_on else R.string.change_radio_off, r.name), r.name, on) }
                    r.ifaces.forEach { iface ->
                        GroupSwitch(
                            iface.ssid.ifBlank { iface.section },
                            listOfNotNull(
                                encryptionLabel(iface.encryption),
                                iface.network.joinToString(", ").ifBlank { null },
                                iface.mode.ifBlank { null },
                                if (iface.isGuest) stringResource(R.string.guest_wifi) else null,
                            ).joinToString(" · "),
                            checked = !iface.disabled,
                        ) { on -> if (canEdit) toggle(context.getString(if (on) R.string.change_ssid_on else R.string.change_ssid_off, iface.ssid), iface.section, on) }
                        if (canEdit) {
                            GroupRow(stringResource(R.string.wifi_edit, iface.ssid.ifBlank { iface.section }), icon = Icons.Outlined.Edit, showChevron = true, onClick = { editing = iface })
                        }
                    }
                }
            }
            if (!canEdit && radios.isNotEmpty()) {
                Text(stringResource(R.string.not_allowed_here), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, modifier = Modifier.padding(horizontal = 24.dp))
            }
        }
    }

    editing?.let { iface ->
        val section = data.value?.sections?.firstOrNull { it.name == iface.section }
        WifiEditDialog(iface, section?.opt("key").orEmpty(), onDismiss = { editing = null }) { ssid, key ->
            editing = null
            val client = active?.client ?: return@WifiEditDialog
            scope.launch {
                c.changes.apply(client, context.getString(R.string.change_wifi_edit, ssid)) {
                    val values = mutableMapOf<String, Any>("ssid" to ssid)
                    if (key != null) values["key"] = key
                    uciSet("wireless", iface.section, values)
                }
                data.refresh()
            }
        }
    }
}

/**
 * Changes a network's name and password, with a strong warning: every device on it (maybe this phone)
 * drops off and has to join again. The rollback puts the old settings back if WrtCtrl loses the router.
 */
@Composable
private fun WifiEditDialog(iface: WifiIface, currentKey: String, onDismiss: () -> Unit, onSave: (ssid: String, key: String?) -> Unit) {
    // The password is shown here: keep it out of screenshots and Recents.
    SecureScreen()
    var ssid by remember { mutableStateOf(iface.ssid) }
    var key by remember { mutableStateOf(currentKey) }
    var show by remember { mutableStateOf(false) }
    val open = iface.encryption.isBlank() || iface.encryption == "none" || iface.encryption.startsWith("owe")
    val ssidOk = ssid.toByteArray().size in 1..32
    val keyOk = open || key.length in 8..63
    CtrlDialog(
        onDismissRequest = onDismiss,
        // A dialog is a window of its own: FLAG_SECURE on the app's window doesn't cover it.
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        icon = { Icon(Icons.Outlined.Warning, null, tint = CtrlColors.Warn) },
        title = { Text(stringResource(R.string.wifi_edit_title)) },
        text = {
            // Inside the dialog, so its own window is shielded too.
            // Not marked sensitive for accessibility: password managers fill these fields through it.
            ShieldScreen(sensitive = false)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.wifi_edit_warning), color = CtrlColors.Warn, style = MaterialTheme.typography.bodyMedium)
                CtrlTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = { Text(stringResource(R.string.field_ssid)) },
                    isError = !ssidOk,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!open) {
                    CtrlTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text(stringResource(R.string.field_wifi_key)) },
                        isError = !keyOk,
                        supportingText = if (!keyOk) ({ Text(stringResource(R.string.wifi_key_invalid)) }) else null,
                        singleLine = true,
                        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { show = !show }) {
                                Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, stringResource(if (show) R.string.hide_secret else R.string.show_secret))
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(ssid, if (open || key == currentKey) null else key) }, enabled = ssidOk && keyOk && (ssid != iface.ssid || key != currentKey)) {
                Text(stringResource(R.string.wifi_edit_apply), color = CtrlColors.Error)
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
