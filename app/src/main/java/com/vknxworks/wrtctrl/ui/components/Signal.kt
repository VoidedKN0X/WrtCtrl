package com.vknxworks.wrtctrl.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import com.vknxworks.wrtctrl.ui.theme.CtrlShapes
import com.vknxworks.wrtctrl.ui.theme.CtrlType

// Shared building blocks of the Ctrl / Signal look. Screens use these instead of styling
// Material components one by one, so the whole app changes in one place.

// ---------- Text ----------

/** Mono, upper-case section label ("PRIVACY", "CPU LOAD"). */
@Composable
fun CtrlSectionLabel(text: String, modifier: Modifier = Modifier, color: Color = CtrlColors.Muted) {
    Text(text.uppercase(), style = CtrlType.Label, color = color, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** The "<NAME>/CTRL" wordmark with the slash in the accent. [name] is e.g. "PROX". */
@Composable
fun CtrlWordmark(name: String, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append(name.uppercase())
            withStyle(SpanStyle(color = CtrlColors.AccentText)) { append("/") }
            append("CTRL")
        },
        style = CtrlType.Wordmark,
        color = CtrlColors.Text,
        modifier = modifier,
        maxLines = 1,
    )
}

// ---------- Cards ----------

/** Secondary card: surface fill, 1dp border, 22dp corners. */
@Composable
fun CtrlCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    color: Color = CtrlColors.Surface,
    borderColor: Color = CtrlColors.Outline,
    shape: Shape = CtrlShapes.Card,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = shape,
        color = color,
        contentColor = CtrlColors.Text,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** The one most important thing on a screen: accent fill with near-black content. */
@Composable
fun CtrlHeroCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = CtrlShapes.Hero,
        color = CtrlColors.Accent,
        contentColor = CtrlColors.OnAccent,
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(CtrlShapes.Hero).clickable(onClick = onClick) else Modifier),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

// ---------- Lists ----------

/** A 1dp list divider. */
@Composable
fun CtrlDivider(modifier: Modifier = Modifier, color: Color = CtrlColors.Divider) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/**
 * A column that draws a 1dp divider between its children, used inside grouped cards so rows that
 * only show sometimes never leave a double or dangling line.
 */
@Composable
fun DividedColumn(modifier: Modifier = Modifier, color: Color = CtrlColors.Outline, content: @Composable () -> Unit) {
    val lines = remember { ArrayList<Int>() }
    Layout(
        content = content,
        modifier = modifier.drawWithContent {
            drawContent()
            val h = 1.dp.toPx()
            lines.forEach { y -> drawRect(color, Offset(0f, y.toFloat()), Size(size.width, h)) }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val width = placeables.maxOfOrNull { it.width }?.coerceAtLeast(constraints.minWidth) ?: constraints.minWidth
        val height = placeables.sumOf { it.height }.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            lines.clear()
            // Lines only go between real rows: spacers and other thin children (under 12dp) get none.
            val minRow = 12.dp.roundToPx()
            var y = 0
            var prevRow = false
            placeables.forEach { p ->
                if (p.height > 0) {
                    val isRow = p.height >= minRow
                    if (isRow && prevRow) lines.add(y)
                    prevRow = isRow
                }
                p.place(0, y)
                y += p.height
            }
        }
    }
}

/** An 8dp square status dot (2dp corners). */
@Composable
fun CtrlStatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.25f)).background(color))
}

/**
 * Plain list row: leading mono meta (ID) in muted, a 16sp title, and a trailing mono status with a
 * square dot (accent when [active], grey otherwise). A 1dp divider sits on top.
 */
@Composable
fun CtrlListRow(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    subtitle: String? = null,
    status: String? = null,
    active: Boolean = true,
    statusColor: Color = if (active) CtrlColors.AccentText else CtrlColors.Stopped,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        if (divider) CtrlDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (meta != null) {
                Text(meta, style = CtrlType.Mono, color = CtrlColors.Muted, modifier = Modifier.width(40.dp), maxLines = 1)
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) Text(subtitle, style = CtrlType.Mono.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize), color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (status != null) {
                Spacer(Modifier.width(10.dp))
                Text(status.uppercase(), style = CtrlType.MonoStatus, color = statusColor, maxLines = 1)
                Spacer(Modifier.width(10.dp))
                CtrlStatusDot(statusColor)
            }
        }
    }
}

// ---------- Controls ----------

/**
 * Rounded-rect switch, 52x32dp: accent track with a near-black knob when on, dark track with a grey
 * knob when off. Announced as a switch to TalkBack.
 */
@Composable
fun CtrlSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val track by animateColorAsState(if (checked) CtrlColors.Accent else CtrlColors.SwitchTrackOff, label = "switch-track")
    val knob by animateColorAsState(if (checked) CtrlColors.OnAccent else CtrlColors.Stopped, label = "switch-knob")
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, label = "switch-x")
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(shape)
            .background(if (enabled) track else track.copy(alpha = 0.38f))
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                        onValueChange = onCheckedChange,
                    )
                } else {
                    Modifier
                },
            )
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .offset(x = x)
                .size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (enabled) knob else knob.copy(alpha = 0.5f)),
        )
    }
}

