package com.vknxworks.wrtctrl.data.router

import com.vknxworks.wrtctrl.data.api.UbusClient
import com.vknxworks.wrtctrl.data.api.UciSection
import com.vknxworks.wrtctrl.data.api.normMac
import com.vknxworks.wrtctrl.data.api.uciAdd
import com.vknxworks.wrtctrl.data.api.uciDelete
import com.vknxworks.wrtctrl.data.api.uciSet
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/** How a blocked device's traffic is refused: REJECT tells it at once, DROP leaves it waiting. */
enum class BlockTarget { REJECT, DROP }

/**
 * When a block applies, in the router's local time: from [start] to [stop] on [days] (every day when empty).
 * A [stop] earlier than [start] runs past midnight (22:00–07:00). fw4 matches the clock time, so a ticked
 * day covers its own evening and its own early morning.
 */
data class BlockSchedule(val start: LocalTime, val stop: LocalTime, val days: Set<DayOfWeek> = emptySet())

/** A firewall rule WrtCtrl added to block a device (its name starts with [BLOCK_RULE_PREFIX]). */
data class BlockRule(
    /** The uci section name (cfg0a92bd or a named section). */
    val section: String,
    val macs: List<String>,
    /** What follows the prefix in the name: the device's name when it was blocked. */
    val label: String,
    val target: BlockTarget,
    val enabled: Boolean,
    val schedule: BlockSchedule?,
) {
    /** Whether the rule blocks right now, given the router's clock [now]. */
    fun activeAt(now: LocalDateTime): Boolean = enabled && (schedule == null || BlockRules.inSchedule(schedule, now))
}

/** A staged uci change, so what WrtCtrl will send can be checked (and tested) before it's sent. */
sealed interface UciOp {
    data class Add(val config: String, val type: String, val values: Map<String, Any>) : UciOp
    data class Set(val config: String, val section: String, val values: Map<String, Any>) : UciOp
    data class Delete(val config: String, val section: String, val options: List<String> = emptyList()) : UciOp
}

/** The rules for building and reading WrtCtrl's block rules. Pure, so they're unit-tested. */
object BlockRules {
    /** fw4's time options that WrtCtrl writes; removed again when a schedule is taken off. */
    val TIME_OPTIONS = listOf("start_time", "stop_time", "weekdays")

    private val WEEKDAY_NAMES = mapOf(
        DayOfWeek.MONDAY to "Mon", DayOfWeek.TUESDAY to "Tue", DayOfWeek.WEDNESDAY to "Wed", DayOfWeek.THURSDAY to "Thu",
        DayOfWeek.FRIDAY to "Fri", DayOfWeek.SATURDAY to "Sat", DayOfWeek.SUNDAY to "Sun",
    )

    /** Only rules whose name starts with the prefix are WrtCtrl's; every other rule is left alone. */
    fun isOurs(s: UciSection): Boolean = s.type == "rule" && s.opt("name").startsWith(BLOCK_RULE_PREFIX)

    /** Reads one of WrtCtrl's block rules; null for any other section. */
    fun parse(s: UciSection): BlockRule? {
        if (!isOurs(s)) return null
        val start = parseTime(s.opt("start_time"))
        val stop = parseTime(s.opt("stop_time"))
        val days = parseWeekdays(s.opt("weekdays"))
        return BlockRule(
            section = s.name,
            macs = s.list("src_mac").map(::normMac).filter { it.isNotBlank() },
            label = s.opt("name").removePrefix(BLOCK_RULE_PREFIX),
            target = if (s.opt("target").equals("DROP", ignoreCase = true)) BlockTarget.DROP else BlockTarget.REJECT,
            enabled = s.flag("enabled", true),
            schedule = if (start != null && stop != null) BlockSchedule(start, stop, days) else null,
        )
    }

    fun parseAll(sections: List<UciSection>): List<BlockRule> = sections.mapNotNull(::parse)

    /** The rule [mac] is blocked by, if any. */
    fun forMac(rules: List<BlockRule>, mac: String): BlockRule? = normMac(mac).let { m -> rules.firstOrNull { m in it.macs } }

    /** "H", "H:M" or "H:M:S" as fw4 accepts them; null for anything else. */
    fun parseTime(raw: String): LocalTime? {
        val m = Regex("^([0-9]{1,2})(:([0-9]{1,2})(:([0-9]{1,2}))?)?$").matchEntire(raw.trim()) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[3].ifEmpty { "0" }.toInt()
        val sec = m.groupValues[5].ifEmpty { "0" }.toInt()
        if (h > 23 || min > 59 || sec > 59) return null
        return LocalTime.of(h, min, sec)
    }

    fun formatTime(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)

    /**
     * fw4's weekdays: names matched by prefix, ignoring case ("Mon", "monday"). A leading "!" means
     * "every day except", which WrtCtrl reads as the remaining days. Unknown names give "every day" (empty).
     */
    fun parseWeekdays(raw: String): Set<DayOfWeek> {
        var s = raw.trim()
        if (s.isEmpty()) return emptySet()
        val invert = s.startsWith("!")
        if (invert) s = s.removePrefix("!").trim()
        val days = mutableSetOf<DayOfWeek>()
        for (token in s.split(Regex("\\s+"))) {
            val t = token.lowercase()
            val day = DayOfWeek.entries.firstOrNull { d ->
                val full = d.name.lowercase()
                t.isNotEmpty() && full.startsWith(t)
            } ?: return emptySet()
            days += day
        }
        val result = if (invert) DayOfWeek.entries.toSet() - days else days
        return if (result.size == 7) emptySet() else result
    }

