package com.vknxworks.wrtctrl.ui.system

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.container
import com.vknxworks.wrtctrl.data.api.Service
import com.vknxworks.wrtctrl.data.api.serviceAction
import com.vknxworks.wrtctrl.data.api.services
import com.vknxworks.wrtctrl.ui.components.ConfirmDialog
import com.vknxworks.wrtctrl.ui.components.CtrlDropdownMenu
import com.vknxworks.wrtctrl.ui.components.CtrlTextField
import com.vknxworks.wrtctrl.ui.components.ErrorCard
import com.vknxworks.wrtctrl.ui.components.ScreenTopBar
import com.vknxworks.wrtctrl.ui.components.activeRouter
import com.vknxworks.wrtctrl.ui.components.ctrlMenuItemColors
import com.vknxworks.wrtctrl.ui.components.errorMessage
import com.vknxworks.wrtctrl.ui.components.rememberRouterData
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import com.vknxworks.wrtctrl.ui.theme.CtrlType
import com.vknxworks.wrtctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

/** Services the router runs: whether each is running and starts at boot; start, stop, restart, enable, disable. */
@Composable
fun ServicesScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val active = activeRouter()
    val router = active?.router
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val canAct = router?.readOnly == false && access.acl.canCall("rc", "init")
    var query by rememberSaveable { mutableStateOf("") }
    var pending by remember { mutableStateOf<Pair<Service, String>?>(null) }
    val data = rememberRouterData(10_000) { client -> client.services() }
    val list = data.value.orEmpty().filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }

    fun doAction(s: Service, action: String) {
        val client = active?.client ?: return
        scope.launch {
            runCatching { client.serviceAction(s.name, action) }
                .onSuccess { Toast.makeText(context, context.getString(R.string.service_done, s.name, action), Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, errorMessage(context, it, router?.host.orEmpty()), Toast.LENGTH_LONG).show() }
            data.refresh()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.sys_services))
        CtrlTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.services_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp),
        )
        data.error?.let { ErrorCard(it, router?.host.orEmpty(), onRetry = { data.refresh() }) }
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
            items(list, key = { it.name }) { s ->
                var menu by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().padding(start = Gutter, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOf(
                                stringResource(if (s.running) R.string.service_running else R.string.service_stopped),
                                stringResource(if (s.enabled) R.string.service_enabled else R.string.service_disabled),
                            ).joinToString(" · ").uppercase(),
                            style = CtrlType.MonoStatus,
                            color = if (s.running) CtrlColors.AccentText else CtrlColors.Stopped,
                        )
                    }
                    if (canAct) {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.service_actions, s.name)) }
                        CtrlDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            listOf(
                                "start" to R.string.service_start,
                                "restart" to R.string.service_restart,
                                "reload" to R.string.service_reload,
                                "stop" to R.string.service_stop,
                                (if (s.enabled) "disable" else "enable") to (if (s.enabled) R.string.service_disable else R.string.service_enable),
                            ).forEach { (action, label) ->
                                DropdownMenuItem(text = { Text(stringResource(label)) }, colors = ctrlMenuItemColors(), onClick = {
                                    menu = false
                                    // Anything that stops a service, or keeps it from starting at boot, asks first.
                                    if (action == "stop" || action == "disable" || action == "restart") pending = s to action else doAction(s, action)
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    pending?.let { (s, action) ->
        ConfirmDialog(
            title = stringResource(R.string.service_confirm_title, action, s.name),
            body = stringResource(
                when {
                    s.name in CRITICAL_SERVICES -> R.string.service_confirm_critical
                    action == "disable" -> R.string.service_confirm_disable
                    else -> R.string.service_confirm_body
                },
                s.name,
            ),
            confirmLabel = stringResource(
                when (action) {
                    "stop" -> R.string.service_stop
                    "disable" -> R.string.service_disable
                    else -> R.string.service_restart
                },
            ),
            onDismiss = { pending = null },
            onConfirm = {
                pending = null
                doAction(s, action)
            },
        )
    }
}

/** Services whose stop or restart can cut WrtCtrl (and everyone else) off the router for a while. */
private val CRITICAL_SERVICES = setOf("network", "firewall", "uhttpd", "rpcd", "dnsmasq", "odhcpd", "dropbear", "adguardhome")
