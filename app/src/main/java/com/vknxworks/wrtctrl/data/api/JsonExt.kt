package com.vknxworks.wrtctrl.data.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/*
 * Lenient readers for router replies: a missing field, a number sent as a string or a boolean sent
 * as "1" never fails, it just gives the default.
 */

fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.arr(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())

fun JsonObject.str(key: String, default: String = ""): String =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull ?: default

fun JsonObject.long(key: String, default: Long = 0): Long {
    val p = this[key] as? JsonPrimitive ?: return default
    return p.longOrNull ?: p.doubleOrNull?.toLong() ?: p.contentOrNull?.trim()?.toLongOrNull() ?: default
}

fun JsonObject.double(key: String, default: Double = 0.0): Double {
    val p = this[key] as? JsonPrimitive ?: return default
    return p.doubleOrNull ?: p.contentOrNull?.trim()?.toDoubleOrNull() ?: default
}

fun JsonObject.int(key: String, default: Int = 0): Int = long(key, default.toLong()).toInt()

/** true / false, 1 / 0, "1" / "0", "on" / "off", "yes" / "no". */
fun JsonObject.bool(key: String, default: Boolean = false): Boolean {
    val p = this[key] as? JsonPrimitive ?: return default
    p.booleanOrNull?.let { return it }
    return when (p.contentOrNull?.trim()?.lowercase()) {
        "1", "true", "on", "yes", "enabled" -> true
        "0", "false", "off", "no", "disabled" -> false
        else -> default
    }
}

/** A uci option that may be a single value or a list. */
fun JsonObject.strings(key: String): List<String> = when (val v = this[key]) {
    is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    is JsonPrimitive -> listOfNotNull(v.contentOrNull?.takeIf { it.isNotEmpty() })
    else -> emptyList()
}

fun JsonElement?.asObj(): JsonObject? = this as? JsonObject

fun JsonArray.objects(): List<JsonObject> = mapNotNull { it as? JsonObject }
