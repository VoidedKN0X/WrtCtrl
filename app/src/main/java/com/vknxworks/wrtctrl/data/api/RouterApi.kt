package com.vknxworks.wrtctrl.data.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/*
 * Typed calls for everything WrtCtrl reads from or does on a router, each checked against OpenWrt
 * 25.12's ubus objects and LuCI's own use of them. Replies are parsed leniently.
 */

// ---------- System ----------

/** `system.info`. Memory in bytes, load as the usual 1/5/15-minute averages. */
data class SystemInfo(
    val uptime: Long,
    val load: List<Double>,
    val memTotal: Long,
    val memFree: Long,
    val memAvailable: Long,
    val memBuffered: Long,
    val memCached: Long,
    val swapTotal: Long,
    val swapFree: Long,
) {
    val memUsed: Long
        get() = if (memAvailable > 0) memTotal - memAvailable else memTotal - memFree - memBuffered - memCached

    val memFraction: Double get() = if (memTotal > 0) memUsed.toDouble() / memTotal else 0.0
}

suspend fun UbusClient.systemInfo(): SystemInfo {
    val d = call("system", "info")
    val mem = d.obj("memory") ?: JsonObject(emptyMap())
    val swap = d.obj("swap") ?: JsonObject(emptyMap())
    // ubus sends the load averages as fixed point (1.0 = 65536).
    val load = d.arr("load").mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.div(65536.0) }
    return SystemInfo(
        uptime = d.long("uptime"),
        load = load,
        memTotal = mem.long("total"),
        memFree = mem.long("free"),
        memAvailable = mem.long("available"),
        memBuffered = mem.long("buffered"),
        memCached = mem.long("cached"),
        swapTotal = swap.long("total"),
        swapFree = swap.long("free"),
    )
}

/** One mounted filesystem from `luci.getMountPoints`. */
data class Mount(val device: String, val mount: String, val size: Long, val avail: Long, val free: Long) {
    val used: Long get() = (size - free).coerceAtLeast(0)
    val fraction: Double get() = if (size > 0) used.toDouble() / size else 0.0
}

suspend fun UbusClient.mountPoints(): List<Mount> =
    call("luci", "getMountPoints").arr("result").objects().map {
        Mount(it.str("device"), it.str("mount"), it.long("size"), it.long("avail"), it.long("free"))
    }

/** Where settings and installed packages live: the overlay on flash routers, the root filesystem on x86. */
fun List<Mount>.storage(): Mount? = firstOrNull { it.mount == "/overlay" } ?: firstOrNull { it.mount == "/" }

/** CPU time counters from /proc/stat ("cpu" line): busy and total jiffies, to compute use between two reads. */
data class CpuTimes(val busy: Long, val total: Long)

suspend fun UbusClient.cpuTimes(): CpuTimes? {
    val text = readFile("/proc/stat") ?: return null
    val line = text.lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return null
    val n = line.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
    if (n.size < 4) return null
    // user nice system idle iowait irq softirq steal
    val idle = n[3] + (n.getOrNull(4) ?: 0)
    val total = n.take(8).sum()
    return CpuTimes(total - idle, total)
}

fun cpuFraction(prev: CpuTimes?, now: CpuTimes?): Double? {
    if (prev == null || now == null) return null
    val dt = now.total - prev.total
    if (dt <= 0) return null
    return ((now.busy - prev.busy).toDouble() / dt).coerceIn(0.0, 1.0)
}

/** The highest temperature any thermal zone reports, in °C, or null when none can be read. */
suspend fun UbusClient.temperature(): Double? {
    var best: Double? = null
    for (i in 0 until 8) {
        val raw = runCatching { readFile("/sys/class/thermal/thermal_zone$i/temp") }.getOrNull() ?: break
        val milli = raw.trim().toLongOrNull() ?: continue
        val c = milli / 1000.0
        if (c > -40 && c < 150) best = maxOf(best ?: c, c)
    }
    return best
}

/** `file.read`: the file's text, or null when it can't be read (missing or not allowed). */
suspend fun UbusClient.readFile(path: String): String? = runCatching {
    call("file", "read", buildJsonObject { put("path", path) }).str("data")
}.getOrNull()

/** `file.stat`: whether [path] exists. */
suspend fun UbusClient.exists(path: String): Boolean = runCatching {
    call("file", "stat", buildJsonObject { put("path", path) }).str("type").isNotEmpty()
}.getOrDefault(false)