    fun formatWeekdays(days: Set<DayOfWeek>): String =
        DayOfWeek.entries.filter { it in days }.joinToString(" ") { WEEKDAY_NAMES.getValue(it) }

    /** Whether [now] (the router's local time) falls inside [s]. */
    fun inSchedule(s: BlockSchedule, now: LocalDateTime): Boolean {
        if (s.days.isNotEmpty() && now.dayOfWeek !in s.days) return false
        val t = now.toLocalTime()
        return if (s.start <= s.stop) !t.isBefore(s.start) && t.isBefore(s.stop) else !t.isBefore(s.start) || t.isBefore(s.stop)
    }

    /** A schedule only makes sense when start and stop differ. */
    fun validSchedule(s: BlockSchedule): Boolean = s.start != s.stop

    /** The rule name for a device: the prefix and its name (or MAC), kept short and on one line. */
    fun ruleName(label: String, mac: String): String =
        BLOCK_RULE_PREFIX + label.replace(Regex("[\\p{Cntrl}]"), " ").trim().ifBlank { mac }.take(48)

    /** The options of a new block rule: from the LAN zone to the WAN zone, everything from [mac]. */
    fun values(mac: String, label: String, target: BlockTarget, schedule: BlockSchedule?): Map<String, Any> = buildMap {
        put("name", ruleName(label, mac))
        put("src", "lan")
        put("dest", "wan")
        put("src_mac", normMac(mac).ifBlank { throw IllegalArgumentException("not a MAC address") })
        put("proto", "all")
        put("target", target.name)
        if (schedule != null) putAll(scheduleValues(schedule))
    }

    private fun scheduleValues(s: BlockSchedule): Map<String, Any> {
        require(validSchedule(s)) { "start and stop are the same" }
        return buildMap {
            put("start_time", formatTime(s.start))
            put("stop_time", formatTime(s.stop))
            if (s.days.isNotEmpty() && s.days.size < 7) put("weekdays", formatWeekdays(s.days))
        }
    }

    /** Blocking a device that isn't blocked yet. */
    fun blockOps(mac: String, label: String, target: BlockTarget, schedule: BlockSchedule?): List<UciOp> =
        listOf(UciOp.Add("firewall", "rule", values(mac, label, target, schedule)))

    /**
     * Changing how an existing block works (target and schedule). Refuses a section that isn't one of
     * WrtCtrl's rules, so no other firewall rule is ever touched.
     */
    fun updateOps(rule: UciSection, target: BlockTarget, schedule: BlockSchedule?): List<UciOp> {
        requireOurs(rule)
        val set = mutableMapOf<String, Any>("target" to target.name, "enabled" to "1")
        val delete = mutableListOf<String>()
        if (schedule == null) {
            delete += TIME_OPTIONS.filter { rule.opt(it).isNotEmpty() }
        } else {
            val v = scheduleValues(schedule)
            set.putAll(v)
            if ("weekdays" !in v && rule.opt("weekdays").isNotEmpty()) delete += "weekdays"
        }
        return buildList {
            add(UciOp.Set("firewall", rule.name, set))
            if (delete.isNotEmpty()) add(UciOp.Delete("firewall", rule.name, delete))
        }
    }

    /** Removing a block. Refuses any rule that isn't WrtCtrl's. */
    fun unblockOps(rule: UciSection): List<UciOp> {
        requireOurs(rule)
        return listOf(UciOp.Delete("firewall", rule.name))
    }

    fun requireOurs(rule: UciSection) {
        require(isOurs(rule)) { "not a WrtCtrl block rule" }
    }
}

/** Stages [ops] with uci (applied later through [Changes]). */
suspend fun UbusClient.stage(ops: List<UciOp>) {
    for (op in ops) {
        when (op) {
            is UciOp.Add -> uciAdd(op.config, op.type, op.values)
            is UciOp.Set -> uciSet(op.config, op.section, op.values)
            is UciOp.Delete -> uciDelete(op.config, op.section, op.options)
        }
    }
}

/**
 * Whether a device is the phone WrtCtrl runs on: one of its addresses is one of the phone's own
 * addresses on the network it's connected through. The router sees the phone by IP (Android hides its
 * MAC address from apps and uses a private, random one per network anyway), so the MAC is found from that.
 */
object ThisPhone {
    fun matches(deviceIps: Collection<String>, phoneIps: Collection<String>): Boolean {
        val phone = phoneIps.map(::normaliseIp).filter { it.isNotEmpty() }.toSet()
        return deviceIps.any { normaliseIp(it) in phone }
    }

    /** Lower case, without a zone (%wlan0) or prefix length, so IPv6 forms compare equal. */
    fun normaliseIp(ip: String): String = ip.trim().substringBefore('%').substringBefore('/').lowercase()
}
