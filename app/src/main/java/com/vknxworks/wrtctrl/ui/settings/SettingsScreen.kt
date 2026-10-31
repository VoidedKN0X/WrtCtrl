package com.vknxworks.wrtctrl.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.vknxworks.wrtctrl.data.lock.AppLock
import com.vknxworks.wrtctrl.data.lock.LockVault
import com.vknxworks.wrtctrl.data.lock.PinRequest
import com.vknxworks.wrtctrl.data.settings.LockTimeout
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Timer
import androidx.fragment.app.FragmentActivity
import android.widget.Toast
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.container
import com.vknxworks.wrtctrl.data.settings.ThemeChoice
import com.vknxworks.wrtctrl.ui.components.Group
import com.vknxworks.wrtctrl.ui.components.GroupChoice
import com.vknxworks.wrtctrl.ui.components.GroupRow
import com.vknxworks.wrtctrl.ui.nav.LocalNavigator
import com.vknxworks.wrtctrl.ui.components.GroupSwitch
import com.vknxworks.wrtctrl.ui.components.ScreenTopBar
import kotlinx.coroutines.launch

/** App settings (Ctrl / Signal is dark only, so there is no appearance section): notifications, privacy and security (app lock), warning thresholds and widgets. */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val settings by c.settings.settings.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.settings))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            val lockOn = c.vault.enabled
            val bioAvailable = remember { AppLock.biometricAvailable(context) }
            val bioFailed = stringResource(R.string.lock_fingerprint_failed)
            val bioTitle = stringResource(R.string.lock_fingerprint_prompt_title)
            val bioSubtitle = stringResource(R.string.lock_fingerprint_prompt_subtitle)
            Group(stringResource(R.string.group_privacy), footer = stringResource(R.string.lock_footer) + "\n\n" + stringResource(R.string.hide_content_footer)) {
                GroupSwitch(
                    stringResource(R.string.lock_app),
                    stringResource(R.string.lock_app_subtitle),
                    checked = lockOn,
                    icon = Icons.Outlined.Lock,
                ) { on -> AppLock.request = if (on) PinRequest.ENABLE else PinRequest.DISABLE }
                if (lockOn) {
                    GroupRow(
                        stringResource(R.string.lock_change_pin),
                        stringResource(R.string.lock_change_pin_subtitle, LockVault.PIN_MIN),
                        icon = Icons.Outlined.Password,
                        showChevron = true,
                        onClick = { AppLock.request = PinRequest.CHANGE },
                    )
                    GroupSwitch(
                        stringResource(R.string.lock_fingerprint),
                        stringResource(if (bioAvailable || c.vault.biometricEnabled) R.string.lock_fingerprint_subtitle else R.string.lock_fingerprint_unavailable),
                        checked = c.vault.biometricEnabled,
                        icon = Icons.Outlined.Fingerprint,
                    ) { on ->
                        if (!on) {
                            c.vault.disableBiometric()
                        } else if (bioAvailable) {
                            val activity = context as? FragmentActivity
                            val cipher = c.vault.biometricEnrolCipher()
                            if (activity == null || cipher == null) {
                                Toast.makeText(context, bioFailed, Toast.LENGTH_SHORT).show()
                            } else {
                                AppLock.biometricPrompt(activity, bioTitle, bioSubtitle, cipher) { unlocked ->
                                    if (!c.vault.finishBiometricEnrol(unlocked)) Toast.makeText(context, bioFailed, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    GroupChoice(
                        stringResource(R.string.lock_again),
                        LockTimeout.entries.map { it to stringResource(it.label) },
                        settings.lockTimeout,
                        icon = Icons.Outlined.Timer,
                    ) { v -> c.appScope.launch { c.settings.update { it.copy(lockTimeout = v) } } }
                    GroupSwitch(
                        stringResource(R.string.lock_changes),
                        stringResource(R.string.lock_changes_subtitle),
                        checked = settings.lockChanges,
                        icon = Icons.Outlined.Key,
                    ) { on -> c.appScope.launch { c.settings.update { it.copy(lockChanges = on) } } }
                }
                GroupSwitch(
                    stringResource(R.string.hide_screenshots),
                    stringResource(R.string.hide_screenshots_subtitle),
                    checked = settings.hideScreenshots,
                    icon = Icons.Outlined.VisibilityOff,
                ) { on -> c.appScope.launch { c.settings.update { it.copy(hideScreenshots = on) } } }
                GroupSwitch(
                    stringResource(R.string.hide_recents),
                    stringResource(R.string.hide_recents_subtitle),
                    checked = settings.hideRecents,
                    icon = Icons.Outlined.Layers,
                ) { on -> c.appScope.launch { c.settings.update { it.copy(hideRecents = on) } } }
            }
            Group(stringResource(R.string.group_warnings)) {
                GroupChoice(
                    stringResource(R.string.overlay_warn_title),
                    listOf(70, 75, 80, 85, 90, 95).map { it to stringResource(R.string.overlay_warn_subtitle, it) },
                    settings.overlayWarnPercent,
                    icon = Icons.Outlined.Storage,
                ) { pct -> c.appScope.launch { c.settings.update { it.copy(overlayWarnPercent = pct) } } }
                GroupChoice(
                    stringResource(R.string.mem_warn_title),
                    listOf(75, 80, 85, 90, 95).map { it to stringResource(R.string.mem_warn_subtitle, it) },
                    settings.memWarnPercent,
                    icon = Icons.Outlined.Memory,
                ) { pct -> c.appScope.launch { c.settings.update { it.copy(memWarnPercent = pct) } } }
            }
        }
    }
}
