package com.vknxworks.wrtctrl

import com.vknxworks.wrtctrl.data.api.UbusClient
import com.vknxworks.wrtctrl.data.api.UbusJson
import com.vknxworks.wrtctrl.data.router.Router
import com.vknxworks.wrtctrl.data.router.RouterSecrets
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import java.util.Collections

/**
 * A fake rpcd on 127.0.0.1 answering ubus JSON-RPC calls with recorded replies, so the client code runs
 * for real without a router. [answer] gets (object, method, args) and returns the reply's data object
 * (as JSON text), or null for "method not found".
 */
class FakeUbus(private val answer: (String, String, JsonObject) -> String?) : AutoCloseable {
    data class Call(val session: String, val obj: String, val method: String, val args: JsonObject)

    val calls: MutableList<Call> = Collections.synchronizedList(mutableListOf())

    private val server = FakeHttpServer { req ->
        val body = UbusJson.parseToJsonElement(req.body).jsonObject
        val id = body["id"]
        val p = body["params"]!!.jsonArray
        val call = Call(
            (p[0] as JsonPrimitive).contentOrNull.orEmpty(),
            (p[1] as JsonPrimitive).contentOrNull.orEmpty(),
            (p[2] as JsonPrimitive).contentOrNull.orEmpty(),
            p[3] as JsonObject,
        )
        calls += call
        val data = answer(call.obj, call.method, call.args)
        val result = if (data == null) "[3]" else "[0, $data]"
        FakeHttpServer.Response(200, """{"jsonrpc":"2.0","id":$id,"result":$result}""")
    }

    /** A client signed in with a made-up session (no login call needed). */
    fun client(readOnly: Boolean = false): UbusClient = UbusClient(
        Router(id = "r1", name = "Test router", url = server.url, username = "wrtctrl", readOnly = readOnly),
        OkHttpClient(),
        RouterSecrets(password = "unused", session = "0123456789abcdef0123456789abcdef"),
    )

    fun callsTo(obj: String, method: String): List<Call> = calls.filter { it.obj == obj && it.method == method }

    override fun close() = server.close()

    companion object {
        fun strings(a: JsonArray?): List<String> = a?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
    }
}
