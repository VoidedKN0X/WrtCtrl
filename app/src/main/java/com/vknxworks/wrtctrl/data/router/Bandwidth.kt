package com.vknxworks.wrtctrl.data.router

import com.vknxworks.wrtctrl.data.api.RouterAcl
import com.vknxworks.wrtctrl.data.api.UbusClient
import com.vknxworks.wrtctrl.data.api.UbusJson
import com.vknxworks.wrtctrl.data.api.Usage
import com.vknxworks.wrtctrl.data.api.arr
import com.vknxworks.wrtctrl.data.api.exec
import com.vknxworks.wrtctrl.data.api.long
import com.vknxworks.wrtctrl.data.api.normMac
import com.vknxworks.wrtctrl.data.api.objects
import com.vknxworks.wrtctrl.data.api.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Per-device bandwidth, from what the router has:
 * - nlbwmon: totals per accounting period (`nlbw -c list` gives the periods, `nlbw -c json -g mac -t <date>`
 *   one period's totals per MAC). Periods are nlbwmon's database_interval: monthly by default, daily when
 *   it's set to one day.
 * - Otherwise live rates from the connection tracking table (LuCI's `luci.getConntrackList`, which sums each
 *   connection's bytes over both directions), sampled every few seconds.
 */

const val NLBW = "/usr/sbin/nlbw"

/** What the login and the router allow for bandwidth. */
enum class BandwidthSource { NLBWMON, CONNTRACK, NONE }

/** One nlbwmon accounting period for one device. */
data class PeriodUsage(val start: LocalDate, val rxBytes: Long, val txBytes: Long) {
    val total: Long get() = rxBytes + txBytes
}

/** How long nlbwmon's periods are, worked out from their start dates. */
enum class PeriodLength { DAY, WEEK, MONTH, OTHER }

/** One tracked connection, as LuCI reports it. [key] identifies it between two samples. */
data class ConnEntry(val key: String, val src: String, val dst: String, val bytes: Long)

object Bandwidth {
    /** The ACL entries that let WrtCtrl read nlbwmon's periods and one period's totals. */
    fun canReadNlbwPeriods(acl: RouterAcl): Boolean =
        acl.canExec("$NLBW -c list") && acl.canExec("$NLBW -c json -g mac -t 2026-01-01")

    fun canReadConntrack(acl: RouterAcl): Boolean = acl.canCall("luci", "getConntrackList")

    fun source(acl: RouterAcl, nlbwInstalled: Boolean): BandwidthSource = when {
        nlbwInstalled && canReadNlbwPeriods(acl) -> BandwidthSource.NLBWMON
        canReadConntrack(acl) -> BandwidthSource.CONNTRACK
        else -> BandwidthSource.NONE
    }

    /** `nlbw -c list`: one YYYY-MM-DD per line, oldest first. */
    fun parsePeriods(stdout: String): List<LocalDate> =
        stdout.lineSequence().mapNotNull { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }.distinct().sorted().toList()

    fun periodLength(periods: List<LocalDate>): PeriodLength {
        if (periods.size < 2) return PeriodLength.OTHER
        val gaps = periods.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }
        return when {
            gaps.all { it == 1L } -> PeriodLength.DAY
            gaps.all { it == 7L } -> PeriodLength.WEEK
            periods.zipWithNext { a, b -> ChronoUnit.MONTHS.between(a, b) == 1L && a.dayOfMonth == b.dayOfMonth }.all { it } -> PeriodLength.MONTH
            else -> PeriodLength.OTHER
        }
    }

    /** `nlbw -c json -g mac`: totals per MAC (columns by name, so their order doesn't matter). */
    fun parseUsage(stdout: String): Map<String, Usage> {
        val o = runCatching { UbusJson.parseToJsonElement(stdout) as? JsonObject }.getOrNull() ?: return emptyMap()
        val cols = o.arr("columns").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        val iMac = cols.indexOf("mac")
        if (iMac < 0) return emptyMap()
        val iRx = cols.indexOf("rx_bytes")
        val iTx = cols.indexOf("tx_bytes")
        val iConn = cols.indexOf("conns")
        val out = LinkedHashMap<String, Usage>()
        o.arr("data").forEach { row ->
            val r = row as? JsonArray ?: return@forEach
            fun at(i: Int): String? = if (i >= 0) (r.getOrNull(i) as? JsonPrimitive)?.contentOrNull else null
            val mac = normMac(at(iMac).orEmpty())
            if (mac.isBlank()) return@forEach
            val u = Usage(at(iRx)?.toLongOrNull() ?: 0, at(iTx)?.toLongOrNull() ?: 0, at(iConn)?.toLongOrNull() ?: 0)
            // The same MAC can show up once per address family: add them up.
            out[mac] = out[mac]?.let { Usage(it.rxBytes + u.rxBytes, it.txBytes + u.txBytes, it.connections + u.connections) } ?: u
        }
        return out
    }

    /** `luci.getConntrackList`: `{ result: [ { layer3, layer4, src, dst, sport, dport, bytes, … } ] }`. */
    fun parseConntrack(reply: JsonObject): List<ConnEntry> =
        reply.arr("result").objects().mapNotNull { e ->
            val src = e.str("src")
            val dst = e.str("dst")
            if (src.isBlank() || dst.isBlank()) return@mapNotNull null
            val key = listOf(e.str("layer3"), e.str("layer4"), src, e.str("sport"), dst, e.str("dport")).joinToString("|")
            ConnEntry(key, src.lowercase(), dst.lowercase(), e.long("bytes"))
        }

    /**
     * Bytes per second of the device with [ips] between two samples [seconds] apart: for each of its
     * connections, the bytes since the first sample (all of them for a connection that's new). Closed
     * connections drop out, so short bursts between samples can be missed; it's an estimate.
     */
    fun rate(before: List<ConnEntry>, after: List<ConnEntry>, ips: Collection<String>, seconds: Double): Double {
        if (seconds <= 0) return 0.0
        val mine = ips.map { ThisPhone.normaliseIp(it) }.filter { it.isNotEmpty() }.toSet()
        if (mine.isEmpty()) return 0.0
        val old = before.associate { it.key to it.bytes }
        var total = 0L
        for (c in after) {
            if (c.src !in mine && c.dst !in mine) continue
            val prev = old[c.key]
            total += if (prev == null || c.bytes < prev) c.bytes else c.bytes - prev
        }
        return total / seconds
    }

    /** Whether conntrack counts bytes at all (nf_conntrack_acct on): any connection with bytes. */
    fun countsBytes(entries: List<ConnEntry>): Boolean = entries.isEmpty() || entries.any { it.bytes > 0 }
}

suspend fun UbusClient.nlbwPeriods(): List<LocalDate> = Bandwidth.parsePeriods(exec(NLBW, listOf("-c", "list")).stdout)

/** One period's totals per MAC ([start] as `nlbw -c list` gave it). */
suspend fun UbusClient.nlbwUsage(start: LocalDate): Map<String, Usage> =
    Bandwidth.parseUsage(exec(NLBW, listOf("-c", "json", "-g", "mac", "-t", start.toString())).stdout)

/** The last [max] periods for [mac], oldest first; a period the device didn't use counts as 0. */
suspend fun UbusClient.nlbwHistory(mac: String, max: Int = 12): List<PeriodUsage> {
    val m = normMac(mac)
    return nlbwPeriods().takeLast(max).map { p ->
        val u = runCatching { nlbwUsage(p)[m] }.getOrNull()
        PeriodUsage(p, u?.rxBytes ?: 0, u?.txBytes ?: 0)
    }
}

suspend fun UbusClient.conntrack(): List<ConnEntry> = Bandwidth.parseConntrack(call("luci", "getConntrackList"))
