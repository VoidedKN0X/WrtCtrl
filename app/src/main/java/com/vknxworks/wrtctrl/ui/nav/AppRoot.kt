package com.vknxworks.wrtctrl.ui.nav

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.ui.components.LocalBottomClearance
import com.vknxworks.wrtctrl.ui.overview.ClientsScreen
import com.vknxworks.wrtctrl.ui.overview.NetworkScreen
import com.vknxworks.wrtctrl.ui.overview.OverviewScreen
import com.vknxworks.wrtctrl.ui.overview.SystemScreen
import com.vknxworks.wrtctrl.ui.settings.SettingsScreen
import com.vknxworks.wrtctrl.ui.settings.YouScreen
import com.vknxworks.wrtctrl.ui.settings.RoutersScreen
import com.vknxworks.wrtctrl.ui.setup.SetupScreen
import com.vknxworks.wrtctrl.container
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.toRoute

@Composable
fun AppRoot() {
    val c = LocalContext.current.container
    val routers by c.sessions.routers.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (routers.isEmpty()) {
            // First start (or every router removed): setup fills the screen, no tabs.
            SetupScreen(onDone = {}, onCancel = null)
        } else {
            MainScaffold()
        }
    }
}

private data class TopDest(val route: Any, @StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

/** The four tabs. Overview is the only summary screen; account and settings open from the profile picture. */
private val topDestinations = listOf(
    TopDest(OverviewRoute, R.string.tab_overview, Icons.Outlined.Dashboard, Icons.Outlined.Dashboard),
    TopDest(ClientsRoute, R.string.tab_clients, Icons.Outlined.Devices, Icons.Outlined.Devices),
    TopDest(NetworkRoute, R.string.tab_network, Icons.Outlined.Hub, Icons.Outlined.Hub),
    TopDest(SystemRoute, R.string.tab_system, Icons.Outlined.Router, Icons.Outlined.Router),
)

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val navigator = remember(navController) { Navigator(navController) }
    val backStack by navController.currentBackStackEntryAsState()
    val dest = backStack?.destination
    val selectedIndex = topDestinations.indexOfFirst { top -> dest?.hierarchy?.any { it.hasRoute(top.route::class) } == true }
    val tabs = topDestinations.map { TabItem(stringResource(it.label), it.icon, it.selectedIcon) }

    fun selectTab(i: Int) {
        navController.navigate(topDestinations[i].route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (maxWidth >= 600.dp) {
                // Tablets, unfolded foldables and landscape: a side rail.
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = CtrlColors.Background,
                        modifier = Modifier.fillMaxHeight().statusBarsPadding(),
                    ) {
                        Spacer(Modifier.weight(1f))
                        tabs.forEachIndexed { i, tab ->
                            val selected = i == selectedIndex
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectTab(i) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = CtrlColors.OnAccent,
                                    selectedTextColor = CtrlColors.Text,
                                    indicatorColor = CtrlColors.Accent,
                                    unselectedIconColor = CtrlColors.Muted,
                                    unselectedTextColor = CtrlColors.Muted,
                                ),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().navigationBarsPadding()) { AppNavHost(navController) }
                }
            } else {
                PhoneLayout(
                    navController = navController,
                    tabs = tabs,
                    showTabs = selectedIndex >= 0,
                    selectedIndex = selectedIndex,
                    onSelect = ::selectTab,
                )
            }
        }
    }
}

/**
 * Phones: content runs edge to edge and scrolls under the floating tab bar.
 * The island shows on the main tabs; pushed screens get the full height.
 */
@Composable
private fun PhoneLayout(
    navController: NavHostController,
    tabs: List<TabItem>,
    showTabs: Boolean,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val density = LocalDensity.current
    var overlayHeight by remember { mutableStateOf(0.dp) }

    CompositionLocalProvider(
        LocalBottomClearance provides if (showTabs) overlayHeight else 0.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                AppNavHost(navController)
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { overlayHeight = with(density) { it.height.toDp() } }
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(
                    visible = showTabs,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    CtrlBottomBar(items = tabs, selectedIndex = selectedIndex, onSelect = onSelect)
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = OverviewRoute) {
        composable<OverviewRoute> { OverviewScreen() }
        composable<ClientsRoute> { ClientsScreen() }
        composable<NetworkRoute> { NetworkScreen() }
        composable<SystemRoute> { SystemScreen() }

        composable<YouRoute> { YouScreen() }
        composable<SettingsRoute> { SettingsScreen() }
        composable<RoutersRoute> { RoutersScreen() }
        composable<AddRouterRoute> {
            val navigator = LocalNavigator.current
            SetupScreen(onDone = { navController.popBackStack(OverviewRoute, false) }, onCancel = navigator::back)
        }
        composable<EditRouterRoute> {
            val navigator = LocalNavigator.current
            SetupScreen(editId = it.toRoute<EditRouterRoute>().id, onDone = navigator::back, onCancel = navigator::back)
        }
    }
}
