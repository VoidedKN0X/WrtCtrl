package com.vknxworks.wrtctrl.ui.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.vknxworks.wrtctrl.MainActivity
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.container
import com.vknxworks.wrtctrl.data.lock.AppLock
import com.vknxworks.wrtctrl.data.widgets.WidgetData
import com.vknxworks.wrtctrl.data.widgets.WidgetSnapshot
import com.vknxworks.wrtctrl.data.widgets.WidgetStore
import com.vknxworks.wrtctrl.ui.components.formatRate
import com.vknxworks.wrtctrl.ui.components.relativeTime
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ctrl / Signal colours for the widget (colour resources with a values-night variant). */
private object WidgetColors {
    val background = ColorProvider(R.color.signal_widget_bg)
    val border = ColorProvider(R.color.signal_widget_border)
    val text = ColorProvider(R.color.signal_widget_text)
    val muted = ColorProvider(R.color.signal_widget_muted)
    val stopped = ColorProvider(R.color.signal_widget_stopped)
    val accentText = ColorProvider(R.color.signal_widget_accent_text)
    val critical = ColorProvider(R.color.signal_widget_error)
}

private fun label(color: ColorProvider = WidgetColors.muted) =
    TextStyle(color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)

/** Opens WrtCtrl on the Overview. */
private fun openApp(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(Shortcuts.ACTION_OVERVIEW)
        .setData(Uri.parse("wrtctrl-widget://overview"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

/** Home-screen widget: WAN status, WAN throughput and online clients of the active router. Tap opens WrtCtrl. */
class RouterWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Away for longer than the "Lock again" time: lock first, so the widget doesn't keep showing the router.
        withContext(Dispatchers.Main) { AppLock.lockIfExpired() }
        val locked = context.container.backgroundBlocked()
        val snapshot = if (locked) null else WidgetStore.read(context)
        provideContent { WidgetContent(snapshot, locked) }
    }
}

@Composable
private fun Frame(content: @Composable () -> Unit) {
    val context = LocalContext.current
    Box(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(22.dp)
            .background(WidgetColors.border)
            .padding(1.dp)
            .clickable(actionStartActivity(openApp(context))),
    ) {
        Box(GlanceModifier.fillMaxSize().cornerRadius(21.dp).background(WidgetColors.background).padding(14.dp)) { content() }
    }
}

@Composable
private fun WidgetContent(s: WidgetSnapshot?, locked: Boolean) {
    val context = LocalContext.current
    Frame {
        if (locked || s == null || s.routerId.isEmpty()) {
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    context.getString(if (locked) R.string.widget_locked else R.string.app_name),
                    style = TextStyle(color = WidgetColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    context.getString(if (locked) R.string.widget_locked_subtitle else R.string.widget_no_data),
                    style = TextStyle(color = WidgetColors.muted, fontSize = 12.sp),
                    maxLines = 2,
                )
            }
            return@Frame
        }
        val wanColor = when {
            s.unreachable -> WidgetColors.stopped
            s.wanUp == true -> WidgetColors.accentText
            s.wanUp == false -> WidgetColors.critical
            else -> WidgetColors.stopped
        }
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.size(8.dp).cornerRadius(2.dp).background(wanColor)) {}
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    context.getString(
                        when {
                            s.unreachable -> R.string.widget_unreachable
                            s.wanUp == true -> R.string.widget_wan_up
                            s.wanUp == false -> R.string.widget_wan_down
                            else -> R.string.widget_wan_unknown
                        },
                    ).uppercase(),
                    style = label(wanColor),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(s.routerName.uppercase(), style = label(), maxLines = 1)
            }
            Spacer(GlanceModifier.height(6.dp))
            Text(
                "↓ " + formatRate(s.down),
                style = TextStyle(color = WidgetColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Text("↑ " + formatRate(s.up), style = TextStyle(color = WidgetColors.muted, fontSize = 13.sp), maxLines = 1)
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (s.online >= 0) context.getString(R.string.widget_online, s.online) else "",
                    style = TextStyle(color = WidgetColors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(
                    if (s.updatedAt > 0) relativeTime(s.updatedAt) else "",
                    style = label(),
                    maxLines = 1,
                )
            }
            if (s.wanIp.isNotBlank()) Text(s.wanIp, style = label(), maxLines = 1)
        }
    }
}

/** Keeps the refresh schedule in line with the widgets on the home screen. */
class RouterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RouterWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        update(context, refreshNow = true)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        update(context, refreshNow = false)
        WidgetStore.clear(context)
    }

    private fun update(context: Context, refreshNow: Boolean) {
        val app = context.applicationContext
        scope.launch {
            WidgetData.schedule(app)
            if (refreshNow) WidgetData.refreshSoon(app)
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })
    }
}
