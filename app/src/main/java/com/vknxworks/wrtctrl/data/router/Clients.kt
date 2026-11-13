package com.vknxworks.wrtctrl.data.router

import android.content.Context
import com.vknxworks.wrtctrl.data.api.UbusClient
import com.vknxworks.wrtctrl.data.api.UciSection
import com.vknxworks.wrtctrl.data.api.Usage
import com.vknxworks.wrtctrl.data.api.dhcpLeases
import com.vknxworks.wrtctrl.data.api.hostHints
import com.vknxworks.wrtctrl.data.api.neighbours
import com.vknxworks.wrtctrl.data.api.normMac
import com.vknxworks.wrtctrl.data.api.stations
import com.vknxworks.wrtctrl.data.api.uciGet
import com.vknxworks.wrtctrl.data.api.uciAdd
import com.vknxworks.wrtctrl.data.api.uciDelete
import com.vknxworks.wrtctrl.data.api.uciSet
import com.vknxworks.wrtctrl.data.api.wireless
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** How a device is connected right now. */
sealed interface Link {
    data object Wired : Link

    /** On Wi-Fi: [band] like "2g", "5g", "6g"; signal in dBm; rates in kbit/s. */
    data class Wifi(val band: String, val ssid: String, val ifname: String, val signal: Int, val rxRate: Long, val txRate: Long) : Link

    /** Not seen on the network right now. */
    data object Offline : Link
}

/** One device on the network, merged from everything the router knows about it. */
data class NetClient(
    val mac: String,
    /** Hostname from DHCP or the router's host hints; empty when it never sent one. */
    val hostname: String,
    val ipv4: List<String>,
    val ipv6: List<String>,
    val vendor: String?,
    val randomisedMac: Boolean,
    val link: Link,
    /** Seconds until the DHCP lease runs out; null without a lease, <= 0 for one that doesn't expire. */
    val leaseExpires: Long?,
    /** The static lease (uci dhcp host section) for this MAC, if any. */
    val staticLease: UciSection?,
    /** The firewall rule WrtCtrl added to block this device, if any. */
    val blockRule: UciSection?,
    val nickname: String,
    val note: String,
    val usage: Usage?,
    /** The LAN bridge or device it was seen on (for Wake-on-LAN). */
    val dev: String,
) {
    val online: Boolean get() = link !is Link.Offline
    val name: String get() = nickname.ifBlank { staticLease?.opt("name")?.ifBlank { null } ?: hostname }
    val ip: String get() = ipv4.firstOrNull() ?: ipv6.firstOrNull().orEmpty()
    val isStatic: Boolean get() = staticLease != null
    val blocked: Boolean get() = blockRule != null && blockRule.flag("enabled", true)
    val signal: Int? get() = (link as? Link.Wifi)?.signal
    val band: String? get() = (link as? Link.Wifi)?.band
}

/** Name prefix of the firewall rules WrtCtrl adds to block a device from the internet. */
const val BLOCK_RULE_PREFIX = "WrtCtrl block "

/**
 * Builds the client list from DHCP leases, host hints, the neighbour table and every Wi-Fi network's
 * association list, plus static leases and WrtCtrl's block rules. Each source is optional: whatever the
 * login may read is used, the rest is skipped.
 */
