package com.vknxworks.wrtctrl.ui.overview

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Router
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.ui.components.EmptyState
import com.vknxworks.wrtctrl.ui.components.LocalBottomClearance
import com.vknxworks.wrtctrl.ui.components.TabTitle

/*
 * The four tabs. Each one gets its real content in its own build stage (ubus client and router setup,
 * then Overview, Clients, Network and System); until then they say what's coming.
 */

@Composable
fun OverviewScreen() = PlaceholderTab(R.string.tab_overview, Icons.Outlined.Dashboard, R.string.placeholder_overview)

@Composable
fun ClientsScreen() = PlaceholderTab(R.string.tab_clients, Icons.Outlined.Devices, R.string.placeholder_clients)

@Composable
fun NetworkScreen() = PlaceholderTab(R.string.tab_network, Icons.Outlined.Hub, R.string.placeholder_network)

@Composable
fun SystemScreen() = PlaceholderTab(R.string.tab_system, Icons.Outlined.Router, R.string.placeholder_system)

@Composable
private fun PlaceholderTab(@StringRes title: Int, icon: ImageVector, @StringRes body: Int) {
    Column(Modifier.fillMaxSize().padding(bottom = LocalBottomClearance.current)) {
        TabTitle(stringResource(title), Modifier.statusBarsPadding())
        Box(Modifier.weight(1f)) {
            EmptyState(
                icon = icon,
                title = stringResource(R.string.placeholder_title),
                body = stringResource(body),
            )
        }
    }
}
