package com.vknxworks.wrtctrl.ui.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.data.router.BlockRule
import com.vknxworks.wrtctrl.data.router.BlockRules
import com.vknxworks.wrtctrl.data.router.BlockSchedule
import com.vknxworks.wrtctrl.data.router.BlockTarget
import com.vknxworks.wrtctrl.ui.components.CtrlDialog
import com.vknxworks.wrtctrl.ui.components.CtrlSwitch
import com.vknxworks.wrtctrl.ui.components.CtrlTextField
import com.vknxworks.wrtctrl.ui.components.ShieldScreen
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Blocking a device from the internet, or changing a block: Reject or Drop, and optionally only at certain
 * times on certain days (the router's local time). [existing] fills in a block that's already there.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockDialog(label: String, existing: BlockRule?, onDismiss: () -> Unit, onSave: (BlockTarget, BlockSchedule?) -> Unit) {
    ShieldScreen(sensitive = false)
    var target by remember { mutableStateOf(existing?.target ?: BlockTarget.REJECT) }
    var scheduled by remember { mutableStateOf(existing?.schedule != null) }
    var start by remember { mutableStateOf(existing?.schedule?.start?.let(BlockRules::formatTime) ?: "22:00") }
    var stop by remember { mutableStateOf(existing?.schedule?.stop?.let(BlockRules::formatTime) ?: "07:00") }
    var days by remember { mutableStateOf(existing?.schedule?.days ?: emptySet()) }

    val startTime = BlockRules.parseTime(start)
    val stopTime = BlockRules.parseTime(stop)
    val schedule = if (scheduled && startTime != null && stopTime != null) BlockSchedule(startTime, stopTime, days) else null
    val scheduleOk = !scheduled || (schedule != null && BlockRules.validSchedule(schedule))

    CtrlDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Block, null) },
        title = { Text(stringResource(if (existing != null) R.string.block_change_title else R.string.block_title, label)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.client_block_body, label), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
                BlockTarget.entries.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = target == t, role = Role.RadioButton) { target = t },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = target == t, onClick = null)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(stringResource(if (t == BlockTarget.REJECT) R.string.block_reject else R.string.block_drop), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(if (t == BlockTarget.REJECT) R.string.block_reject_sub else R.string.block_drop_sub), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().toggleable(value = scheduled, role = Role.Switch) { scheduled = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.block_schedule), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.block_schedule_sub), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
                    }
                    CtrlSwitch(checked = scheduled, onCheckedChange = null)
                }
                if (scheduled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CtrlTextField(
                            value = start,
                            onValueChange = { start = it.take(5) },
                            label = { Text(stringResource(R.string.block_from)) },
                            isError = startTime == null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        CtrlTextField(
                            value = stop,
                            onValueChange = { stop = it.take(5) },
                            label = { Text(stringResource(R.string.block_until)) },
                            isError = stopTime == null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { d ->
                            FilterChip(
                                selected = d in days,
                                onClick = { days = if (d in days) days - d else days + d },
                                label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                            )
                        }
                    }
                    Text(
                        when {
                            startTime == null || stopTime == null -> stringResource(R.string.block_time_invalid)
                            !scheduleOk -> stringResource(R.string.block_time_same)
                            else -> stringResource(if (days.isEmpty()) R.string.block_schedule_every_day else R.string.block_schedule_days_note)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (scheduleOk) CtrlColors.Muted else CtrlColors.Error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(target, schedule) }, enabled = scheduleOk) {
                Text(stringResource(if (existing != null) R.string.save_changes else R.string.client_block), color = if (scheduleOk) CtrlColors.Error else CtrlColors.Muted)
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
