package com.vknxworks.wrtctrl.data.router

import com.vknxworks.wrtctrl.FakeUbus
import com.vknxworks.wrtctrl.data.api.str
import com.vknxworks.wrtctrl.data.api.uciGet
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class BlockRulesTest {
    /**
     * `uci get firewall` (type rule), as rpcd answers it on OpenWrt 24.10 with fw4: the stock rules, a
     * user's own rule, WrtCtrl's always-on block and a scheduled one.
     */
    private val firewallRules = """
        {"values": {
          "cfg0a92bd": {".anonymous": true, ".type": "rule", ".name": "cfg0a92bd", ".index": 4,
            "name": "Allow-DHCP-Renew", "src": "wan", "proto": "udp", "dest_port": "68", "target": "ACCEPT", "family": "ipv4"},
          "cfg0b92bd": {".anonymous": true, ".type": "rule", ".name": "cfg0b92bd", ".index": 5,
            "name": "Block kids tablet (by hand)", "src": "lan", "dest": "wan", "src_mac": ["AA:BB:CC:00:00:01"], "target": "REJECT"},
          "cfg1092bd": {".anonymous": true, ".type": "rule", ".name": "cfg1092bd", ".index": 12,
            "name": "WrtCtrl block Living room TV", "src": "lan", "dest": "wan", "src_mac": "aa:bb:cc:dd:ee:ff",
            "proto": "all", "target": "REJECT"},
          "cfg1192bd": {".anonymous": true, ".type": "rule", ".name": "cfg1192bd", ".index": 13,
            "name": "WrtCtrl block Tom's phone", "src": "lan", "dest": "wan", "src_mac": ["11-22-33-44-55-66"],
            "proto": "all", "target": "DROP", "start_time": "21:30", "stop_time": "7:00", "weekdays": "Sun Mon Tue Wed Thu", "enabled": "1"},
          "cfg1292bd": {".anonymous": true, ".type": "rule", ".name": "cfg1292bd", ".index": 14,
            "name": "WrtCtrl block Old laptop", "src": "lan", "dest": "wan", "src_mac": "22:22:22:22:22:22",
            "target": "REJECT", "enabled": "0"}
        }}
    """.trimIndent()

    private fun fake() = FakeUbus { obj, method, args ->
        when {
            obj == "uci" && method == "get" && args.str("config") == "firewall" -> firewallRules
            obj == "uci" && method == "add" -> """{"section": "cfg1392bd"}"""
            obj == "uci" && (method == "set" || method == "delete") -> "{}"
            else -> null
        }
    }

    @Test
    fun parsesOnlyWrtCtrlRules() = runBlocking {
        fake().use { ubus ->
            val sections = ubus.client().uciGet("firewall", "rule")
            assertEquals(5, sections.size)
            val rules = BlockRules.parseAll(sections)
            assertEquals(listOf("cfg1092bd", "cfg1192bd", "cfg1292bd"), rules.map { it.section })

            val tv = rules[0]
            assertEquals("Living room TV", tv.label)
            assertEquals(listOf("AA:BB:CC:DD:EE:FF"), tv.macs)
            assertEquals(BlockTarget.REJECT, tv.target)
            assertNull(tv.schedule)
            assertTrue(tv.enabled)

            val tom = rules[1]
            assertEquals(BlockTarget.DROP, tom.target)
            assertEquals(listOf("11:22:33:44:55:66"), tom.macs)
            assertEquals(BlockSchedule(LocalTime.of(21, 30), LocalTime.of(7, 0), setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)), tom.schedule)

            assertFalse(rules[2].enabled)
            assertEquals(tom, BlockRules.forMac(rules, "11:22:33:44:55:66"))
            // The user's own rule for AA:BB:CC:00:00:01 isn't WrtCtrl's.
            assertNull(BlockRules.forMac(rules, "aa:bb:cc:00:00:01"))
        }
    }

    @Test
    fun buildsANewBlock() {
        val ops = BlockRules.blockOps("aa-bb-cc-dd-ee-ff", "Living room TV", BlockTarget.REJECT, null)
        val add = ops.single() as UciOp.Add
        assertEquals("firewall", add.config)
        assertEquals("rule", add.type)
        assertEquals(
            mapOf(
                "name" to "WrtCtrl block Living room TV",
                "src" to "lan",
                "dest" to "wan",
                "src_mac" to "AA:BB:CC:DD:EE:FF",
                "proto" to "all",
                "target" to "REJECT",
            ),
            add.values,
        )
    }

    @Test
    fun buildsAScheduledDropBlock() {
        val s = BlockSchedule(LocalTime.of(22, 0), LocalTime.of(7, 0), setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY))
        val v = (BlockRules.blockOps("11:22:33:44:55:66", "", BlockTarget.DROP, s).single() as UciOp.Add).values
        assertEquals("DROP", v["target"])
        assertEquals("22:00", v["start_time"])
        assertEquals("07:00", v["stop_time"])
        assertEquals("Mon Fri", v["weekdays"])
        // No name: the MAC address is used.
        assertEquals("WrtCtrl block 11:22:33:44:55:66", v["name"])
        // Every day ticked is the same as none: no weekdays option.
        val all = BlockSchedule(LocalTime.of(22, 0), LocalTime.of(7, 0), DayOfWeek.entries.toSet())
        assertFalse("weekdays" in (BlockRules.blockOps("11:22:33:44:55:66", "x", BlockTarget.DROP, all).single() as UciOp.Add).values)
    }

    @Test
    fun refusesBadInput() {
        try {
            BlockRules.blockOps("not a mac", "x", BlockTarget.REJECT, null); fail()
        } catch (_: IllegalArgumentException) {
        }
        try {
            BlockRules.blockOps("11:22:33:44:55:66", "x", BlockTarget.REJECT, BlockSchedule(LocalTime.NOON, LocalTime.NOON)); fail()
        } catch (_: IllegalArgumentException) {
        }
        // Control characters can't end up in the rule name.
        assertEquals("WrtCtrl block a b", BlockRules.ruleName("a\nb", "11:22:33:44:55:66"))
    }

    @Test
    fun changesAndRemovesOnlyWrtCtrlRules() = runBlocking {
        fake().use { ubus ->
            val sections = ubus.client().uciGet("firewall", "rule").associateBy { it.name }
            // Taking the schedule off removes the time options that are there.
            val off = BlockRules.updateOps(sections.getValue("cfg1192bd"), BlockTarget.REJECT, null)
            assertEquals(UciOp.Set("firewall", "cfg1192bd", mapOf("target" to "REJECT", "enabled" to "1")), off[0])
            assertEquals(UciOp.Delete("firewall", "cfg1192bd", listOf("start_time", "stop_time", "weekdays")), off[1])
            // A schedule every day drops the old weekdays.
            val everyDay = BlockRules.updateOps(sections.getValue("cfg1192bd"), BlockTarget.DROP, BlockSchedule(LocalTime.of(23, 0), LocalTime.of(6, 30)))
            assertEquals(mapOf("target" to "DROP", "enabled" to "1", "start_time" to "23:00", "stop_time" to "06:30"), (everyDay[0] as UciOp.Set).values)
            assertEquals(UciOp.Delete("firewall", "cfg1192bd", listOf("weekdays")), everyDay[1])
            // Adding a schedule to an always-on block: nothing to delete.
            assertEquals(1, BlockRules.updateOps(sections.getValue("cfg1092bd"), BlockTarget.REJECT, BlockSchedule(LocalTime.of(8, 0), LocalTime.of(9, 0))).size)
            assertEquals(listOf(UciOp.Delete("firewall", "cfg1092bd")), BlockRules.unblockOps(sections.getValue("cfg1092bd")))

            // The user's own rule and a stock rule are never touched.
            for (foreign in listOf("cfg0a92bd", "cfg0b92bd")) {
                try {
                    BlockRules.unblockOps(sections.getValue(foreign)); fail(foreign)
                } catch (_: IllegalArgumentException) {
                }
                try {
                    BlockRules.updateOps(sections.getValue(foreign), BlockTarget.DROP, null); fail(foreign)
                } catch (_: IllegalArgumentException) {
                }
            }
        }
    }

    @Test
    fun stagingSendsTheRightUbusCalls() = runBlocking {
        fake().use { ubus ->
            val client = ubus.client()
            client.stageBlock("aa:bb:cc:dd:ee:ff", "TV", BlockTarget.DROP, BlockSchedule(LocalTime.of(22, 0), LocalTime.of(7, 0), setOf(DayOfWeek.SATURDAY)))
            val add = ubus.callsTo("uci", "add").single()
            assertEquals("firewall", add.args.str("config"))
            assertEquals("rule", add.args.str("type"))
            val values = add.args["values"] as kotlinx.serialization.json.JsonObject
            assertEquals("WrtCtrl block TV", values.str("name"))
            assertEquals("AA:BB:CC:DD:EE:FF", values.str("src_mac"))
            assertEquals("DROP", values.str("target"))
            assertEquals("Sat", values.str("weekdays"))

            val sections = client.uciGet("firewall", "rule").associateBy { it.name }
            client.stageUnblock(sections.getValue("cfg1092bd"))
            assertEquals("cfg1092bd", ubus.callsTo("uci", "delete").single().args.str("section"))

            // A rule without the prefix: refused before anything is sent.
            val before = ubus.calls.size
            try {
                client.stageUnblock(sections.getValue("cfg0b92bd")); fail()
            } catch (_: IllegalArgumentException) {
            }
            assertEquals(before, ubus.calls.size)
        }
    }

    @Test
    fun readOnlyModeSendsNothing() = runBlocking {
        fake().use { ubus ->
            try {
                ubus.client(readOnly = true).stageBlock("aa:bb:cc:dd:ee:ff", "TV"); fail()
            } catch (_: com.vknxworks.wrtctrl.data.api.RouterError.ReadOnly) {
            }
            assertTrue(ubus.calls.isEmpty())
        }
    }

    @Test
    fun timesAndWeekdaysLikeFw4() {
        assertEquals(LocalTime.of(7, 0), BlockRules.parseTime("7"))
        assertEquals(LocalTime.of(7, 5), BlockRules.parseTime("7:5"))
        assertEquals(LocalTime.of(23, 59, 59), BlockRules.parseTime("23:59:59"))
        assertNull(BlockRules.parseTime("24:00"))
        assertNull(BlockRules.parseTime("7.30"))
        assertNull(BlockRules.parseTime(""))
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY), BlockRules.parseWeekdays("monday Sat"))
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), BlockRules.parseWeekdays("! Mon Tue Wed Thu Fri"))
        assertEquals(emptySet<DayOfWeek>(), BlockRules.parseWeekdays("Mon Tue Wed Thu Fri Sat Sun"))
        assertEquals(emptySet<DayOfWeek>(), BlockRules.parseWeekdays("Funday"))
        assertEquals("Mon Wed Sun", BlockRules.formatWeekdays(setOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY)))
    }

    @Test
    fun scheduleWindowsIncludingOvernight() {
        val night = BlockSchedule(LocalTime.of(22, 0), LocalTime.of(7, 0), setOf(DayOfWeek.MONDAY))
        // 2026-11-16 is a Monday.
        assertTrue(BlockRules.inSchedule(night, LocalDateTime.of(2026, 11, 16, 23, 0)))
        assertTrue(BlockRules.inSchedule(night, LocalDateTime.of(2026, 11, 16, 6, 59)))
        assertFalse(BlockRules.inSchedule(night, LocalDateTime.of(2026, 11, 16, 7, 0)))
        assertFalse(BlockRules.inSchedule(night, LocalDateTime.of(2026, 11, 16, 12, 0)))
        // Tuesday morning isn't Monday: the router matches the clock's day.
        assertFalse(BlockRules.inSchedule(night, LocalDateTime.of(2026, 11, 17, 1, 0)))
        val day = BlockSchedule(LocalTime.of(9, 0), LocalTime.of(17, 0))
        assertTrue(BlockRules.inSchedule(day, LocalDateTime.of(2026, 11, 17, 9, 0)))
        assertFalse(BlockRules.inSchedule(day, LocalDateTime.of(2026, 11, 17, 17, 0)))
        val disabled = BlockRule("s", listOf("11:22:33:44:55:66"), "x", BlockTarget.REJECT, enabled = false, schedule = null)
        assertFalse(disabled.activeAt(LocalDateTime.of(2026, 11, 17, 12, 0)))
    }

    @Test
    fun recognisesThisPhone() {
        val phone = setOf("192.168.1.42", "fd00::1234")
        assertTrue(ThisPhone.matches(listOf("192.168.1.42"), phone))
        assertTrue(ThisPhone.matches(listOf("FD00::1234/64"), phone))
        assertTrue(ThisPhone.matches(listOf("fd00::1234%wlan0"), phone))
        assertFalse(ThisPhone.matches(listOf("192.168.1.43"), phone))
        assertFalse(ThisPhone.matches(listOf("192.168.1.42"), emptySet()))
    }
}
