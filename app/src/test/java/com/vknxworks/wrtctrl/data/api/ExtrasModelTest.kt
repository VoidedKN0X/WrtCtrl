package com.vknxworks.wrtctrl.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasModelTest {
    private fun obj(text: String) = Json.parseToJsonElement(text) as JsonObject

    @Test
    fun featuresComeFromInitScriptsAndInterfaces() {
        val f = featuresFrom(listOf("network", "tailscale", "adguardhome", "dnsmasq"), listOf("wg0"), adguardBinary = false)
        assertTrue(f.tailscale)
        assertTrue(f.adguardHome)
        assertEquals(listOf("wg0"), f.wireguard)
        assertFalse(f.ddns)
        assertFalse(f.mwan3)
        // AdGuard's own installer names its init script with capitals; the program alone counts too.
        assertTrue(featuresFrom(listOf("AdGuardHome"), emptyList(), false).adguardHome)
        assertTrue(featuresFrom(emptyList(), emptyList(), true).adguardHome)
        assertFalse(featuresFrom(listOf("network"), emptyList(), false).adguardHome)
    }

    @Test
    fun tailscaleStatusIsRead() {
        val s = parseTailscaleStatus(
            """{"Version":"1.104.1","BackendState":"Running","Health":["something is off"],
            "Self":{"HostName":"OpenWrt","DNSName":"router.tail1234.ts.net.","TailscaleIPs":["100.64.0.1","fd7a::1"]},
            "CurrentTailnet":{"Name":"example.org"},
            "Peer":{
              "nodekey:a":{"ID":"n1","HostName":"phone","DNSName":"phone.tail1234.ts.net.","TailscaleIPs":["100.64.0.2"],"OS":"android","Online":false,"ExitNode":false,"ExitNodeOption":false},
              "nodekey:b":{"ID":"n2","HostName":"vps","DNSName":"","TailscaleIPs":["100.64.0.3"],"OS":"linux","Online":true,"ExitNode":true,"ExitNodeOption":true}
            }}""",
        )!!
        assertTrue(s.running)
        assertEquals("router", s.name)
        assertEquals(listOf("100.64.0.1", "fd7a::1"), s.ips)
        assertEquals("example.org", s.tailnet)
        assertEquals(listOf("something is off"), s.health)
        // Online peers first.
        assertEquals(listOf("vps", "phone"), s.peers.map { it.name })
        assertEquals("vps", s.exitNode?.name)
        assertTrue(s.peers[0].exitNodeOption)
    }

    @Test
    fun tailscaleStatusThatIsNotJsonGivesNothing() {
        assertNull(parseTailscaleStatus(""))
        assertNull(parseTailscaleStatus("failed to connect to local tailscaled"))
        assertNull(parseTailscaleStatus("{}"))
        assertFalse(parseTailscaleStatus("""{"BackendState":"NeedsLogin"}""")!!.running)
    }

    @Test
    fun exitNodeArgumentsNeverCarryAnythingButAnAddress() {
        assertEquals(listOf("set", "--exit-node="), exitNodeArgs(null))
        assertEquals(listOf("set", "--exit-node=100.64.0.3", "--exit-node-allow-lan-access=true"), exitNodeArgs("100.64.0.3"))
        assertTrue(isPlainIp("100.64.0.3"))
        assertTrue(isPlainIp("fd7a:115c:a1e0::3"))
        assertFalse(isPlainIp(""))
        assertFalse(isPlainIp("--advertise-exit-node"))
        assertFalse(isPlainIp("vps --ssh"))
        assertFalse(isPlainIp("host.example"))
    }

    @Test
    fun wireguardInstancesAreRead() {
        val list = parseWgInstances(
            obj(
                """{"wg0":{"name":"wg0","public_key":"PUB","listen_port":"51820","fwmark":"off","peers":[
                {"name":"Phone","public_key":"PEER1","endpoint":"203.0.113.5:40000","allowed_ips":["10.0.0.2/32"],"latest_handshake":"1763700000","transfer_rx":"1024","transfer_tx":"2048","persistent_keepalive":"25"},
                {"public_key":"PEER2","endpoint":"(none)","allowed_ips":[],"latest_handshake":"0","transfer_rx":"0","transfer_tx":"0","persistent_keepalive":"off"}
                ]}}""",
            ),
        )
        assertEquals(1, list.size)
        assertEquals("51820", list[0].listenPort)
        assertEquals(1763700000L, list[0].peers[0].latestHandshake)
        assertEquals(2048L, list[0].peers[0].txBytes)
        assertEquals("", list[0].peers[1].endpoint)
        assertEquals(0L, list[0].peers[1].latestHandshake)
    }

    @Test
    fun mwanStatusIsRead() {
        val s = parseMwanStatus(
            obj(
                """{"interfaces":{"wan":{"age":3,"online":120,"offline":0,"uptime":500,"score":10,"lost":0,"turn":0,"status":"online","enabled":true,"running":true,"tracking":"active","up":true,
                "track_ip":[{"ip":"1.1.1.1","status":"up","latency":12,"packetloss":0}]},
                "wanb":{"online":0,"offline":44,"status":"offline","enabled":true,"tracking":"active","up":false,"track_ip":[]}},
                "policies":{"ipv4":{"balanced":[{"interface":"wan","percent":100.0}]},"ipv6":{"balanced":[]}}}""",
            ),
        )
        assertEquals(listOf("wan", "wanb"), s.interfaces.map { it.name })
        assertEquals("online", s.interfaces[0].status)
        assertEquals(120L, s.interfaces[0].onlineSeconds)
        assertEquals(12, s.interfaces[0].trackIps[0].latencyMs)
        assertEquals(44L, s.interfaces[1].offlineSeconds)
        assertEquals(mapOf("balanced" to listOf(MwanMember("wan", 100))), s.policies)
        assertTrue(parseMwanStatus(obj("{}")).interfaces.isEmpty())
    }

    @Test
    fun firmwareCandidatesFollowTheUpgradeServerList() {
        val latest = listOf("25.12.6", "24.10.8")
        assertEquals(emptyList<String>(), firmwareCandidates("25.12.6", latest))
        assertEquals(listOf("25.12.6"), firmwareCandidates("25.12.2", latest))
        // On the older branch: the newest release, and the last one of its own branch.
        assertEquals(listOf("25.12.6", "24.10.8"), firmwareCandidates("24.10.2", latest))
        assertEquals(listOf("25.12.6"), firmwareCandidates("24.10.8", latest))
        // A release candidate is older than its release; a version newer than the list has nothing to get.
        assertEquals(listOf("25.12.6"), firmwareCandidates("25.12.0-rc2", latest))
        assertEquals(emptyList<String>(), firmwareCandidates("25.12.7", latest))
        // Two-digit parts compare as numbers, not as text.
        assertEquals(listOf("25.12.10"), firmwareCandidates("25.12.9", listOf("25.12.10")))
        assertNull(firmwareCandidates("SNAPSHOT", latest))
        assertNull(firmwareCandidates("25.12-SNAPSHOT", latest))
    }

    @Test
    fun releaseListIsReadFromTheOverview() {
        assertEquals(listOf("25.12.6", "24.10.8"), parseLatestReleases("""{"latest":["25.12.6","24.10.8"],"branches":{},"server":{}}"""))
        assertNull(parseLatestReleases("<html>captive portal</html>"))
        assertNull(parseLatestReleases("""{"branches":{}}"""))
        assertNull(parseLatestReleases(""))
    }

    @Test
    fun diagnosticHostsCannotBeOptions() {
        assertTrue(isDiagHost("openwrt.org"))
        assertTrue(isDiagHost("192.168.1.1"))
        assertTrue(isDiagHost("2606:4700:4700::1111"))
        assertFalse(isDiagHost("-f"))
        assertFalse(isDiagHost("openwrt.org; reboot"))
        assertFalse(isDiagHost("a b"))
        assertFalse(isDiagHost(""))
        assertEquals(listOf("-4", "-c", "5", "-W", "1", "openwrt.org"), DiagTool.PING.args("openwrt.org"))
        assertEquals("20", DiagTool.TRACEROUTE.args("openwrt.org")[7])
    }

    @Test
    fun addressesNamesAndDhcpOptionsAreChecked() {
        assertTrue(isIpv6("fd00::1"))
        assertTrue(isIpv6("::1"))
        assertTrue(isIpv6("2001:db8:0:0:0:0:0:1"))
        assertFalse(isIpv6("192.168.1.1"))
        assertFalse(isIpv6("fd00:::1"))
        assertFalse(isIpv6("fd00::1::2"))
        assertFalse(isIpv6("2001:db8:0:0:0:0:1"))
        assertFalse(isIpv6("12345::1"))

        assertTrue(isDnsName("nas"))
        assertTrue(isDnsName("nas.home.arpa"))
        assertFalse(isDnsName(""))
        assertFalse(isDnsName("-nas.lan"))
        assertFalse(isDnsName("nas..lan"))
        assertFalse(isDnsName("my nas"))

        assertTrue(isDhcpOption("6,192.168.1.2"))
        assertTrue(isDhcpOption("6,192.168.1.2,192.168.1.3"))
        assertTrue(isDhcpOption("option:router,192.168.1.1"))
        assertTrue(isDhcpOption("tag:guest,6,1.1.1.1"))
        assertFalse(isDhcpOption("6"))
        assertFalse(isDhcpOption("6,"))
        assertFalse(isDhcpOption("dns,1.1.1.1"))
        assertFalse(isDhcpOption("6, 1.1.1.1"))
        assertEquals(6, dhcpOptionCode("6,192.168.1.2"))
        assertEquals(3, dhcpOptionCode("tag:guest,3,10.0.0.1"))
        assertNull(dhcpOptionCode("option:router,192.168.1.1"))
    }
}
