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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.vknxworks.wrtctrl.data.api.DiagTool
import com.vknxworks.wrtctrl.data.api.diagnose
import com.vknxworks.wrtctrl.data.api.isDiagHost
import com.vknxworks.wrtctrl.ui.components.CtrlCard
import com.vknxworks.wrtctrl.ui.components.CtrlTextField
import com.vknxworks.wrtctrl.ui.components.PillButton
import com.vknxworks.wrtctrl.ui.components.errorMessage
import com.vknxworks.wrtctrl.ui.theme.CtrlType
import com.vknxworks.wrtctrl.ui.theme.Gutter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Ping and traceroute, run on the router with the same commands as LuCI's Diagnostics page. */
@Composable
fun DiagnosticsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val active = activeRouter()
    val router = active?.router
    var host by rememberSaveable { mutableStateOf("openwrt.org") }
    var trace by rememberSaveable { mutableStateOf(false) }
    var v6 by rememberSaveable { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var output by rememberSaveable { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val hostOk = isDiagHost(host.trim())

    fun run() {
        val client = active?.client ?: return
        if (!hostOk || running) return
        val tool = when {
            trace && v6 -> DiagTool.TRACEROUTE6
            trace -> DiagTool.TRACEROUTE
            v6 -> DiagTool.PING6
            else -> DiagTool.PING
        }
        running = true
        failed = false
        output = ""
        scope.launch {
            try {
                val r = client.diagnose(tool, host.trim())
                output = listOf(r.stdout.trim(), r.stderr.trim()).filter { it.isNotEmpty() }.joinToString("\n").ifBlank { context.getString(R.string.diag_no_output) }
                failed = r.code != 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                output = errorMessage(context, e, router?.host.orEmpty())
                failed = true
            } finally {
                running = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.net_diag))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            CtrlTextField(
                value = host,
                onValueChange = { host = it.trim() },
                label = { Text(stringResource(R.string.diag_host)) },
                isError = host.isNotEmpty() && !hostOk,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { run() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !trace, onClick = { trace = false }, label = { Text(stringResource(R.string.diag_ping)) })
                FilterChip(selected = trace, onClick = { trace = true }, label = { Text(stringResource(R.string.diag_traceroute)) })
                FilterChip(selected = !v6, onClick = { v6 = false }, label = { Text("IPv4") })
                FilterChip(selected = v6, onClick = { v6 = true }, label = { Text("IPv6") })
            }
            PillButton(
                stringResource(if (running) R.string.diag_running else R.string.diag_run),
                onClick = { run() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
                icon = Icons.Outlined.PlayArrow,
                enabled = hostOk && !running && active != null,
            )
            if (output.isNotEmpty()) {
                CtrlCard(Modifier.padding(horizontal = Gutter, vertical = 6.dp)) {
                    SelectionContainer {
                        Text(output, style = CtrlType.Mono, color = if (failed) CtrlColors.Error else CtrlColors.Text, modifier = Modifier.horizontalScroll(rememberScrollState()))
                    }
                }
            }
            Text(
                stringResource(if (trace) R.string.diag_footer_trace else R.string.diag_footer_ping),
                style = MaterialTheme.typography.bodySmall,
                color = CtrlColors.Muted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }
}
