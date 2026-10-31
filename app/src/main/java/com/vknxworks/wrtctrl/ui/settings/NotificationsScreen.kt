package com.vknxworks.wrtctrl.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.container
import com.vknxworks.wrtctrl.data.lock.AppLock
import com.vknxworks.wrtctrl.data.notify.CheckStore
import com.vknxworks.wrtctrl.data.notify.NotifyEvent
import com.vknxworks.wrtctrl.data.notify.NotifySettings
import com.vknxworks.wrtctrl.data.notify.NotifyWorker
import com.vknxworks.wrtctrl.data.notify.Push
import com.vknxworks.wrtctrl.data.notify.PushStatus
import com.vknxworks.wrtctrl.data.notify.UnifiedPush
import com.vknxworks.wrtctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.wrtctrl.ui.components.Group
import com.vknxworks.wrtctrl.ui.components.GroupChoice
import com.vknxworks.wrtctrl.ui.components.GroupRow
import com.vknxworks.wrtctrl.ui.components.GroupSwitch
import com.vknxworks.wrtctrl.ui.components.ScreenTopBar
import com.vknxworks.wrtctrl.ui.components.copySensitive
import com.vknxworks.wrtctrl.ui.components.relativeTime
import com.vknxworks.wrtctrl.ui.components.rememberNotificationAccess
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import com.vknxworks.wrtctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

/**
 * Background checks (WrtCtrl checks your routers itself), push through ntfy that wakes those checks at
 * once (from the router's own hotplug scripts, see the README), what to notify about, and battery advice.
 */
