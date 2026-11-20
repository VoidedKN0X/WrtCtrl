package com.vknxworks.wrtctrl.ui.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.data.router.ClientNotes
import com.vknxworks.wrtctrl.ui.components.ConfirmDialog
import com.vknxworks.wrtctrl.ui.components.CtrlCard
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import com.vknxworks.wrtctrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shown on the Clients screens while the saved nicknames and notes of [routerId] can't be opened (they are
 * encrypted at rest, and the key is gone or the file is damaged). Nothing is overwritten in that state.
 * "Try again" asks for the key again; "Start empty" sets the unreadable notes aside (renamed, not deleted)
 * so new ones can be saved. [onChanged] reloads the list afterwards.
 */
@Composable
fun NotesUnreadableCard(routerId: String?, onChanged: suspend () -> Unit) {
    val unreadable by ClientNotes.unreadable.collectAsStateWithLifecycle()
    if (routerId != null && routerId in unreadable) {
        val context = LocalContext.current.applicationContext
        val scope = rememberCoroutineScope()
        var confirm by remember { mutableStateOf(false) }
        CtrlCard(Modifier.padding(horizontal = Gutter, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Lock, null, tint = CtrlColors.Error)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.notes_unreadable_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.notes_unreadable_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { ClientNotes.retry(context, routerId) }
                            onChanged()
                        }
                    },
                ) { Text(stringResource(R.string.notes_unreadable_retry)) }
                TextButton(onClick = { confirm = true }) { Text(stringResource(R.string.notes_unreadable_start_empty)) }
            }
        }
        if (confirm) {
            ConfirmDialog(
                title = stringResource(R.string.notes_start_empty_title),
                body = stringResource(R.string.notes_start_empty_body),
                confirmLabel = stringResource(R.string.notes_unreadable_start_empty),
                onDismiss = { confirm = false },
                onConfirm = {
                    confirm = false
                    scope.launch {
                        withContext(Dispatchers.IO) { ClientNotes.startEmpty(context, routerId) }
                        onChanged()
                    }
                },
            )
        }
    }
}