/** Primary button: accent fill, near-black text, 16dp corners (56dp tall for a screen's main action). */
@Composable
fun CtrlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = CtrlShapes.Button,
        colors = ButtonDefaults.buttonColors(
            containerColor = CtrlColors.Accent,
            contentColor = CtrlColors.OnAccent,
            disabledContainerColor = CtrlColors.Raised,
            disabledContentColor = CtrlColors.Stopped,
        ),
        contentPadding = contentPadding,
        content = content,
    )
}

/** Secondary button: surface fill and a 1dp border. Also used where Material had a tonal button. */
@Composable
fun CtrlSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = CtrlShapes.Control,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = CtrlColors.Surface,
            contentColor = CtrlColors.Text,
            disabledContainerColor = CtrlColors.Surface,
            disabledContentColor = CtrlColors.Stopped,
        ),
        border = BorderStroke(1.dp, CtrlColors.ControlOutline),
        contentPadding = contentPadding,
        content = content,
    )
}

/** A 44dp square icon button in the top-bar style (back, close, actions). */
@Composable
fun CtrlSquareButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = CtrlColors.Text,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(CtrlShapes.Control)
            .background(CtrlColors.Surface)
            .border(1.dp, CtrlColors.ControlOutline, CtrlShapes.Control)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(20.dp), tint = tint)
    }
}

/** Text field: surface fill, 1dp border, 16dp corners, accent border and cursor when focused. */
@Composable
fun CtrlTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        shape = CtrlShapes.Field,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CtrlColors.Surface,
            unfocusedContainerColor = CtrlColors.Surface,
            disabledContainerColor = CtrlColors.Surface,
            errorContainerColor = CtrlColors.Surface,
            focusedBorderColor = CtrlColors.AccentText,
            unfocusedBorderColor = CtrlColors.ControlOutline,
            disabledBorderColor = CtrlColors.Outline,
            cursorColor = CtrlColors.AccentText,
            focusedLabelColor = CtrlColors.AccentText,
            unfocusedLabelColor = CtrlColors.Muted,
        ),
    )
}

/** Progress bar: 6dp, 3dp corners, dark track. Without [progress] it runs as an indeterminate sweep. */
@Composable
fun CtrlProgressBar(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    color: Color = CtrlColors.Accent,
    track: Color = CtrlColors.ProgressTrack,
) {
    val sweep = if (progress == null) {
        val t = rememberInfiniteTransition(label = "progress")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "progress-x").value
    } else {
        0f
    }
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        val r = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        drawRoundRect(track, Offset.Zero, size, r)
        if (progress != null) {
            val f = progress().coerceIn(0f, 1f)
            if (f > 0f) drawRoundRect(color, Offset.Zero, Size(size.width * f, size.height), r)
        } else {
            val seg = size.width * 0.3f
            val start = (size.width + seg) * sweep - seg
            val l = start.coerceAtLeast(0f)
            val rr = (start + seg).coerceAtMost(size.width)
            if (rr > l) drawRoundRect(color, Offset(l, 0f), Size(rr - l, size.height), r)
        }
    }
}

// ---------- Dialogs ----------

/** Alert dialog on the surface colour with 26dp corners and a 1dp border. */
@Composable
fun CtrlDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    // Not over the lock screen, and not on an activity that is closing (see LocalAppCovered).
    if (LocalAppCovered.current) return
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.border(1.dp, CtrlColors.Outline, CtrlShapes.Dialog),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = CtrlShapes.Dialog,
        containerColor = CtrlColors.Surface,
        iconContentColor = CtrlColors.AccentText,
        titleContentColor = CtrlColors.Text,
        textContentColor = CtrlColors.Muted,
        tonalElevation = 0.dp,
        properties = properties,
    )
}

/** Shape of bottom sheets (top corners only). */
val CtrlSheetShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)

/** Tints its content near-black, for text and icons on accent fills. */
@Composable
fun OnAccent(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides CtrlColors.OnAccent, content = content)
}

// ---------- Popup menus ----------

/** Corner radius of popup menus. */
private val CtrlMenuShape = RoundedCornerShape(16.dp)

/**
 * A popup menu in the Signal look: flat surface, 16dp corners, a thin card border and no shadow or
 * tonal tint. Material's default shadow is drawn inside the popup window and shows up as a grey box,
 * so every menu in the app goes through this. Same parameters and behaviour as material3 [DropdownMenu].
 */
@Composable
fun CtrlDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        shape = CtrlMenuShape,
        containerColor = CtrlColors.Surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, CtrlColors.Outline),
        content = content,
    )
}

/**
 * Colours for items in a [CtrlDropdownMenu]: text in the text colour, icons muted, disabled items in
 * the stopped grey. [destructive] tints the item with the error colour.
 */
@Composable
fun ctrlMenuItemColors(destructive: Boolean = false): MenuItemColors {
    val text = if (destructive) CtrlColors.Error else CtrlColors.Text
    val icon = if (destructive) CtrlColors.Error else CtrlColors.Muted
    return MenuDefaults.itemColors(
        textColor = text,
        leadingIconColor = icon,
        trailingIconColor = icon,
        disabledTextColor = CtrlColors.Stopped,
        disabledLeadingIconColor = CtrlColors.Stopped,
        disabledTrailingIconColor = CtrlColors.Stopped,
    )
}
