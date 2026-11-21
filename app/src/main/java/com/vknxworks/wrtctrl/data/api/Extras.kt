package com.vknxworks.wrtctrl.data.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/*
 * Tailscale, WireGuard, dynamic DNS, multi-WAN, the firmware check and the diagnostics. Each call was
 * checked against the OpenWrt 25.12 package or the LuCI app that owns it (named at each call), and
 * every reply is parsed leniently.
 */

// ---------- What the router has ----------

/** Where AdGuard Home's program lives: OpenWrt's package, then AdGuard's own installer. */
private val ADGUARD_BINARIES = listOf("/usr/bin/AdGuardHome", "/opt/AdGuardHome/AdGuardHome")

/**
 * Looks for Tailscale, dynamic DNS, mwan3 and AdGuard Home by their init scripts (`rc list`), for
 * AdGuard Home also by its program, and for WireGuard by the protocol of the interfaces. Anything
 * that can't be read counts as not there.
 */
suspend fun UbusClient.features(): RouterFeatures {
    val services = runCatching { services().map { it.name } }.getOrDefault(emptyList())
    val wg = runCatching { interfaces().filter { it.proto == "wireguard" }.map { it.name } }.getOrDefault(emptyList())
    var binary = false
    if (services.none { it.lowercase() in ADGUARD_SERVICES }) {
        for (path in ADGUARD_BINARIES) {
            if (exists(path)) {
                binary = true
                break
            }
        }
    }
    return featuresFrom(services, wg, binary)
}

// ---------- Tailscale ----------

/** The Tailscale program of OpenWrt's `tailscale` package (a link to tailscaled). */
const val TAILSCALE = "/usr/sbin/tailscale"

/** `tailscale status --json` (what LuCI's Tailscale app reads too). Null when Tailscale gave no status. */
suspend fun UbusClient.tailscaleStatus(): TailscaleStatus? =
    parseTailscaleStatus(exec(TAILSCALE, listOf("status", "--json")).stdout)

/**
 * Sends the router's internet traffic through the peer with [ip], or directly again with null
 * (`tailscale set --exit-node=…`, with `--exit-node-allow-lan-access=true` like LuCI's Tailscale app,
 * so the router stays reachable from the local network).
 */
suspend fun UbusClient.tailscaleExitNode(ip: String?): ExecResult {
    require(ip == null || isPlainIp(ip)) { "not an IP address" }
    return exec(TAILSCALE, exitNodeArgs(ip), write = true)
}

// ---------- WireGuard ----------

/** The running WireGuard interfaces and their peers (`luci.wireguard getWgInstances`, from luci-proto-wireguard). */
suspend fun UbusClient.wireguardInstances(): List<WgInstance> = parseWgInstances(call("luci.wireguard", "getWgInstances"))

/** A configured peer: the uci section of type `wireguard_<interface>`. Keys other than the public one are never kept. */
data class WgPeerConfig(val section: String, val iface: String, val description: String, val publicKey: String, val disabled: Boolean, val allowedIps: List<String>)

/** The peers configured for [iface] in /etc/config/network. */
suspend fun UbusClient.wireguardPeers(iface: String): List<WgPeerConfig> =
    uciGet("network", "wireguard_$iface").map {
        WgPeerConfig(it.name, iface, it.opt("description"), it.opt("public_key"), it.flag("disabled"), it.list("allowed_ips"))
    }

// ---------- Dynamic DNS ----------

data class DdnsService(
    val section: String,
    val enabled: Boolean,
    val lookupHost: String,
    val provider: String,
    val ipv6: Boolean,
    /** The address registered now, as ddns-scripts last saw it. */
    val ip: String,
    /** Date texts in the router's own format; empty when unknown. */
    val lastUpdate: String,
    val nextUpdate: String,
    val running: Boolean,
)

internal fun ddnsServices(sections: List<UciSection>, status: JsonObject): List<DdnsService> =
    sections.map { s ->
        val st = status.obj(s.name) ?: JsonObject(emptyMap())
        DdnsService(
            section = s.name,
            enabled = s.flag("enabled"),
            lookupHost = s.opt("lookup_host").ifBlank { s.opt("domain") },
            provider = s.opt("service_name").ifBlank { s.opt("update_url").let { if (it.isBlank()) "" else "custom" } },
            ipv6 = s.flag("use_ipv6"),
            // LuCI's helper joins several addresses with an HTML line break.
            ip = st.str("ip").replace("<br/>", ", ").replace("<br />", ", ").trim(),
            lastUpdate = st.str("last_update"),
            nextUpdate = st.str("next_update"),
            running = st.long("pid") > 0,
        )
    }

/** The dynamic DNS services of ddns-scripts with their state (`luci.ddns get_services_status`, from luci-app-ddns). */
suspend fun UbusClient.ddnsServices(): List<DdnsService> {
    val sections = uciGet("ddns", "service")
    val status = runCatching { call("luci.ddns", "get_services_status") }.getOrElse { e ->
        if (e is kotlinx.coroutines.CancellationException) throw e
        JsonObject(emptyMap())
    }
    return ddnsServices(sections, status)
}

// ---------- Multi-WAN (mwan3) ----------

/** `mwan3 status` over ubus (mwan3's own rpcd script): every tracked WAN, and how each policy shares the traffic. */
suspend fun UbusClient.mwanStatus(): MwanStatus {
    val interfaces = call("mwan3", "status", buildJsonObject { put("section", "interfaces") })
    val policies = runCatching { call("mwan3", "status", buildJsonObject { put("section", "policies") }) }.getOrElse { e ->
        if (e is kotlinx.coroutines.CancellationException) throw e
        JsonObject(emptyMap())
    }
    return parseMwanStatus(JsonObject(interfaces + policies))
}

// ---------- Firmware check ----------

/** The command the router runs for the firmware check: the release list of OpenWrt's upgrade server. */
const val UCLIENT_FETCH = "/bin/uclient-fetch"
val FIRMWARE_CHECK_ARGS = listOf("-q", "-T", "15", "-O", "-", "https://sysupgrade.openwrt.org/api/v1/overview")

/**
 * Asks the router to fetch the release list from OpenWrt's upgrade server (the one LuCI's attended
 * sysupgrade and `owut` use) and compares it with [current]. The phone itself contacts nobody but the router.
 */
suspend fun UbusClient.firmwareCheck(current: String): FirmwareCheck {
    if (versionKey(current) == null) return FirmwareCheck(current, emptyList(), notARelease = true)
    val res = exec(UCLIENT_FETCH, FIRMWARE_CHECK_ARGS)
    val latest = parseLatestReleases(res.stdout) ?: throw FirmwareCheckFailed(res.stderr.trim().take(200))
    return FirmwareCheck(current, firmwareCandidates(current, latest).orEmpty(), notARelease = false)
}

// ---------- Ping and traceroute ----------

/** Runs [tool] against [host] on the router and returns what it printed. */
suspend fun UbusClient.diagnose(tool: DiagTool, host: String): ExecResult {
    require(isDiagHost(host)) { "not a host" }
    var last: Exception? = null
    for (path in tool.paths()) {
        try {
            return exec(path, tool.args(host))
        } catch (e: RouterError.Ubus) {
            // Not at this path: try the next one.
            if (e.code != UbusStatus.NOT_FOUND) throw e
            last = e
        } catch (e: RouterError.Forbidden) {
            // This login may run the tool at another path only.
            last = e
        }
    }
    throw last ?: RouterError.Ubus(UbusStatus.NOT_FOUND, null)
}
