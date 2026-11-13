package com.vknxworks.wrtctrl.ui.components

import com.vknxworks.wrtctrl.ui.components.CtrlDialog
import com.vknxworks.wrtctrl.ui.components.CtrlTextField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vknxworks.wrtctrl.R

/**
 * Confirmation for the most dangerous actions: the button only works once [expected] (a guest ID or
 * node name) has been typed exactly. [extra] can add options such as "start afterwards".
 */
@Composable
fun TypedConfirmDialog(
    title: String,
    body: String,
    expected: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    icon: ImageVector = Icons.Outlined.Warning,
    extra: (@Composable () -> Unit)? = null,
) {
    // No other app's floating window can cover the question or trick a tap onto the confirm button.
    ShieldScreen(sensitive = false)
    var typed by remember { mutableStateOf("") }
    val matches = typed.trim() == expected
    CtrlDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (extra != null) {
                    Spacer(Modifier.height(8.dp))
                    extra()
                }
                Spacer(Modifier.height(16.dp))
                CtrlTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.confirm_type_label, expected)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (expected.all(Char::isDigit)) KeyboardType.Number else KeyboardType.Ascii,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    isError = typed.isNotEmpty() && !matches,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = matches) {
                Text(confirmLabel, color = if (matches) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A plain confirmation for destructive actions that don't need typing. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    danger: Boolean = true,
    icon: ImageVector? = null,
) {
    // No other app's floating window can cover the question or trick a tap onto the confirm button.
    ShieldScreen(sensitive = false)
    CtrlDialog(
        onDismissRequest = onDismiss,
        icon = if (icon != null) {
            { Icon(icon, null, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
        title = { Text(title) },
        text = { Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
