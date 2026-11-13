package com.vknxworks.wrtctrl.data.router

import com.vknxworks.wrtctrl.FakeUbus
import com.vknxworks.wrtctrl.data.api.RouterAcl
import com.vknxworks.wrtctrl.data.api.UbusJson
import com.vknxworks.wrtctrl.data.api.str
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BandwidthTest {
    /** `nlbw -c json -g mac` as nlbwmon prints it (one row per MAC and address family). */
    private val nlbwJson = """
        {"columns":["family","mac","conns","rx_bytes","rx_pkts","tx_bytes","tx_pkts"],
         "data":[[4,"aa:bb:cc:dd:ee:ff",120,5000000,4000,700000,3000],
                 [6,"aa:bb:cc:dd:ee:ff",10,1000000,800,300000,600],
                 [4,"11:22:33:44:55:66",3,1200,10,800,9],
                 [4,"",1,1,1,1,1]]}
    """.trimIndent()

    @Test
    fun parsesNlbwTotalsAddingFamilies() {
        val u = Bandwidth.parseUsage(nlbwJson)
        assertEquals(2, u.size)
        val tv = u.getValue("AA:BB:CC:DD:EE:FF")
        assertEquals(6_000_000L, tv.rxBytes)
        assertEquals(1_000_000L, tv.txBytes)
        assertEquals(130L, tv.connections)
        assertTrue(Bandwidth.parseUsage("not json").isEmpty())
        assertTrue(Bandwidth.parseUsage("""{"columns":["ip"],"data":[["1.2.3.4"]]}""").isEmpty())
    }

    @Test
    fun periodsAndTheirLength() {
        val p = Bandwidth.parsePeriods("2026-09-01\n2026-11-01\n2026-10-01\ngarbage\n\n")
        assertEquals(listOf(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)), p)
        assertEquals(PeriodLength.MONTH, Bandwidth.periodLength(p))
        assertEquals(PeriodLength.DAY, Bandwidth.periodLength(Bandwidth.parsePeriods("2026-11-11\n2026-11-12\n2026-11-13")))
        assertEquals(PeriodLength.WEEK, Bandwidth.periodLength(Bandwidth.parsePeriods("2026-11-01\n2026-11-08")))
        assertEquals(PeriodLength.OTHER, Bandwidth.periodLength(Bandwidth.parsePeriods("2026-11-01\n2026-11-15")))
        assertEquals(PeriodLength.OTHER, Bandwidth.periodLength(emptyList()))
    }

    @Test
    fun historyAsksNlbwPerPeriod() = runBlocking {
        FakeUbus { obj, method, args ->
            if (obj != "file" || method != "exec") return@FakeUbus null
            val params = FakeUbus.strings(args["params"] as? kotlinx.serialization.json.JsonArray)
            val out = when {
                params == listOf("-c", "list") -> "2026-09-01\n2026-10-01\n2026-11-01\n"
                params.take(4) == listOf("-c", "json", "-g", "mac") && params.getOrNull(4) == "-t" ->
                    if (params[5] == "2026-10-01") """{"columns":["mac","rx_bytes","tx_bytes"],"data":[]}""" else nlbwJson
                else -> ""
            }
            buildJsonObject { put("code", 0); put("stdout", out); put("stderr", "") }.toString()
        }.use { ubus ->
            val h = ubus.client().nlbwHistory("aa:bb:cc:dd:ee:ff")
            assertEquals(listOf(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)), h.map { it.start })
            assertEquals(listOf(7_000_000L, 0L, 7_000_000L), h.map { it.total })
            val execs = ubus.callsTo("file", "exec")
            assertTrue(execs.all { it.args.str("command") == NLBW })
            assertEquals(4, execs.size)
            // Every command is one the README's permission file allows.
            val acl = RouterAcl(
                ubus = mapOf("file" to listOf("exec")),
                file = mapOf("$NLBW -c list" to listOf("exec"), "$NLBW -c json -g mac -t *" to listOf("exec")),
            )
            execs.forEach { e ->
                val line = (listOf(e.args.str("command")) + FakeUbus.strings(e.args["params"] as? kotlinx.serialization.json.JsonArray)).joinToString(" ")
                assertTrue(line, acl.canExec(line))
            }
            assertTrue(Bandwidth.canReadNlbwPeriods(acl))
        }
    }

    /** `luci.getConntrackList` replies, three seconds apart. */
    private fun conntrack(tvBytes: Long, newConn: Boolean): JsonObject = UbusJson.parseToJsonElement(
        """
        {"result":[
          {"layer3":"ipv4","layer4":"tcp","src":"192.168.1.20","sport":51000,"dst":"203.0.113.5","dport":443,"bytes":$tvBytes,"packets":10,"timeout":7400},
          {"layer3":"ipv4","layer4":"udp","src":"192.168.1.30","sport":5353,"dst":"192.168.1.20","dport":5353,"bytes":4000,"packets":4,"timeout":30},
          {"layer3":"ipv4","layer4":"tcp","src":"192.168.1.99","sport":40000,"dst":"198.51.100.1","dport":443,"bytes":999999,"packets":4,"timeout":30}
          ${if (newConn) """,{"layer3":"ipv6","layer4":"tcp","src":"FD00::20","sport":52000,"dst":"2001:db8::1","dport":443,"bytes":3000,"packets":3,"timeout":60}""" else ""}
        ]}
        """.trimIndent(),
    ) as JsonObject

    @Test
    fun liveRateFromConntrack() {
        val before = Bandwidth.parseConntrack(conntrack(1_000_000, newConn = false))
        val after = Bandwidth.parseConntrack(conntrack(1_300_000, newConn = true))
        // TV: +300 kB on its TCP connection, 0 on the mDNS one (no change), 3 kB on a new IPv6 connection.
        val rate = Bandwidth.rate(before, after, listOf("192.168.1.20", "fd00::20"), 3.0)
        assertEquals(101_000.0, rate, 0.001)
        // Another device isn't counted for it.
        assertEquals(0.0, Bandwidth.rate(before, after, listOf("192.168.1.55"), 3.0), 0.0)
        // A connection whose counter went down (reused) counts from zero.
        val reset = Bandwidth.parseConntrack(conntrack(500, newConn = false))
        assertEquals(500.0, Bandwidth.rate(after, reset, listOf("192.168.1.20"), 1.0), 0.0)
        assertTrue(Bandwidth.countsBytes(after))
        assertFalse(Bandwidth.countsBytes(Bandwidth.parseConntrack(conntrack(0, false)).map { it.copy(bytes = 0) }))
    }

    @Test
    fun picksTheSourceFromWhatTheLoginMayDo() {
        val none = RouterAcl()
        assertEquals(BandwidthSource.NONE, Bandwidth.source(none, nlbwInstalled = true))
        val conntrackOnly = RouterAcl(ubus = mapOf("luci" to listOf("getConntrackList")))
        assertEquals(BandwidthSource.CONNTRACK, Bandwidth.source(conntrackOnly, nlbwInstalled = true))
        val full = RouterAcl(ubus = mapOf("*" to listOf("*")), file = mapOf("/usr/sbin/nlbw *" to listOf("exec")))
        assertEquals(BandwidthSource.NLBWMON, Bandwidth.source(full, nlbwInstalled = true))
        assertEquals(BandwidthSource.CONNTRACK, Bandwidth.source(full, nlbwInstalled = false))
    }
}
