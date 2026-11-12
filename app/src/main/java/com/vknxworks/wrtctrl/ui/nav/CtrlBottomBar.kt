package com.vknxworks.wrtctrl.ui.nav

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.wrtctrl.ui.theme.CtrlColors

data class TabItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val BarShape = RoundedCornerShape(22.dp)
private val ItemShape = RoundedCornerShape(16.dp)

/**
 * Ctrl / Signal floating tab bar: a 64dp rounded bar on the surface colour. The selected tab is an
 * accent-filled block with its icon and label; the others show their icon only.
 */
@Composable
fun CtrlBottomBar(
    items: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(BarShape)
            .background(CtrlColors.Surface.copy(alpha = 0.92f))
            .border(1.dp, CtrlColors.ControlOutline, BarShape)
            .padding(horizontal = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, item ->
            val selected = i == selectedIndex
            Row(
                Modifier
                    .height(48.dp)
                    .widthIn(min = 48.dp)
                    .clip(ItemShape)
                    .background(if (selected) CtrlColors.Accent else CtrlColors.Surface.copy(alpha = 0f))
                    .selectable(
                        selected = selected,
                        onClick = {
                            if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(i)
                        },
                        role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                    )
                    .semantics { contentDescription = item.label }
                    .animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
                    .padding(horizontal = if (selected) 16.dp else 0.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected) {
                    Icon(item.icon, contentDescription = null, tint = CtrlColors.OnAccent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = CtrlColors.OnAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(item.icon, contentDescription = null, tint = CtrlColors.Muted, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}
