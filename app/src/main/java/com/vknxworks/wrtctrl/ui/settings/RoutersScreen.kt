package com.vknxworks.wrtctrl.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.container
import com.vknxworks.wrtctrl.data.router.Router
import com.vknxworks.wrtctrl.ui.components.CtrlDialog
import com.vknxworks.wrtctrl.ui.components.Group
import com.vknxworks.wrtctrl.ui.components.GroupRow
import com.vknxworks.wrtctrl.ui.components.GroupSwitch
import com.vknxworks.wrtctrl.ui.components.ScreenTopBar
import com.vknxworks.wrtctrl.ui.nav.AddRouterRoute
import com.vknxworks.wrtctrl.ui.nav.EditRouterRoute
import com.vknxworks.wrtctrl.ui.nav.LocalNavigator
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import com.vknxworks.wrtctrl.ui.theme.Health

/** One line about a router: who signs in, which OpenWrt, and whether the connection is encrypted. */
@Composable
fun routerSummary(router: Router): String = listOfNotNull(
    stringResource(R.string.signed_in_as, router.username),
    router.openWrtVersion.takeIf { it.isNotBlank() }?.let { stringResource(R.string.openwrt_version, it) },
    // The login travels unencrypted: say so wherever the router is shown.
    if (router.isPlainHttp) {
        stringResource(R.string.router_not_encrypted)
    } else {
        stringResource(if (router.certPin.isNotBlank()) R.string.cert_pinned else R.string.cert_system)
    },
).joinToString("  ·  ")

/** All saved routers: switch between them, change one, set read-only mode, or remove one. */
@Composable
fun RoutersScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val routers by c.sessions.routers.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<Router?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.routers_title))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            routers.forEach { router ->
                val isActive = router.id == active?.router?.id
                Group(router.name) {
                    GroupRow(
                        router.url.removePrefix("http://").removePrefix("https://"),
                        routerSummary(router),
                        icon = Icons.Outlined.Router,
                        trailing = if (isActive) {
                            { Icon(Icons.Outlined.CheckCircle, stringResource(R.string.active_router), tint = Health.ok) }
                        } else {
                            null
                        },
                    )
                    if (!isActive) {
                        GroupRow(stringResource(R.string.switch_to), icon = Icons.Outlined.SwapHoriz, onClick = { c.sessions.switchTo(router.id) })
                    }
                    GroupSwitch(
                        stringResource(R.string.read_only_mode),
                        stringResource(R.string.read_only_mode_subtitle),
                        checked = router.readOnly,
                        icon = Icons.Outlined.Visibility,
                    ) { c.sessions.setReadOnly(router.id, it) }
                    GroupRow(stringResource(R.string.edit_router), icon = Icons.Outlined.Edit, showChevron = true, onClick = { nav.navigate(EditRouterRoute(router.id)) })
                    GroupRow(
                        stringResource(R.string.remove_router),
                        icon = Icons.Outlined.Delete,
                        iconTint = CtrlColors.Error,
                        titleColor = CtrlColors.Error,
                        onClick = { removing = router },
                    )
                }
            }
            Group {
                GroupRow(stringResource(R.string.add_router), icon = Icons.Outlined.Add, showChevron = true, onClick = { nav.navigate(AddRouterRoute) })
            }
        }
    }

    removing?.let { router ->
        CtrlDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.remove_router_title, router.name)) },
            text = { Text(stringResource(R.string.remove_router_body)) },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    c.sessions.remove(router.id)
                }) { Text(stringResource(R.string.remove), color = CtrlColors.Error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
