package com.vknxworks.wrtctrl.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/*
 * The models and parsers behind Extras.kt: plain Kotlin with no router or Android in it, so the unit
 * tests can run them as they are.
 */

/** Lenient JSON for what programs on the router print. */
private val ParseJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/** The optional parts found on a router; the Network tab only shows rows for what is there. */
data class RouterFeatures(
    val tailscale: Boolean = false,
    /** The WireGuard interfaces (logical names, e.g. "wg0"). */
    val wireguard: List<String> = emptyList(),
    val ddns: Boolean = false,
    val mwan3: Boolean = false,
    val adguardHome: Boolean = false,
)

/** Init scripts that mean AdGuard Home is installed: OpenWrt's package, and AdGuard's own installer. */
internal val ADGUARD_SERVICES = setOf("adguardhome")

/** Decides what is installed from the init scripts ([services], lower or mixed case) and the interface protocols. */
internal fun featuresFrom(services: Collection<String>, wireguardInterfaces: List<String>, adguardBinary: Boolean): RouterFeatures {
    val names = services.map { it.lowercase() }.toSet()
    return RouterFeatures(
        tailscale = "tailscale" in names,
        wireguard = wireguardInterfaces,
        ddns = "ddns" in names,
        mwan3 = "mwan3" in names,
        adguardHome = adguardBinary || names.any { it in ADGUARD_SERVICES },
    )
}

data class TailscalePeer(
    val id: String,
    val name: String,
    val ips: List<String>,
    val os: String,
    val online: Boolean,
    /** This peer is the exit node the router uses now. */
    val exitNode: Boolean,
    /** This peer offers to be an exit node. */
    val exitNodeOption: Boolean,
)

/** `tailscale status --json`, the parts WrtCtrl shows. */
data class TailscaleStatus(
    val version: String,
    /** Running, Stopped, NeedsLogin, … as Tailscale names it. */
    val state: String,
    val name: String,
    val ips: List<String>,
    val tailnet: String,
    val health: List<String>,
    val peers: List<TailscalePeer>,
) {
    val running: Boolean get() = state == "Running"
    val exitNode: TailscalePeer? get() = peers.firstOrNull { it.exitNode }
}

/** The first label of a MagicDNS name ("phone.tail1234.ts.net." → "phone"). */
private fun shortDnsName(dns: String): String = dns.substringBefore('.')

