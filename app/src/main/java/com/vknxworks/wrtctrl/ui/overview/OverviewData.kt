package com.vknxworks.wrtctrl.ui.overview

import com.vknxworks.wrtctrl.data.api.CpuTimes
import com.vknxworks.wrtctrl.data.api.Mount
import com.vknxworks.wrtctrl.data.api.NetInterface
import com.vknxworks.wrtctrl.data.api.SystemInfo
import com.vknxworks.wrtctrl.data.api.UbusClient
import com.vknxworks.wrtctrl.data.api.WifiRadio
import com.vknxworks.wrtctrl.data.api.cpuFraction
import com.vknxworks.wrtctrl.data.api.cpuTimes
import com.vknxworks.wrtctrl.data.api.deviceStats
import com.vknxworks.wrtctrl.data.api.interfaces
import com.vknxworks.wrtctrl.data.api.mountPoints
import com.vknxworks.wrtctrl.data.api.systemInfo
import com.vknxworks.wrtctrl.data.api.temperature
import com.vknxworks.wrtctrl.data.api.uciChanges
import com.vknxworks.wrtctrl.data.api.wan
import com.vknxworks.wrtctrl.data.api.wireless
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Everything the Overview shows, from one refresh. Parts the login may not read are null or empty. */
data class OverviewSnapshot(
    val info: SystemInfo?,
    val storage: Mount?,
    val cpu: Double?,
    val temperature: Double?,
    val interfaces: List<NetInterface>,
    val radios: List<WifiRadio>,
    val pendingChanges: Int,
)

/** One throughput sample: down and up in bits per second. */
data class RatePoint(val time: Long, val down: Double, val up: Double)

/**
 * The live WAN throughput graph and CPU use, kept per router for as long as the app runs, so going to
 * another tab and back doesn't empty the graph. Samples only while the Overview is open.
 */
object LiveStats {
    private const val MAX_POINTS = 90

    private class State {
        var lastRx = -1L
        var lastTx = -1L
        var lastAt = 0L
        var lastDevice = ""
        var cpu: CpuTimes? = null
        val points = ArrayDeque<RatePoint>()
    }

    private val states = HashMap<String, State>()
    private val lock = Mutex()

    /** Reads the WAN device's counters and adds a sample. Returns the samples so far. */
    suspend fun sampleWan(routerId: String, client: UbusClient, device: String): List<RatePoint> {
        if (device.isBlank()) return points(routerId)
        val stats = client.deviceStats(device)
        val now = System.currentTimeMillis()
        return lock.withLock {
            val s = states.getOrPut(routerId) { State() }
            if (s.lastDevice != device) {
                s.points.clear()
                s.lastRx = -1
            }
            if (s.lastRx >= 0 && now > s.lastAt && stats.rxBytes >= s.lastRx && stats.txBytes >= s.lastTx) {
                val secs = (now - s.lastAt) / 1000.0
                s.points.addLast(RatePoint(now, (stats.rxBytes - s.lastRx) * 8 / secs, (stats.txBytes - s.lastTx) * 8 / secs))
                while (s.points.size > MAX_POINTS) s.points.removeFirst()
            }
            s.lastRx = stats.rxBytes
            s.lastTx = stats.txBytes
            s.lastAt = now
            s.lastDevice = device
            s.points.toList()
        }
    }

    fun points(routerId: String): List<RatePoint> = states[routerId]?.points?.toList().orEmpty()

    /** CPU use since the previous call for this router (null on the first). */
    suspend fun cpu(routerId: String, client: UbusClient): Double? {
        val now = client.cpuTimes() ?: return null
        return lock.withLock {
            val s = states.getOrPut(routerId) { State() }
            val f = cpuFraction(s.cpu, now)
            s.cpu = now
            f
        }
    }
}

/** Reads everything the Overview needs, in parallel; each part is skipped when the login may not read it. */
suspend fun loadOverview(routerId: String, client: UbusClient): OverviewSnapshot = coroutineScope {
    val acl = client.acl
    fun can(o: String, m: String) = acl?.canCall(o, m) != false
    val info = async { if (can("system", "info")) client.systemInfo() else null }
    val mounts = async { if (can("luci", "getMountPoints")) runCatching { client.mountPoints() }.getOrDefault(emptyList()) else emptyList() }
    val cpu = async { runCatching { LiveStats.cpu(routerId, client) }.getOrNull() }
    val temp = async { runCatching { client.temperature() }.getOrNull() }
    val ifaces = async { if (can("network.interface", "dump")) client.interfaces() else emptyList() }
    val radios = async { if (can("luci-rpc", "getWirelessDevices")) runCatching { client.wireless() }.getOrDefault(emptyList()) else emptyList() }
    val changes = async { if (can("uci", "changes")) runCatching { client.uciChanges().values.sum() }.getOrDefault(0) else 0 }
    OverviewSnapshot(
        info = info.await(),
        storage = mounts.await().let { m -> m.firstOrNull { it.mount == "/overlay" } ?: m.firstOrNull { it.mount == "/" } },
        cpu = cpu.await(),
        temperature = temp.await(),
        interfaces = ifaces.await(),
        radios = radios.await(),
        pendingChanges = changes.await(),
    )
}

/** Something on the Overview that needs a look. */
sealed interface Attention {
    data object WanDown : Attention
    data class RadioOff(val radio: WifiRadio) : Attention
    data class LowMemory(val fraction: Double) : Attention
    data class StorageFull(val fraction: Double) : Attention
    data class Updates(val count: Int) : Attention
    data class PendingChanges(val count: Int) : Attention
}

fun attention(s: OverviewSnapshot, memWarn: Int, storageWarn: Int, updates: Int?): List<Attention> = buildList {
    val wan = s.interfaces.wan()
    if (wan != null && !wan.up) add(Attention.WanDown)
    s.radios.filter { it.disabled || !it.up }.forEach { add(Attention.RadioOff(it)) }
    s.info?.memFraction?.let { if (it * 100 >= memWarn) add(Attention.LowMemory(it)) }
    s.storage?.fraction?.let { if (it * 100 >= storageWarn) add(Attention.StorageFull(it)) }
    if (updates != null && updates > 0) add(Attention.Updates(updates))
    if (s.pendingChanges > 0) add(Attention.PendingChanges(s.pendingChanges))
}

/** Upgradable package counts per router, from the last package check (System tab or background check). */
object UpdateCounts {
    private val counts = HashMap<String, Int>()

    fun set(routerId: String, count: Int) = synchronized(counts) { counts[routerId] = count }

    fun get(routerId: String): Int? = synchronized(counts) { counts[routerId] }
}
