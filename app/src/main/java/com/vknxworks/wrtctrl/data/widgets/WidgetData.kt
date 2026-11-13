package com.vknxworks.wrtctrl.data.widgets

import android.content.Context
import android.service.quicksettings.TileService
import android.content.ComponentName
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vknxworks.wrtctrl.container
import com.vknxworks.wrtctrl.data.api.UbusJson
import com.vknxworks.wrtctrl.data.api.deviceStats
import com.vknxworks.wrtctrl.data.api.interfaces
import com.vknxworks.wrtctrl.data.api.wan
import com.vknxworks.wrtctrl.data.lock.AppLock
import com.vknxworks.wrtctrl.data.router.loadClients
import com.vknxworks.wrtctrl.ui.network.guestNetworks
import com.vknxworks.wrtctrl.ui.widgets.GuestWifiTileService
import com.vknxworks.wrtctrl.ui.widgets.RouterWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.TimeUnit

/** What the widget and the tile show about the active router. Kept in no-backup storage. */
@Serializable
data class WidgetSnapshot(
    val routerId: String = "",
    val routerName: String = "",
    /** The WAN is up; null when unknown (no WAN, or not readable). */
    val wanUp: Boolean? = null,
    val wanIp: String = "",
    /** WAN throughput at the last refresh, in bits per second. */
    val down: Double = 0.0,
    val up: Double = 0.0,
    val online: Int = -1,
    val clients: Int = -1,
    /** The router has a guest Wi-Fi network, and whether it's on. */
    val hasGuest: Boolean = false,
    val guestOn: Boolean = false,
    val updatedAt: Long = 0,
    /** The last refresh couldn't reach the router; the values are from [updatedAt]. */
    val unreachable: Boolean = false,
)

object WidgetStore {
    private fun file(context: Context) = File(context.noBackupFilesDir, "widget.json")

    fun read(context: Context): WidgetSnapshot? =
        runCatching { UbusJson.decodeFromString(WidgetSnapshot.serializer(), file(context).readText()) }.getOrNull()

    fun save(context: Context, snapshot: WidgetSnapshot) {
        runCatching { file(context).writeText(UbusJson.encodeToString(WidgetSnapshot.serializer(), snapshot)) }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}

/**
 * Keeps the widget and the guest Wi-Fi tile current: reads the active router (only reads), every
 * 30 minutes while a widget is placed, after every background check, and when the app asks. Nothing is
 * read while the login is sealed by the app lock; the widget then only says that WrtCtrl is locked.
 */
object WidgetData {
    private val lock = Mutex()

    suspend fun anyWidgets(context: Context): Boolean =
        runCatching { GlanceAppWidgetManager(context).getGlanceIds(RouterWidget::class.java).isNotEmpty() }.getOrDefault(false)

    /** Reads the active router (never throws) and redraws the widget and the tile. */
    suspend fun refresh(context: Context) = lock.withLock {
        val app = context.applicationContext
        val c = app.container
        // Away for longer than the "Lock again" time: lock now, so sealed logins aren't used and the widget says it's locked.
        withContext(Dispatchers.Main) { AppLock.lockIfExpired() }
        if (c.backgroundBlocked()) {
            redraw(app)
            return@withLock
        }
        val router = c.routerStore.routers.value.firstOrNull { it.id == c.routerStore.activeId.value }
        if (router == null) {
            WidgetStore.clear(app)
            redraw(app)
            return@withLock
        }
        val previous = WidgetStore.read(app)?.takeIf { it.routerId == router.id }
        val client = c.sessions.clientFor(router)
        val now = System.currentTimeMillis()
        val next = try {
            val wan = runCatching { client.interfaces().wan() }.getOrNull()
            var down = 0.0
            var up = 0.0
            if (wan != null && wan.statsDevice.isNotBlank()) {
                runCatching {
                    val a = client.deviceStats(wan.statsDevice)
                    val t0 = System.currentTimeMillis()
                    delay(1_500)
                    val b = client.deviceStats(wan.statsDevice)
                    val secs = (System.currentTimeMillis() - t0) / 1000.0
                    if (secs > 0) {
                        down = ((b.rxBytes - a.rxBytes).coerceAtLeast(0) * 8) / secs
                        up = ((b.txBytes - a.txBytes).coerceAtLeast(0) * 8) / secs
                    }
                }
            }
            val clients = runCatching { loadClients(app, client, router.id) }.getOrNull()
            val guests = client.guestNetworks()
            WidgetSnapshot(
                routerId = router.id,
                routerName = router.name,
                wanUp = wan?.up,
                wanIp = wan?.ipv4?.firstOrNull()?.address.orEmpty(),
                down = down,
                up = up,
                online = clients?.count { it.online } ?: previous?.online ?: -1,
                clients = clients?.size ?: previous?.clients ?: -1,
                hasGuest = guests.isNotEmpty(),
                guestOn = guests.any { !it.disabled },
                updatedAt = now,
            )
        } catch (e: Exception) {
            (previous ?: WidgetSnapshot(routerId = router.id, routerName = router.name)).copy(unreachable = true)
        }
        WidgetStore.save(app, next)
        redraw(app)
    }

    /** Redraws the widget and the tile from what's saved (no network). */
    suspend fun redraw(context: Context) {
        runCatching { RouterWidget().updateAll(context) }
        runCatching { TileService.requestListeningState(context, ComponentName(context, GuestWifiTileService::class.java)) }
    }

    /** Starts or stops the 30-minute refresh to match whether a widget is placed. */
    suspend fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (anyWidgets(context)) {
            val req = PeriodicWorkRequestBuilder<WidgetWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        } else {
            wm.cancelUniqueWork(PERIODIC)
        }
    }

    /** One refresh as soon as there's a network (widget placed, router switched, app opened). */
    fun refreshSoon(context: Context) {
        val req = OneTimeWorkRequestBuilder<WidgetWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, req)
    }

    private const val PERIODIC = "wrtctrl-widget"
    private const val ONCE = "wrtctrl-widget-now"
}

class WidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        withContext(Dispatchers.IO) { WidgetData.refresh(applicationContext) }
        return Result.success()
    }
}
