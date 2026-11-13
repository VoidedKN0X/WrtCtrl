package com.vknxworks.wrtctrl.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.ui.text.style.TextOverflow
import com.vknxworks.wrtctrl.ui.components.CtrlSectionLabel
import com.vknxworks.wrtctrl.ui.components.CtrlSquareButton
import com.vknxworks.wrtctrl.ui.components.PillButton
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import com.vknxworks.wrtctrl.ui.theme.CtrlType
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.vknxworks.wrtctrl.BuildConfig
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.container
import com.vknxworks.wrtctrl.ui.components.GroupSwitch
import com.vknxworks.wrtctrl.ui.components.errorMessage
import com.vknxworks.wrtctrl.ui.nav.RoutersRoute
import com.vknxworks.wrtctrl.ui.setup.AccessSummary
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.wrtctrl.ui.components.Group
import com.vknxworks.wrtctrl.ui.components.GroupRow
import com.vknxworks.wrtctrl.ui.nav.LocalNavigator
import com.vknxworks.wrtctrl.ui.nav.SettingsRoute
import com.vknxworks.wrtctrl.ui.theme.Gutter

private const val SPONSOR_URL = "https://github.com/sponsors/VoidedKN0X"
private const val REPO_URL = "https://github.com/VoidedKN0X/WrtCtrl"
private const val ISSUES_URL = "https://github.com/VoidedKN0X/WrtCtrl/issues"
private const val LICENCE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/**
 * Account and settings, opened from the profile picture on any tab. Shows which router you're
 * connected to (once router setup exists), settings, and the About / support section.
 */
@Composable
fun YouScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val server = active?.router
    fun open(url: String) {
        try {
            com.vknxworks.wrtctrl.data.lock.AppLock.expectReturn()
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Signal profile header: a square close button and a mono "PROFILE" label.
        Box(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Gutter, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            CtrlSquareButton(
                Icons.Outlined.Close,
                stringResource(R.string.back),
                onClick = nav::back,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            CtrlSectionLabel(stringResource(R.string.signal_profile))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 14.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(68.dp).clip(RoundedCornerShape(22.dp)).background(CtrlColors.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    if (server == null) {
                        Icon(Icons.Outlined.Router, null, Modifier.size(30.dp), tint = CtrlColors.OnAccent)
                    } else {
                        Text(server.initial.uppercase(), style = MaterialTheme.typography.headlineSmall, color = CtrlColors.OnAccent)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        server?.name ?: stringResource(R.string.you_not_connected),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (server != null) {
                        Text(server.host, style = CtrlType.Mono, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (server.isPlainHttp) {
                            Text(stringResource(R.string.router_not_encrypted), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Warn)
                        }
                    }
                    Text(
                        if (server == null) {
                            stringResource(R.string.you_not_connected_body)
                        } else {
                            listOfNotNull(
                                server.model.takeIf { it.isNotBlank() },
                                server.openWrtVersion.takeIf { it.isNotBlank() }?.let { stringResource(R.string.openwrt_version, it) },
                                server.username.takeIf { it.isNotBlank() }?.let { stringResource(R.string.signed_in_as, it) },
                            ).joinToString("\n")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = CtrlColors.Muted,
                    )
                }
            }

            if (server != null) {
                Group {
                    GroupRow(
                        stringResource(R.string.routers_title),
                        stringResource(R.string.routers_subtitle),
                        icon = Icons.Outlined.SwapHoriz,
                        showChevron = true,
                        onClick = { nav.navigate(RoutersRoute) },
                    )
                    GroupSwitch(
                        stringResource(R.string.read_only_mode),
                        stringResource(R.string.read_only_mode_subtitle),
                        checked = server.readOnly,
                        icon = Icons.Outlined.Visibility,
                    ) { c.sessions.setReadOnly(server.id, it) }
                }
                access.error?.let { e ->
                    Text(
                        errorMessage(context, e, server.host),
                        style = MaterialTheme.typography.bodySmall,
                        color = CtrlColors.Error,
                        modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp),
                    )
                }
                if (!access.loading && access.error == null) AccessSummary(access.acl)
            }

            Group {
                GroupRow(
                    stringResource(R.string.settings),
                    stringResource(R.string.settings_subtitle),
                    icon = Icons.Outlined.Settings,
                    showChevron = true,
                    onClick = { nav.navigate(SettingsRoute) },
                )
            }

            Group(stringResource(R.string.group_about)) {
                GroupRow(
                    stringResource(R.string.bugs_title),
                    stringResource(R.string.bugs_subtitle),
                    icon = Icons.Outlined.BugReport,
                    showChevron = true,
                    onClick = { open(ISSUES_URL) },
                )
                GroupRow(
                    stringResource(R.string.source_title),
                    stringResource(R.string.source_subtitle),
                    icon = Icons.Outlined.Code,
                    showChevron = true,
                    onClick = { open(REPO_URL) },
                )
                GroupRow(
                    stringResource(R.string.licence_title),
                    stringResource(R.string.licence_subtitle),
                    icon = Icons.Outlined.Gavel,
                    showChevron = true,
                    onClick = { open(LICENCE_URL) },
                )
            }

            // Support is the accent button at the bottom, as in every Ctrl app.
            Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PillButton(
                    stringResource(R.string.support_title),
                    onClick = { open(SPONSOR_URL) },
                    icon = Icons.Outlined.FavoriteBorder,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.support_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = CtrlColors.Muted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.about_footer, stringResource(R.string.app_name), BuildConfig.VERSION_NAME, stringResource(R.string.vendor)),
                    style = CtrlType.Mono,
                    color = CtrlColors.Muted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.signal_footer),
                    style = CtrlType.Label.copy(letterSpacing = 0.12.em),
                    color = CtrlColors.Muted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
