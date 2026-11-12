package com.vknxworks.wrtctrl.ui.nav

import kotlinx.serialization.Serializable

// Tabs
@Serializable data object OverviewRoute
@Serializable data object ClientsRoute
@Serializable data object NetworkRoute
@Serializable data object SystemRoute

// Account and settings, opened from the profile picture
@Serializable data object YouRoute
@Serializable data object SettingsRoute
@Serializable data object RoutersRoute
@Serializable data object AddRouterRoute
@Serializable data class EditRouterRoute(val id: String)
@Serializable data object NotificationsRoute

// Pushed screens
@Serializable data class ClientRoute(val mac: String)
@Serializable data object WifiRoute
@Serializable data object DhcpRoute
@Serializable data object PortForwardsRoute
@Serializable data object InterfacesRoute
@Serializable data object ServicesRoute
@Serializable data object PackagesRoute
@Serializable data object LogsRoute
