package com.vknxworks.wrtctrl.data.notify

import android.content.Context
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.data.api.PKG_CALL
import com.vknxworks.wrtctrl.data.api.UbusClient
import com.vknxworks.wrtctrl.data.api.exec
import com.vknxworks.wrtctrl.data.api.interfaces
import com.vknxworks.wrtctrl.data.api.packageManager
import com.vknxworks.wrtctrl.data.api.upgradable
import com.vknxworks.wrtctrl.data.api.wan
import com.vknxworks.wrtctrl.data.router.Router
import com.vknxworks.wrtctrl.data.router.loadClients
import com.vknxworks.wrtctrl.ui.overview.UpdateCounts
import kotlinx.serialization.Serializable

/** What the last background check of a router saw, to notify only about changes. */
@Serializable
data class CheckState(
    /** When the last check ran (ms) and how it went ("ok" or "fail"). */
    val lastCheckAt: Long = 0,
    val lastResult: String = "",
    /** Checks in a row that couldn't reach the router. */
    val failures: Int = 0,
    val unreachableNotified: Boolean = false,
    /** The WAN was up at the last check; null before the first. */
    val wanUp: Boolean? = null,
    /** Every device seen so far, so only new ones are announced. Empty until the first check fills it. */
    val knownMacs: Set<String> = emptySet(),
    val seeded: Boolean = false,
    /** The upgradable packages last notified about, and when the lists were last checked (ms). */
    val updatesKey: String = "",
    val updatesCheckedAt: Long = 0,
)

/** The outcome of one check: notifications to post or remove, and the new state. */
class CheckResult(val post: List<Alert>, val cancel: List<String>, val state: CheckState)

/**
 * One background check of one router. Only reads (plus refreshing the package lists once a day when
 * the login allows it); never changes anything on the router.
 */
class Checker(private val context: Context) {

    suspend fun check(router: Router, client: UbusClient, s: NotifySettings, prev: CheckState): CheckResult {
        val now = System.currentTimeMillis()
        val post = mutableListOf<Alert>()
        val cancel = mutableListOf<String>()
        val name = router.name.ifBlank { router.host }
        fun key(what: String) = "${router.id}:$what"

        // 1. Reachable, and still able to sign in?
        try {
            client.call("system", "board")
        } catch (e: Exception) {
            val failures = prev.failures + 1
            // Two misses in a row, so a single dropped request doesn't wake anyone.
            val notify = failures >= 2 && !prev.unreachableNotified && s.on(NotifyEvent.UNREACHABLE)
            if (notify) {
                post += Alert(key("unreachable"), NotifyEvent.UNREACHABLE, Severity.CRITICAL, context.getString(R.string.nt_unreachable_title, name), context.getString(R.string.nt_unreachable_text, router.host))
            }
            return CheckResult(post, cancel, prev.copy(lastCheckAt = now, lastResult = "fail", failures = failures, unreachableNotified = prev.unreachableNotified || notify))
        }
        var state = prev.copy(lastCheckAt = now, lastResult = "ok", failures = 0, unreachableNotified = false)
        if (prev.unreachableNotified) cancel += key("unreachable")

        // 2. WAN down or back up.
        runCatching { client.interfaces().wan() }.getOrNull()?.let { wan ->
            if (prev.wanUp == true && !wan.up && s.on(NotifyEvent.WAN_DOWN)) {
                post += Alert(key("wan"), NotifyEvent.WAN_DOWN, Severity.CRITICAL, context.getString(R.string.nt_wan_down_title, name), context.getString(R.string.nt_wan_down_text))
            }
            if (prev.wanUp == false && wan.up) {
                cancel += key("wan")
                if (s.on(NotifyEvent.WAN_UP)) {
                    post += Alert(key("wan-up"), NotifyEvent.WAN_UP, Severity.INFO, context.getString(R.string.nt_wan_up_title, name), wan.ipv4.firstOrNull()?.address?.let { context.getString(R.string.nt_wan_up_text, it) } ?: "")
                }
            }
            state = state.copy(wanUp = wan.up)
        }

        // 3. New devices: the first check only learns what's there.
        runCatching { loadClients(context, client, router.id) }.getOrNull()?.let { clients ->
            val macs = clients.map { it.mac }.toSet()
            if (state.seeded && s.on(NotifyEvent.NEW_DEVICE)) {
                clients.filter { it.mac !in state.knownMacs }.take(MAX_NEW_DEVICES).forEach { cl ->
                    val label = cl.name.ifBlank { cl.vendor ?: context.getString(R.string.client_unnamed) }
                    val detail = listOfNotNull(cl.ip.ifBlank { null }, cl.mac, cl.vendor).joinToString(" · ")
                    post += Alert(key("new:${cl.mac}"), NotifyEvent.NEW_DEVICE, Severity.WARNING, context.getString(R.string.nt_new_device_title, label, name), detail)
                }
            }
            state = state.copy(knownMacs = (state.knownMacs + macs).toList().takeLast(MAX_KNOWN).toSet(), seeded = true)
        }

        // 4. Package updates, at most once a day: refresh the lists (when allowed), then one digest.
        if (s.on(NotifyEvent.UPDATES) && now - prev.updatesCheckedAt > DAY_MS) {
            runCatching {
                if (client.acl?.canExec("$PKG_CALL update") == true) client.exec(PKG_CALL, listOf("update"))
                val list = client.upgradable(client.packageManager())
                UpdateCounts.set(router.id, list.size)
                val k = list.joinToString(",") { it.name + "=" + it.available }
                if (list.isNotEmpty() && k != prev.updatesKey) {
                    post += Alert(
                        key("updates"),
                        NotifyEvent.UPDATES,
                        Severity.INFO,
                        context.resources.getQuantityString(R.plurals.nt_updates_title, list.size, list.size, name),
                        list.take(12).joinToString(", ") { it.name } + if (list.size > 12) " …" else "",
                    )
                }
                if (list.isEmpty()) cancel += key("updates")
                state = state.copy(updatesKey = k, updatesCheckedAt = now)
            }
        }
        return CheckResult(post, cancel, state)
    }

    private companion object {
        const val MAX_NEW_DEVICES = 5
        const val MAX_KNOWN = 2000
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