/** Result of `file.exec`. */
data class ExecResult(val code: Int, val stdout: String, val stderr: String)

/** `file.exec` of [command] with [params]; the ACL must allow the full command line. */
suspend fun UbusClient.exec(command: String, params: List<String> = emptyList(), write: Boolean = false): ExecResult {
    val d = call(
        "file",
        "exec",
        buildJsonObject {
            put("command", command)
            put("params", buildJsonArray { params.forEach { add(JsonPrimitive(it)) } })
        },
        write = write,
    )
    return ExecResult(d.int("code"), d.str("stdout"), d.str("stderr"))
}

suspend fun UbusClient.reboot() {
    call("system", "reboot", write = true)
}

// ---------- Interfaces and devices ----------

data class IpAddr(val address: String, val mask: Int) {
    override fun toString(): String = if (mask > 0) "$address/$mask" else address
}

data class Route(val target: String, val mask: Int, val nexthop: String)

/** One logical interface from `network.interface.dump` (lan, wan, wan6, …). */
data class NetInterface(
    val name: String,
    val up: Boolean,
    val pending: Boolean,
    val proto: String,
    val device: String,
    val l3Device: String,
    val uptime: Long,
    val ipv4: List<IpAddr>,
    val ipv6: List<IpAddr>,
    val ipv6Prefixes: List<IpAddr>,
    val routes: List<Route>,
    val dns: List<String>,
    val errors: List<String>,
) {
    val gateway: String? get() = routes.firstOrNull { it.target == "0.0.0.0" && it.mask == 0 || it.target == "::" && it.mask == 0 }?.nexthop
    val hasDefaultRoute: Boolean get() = gateway != null
    /** The device carrying the traffic: the layer-3 device once up (pppoe-wan), the configured one otherwise. */
    val statsDevice: String get() = l3Device.ifBlank { device }
}

private fun ips(a: JsonArray): List<IpAddr> = a.objects().map { IpAddr(it.str("address"), it.int("mask")) }

suspend fun UbusClient.interfaces(): List<NetInterface> =
    call("network.interface", "dump").arr("interface").objects().map { i ->
        val prefixes = i.arr("ipv6-prefix-assignment").objects().map { p ->
            val local = p.obj("local-address")
            if (local != null) IpAddr(local.str("address"), local.int("mask")) else IpAddr(p.str("address"), p.int("mask"))
        }
        NetInterface(
            name = i.str("interface"),
            up = i.bool("up"),
            pending = i.bool("pending"),
            proto = i.str("proto"),
            device = i.str("device"),
            l3Device = i.str("l3_device"),
            uptime = i.long("uptime"),
            ipv4 = ips(i.arr("ipv4-address")),
            ipv6 = ips(i.arr("ipv6-address")),
            ipv6Prefixes = prefixes,
            routes = i.arr("route").objects().map { Route(it.str("target"), it.int("mask"), it.str("nexthop")) },
            dns = i.arr("dns-server").mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
            errors = i.arr("errors").objects().map { e -> listOf(e.str("subsystem"), e.str("code")).filter { it.isNotBlank() }.joinToString(": ") },
        )
    }

/** The WAN: the interface called "wan", or else the first IPv4 interface with a default route. */
fun List<NetInterface>.wan(): NetInterface? =
    firstOrNull { it.name == "wan" } ?: firstOrNull { it.name != "loopback" && it.ipv4.isNotEmpty() && it.hasDefaultRoute }

fun List<NetInterface>.wan6(): NetInterface? = firstOrNull { it.name == "wan6" }

/** Traffic counters of a network device. */
data class DeviceStats(val name: String, val up: Boolean, val carrier: Boolean, val speed: String, val mac: String, val rxBytes: Long, val txBytes: Long)

private fun parseDevice(name: String, d: JsonObject): DeviceStats {
    val st = d.obj("statistics") ?: JsonObject(emptyMap())
    return DeviceStats(name, d.bool("up"), d.bool("carrier"), d.str("speed"), d.str("macaddr"), st.long("rx_bytes"), st.long("tx_bytes"))
}

suspend fun UbusClient.deviceStats(name: String): DeviceStats =
    parseDevice(name, call("network.device", "status", buildJsonObject { put("name", name) }))

suspend fun UbusClient.allDeviceStats(): Map<String, DeviceStats> =
    call("network.device", "status").mapNotNull { (k, v) -> (v as? JsonObject)?.let { k to parseDevice(k, it) } }.toMap()

