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
