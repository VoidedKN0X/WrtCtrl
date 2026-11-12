package com.vknxworks.wrtctrl.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** `system.board`: what the router is and which OpenWrt it runs. Every field is optional. */
@Serializable
data class BoardInfo(
    val hostname: String = "",
    val model: String = "",
    val kernel: String = "",
    val system: String = "",
    val board_name: String = "",
    val release: Release = Release(),
) {
    @Serializable
    data class Release(
        val distribution: String = "",
        val version: String = "",
        val revision: String = "",
        val target: String = "",
        val description: String = "",
    )

    companion object {
        fun parse(data: JsonObject): BoardInfo = runCatching { UbusJson.decodeFromJsonElement(serializer(), data) }.getOrDefault(BoardInfo())
    }
}

/** Reads `system.board`. */
suspend fun UbusClient.board(): BoardInfo = BoardInfo.parse(call("system", "board"))
