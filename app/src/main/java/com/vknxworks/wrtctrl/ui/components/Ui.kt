package com.vknxworks.wrtctrl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.wrtctrl.container
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.ui.nav.LocalNavigator
import com.vknxworks.wrtctrl.ui.nav.YouRoute
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import com.vknxworks.wrtctrl.ui.theme.CtrlShapes
import com.vknxworks.wrtctrl.ui.theme.CtrlType
import com.vknxworks.wrtctrl.ui.theme.Gutter

// ---------- Top bars ----------

/**
 * Top bar for pushed screens: a 44dp square back button and the title in Space Grotesk, on the
 * background (no Material fill). Handles the status bar inset itself.
 */
@Composable
fun ScreenTopBar(
    title: String,
    showBack: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val nav = LocalNavigator.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(CtrlColors.Background)
            .statusBarsPadding()
            .heightIn(min = 64.dp)
            .padding(start = Gutter, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            CtrlSquareButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), onClick = nav::back)
            Spacer(Modifier.width(14.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        CompositionLocalProvider(LocalContentColor provides CtrlColors.Muted) { actions() }
    }
}

/**
 * Top of a main tab: the WRT/CTRL wordmark on the left, the profile picture on the right, and the
 * tab's headline below. [showTitle] = false leaves the headline to the screen (Overview's hero).
 */
@Composable
fun TabTitle(
    title: String,
    modifier: Modifier = Modifier,
    showProfile: Boolean = true,
    showTitle: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = Gutter, end = Gutter, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CtrlWordmark(stringResource(R.string.wordmark_name), Modifier.weight(1f))
            CompositionLocalProvider(LocalContentColor provides CtrlColors.Muted) { trailing() }
            if (showProfile) {
                Spacer(Modifier.width(6.dp))
                ProfileButton()
            }
        }
        if (showTitle) {
            Text(
                title,
                style = CtrlType.Headline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Gutter, end = Gutter, top = 10.dp, bottom = 10.dp),
            )
        }
    }
}

/**
 * The profile picture: a 44dp rounded square with the router's initial and a small accent square
 * while connected. Tapping it opens the You page with the router, Settings and About (there is no
 * settings tab).
 */
@Composable
fun ProfileButton() {
    val nav = LocalNavigator.current
    val c = LocalContext.current.container
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val initial = active?.router?.initial
    val connected = active != null && !access.loading && access.error == null
    val label = stringResource(R.string.you_title)
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CtrlShapes.Control)
                .background(CtrlColors.Surface)
                .border(1.dp, CtrlColors.ControlOutline, CtrlShapes.Control)
                .clickable(role = Role.Button, onClickLabel = label) { nav.navigate(YouRoute) }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            if (initial.isNullOrBlank()) {
                Icon(Icons.Outlined.AccountCircle, null, Modifier.size(22.dp), tint = CtrlColors.Text)
            } else {
                Text(initial.take(1).uppercase(), style = MaterialTheme.typography.labelLarge, color = CtrlColors.Text)
            }
        }
        if (connected) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-1).dp, y = (-1).dp)
                    .size(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CtrlColors.Background)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(CtrlColors.Accent),
            )
        }
    }
}

// ---------- Grouped lists (settings, account, detail pages) ----------

/** A surface card of related rows with 1dp dividers, under an optional mono section label. */
@Composable
fun Group(
    title: String? = null,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp)) {
        if (title != null) {
            CtrlSectionLabel(title, Modifier.padding(start = 4.dp, bottom = 8.dp))
        }
        CtrlCard(contentPadding = PaddingValues(0.dp)) {
            // Each row is its own child, so dividers go between rows (and never around hidden ones).
            DividedColumn(Modifier.fillMaxWidth(), content = content)
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = CtrlColors.Muted,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )
        }
    }
}

@Composable
private fun RowIcon(icon: ImageVector, tint: Color) {
    Icon(icon, null, Modifier.size(20.dp), tint = tint)
    Spacer(Modifier.width(14.dp))
}

@Composable
private fun RowTexts(title: String, subtitle: String?, titleColor: Color, modifier: Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = CtrlColors.Muted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun GroupRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = CtrlColors.Muted,
    titleColor: Color = CtrlColors.Text,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) RowIcon(icon, iconTint)
        RowTexts(title, subtitle, titleColor, Modifier.weight(1f))
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        } else if (showChevron) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = CtrlColors.Muted)
        }
    }
}

/** A row with a Signal switch. The whole row is one switch for TalkBack. */
@Composable
fun GroupSwitch(title: String, subtitle: String? = null, checked: Boolean, icon: ImageVector? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onValueChange = onChange,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) RowIcon(icon, CtrlColors.Muted)
        RowTexts(title, subtitle, CtrlColors.Text, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        CtrlSwitch(checked, onCheckedChange = null)
    }
}

/** A row showing the current value; tapping opens a sheet to pick another. */
@Composable
fun <T> GroupChoice(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    icon: ImageVector? = null,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second.orEmpty()
    GroupRow(title, current, icon, showChevron = true, onClick = { open = true })
    if (open) {
        ChoiceSheet(title, options, selected, onDismiss = { open = false }) { onPick(it); open = false }
    }
}

@Composable
fun <T> ChoiceSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    if (LocalAppCovered.current) return
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CtrlColors.Surface, shape = CtrlSheetShape) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(options, key = { it.second }) { (value, label) ->
                val isSel = value == selected
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(value) }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSel) CtrlColors.AccentText else CtrlColors.Text,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSel) Icon(Icons.Outlined.Check, stringResource(R.string.selected), tint = CtrlColors.AccentText)
                }
            }
        }
    }
}

// ---------- Buttons ----------

/** The one main action on a screen: accent fill, 56dp tall. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    CtrlButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------- Empty states ----------

/** Centred icon, title and explanation for a screen with nothing to show yet. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(CtrlColors.Surface).border(1.dp, CtrlColors.Outline, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(30.dp), tint = CtrlColors.AccentText) }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = CtrlColors.Muted,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}