internal fun parseTailscaleStatus(text: String): TailscaleStatus? {
    val o = runCatching { ParseJson.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
    if (o["BackendState"] == null && o["Self"] == null) return null
    val self = o.obj("Self") ?: JsonObject(emptyMap())
    val peers = (o.obj("Peer") ?: JsonObject(emptyMap())).values.mapNotNull { v ->
        val p = v as? JsonObject ?: return@mapNotNull null
        TailscalePeer(
            id = p.str("ID"),
            name = shortDnsName(p.str("DNSName")).ifBlank { p.str("HostName") },
            ips = p.strings("TailscaleIPs"),
            os = p.str("OS"),
            online = p.bool("Online"),
            exitNode = p.bool("ExitNode"),
            exitNodeOption = p.bool("ExitNodeOption"),
        )
    }.sortedWith(compareByDescending<TailscalePeer> { it.online }.thenBy { it.name.lowercase() })
    return TailscaleStatus(
        version = o.str("Version"),
        state = o.str("BackendState"),
        name = shortDnsName(self.str("DNSName")).ifBlank { self.str("HostName") },
        ips = self.strings("TailscaleIPs"),
        tailnet = o.obj("CurrentTailnet")?.str("Name").orEmpty(),
        health = o.strings("Health"),
        peers = peers,
    )
}

/** An IPv4 or IPv6 address and nothing else: what may be handed to `tailscale set --exit-node=`. */
internal fun isPlainIp(s: String): Boolean =
    s.isNotEmpty() && s.length <= 45 && s.first() != '-' && (s.all { it.isDigit() || it == '.' } || (s.contains(':') && s.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' }))

/** The arguments for choosing [ip] as the exit node (keeping the local network reachable), or for using none (null). */
internal fun exitNodeArgs(ip: String?): List<String> =
    if (ip == null) listOf("set", "--exit-node=") else listOf("set", "--exit-node=$ip", "--exit-node-allow-lan-access=true")

/** A peer as `wg show all dump` reports it, through LuCI's `luci.wireguard getWgInstances`. */
data class WgPeerStatus(
    val publicKey: String,
    val endpoint: String,
    val allowedIps: List<String>,
    /** Seconds since the epoch of the last handshake, 0 when there never was one. */
    val latestHandshake: Long,
    val rxBytes: Long,
    val txBytes: Long,
)

data class WgInstance(val name: String, val listenPort: String, val peers: List<WgPeerStatus>)

internal fun parseWgInstances(o: JsonObject): List<WgInstance> =
    o.mapNotNull { (name, v) ->
        val i = v as? JsonObject ?: return@mapNotNull null
        WgInstance(
            name = i.str("name", name),
            listenPort = i.str("listen_port"),
            peers = i.arr("peers").objects().map { p ->
                WgPeerStatus(
                    publicKey = p.str("public_key"),
                    endpoint = p.str("endpoint").takeIf { it != "(none)" }.orEmpty(),
                    allowedIps = p.strings("allowed_ips"),
                    latestHandshake = p.long("latest_handshake"),
                    rxBytes = p.long("transfer_rx"),
                    txBytes = p.long("transfer_tx"),
                )
            },
        )
    }.sortedBy { it.name }

data class MwanTrackIp(val ip: String, val status: String, val latencyMs: Int, val lossPercent: Int)

data class MwanInterface(
    val name: String,
    /** online, offline, connecting, disconnecting, disabled, error, unknown: mwan3track's own word. */
    val status: String,
    val enabled: Boolean,
    val up: Boolean,
    /** active, paused, down or disabled. */
    val tracking: String,
    /** Seconds in the current online (or offline) state. */
    val onlineSeconds: Long,
    val offlineSeconds: Long,
    val trackIps: List<MwanTrackIp>,
)

data class MwanMember(val iface: String, val percent: Int)

data class MwanStatus(val interfaces: List<MwanInterface>, val policies: Map<String, List<MwanMember>>)

internal fun parseMwanStatus(o: JsonObject): MwanStatus {
    val interfaces = (o.obj("interfaces") ?: JsonObject(emptyMap())).mapNotNull { (name, v) ->
        val i = v as? JsonObject ?: return@mapNotNull null
        MwanInterface(
            name = name,
            status = i.str("status", "unknown"),
            enabled = i.bool("enabled"),
            up = i.bool("up"),
            tracking = i.str("tracking"),
            onlineSeconds = i.long("online"),
            offlineSeconds = i.long("offline"),
            trackIps = i.arr("track_ip").objects().map { MwanTrackIp(it.str("ip"), it.str("status"), it.int("latency"), it.int("packetloss")) },
        )
    }
    val policies = linkedMapOf<String, List<MwanMember>>()
    val pol = o.obj("policies") ?: JsonObject(emptyMap())
    for (family in listOf("ipv4", "ipv6")) {
        (pol.obj(family) ?: continue).forEach { (name, v) ->
            val members = (v as? JsonArray)?.objects()?.map { MwanMember(it.str("interface"), it.double("percent").toInt()) }.orEmpty()
            val key = if (family == "ipv6") "$name (IPv6)" else name
            if (members.isNotEmpty() || family == "ipv4") policies[key] = members
        }
    }
    return MwanStatus(interfaces, policies)
}

/** "25.12.3" → [25, 12, 3, 1000]; "25.12.0-rc2" → [25, 12, 0, 2]: a release sorts after its release candidates. */
internal fun versionKey(v: String): List<Int>? {
    val main = v.substringBefore('-')
    val nums = main.split('.').map { it.toIntOrNull() ?: return null }
    if (nums.size < 2) return null
    val suffix = v.substringAfter('-', "")
    val rc = when {
        suffix.isEmpty() -> 1000
        suffix.startsWith("rc") -> suffix.removePrefix("rc").toIntOrNull() ?: return null
        else -> return null
    }
    return (nums + List((3 - nums.size).coerceAtLeast(0)) { 0 }).take(3) + rc
}

internal fun compareVersions(a: List<Int>, b: List<Int>): Int {
    for (i in 0 until maxOf(a.size, b.size)) {
        val c = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 })
        if (c != 0) return c
    }
    return 0
}

/** "25.12.3" → "25.12". */
internal fun branchOf(v: String): String = v.substringBefore('-').split('.').take(2).joinToString(".")

/**
 * The releases worth upgrading to from [current], newest first, picked the way LuCI's attended
 * sysupgrade does from the upgrade server's `latest` list: every newer release down to the one of
 * the branch [current] is on. Null when [current] isn't a release (a snapshot or a custom build).
 */
internal fun firmwareCandidates(current: String, latest: List<String>): List<String>? {
    val cur = versionKey(current) ?: return null
    val branch = branchOf(current)
    val sorted = latest.mapNotNull { v -> versionKey(v)?.let { v to it } }.sortedWith { x, y -> compareVersions(y.second, x.second) }
    val out = mutableListOf<String>()
    for ((v, key) in sorted) {
        if (compareVersions(key, cur) <= 0) break
        out += v
        if (branchOf(v) == branch) break
    }
    return out
}

/** The `latest` list of the upgrade server's overview, or null when [text] isn't that document. */
internal fun parseLatestReleases(text: String): List<String>? {
    val o = runCatching { ParseJson.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
    val latest = o["latest"] as? JsonArray ?: return null
    return latest.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
}

/** The result of a firmware check: the newer releases (newest first), or [notARelease] for snapshots and custom builds. */
data class FirmwareCheck(val current: String, val newer: List<String>, val notARelease: Boolean)

/** The router couldn't fetch the release list (no internet, no HTTPS support, or the server is down). */
class FirmwareCheckFailed(val detail: String) : Exception(detail)

enum class DiagTool { PING, PING6, TRACEROUTE, TRACEROUTE6 }

/** Where busybox (or iputils / traceroute packages) puts each tool: the paths LuCI's diagnostics page is allowed to run. */
fun DiagTool.paths(): List<String> = when (this) {
    DiagTool.PING -> listOf("/bin/ping", "/usr/bin/ping")
    DiagTool.PING6 -> listOf("/bin/ping6", "/usr/bin/ping6")
    DiagTool.TRACEROUTE -> listOf("/usr/bin/traceroute", "/bin/traceroute")
    DiagTool.TRACEROUTE6 -> listOf("/usr/bin/traceroute6", "/bin/traceroute6")
}

/** The arguments LuCI's diagnostics page uses, with the hop limit kept under rpcd's 30-second limit for a command. */
fun DiagTool.args(host: String): List<String> = when (this) {
    DiagTool.PING -> listOf("-4", "-c", "5", "-W", "1", host)
    DiagTool.PING6 -> listOf("-6", "-c", "5", host)
    DiagTool.TRACEROUTE -> listOf("-4", "-q", "1", "-w", "1", "-n", "-m", "20", host)
    DiagTool.TRACEROUTE6 -> listOf("-q", "1", "-w", "1", "-n", "-m", "20", host)
}

/** A hostname or an IPv4 / IPv6 address, and never something a program could take for an option. */
fun isDiagHost(s: String): Boolean = Regex("^[A-Za-z0-9][A-Za-z0-9.:_-]{0,252}$").matches(s)

/** An IPv6 address in its usual spellings (hex groups and "::"), without a zone or prefix length. */
fun isIpv6(s: String): Boolean {
    if (s.length !in 2..39 || !s.contains(':') || !s.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' }) return false
    if (s.contains(":::") || s.split("::").size > 2) return false
    val groups = s.split(':')
    if (groups.any { it.length > 4 }) return false
    val filled = groups.count { it.isNotEmpty() }
    return if (s.contains("::")) filled <= 7 else groups.size == 8 && filled == 8
}

/** A name dnsmasq can answer for: labels of letters, digits and hyphens, joined by dots. */
fun isDnsName(s: String): Boolean =
    s.length in 1..253 && s.split('.').all { Regex("^[A-Za-z0-9_]([A-Za-z0-9_-]{0,61}[A-Za-z0-9_])?$").matches(it) }

/**
 * A dnsmasq DHCP option as uci's `dhcp_option` holds it: an option number or `option:name`, then
 * its values, each after a comma ("6,192.168.1.2,192.168.1.3"), optionally behind `tag:name,`.
 */
fun isDhcpOption(s: String): Boolean =
    Regex("^(tag:[A-Za-z0-9_-]+,)?(\\d{1,3}|option6?:[A-Za-z0-9-]+)(,[^,\\s][^,]*)+$").matches(s)

/** The option number of a `dhcp_option` value, for the well-known ones WrtCtrl names (3 router, 6 DNS, …). */
fun dhcpOptionCode(s: String): Int? =
    s.split(',').firstOrNull { !it.startsWith("tag:") }?.toIntOrNull()