@Composable
fun NotificationsScreen() {
    val context = LocalContext.current
    val c = context.container
    val settings by c.notifySettings.settings.collectAsStateWithLifecycle()
    val routers by c.sessions.routers.collectAsStateWithLifecycle()
    val lastCheck by CheckStore.lastCheck.collectAsStateWithLifecycle()
    val pushStatus by Push.status.collectAsStateWithLifecycle()
    val access = rememberNotificationAccess()
    val canPost = access.canPost
    var resumed by remember { mutableIntStateOf(0) }
    val unrestricted = remember(resumed) {
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    val ntfyInstalled = remember(resumed) { UnifiedPush.isNtfyInstalled(context) }
    val ntfyUnrestricted = remember(resumed) { UnifiedPush.isNtfyUnrestricted(context) }

    // Re-check permission, battery and push state when coming back from system settings or ntfy.
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            resumed++
            routers.forEach { CheckStore.read(context, it.id) }
            Push.load(context, c.notifySettings.current().push)
        }
    }

    fun ensurePermission() {
        if (!canPost) access.fix()
    }
    fun update(transform: (NotifySettings) -> NotifySettings) {
        c.appScope.launch { c.notifySettings.update(transform) }
    }
    fun openOutside(intent: Intent) {
        AppLock.expectReturn()
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.notifications))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(stringResource(R.string.notif_intro), style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Muted, modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp))

            if (!canPost && settings.polling) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, CtrlColors.Outline),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.notif_permission_off), color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = { access.fix() }) { Text(stringResource(R.string.notif_permission_allow)) }
                    }
                }
            }

            Group(stringResource(R.string.group_polling), footer = stringResource(R.string.polling_footer)) {
                GroupSwitch(stringResource(R.string.polling_title), stringResource(R.string.polling_subtitle), checked = settings.polling && canPost, icon = Icons.Outlined.Schedule) { on ->
                    if (on) ensurePermission()
                    update { it.copy(polling = on) }
                }
                if (settings.polling && canPost) {
                    GroupChoice(
                        stringResource(R.string.polling_interval),
                        NotifySettings.INTERVALS.map { it to pluralStringResource(R.plurals.polling_interval_value, it, it) },
                        settings.intervalMinutes,
                        icon = Icons.Outlined.Timer,
                    ) { m -> update { it.copy(intervalMinutes = m) } }
                }
                routers.forEach { r ->
                    val st = lastCheck[r.id]
                    GroupRow(
                        r.name,
                        when {
                            st == null || st.lastCheckAt == 0L -> stringResource(R.string.last_check_never)
                            st.lastResult == "ok" -> stringResource(R.string.last_check, relativeTime(st.lastCheckAt))
                            else -> stringResource(R.string.last_check_failed, relativeTime(st.lastCheckAt))
                        },
                    )
                }
                GroupRow(stringResource(R.string.check_now), icon = Icons.Outlined.Refresh, onClick = {
                    access.askIfPossible()
                    NotifyWorker.runNow(context)
                    Toast.makeText(context, context.getString(R.string.check_started), Toast.LENGTH_SHORT).show()
                })
            }

            Group(stringResource(R.string.group_push), footer = stringResource(R.string.push_footer)) {
                GroupSwitch(stringResource(R.string.push_title), stringResource(R.string.push_subtitle), checked = settings.push && canPost, icon = Icons.Outlined.Campaign) { on ->
                    if (on) {
                        ensurePermission()
                        Push.register(context)
                    } else {
                        Push.unregister(context)
                    }
                    update { it.copy(push = on) }
                }
                if (!ntfyInstalled) {
                    Note(stringResource(R.string.push_no_ntfy)) {
                        CtrlSecondaryButton(onClick = { openOutside(Intent(Intent.ACTION_VIEW, Uri.parse(UnifiedPush.NTFY_DOWNLOAD))) }) {
                            Text(stringResource(R.string.push_install_ntfy))
                        }
                    }
                }
                if (settings.push && canPost && ntfyInstalled) {
                    if (!settings.polling) Note(stringResource(R.string.push_needs_polling)) {}
                    when (val st = pushStatus) {
                        PushStatus.NoNtfy, PushStatus.Off, PushStatus.Registering -> Note(stringResource(R.string.push_registering)) {
                            TextButton(onClick = { Push.register(context) }) { Text(stringResource(R.string.retry)) }
                        }
                        is PushStatus.Failed -> Note(stringResource(R.string.push_failed, st.reason)) {
                            TextButton(onClick = { Push.register(context) }) { Text(stringResource(R.string.retry)) }
                        }
                        is PushStatus.Unavailable -> Note(stringResource(R.string.push_unavailable)) {}
                        is PushStatus.Registered -> {
                            GroupRow(
                                stringResource(R.string.push_endpoint),
                                st.endpoint,
                                icon = Icons.Outlined.NotificationsActive,
                                trailing = { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy)) },
                                onClick = {
                                    // The push address holds the secret ntfy topic: keep it out of clipboard previews.
                                    copySensitive(context, "WrtCtrl", st.endpoint)
                                    Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                                },
                            )
                            Note(stringResource(R.string.push_endpoint_note)) {}
                        }
                    }
                }
            }

            Group(stringResource(R.string.group_events)) {
                NotifyEvent.entries.forEach { e ->
                    GroupSwitch(stringResource(e.label), stringResource(e.summary), checked = settings.on(e) && canPost) { on ->
                        if (on) ensurePermission()
                        update { s -> s.copy(events = if (on) s.events + e else s.events - e) }
                    }
                }
            }

            Group(stringResource(R.string.group_battery), footer = stringResource(R.string.battery_footer)) {
                GroupRow(
                    stringResource(if (unrestricted) R.string.battery_unrestricted else R.string.battery_restricted),
                    icon = if (unrestricted) Icons.Outlined.BatteryFull else Icons.Outlined.BatteryAlert,
                    iconTint = if (unrestricted) CtrlColors.AccentText else CtrlColors.Error,
                )
                if (settings.push && ntfyInstalled && !ntfyUnrestricted) {
                    GroupRow(
                        stringResource(R.string.battery_ntfy_restricted),
                        stringResource(R.string.battery_ntfy_restricted_body),
                        icon = Icons.Outlined.BatteryAlert,
                        iconTint = CtrlColors.Error,
                        showChevron = true,
                        onClick = { openOutside(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${UnifiedPush.NTFY}"))) },
                    )
                }
                GroupRow(stringResource(R.string.battery_open), showChevron = true, onClick = {
                    // App info is where ColorOS keeps "Allow background activity".
                    openOutside(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                })
            }
        }
    }
}

@Composable
private fun Note(text: String, action: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Muted)
        action()
    }
}
