package com.vknxworks.wrtctrl.data.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vknxworks.wrtctrl.container
import com.vknxworks.wrtctrl.data.api.UbusJson
import com.vknxworks.wrtctrl.data.lock.AppLock
import com.vknxworks.wrtctrl.data.widgets.WidgetData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Saved [CheckState] per router, in no-backup storage. */
object CheckStore {
    private val _lastCheck = MutableStateFlow<Map<String, CheckState>>(emptyMap())

    /** The last check of each router, for the notification settings page. */
    val lastCheck: StateFlow<Map<String, CheckState>> = _lastCheck.asStateFlow()

    private fun dir(context: Context) = File(context.noBackupFilesDir, "notify")

    private fun file(context: Context, routerId: String) = File(dir(context).apply { mkdirs() }, "$routerId.json")

    suspend fun read(context: Context, routerId: String): CheckState = withContext(Dispatchers.IO) {
        val s = runCatching { UbusJson.decodeFromString(CheckState.serializer(), file(context, routerId).readText()) }.getOrNull() ?: CheckState()
        _lastCheck.value = _lastCheck.value + (routerId to s)
        s
    }

    suspend fun write(context: Context, routerId: String, state: CheckState) = withContext(Dispatchers.IO) {
        runCatching { file(context, routerId).writeText(UbusJson.encodeToString(CheckState.serializer(), state)) }
        _lastCheck.value = _lastCheck.value + (routerId to state)
    }

    fun forget(context: Context, routerId: String) {
        runCatching { file(context, routerId).delete() }
        _lastCheck.value = _lastCheck.value - routerId
    }
}

/**
 * Background check of every saved router: every 15, 30, 60 or 120 minutes while background checks are
 * on (Android doesn't allow less than 15), only with a network connection, once right away for "Check
 * now", and right away when ntfy delivers a push to WrtCtrl's push address (see [Push]). Routers whose
 * login is sealed by the app lock are skipped while WrtCtrl is locked.
 */
class NotifyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val notify = c.notifySettings.current()
        if (!notify.polling && !inputData.getBoolean(KEY_FORCE, false)) return Result.success()
        // Away for longer than the "Lock again" time: lock now, so logins sealed by the app lock aren't used.
        withContext(Dispatchers.Main) { AppLock.lockIfExpired() }
        if (c.backgroundBlocked()) return Result.success()
        val checker = Checker(applicationContext)
        for (router in c.routerStore.routers.value) {
            val prev = CheckStore.read(applicationContext, router.id)
            val result = runCatching { checker.check(router, c.sessions.clientFor(router), notify, prev) }.getOrNull() ?: continue
            result.cancel.forEach { Notifier.cancel(applicationContext, it) }
            result.post.forEach { Notifier.post(applicationContext, it) }
            CheckStore.write(applicationContext, router.id, result.state)
        }
        // Widgets get fresh numbers with every check.
        runCatching { WidgetData.refresh(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "wrtctrl-check"
        private const val ONCE = "wrtctrl-check-now"
        private const val PUSHED = "wrtctrl-check-push"
        const val KEY_FORCE = "force"

        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Starts, changes or stops the periodic check to match [settings]. */
        fun schedule(context: Context, settings: NotifySettings) {
            val wm = WorkManager.getInstance(context)
            if (!settings.polling) {
                wm.cancelUniqueWork(PERIODIC)
                return
            }
            val request = PeriodicWorkRequestBuilder<NotifyWorker>(settings.intervalMinutes.toLong(), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag(PERIODIC)
                .build()
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** One check now (from "Check now"), even when background checks are off. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<NotifyWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_FORCE to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, request)
        }

        /** One check because ntfy delivered a push. Runs as soon as possible, only while background checks are on. */
        fun runFromPush(context: Context) {
            val request = OneTimeWorkRequestBuilder<NotifyWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(PUSHED, ExistingWorkPolicy.KEEP, request)
        }
    }
}
