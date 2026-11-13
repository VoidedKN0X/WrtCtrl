package com.vknxworks.wrtctrl.data.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One uci section with its options (lists as JSON arrays). */
data class UciSection(val name: String, val type: String, val anonymous: Boolean, val index: Int, val options: JsonObject) {
    fun opt(key: String, default: String = ""): String = options.str(key, default)
    fun list(key: String): List<String> = options.strings(key)
    fun flag(key: String, default: Boolean = false): Boolean = options.bool(key, default)
}

/**
 * Reads [config] (optionally only sections of [type]), with this session's unapplied changes, sorted as
 * in the file. Option names starting with "." (uci's own bookkeeping) are left out of [UciSection.options].
 */
suspend fun UbusClient.uciGet(config: String, type: String? = null): List<UciSection> {
    val args = buildJsonObject {
        put("config", config)
        if (type != null) put("type", type)
    }
    val values = runCatching { call("uci", "get", args).obj("values") }.getOrElse { e ->
        // An empty or missing config (no wireless on an x86 router) isn't an error for the caller.
        if (e is RouterError.Ubus && e.code == UbusStatus.NOT_FOUND) null else throw e
    } ?: return emptyList()
    return values.mapNotNull { (name, v) ->
        val o = v as? JsonObject ?: return@mapNotNull null
        UciSection(
            name = o.str(".name", name),
            type = o.str(".type"),
            anonymous = o.bool(".anonymous"),
            index = o.int(".index"),
            options = JsonObject(o.filterKeys { !it.startsWith(".") }),
        )
    }.sortedBy { it.index }
}

private fun valuesObject(values: Map<String, Any>): JsonObject = buildJsonObject {
    values.forEach { (k, v) ->
        when (v) {
            is String -> put(k, v)
            is Boolean -> put(k, if (v) "1" else "0")
            is Number -> put(k, v.toString())
            is List<*> -> put(k, JsonArray(v.map { JsonPrimitive(it.toString()) }))
            is JsonElement -> put(k, v)
            else -> put(k, v.toString())
        }
    }
}

/** Stages option changes on a section (applied later with [UbusClient.uciApply]). */
suspend fun UbusClient.uciSet(config: String, section: String, values: Map<String, Any>) {
    call("uci", "set", buildJsonObject {
        put("config", config)
        put("section", section)
        put("values", valuesObject(values))
    }, write = true)
}

/** Stages a new section and returns its name. */
suspend fun UbusClient.uciAdd(config: String, type: String, values: Map<String, Any>): String =
    call("uci", "add", buildJsonObject {
        put("config", config)
        put("type", type)
        put("values", valuesObject(values))
    }, write = true).str("section")

/** Stages removing a section, or only [options] of it. */
suspend fun UbusClient.uciDelete(config: String, section: String, options: List<String> = emptyList()) {
    call("uci", "delete", buildJsonObject {
        put("config", config)
        put("section", section)
        if (options.isNotEmpty()) put("options", JsonArray(options.map { JsonPrimitive(it) }))
    }, write = true)
}

/** Drops this session's unapplied changes to [config]. */
suspend fun UbusClient.uciRevert(config: String) {
    call("uci", "revert", buildJsonObject { put("config", config) })
}

/** How many unapplied changes this session has, per config. */
suspend fun UbusClient.uciChanges(): Map<String, Int> =
    call("uci", "changes").obj("changes")?.mapNotNull { (k, v) -> (v as? JsonArray)?.let { k to it.size } }?.toMap().orEmpty()

/**
 * Applies the staged changes with OpenWrt's rollback: if [uciConfirm] doesn't arrive within
 * [timeoutSeconds], the router puts the old configuration back by itself.
 */
suspend fun UbusClient.uciApply(timeoutSeconds: Int) {
    call("uci", "apply", buildJsonObject { put("rollback", true); put("timeout", timeoutSeconds) }, write = true)
}

/** Keeps the applied changes (cancels the pending rollback). */
suspend fun UbusClient.uciConfirm() {
    call("uci", "confirm")
}

/** Undoes the applied changes now instead of waiting for the rollback. */
suspend fun UbusClient.uciRollback() {
    call("uci", "rollback")
}