/** Takes an interface down and brings it up again (`network.interface.<name>` down, up). */
suspend fun UbusClient.restartInterface(name: String) {
    call("network.interface.$name", "down", write = true)
    call("network.interface.$name", "up", write = true)
}

// ---------- Clients ----------

data class DhcpLease(val mac: String, val ip: String, val hostname: String, val expires: Long)

suspend fun UbusClient.dhcpLeases(): List<DhcpLease> {
    val d = call("luci-rpc", "getDHCPLeases")
    val v4 = d.arr("dhcp_leases").objects().map { DhcpLease(normMac(it.str("macaddr")), it.str("ipaddr"), it.str("hostname"), it.long("expires", -1)) }
    val v6 = d.arr("dhcp6_leases").objects().mapNotNull { l ->
        val mac = normMac(l.str("macaddr"))
        val ip = l.str("ip6addr").ifBlank { l.strings("ip6addrs").firstOrNull().orEmpty() }
        if (mac.isBlank() || ip.isBlank()) null else DhcpLease(mac, ip.substringBefore('/'), l.str("hostname"), l.long("expires", -1))
    }
    return v4 + v6
}

data class HostHint(val mac: String, val name: String, val ipv4: List<String>, val ipv6: List<String>)

suspend fun UbusClient.hostHints(): List<HostHint> =
    call("luci-rpc", "getHostHints").mapNotNull { (mac, v) ->
        val o = v as? JsonObject ?: return@mapNotNull null
        HostHint(normMac(mac), o.str("name"), o.strings("ipaddrs"), o.strings("ip6addrs"))
    }

/** A neighbour (ARP / NDP) entry; [state] like REACHABLE, STALE, FAILED. */
data class Neighbour(val ip: String, val mac: String, val dev: String, val state: String) {
    val online: Boolean get() = state in setOf("REACHABLE", "DELAY", "PROBE", "STALE", "PERMANENT")
}

/** The neighbour table, through `ip -j neigh show` (the same command LuCI's routes page runs). */
suspend fun UbusClient.neighbours(): List<Neighbour> {
    val out = mutableListOf<Neighbour>()
    for (family in listOf("-4", "-6")) {
        val res = runCatching { exec("/sbin/ip", listOf(family, "-j", "neigh", "show")) }.getOrNull() ?: continue
        val arr = runCatching { UbusJson.parseToJsonElement(res.stdout) as? JsonArray }.getOrNull() ?: continue
        arr.objects().forEach { n ->
            val mac = normMac(n.str("lladdr"))
            if (mac.isNotBlank()) {
                val state = n.strings("state").firstOrNull().orEmpty().uppercase()
                out += Neighbour(n.str("dst"), mac, n.str("dev"), state)
            }
        }
    }
    return out
}

/** Upper-case, colon-separated MAC address, or "" for anything that isn't one. */
fun normMac(mac: String): String {
    val hex = mac.filter { it.isLetterOrDigit() }.uppercase()
    if (hex.length != 12 || !hex.all { it in '0'..'9' || it in 'A'..'F' }) return ""
    return hex.chunked(2).joinToString(":")
}

// ---------- Wi-Fi ----------

/** One Wi-Fi network (wifi-iface) on a radio. */
data class WifiIface(
    val section: String,
    val radio: String,
    val ifname: String,
    val ssid: String,
    val network: List<String>,
    val encryption: String,
    val mode: String,
    val disabled: Boolean,
    val up: Boolean,
    val band: String,
) {
    /** A guest network: on an interface called "guest", or with "guest" in its name. */
    val isGuest: Boolean get() = network.any { it.contains("guest", ignoreCase = true) } || ssid.contains("guest", ignoreCase = true)
}

data class WifiRadio(
    val name: String,
    val up: Boolean,
    val disabled: Boolean,
    val band: String,
    val channel: String,
    val htmode: String,
    val txpower: String,
    val ifaces: List<WifiIface>,
)

