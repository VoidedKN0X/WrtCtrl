package com.vknxworks.wrtctrl.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.container
import com.vknxworks.wrtctrl.data.router.ApplyState
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import com.vknxworks.wrtctrl.ui.theme.CtrlType
import com.vknxworks.wrtctrl.ui.theme.Health
import kotlinx.coroutines.delay

/**
 * Shows every configuration change while it's applied: saving, the rollback countdown ("Confirm changes
 * within 30 s") while WrtCtrl checks it can still reach the router, and the outcome. Sits above every
 * screen, so it stays visible when the change came from a dialog that already closed.
 */
@Composable
fun ApplyDialog() {
    val c = LocalContext.current.container
    val state by c.changes.state.collectAsStateWithLifecycle()
    val host = c.sessions.active.collectAsStateWithLifecycle().value?.router?.host.orEmpty()
    when (val s = state) {
        ApplyState.Idle -> Unit
        is ApplyState.Applying -> CtrlDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            icon = { Icon(Icons.Outlined.Sync, null) },
            title = { Text(stringResource(R.string.apply_saving)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s.what)
                    CtrlProgressBar(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {},
        )
        is ApplyState.Confirming -> {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(s.deadline) {
                while (true) {
                    now = System.currentTimeMillis()
                    delay(250)
                }
            }
            val left = ((s.deadline - now) / 1000).coerceAtLeast(0)
            CtrlDialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                icon = { Icon(Icons.Outlined.History, null) },
                title = { Text(stringResource(R.string.apply_confirm_title, s.timeoutSeconds)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(s.what)
                        Text(left.toString(), style = CtrlType.Readout, color = CtrlColors.AccentText)
                        CtrlProgressBar(Modifier.fillMaxWidth(), progress = { (left.toFloat() / s.timeoutSeconds).coerceIn(0f, 1f) })
                        Text(stringResource(R.string.apply_confirm_body), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { c.changes.undo() }) { Text(stringResource(R.string.apply_undo), color = CtrlColors.Error) }
                },
            )
        }
        is ApplyState.Done -> {
            LaunchedEffect(s) {
                delay(1_400)
                c.changes.dismiss()
            }
            CtrlDialog(
                onDismissRequest = { c.changes.dismiss() },
                icon = { Icon(Icons.Outlined.CheckCircle, null, tint = Health.ok) },
                title = { Text(stringResource(R.string.apply_done)) },
                text = { Text(s.what) },
                confirmButton = { TextButton(onClick = { c.changes.dismiss() }) { Text(stringResource(R.string.ok)) } },
            )
        }
        is ApplyState.RolledBack -> CtrlDialog(
            onDismissRequest = { c.changes.dismiss() },
            icon = { Icon(Icons.Outlined.History, null, tint = Health.warn) },
            title = { Text(stringResource(R.string.apply_rolled_back)) },
            text = {
                Text(stringResource(if (s.byUser) R.string.apply_rolled_back_user else R.string.apply_rolled_back_body, host))
            },
            confirmButton = { TextButton(onClick = { c.changes.dismiss() }) { Text(stringResource(R.string.ok)) } },
        )
        is ApplyState.Failed -> {
            val context = LocalContext.current
            CtrlDialog(
                onDismissRequest = { c.changes.dismiss() },
                icon = { Icon(Icons.Outlined.ErrorOutline, null, tint = CtrlColors.Error) },
                title = { Text(stringResource(R.string.apply_failed)) },
                text = { Text(s.what + "\n\n" + errorMessage(context, s.error, host)) },
                confirmButton = { TextButton(onClick = { c.changes.dismiss() }) { Text(stringResource(R.string.ok)) } },
            )
        }
    }
}