suspend fun loadClients(context: Context, client: UbusClient, routerId: String, usage: Map<String, Usage> = emptyMap()): List<NetClient> = coroutineScope {
    val acl = client.acl
    fun can(obj: String, method: String) = acl?.canCall(obj, method) != false

    val leasesD = async { if (can("luci-rpc", "getDHCPLeases")) runCatching { client.dhcpLeases() }.getOrDefault(emptyList()) else emptyList() }
    val hintsD = async { if (can("luci-rpc", "getHostHints")) runCatching { client.hostHints() }.getOrDefault(emptyList()) else emptyList() }
    val neighD = async { if (acl?.canExec("/sbin/ip -4 -j neigh show") != false) runCatching { client.neighbours() }.getOrDefault(emptyList()) else emptyList() }
    val wifiD = async {
        if (!can("luci-rpc", "getWirelessDevices")) return@async emptyList<Pair<com.vknxworks.wrtctrl.data.api.WifiIface, List<com.vknxworks.wrtctrl.data.api.Station>>>()
        val radios = runCatching { client.wireless() }.getOrDefault(emptyList())
        radios.flatMap { it.ifaces }.filter { it.ifname.isNotBlank() && !it.disabled }.map { iface ->
            iface to (if (can("iwinfo", "assoclist")) runCatching { client.stations(iface.ifname) }.getOrDefault(emptyList()) else emptyList())
        }
    }
    val hostsD = async { if (acl?.canReadUci("dhcp") != false) runCatching { client.uciGet("dhcp", "host") }.getOrDefault(emptyList()) else emptyList() }
    val rulesD = async { if (acl?.canReadUci("firewall") != false) runCatching { client.uciGet("firewall", "rule") }.getOrDefault(emptyList()) else emptyList() }

    val leases = leasesD.await()
    val hints = hintsD.await()
    val neigh = neighD.await()
    val wifi = wifiD.await()
    val hosts = hostsD.await()
    val rules = rulesD.await()
    val notes = ClientNotes.all(context, routerId)

    val macs = linkedSetOf<String>()
    leases.forEach { macs += it.mac }
    hints.forEach { macs += it.mac }
    neigh.forEach { macs += it.mac }
    wifi.forEach { (_, st) -> st.forEach { macs += it.mac } }
    hosts.forEach { h -> h.list("mac").forEach { m -> normMac(m).takeIf { it.isNotBlank() }?.let { macs += it } } }
    macs.remove("")

    val stationByMac = wifi.flatMap { (iface, st) -> st.map { it.mac to (iface to it) } }.toMap()

    withContext(Dispatchers.Default) {
        macs.map { mac ->
            val myLeases = leases.filter { it.mac == mac }
            val hint = hints.firstOrNull { it.mac == mac }
            val myNeigh = neigh.filter { it.mac == mac }
            val host = hosts.firstOrNull { h -> h.list("mac").any { normMac(it) == mac } }
            val rule = rules.firstOrNull { r -> BlockRules.isOurs(r) && r.list("src_mac").any { normMac(it) == mac } }
            val station = stationByMac[mac]
            val v4 = (myLeases.map { it.ip }.filter { '.' in it } + hint?.ipv4.orEmpty() + myNeigh.map { it.ip }.filter { '.' in it } +
                listOfNotNull(host?.opt("ip")?.ifBlank { null })).distinct()
            val v6 = (myLeases.map { it.ip }.filter { ':' in it } + hint?.ipv6.orEmpty() + myNeigh.map { it.ip }.filter { ':' in it && !it.startsWith("fe80") }).distinct()
            val link = when {
                station != null -> Link.Wifi(station.first.band, station.first.ssid, station.first.ifname, station.second.signal, station.second.rxRate, station.second.txRate)
                myNeigh.any { it.online } -> Link.Wired
                else -> Link.Offline
            }
            val note = notes[mac]
            NetClient(
                mac = mac,
                hostname = myLeases.firstOrNull { it.hostname.isNotBlank() }?.hostname ?: hint?.name.orEmpty(),
                ipv4 = v4,
                ipv6 = v6,
                vendor = Oui.vendor(context, mac),
                randomisedMac = Oui.isRandomised(mac),
                link = link,
                leaseExpires = myLeases.firstOrNull { '.' in it.ip }?.expires ?: myLeases.firstOrNull()?.expires,
                staticLease = host,
                blockRule = rule,
                nickname = note?.nickname.orEmpty(),
                note = note?.note.orEmpty(),
                usage = usage[mac],
                dev = myNeigh.firstOrNull()?.dev.orEmpty(),
            )
        }
    }
}

// ---------- Changes to a client (staged; apply with Changes) ----------

/** Makes the lease of [mac] static with [ip] and [name] (a uci dhcp host section), or updates the existing one. */
suspend fun UbusClient.stageStaticLease(mac: String, ip: String, name: String, existing: UciSection?) {
    val values = mapOf("name" to name, "mac" to mac, "ip" to ip, "dns" to "1")
    if (existing != null) uciSet("dhcp", existing.name, values) else uciAdd("dhcp", "host", values)
}

suspend fun UbusClient.stageRemoveStaticLease(section: UciSection) = uciDelete("dhcp", section.name)

/**
 * Blocks [mac] from reaching the internet: a firewall rule from the LAN zone to the WAN zone that
 * refuses everything from that MAC ([target]), all the time or on a [schedule]. The device stays on the
 * local network. See [BlockRules].
 */
suspend fun UbusClient.stageBlock(mac: String, label: String, target: BlockTarget = BlockTarget.REJECT, schedule: BlockSchedule? = null) =
    stage(BlockRules.blockOps(mac, label, target, schedule))

/** Changes an existing block's target and schedule; only ever WrtCtrl's own rules. */
suspend fun UbusClient.stageUpdateBlock(rule: UciSection, target: BlockTarget, schedule: BlockSchedule?) =
    stage(BlockRules.updateOps(rule, target, schedule))

/** Removes a block; refuses any firewall rule whose name doesn't start with [BLOCK_RULE_PREFIX]. */
suspend fun UbusClient.stageUnblock(rule: UciSection) = stage(BlockRules.unblockOps(rule))

// ---------- Nicknames and notes (on the phone only) ----------

@Serializable
data class ClientNote(val nickname: String = "", val note: String = "")

/** Nicknames and notes for devices, per router. Stored on the phone only, never sent to the router. */
object ClientNotes {
    private val json = Json { ignoreUnknownKeys = true }
    private val ser = MapSerializer(String.serializer(), ClientNote.serializer())

    private fun prefs(context: Context) = context.getSharedPreferences("client_notes", Context.MODE_PRIVATE)

    fun all(context: Context, routerId: String): Map<String, ClientNote> =
        runCatching { json.decodeFromString(ser, prefs(context).getString(routerId, null) ?: "{}") }.getOrDefault(emptyMap())

    fun set(context: Context, routerId: String, mac: String, note: ClientNote) {
        val map = all(context, routerId).toMutableMap()
        if (note.nickname.isBlank() && note.note.isBlank()) map.remove(mac) else map[mac] = note
        prefs(context).edit().putString(routerId, json.encodeToString(ser, map)).apply()
    }

    fun forget(context: Context, routerId: String) {
        prefs(context).edit().remove(routerId).apply()
    }
}