/** Radios and their networks from `luci-rpc.getWirelessDevices`; empty on a router without Wi-Fi. */
suspend fun UbusClient.wireless(): List<WifiRadio> =
    call("luci-rpc", "getWirelessDevices").mapNotNull { (name, v) ->
        val r = v as? JsonObject ?: return@mapNotNull null
        val cfg = r.obj("config") ?: JsonObject(emptyMap())
        val band = cfg.str("band").ifBlank { cfg.str("hwmode") }
        WifiRadio(
            name = name,
            up = r.bool("up"),
            disabled = r.bool("disabled") || cfg.bool("disabled"),
            band = band,
            channel = cfg.str("channel"),
            htmode = cfg.str("htmode"),
            txpower = cfg.str("txpower"),
            ifaces = r.arr("interfaces").objects().map { i ->
                val ic = i.obj("config") ?: JsonObject(emptyMap())
                WifiIface(
                    section = i.str("section"),
                    radio = name,
                    ifname = i.str("ifname"),
                    ssid = ic.str("ssid"),
                    network = ic.strings("network"),
                    encryption = ic.str("encryption"),
                    mode = ic.str("mode"),
                    disabled = ic.bool("disabled"),
                    up = i.str("ifname").isNotBlank() && r.bool("up") && !ic.bool("disabled"),
                    band = band,
                )
            },
        )
    }

/** One associated Wi-Fi station from `iwinfo.assoclist`. Rates in kbit/s. */
data class Station(val mac: String, val signal: Int, val noise: Int, val inactiveMs: Long, val rxRate: Long, val txRate: Long)

suspend fun UbusClient.stations(ifname: String): List<Station> =
    call("iwinfo", "assoclist", buildJsonObject { put("device", ifname) }).arr("results").objects().map {
        Station(
            mac = normMac(it.str("mac")),
            signal = it.int("signal"),
            noise = it.int("noise"),
            inactiveMs = it.long("inactive"),
            rxRate = it.obj("rx")?.long("rate") ?: 0,
            txRate = it.obj("tx")?.long("rate") ?: 0,
        )
    }

/** Disconnects [mac] from the Wi-Fi network on [ifname] (hostapd deauth, with a one-minute ban so it doesn't rejoin at once). */
suspend fun UbusClient.deauth(ifname: String, mac: String) {
    call(
        "hostapd.$ifname",
        "del_client",
        buildJsonObject {
            put("addr", mac.lowercase())
            put("reason", 5)
            put("deauth", true)
            put("ban_time", 60_000)
        },
        write = true,
    )
}

// ---------- Services ----------

data class Service(val name: String, val enabled: Boolean, val running: Boolean, val start: Int)

suspend fun UbusClient.services(): List<Service> =
    call("rc", "list").mapNotNull { (name, v) ->
        val o = v as? JsonObject ?: return@mapNotNull null
        Service(name, o.bool("enabled"), o.bool("running"), o.int("start"))
    }.sortedBy { it.name }

/** `rc.init`: start, stop, restart, reload, enable or disable an init script. */
suspend fun UbusClient.serviceAction(name: String, action: String) {
    call("rc", "init", buildJsonObject { put("name", name); put("action", action) }, write = true)
}

// ---------- Logs ----------

data class LogLine(val time: Long, val source: String, val priority: Int, val msg: String)

/** The last [lines] lines of the system log (logd). [LogLine.time] in ms. */
suspend fun UbusClient.systemLog(lines: Int = 500): List<LogLine> =
    call("log", "read", buildJsonObject { put("lines", lines); put("oneshot", true); put("stream", false) })
        .arr("log").objects().map {
            val t = it.long("time")
            LogLine(if (t in 1..99_999_999_999L) t * 1000 else t, it.str("source"), it.int("priority", 6), it.str("msg"))
        }

// ---------- Packages ----------

data class PackageUpdate(val name: String, val installed: String, val available: String)

/** Which package manager the router uses. */
enum class PackageManager { APK, OPKG }

/** LuCI's wrapper around apk / opkg: install, update and upgrade take the same arguments with either. */
const val PKG_CALL = RouterAcl.PACKAGE_MANAGER

suspend fun UbusClient.packageManager(): PackageManager =
    if (exists("/usr/bin/apk")) PackageManager.APK else PackageManager.OPKG

/**
 * Packages with a newer version in the repositories, as the package manager reports them
 * (`apk list --upgradable` or `opkg list-upgradable`). Uses the package lists from the last update.
 */
suspend fun UbusClient.upgradable(pm: PackageManager): List<PackageUpdate> = when (pm) {
    PackageManager.APK -> {
        val res = exec("/usr/bin/apk", listOf("list", "--upgradable"))
        res.stdout.lineSequence().mapNotNull { parseApkUpgradable(it) }.toList()
    }
    PackageManager.OPKG -> {
        val res = exec("/bin/opkg", listOf("list-upgradable"))
        res.stdout.lineSequence().mapNotNull { line ->
            val p = line.split(" - ")
            if (p.size >= 3) PackageUpdate(p[0].trim(), p[1].trim(), p[2].trim()) else null
        }.toList()
    }
}.sortedBy { it.name }

