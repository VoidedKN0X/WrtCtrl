package com.vknxworks.wrtctrl.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Space taken at the bottom of the screen by the floating tab bar. Scrolling screens add it to their
 * bottom content padding, so their last items can scroll clear of the bar.
 */
val LocalBottomClearance = compositionLocalOf { 0.dp }
