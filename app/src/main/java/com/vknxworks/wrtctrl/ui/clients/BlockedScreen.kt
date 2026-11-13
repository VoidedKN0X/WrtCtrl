package com.vknxworks.wrtctrl.ui.clients

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.container
import com.vknxworks.wrtctrl.data.api.UciSection
import com.vknxworks.wrtctrl.data.api.uciGet
import com.vknxworks.wrtctrl.data.router.BlockRule
import com.vknxworks.wrtctrl.data.router.BlockRules
import com.vknxworks.wrtctrl.data.router.NetClient
import com.vknxworks.wrtctrl.data.router.loadClients
import com.vknxworks.wrtctrl.data.router.stageBlock
import com.vknxworks.wrtctrl.data.router.stageUnblock
import com.vknxworks.wrtctrl.ui.components.ConfirmDialog
import com.vknxworks.wrtctrl.ui.components.EmptyState
import com.vknxworks.wrtctrl.ui.components.ErrorCard
import com.vknxworks.wrtctrl.ui.components.Group
import com.vknxworks.wrtctrl.ui.components.GroupRow
import com.vknxworks.wrtctrl.ui.components.ScreenTopBar
import com.vknxworks.wrtctrl.ui.components.activeRouter
import com.vknxworks.wrtctrl.ui.components.rememberRouterData
import com.vknxworks.wrtctrl.ui.nav.ClientRoute
import com.vknxworks.wrtctrl.ui.nav.LocalNavigator
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import com.vknxworks.wrtctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

private class BlockedData(val rules: List<Pair<UciSection, BlockRule>>, val clients: Map<String, NetClient>)

/** Every device WrtCtrl blocks (only rules named with its prefix), with what each block does and Unblock. */
@Composable
fun BlockedScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val active = activeRouter()
    val router = active?.router
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val acl = access.acl
    val canFw = router?.readOnly == false && acl.canApply && acl.canWriteUci("firewall")
    val snackbar = remember { SnackbarHostState() }
    var unblock by remember { mutableStateOf<Pair<UciSection, BlockRule>?>(null) }

    val data = rememberRouterData(15_000) { client ->
        val sections = client.uciGet("firewall", "rule").filter(BlockRules::isOurs)
        val clients = runCatching { loadClients(context, client, router?.id.orEmpty()) }.getOrDefault(emptyList())
        BlockedData(sections.mapNotNull { s -> BlockRules.parse(s)?.let { s to it } }, clients.associateBy { it.mac })
    }

    fun nameOf(rule: BlockRule, d: BlockedData?): String =
        rule.macs.firstNotNullOfOrNull { d?.clients?.get(it)?.name?.ifBlank { null } } ?: rule.label.ifBlank { rule.macs.firstOrNull().orEmpty() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(stringResource(R.string.blocked_title))
            data.error?.let { ErrorCard(it, router?.host.orEmpty(), onRetry = { data.refresh() }) }
            val d = data.value
            when {
                d == null -> Unit
                d.rules.isEmpty() -> EmptyState(Icons.Outlined.CheckCircle, stringResource(R.string.blocked_none), stringResource(R.string.blocked_none_body))
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                    Group(footer = stringResource(R.string.blocked_footer)) {
                        d.rules.forEach { (section, rule) ->
                            val name = nameOf(rule, d)
                            val now = rule.activeAt(java.time.LocalDateTime.now())
                            GroupRow(
                                name,
                                listOf(
                                    blockSummary(rule),
                                    stringResource(if (!rule.enabled) R.string.blocked_disabled else if (now) R.string.blocked_now else R.string.blocked_not_now),
                                ).joinToString("\n"),
                                icon = Icons.Outlined.Block,
                                iconTint = if (now) CtrlColors.Error else CtrlColors.Muted,
                                showChevron = true,
                                onClick = { rule.macs.firstOrNull()?.let { nav.navigate(ClientRoute(it)) } },
                                trailing = if (canFw) ({
                                    androidx.compose.material3.TextButton(onClick = { unblock = section to rule }) { Text(stringResource(R.string.client_unblock)) }
                                }) else null,
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.blocked_clock_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = CtrlColors.Muted,
                        modifier = Modifier.padding(horizontal = Gutter + 4.dp),
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    val client = active?.client
    unblock?.let { (section, rule) ->
        val name = nameOf(rule, data.value)
        ConfirmDialog(
            title = stringResource(R.string.client_unblock),
            body = stringResource(R.string.client_unblock_body, name),
            confirmLabel = stringResource(R.string.client_unblock),
            danger = false,
            onDismiss = { unblock = null },
            onConfirm = {
                unblock = null
                if (client == null) return@ConfirmDialog
                scope.launch {
                    val ok = c.changes.apply(client, context.getString(R.string.change_unblock, name)) { stageUnblock(section) }
                    data.refresh()
                    val mac = rule.macs.firstOrNull()
                    if (ok && mac != null) {
                        val r = snackbar.showSnackbar(context.getString(R.string.unblock_done, name), context.getString(R.string.undo), duration = SnackbarDuration.Long)
                        if (r == SnackbarResult.ActionPerformed) {
                            c.changes.apply(client, context.getString(R.string.change_block, name)) { stageBlock(mac, rule.label.ifBlank { name }, rule.target, rule.schedule) }
                            data.refresh()
                        }
                    }
                }
            },
        )
    }
}