/** "name-1.2-r3 x86_64 {feed} (License) [upgradable from: name-1.1-r1]" → name, 1.1-r1, 1.2-r3. */
internal fun parseApkUpgradable(line: String): PackageUpdate? {
    val from = line.substringAfter("[upgradable from: ", "").substringBefore(']').trim()
    if (from.isEmpty()) return null
    val (name, newVersion) = splitApk(line.substringBefore(' ')) ?: return null
    val old = splitApk(from)?.second ?: from
    return PackageUpdate(name, old, newVersion)
}

/** apk's "name-version-rN" into name and "version-rN" (versions themselves contain no '-'). */
private fun splitApk(full: String): Pair<String, String>? {
    val rel = Regex("-r\\d+$").find(full) ?: return null
    val base = full.substring(0, rel.range.first)
    val dash = base.lastIndexOf('-')
    if (dash <= 0) return null
    return base.substring(0, dash) to base.substring(dash + 1) + rel.value
}

/** The wrapper's JSON answer to install / update / upgrade: exit code and output. */
data class PackageResult(val code: Int, val output: String)

private suspend fun UbusClient.pkgCall(vararg args: String): PackageResult {
    val res = exec(PKG_CALL, args.toList(), write = true)
    val o = runCatching { UbusJson.parseToJsonElement(res.stdout) as? JsonObject }.getOrNull()
    return if (o != null) {
        PackageResult(o.int("code"), listOf(o.str("pkmcmd"), o.str("stdout"), o.str("stderr")).filter { it.isNotBlank() }.joinToString("\n\n"))
    } else {
        PackageResult(res.code, (res.stdout + "\n" + res.stderr).trim())
    }
}

/** Refreshes the package lists (apk update / opkg update). */
suspend fun UbusClient.updatePackageLists(): PackageResult = pkgCall("update")

suspend fun UbusClient.upgradePackages(names: List<String>): PackageResult = pkgCall("upgrade", *names.toTypedArray())

suspend fun UbusClient.installPackage(name: String): PackageResult = pkgCall("install", name)

/** Optional packages WrtCtrl uses when they're there, found by their program. */
enum class OptionalPackage(val pkg: String, val binary: String) {
    NLBWMON("nlbwmon", "/usr/sbin/nlbw"),
    ETHERWAKE("etherwake", "/usr/bin/etherwake"),
    COLLECTD("collectd", "/usr/sbin/collectd"),
}

suspend fun UbusClient.installedOptional(): Set<OptionalPackage> =
    OptionalPackage.entries.filter { exists(it.binary) }.toSet()

// ---------- nlbwmon, Wake-on-LAN ----------

/** Data use per MAC address from nlbwmon (this accounting period). */
data class Usage(val rxBytes: Long, val txBytes: Long, val connections: Long)

suspend fun UbusClient.usageByMac(): Map<String, Usage> {
    val res = exec("/usr/sbin/nlbw", listOf("-c", "json", "-g", "mac"))
    val o = runCatching { UbusJson.parseToJsonElement(res.stdout) as? JsonObject }.getOrNull() ?: return emptyMap()
    val cols = o.arr("columns").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    val iMac = cols.indexOf("mac")
    if (iMac < 0) return emptyMap()
    val iRx = cols.indexOf("rx_bytes")
    val iTx = cols.indexOf("tx_bytes")
    val iConn = cols.indexOf("conns")
    return o.arr("data").mapNotNull { row ->
        val r = row as? JsonArray ?: return@mapNotNull null
        fun at(i: Int): String? = if (i >= 0) (r.getOrNull(i) as? JsonPrimitive)?.contentOrNull else null
        val mac = normMac(at(iMac).orEmpty())
        if (mac.isBlank()) null else mac to Usage(at(iRx)?.toLongOrNull() ?: 0, at(iTx)?.toLongOrNull() ?: 0, at(iConn)?.toLongOrNull() ?: 0)
    }.toMap()
}

/** Sends a Wake-on-LAN packet to [mac] on [bridge] with etherwake. */
suspend fun UbusClient.wake(mac: String, bridge: String): ExecResult =
    exec("/usr/bin/etherwake", listOf("-D", "-i", bridge, mac), write = true)
