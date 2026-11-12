package com.vknxworks.wrtctrl.data.api

import com.vknxworks.wrtctrl.data.router.Router
import com.vknxworks.wrtctrl.data.router.RouterSecrets
import com.vknxworks.wrtctrl.data.router.Tls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

/** Lenient JSON for everything the router sends: unknown or missing fields never fail a parse. */
val UbusJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/**
 * OkHttp client for one router: normal certificate checks, or only the pinned certificate when the
 * user confirmed a self-signed one (never "trust all").
 */
fun httpFor(base: OkHttpClient, host: String, certPin: String): OkHttpClient {
    if (certPin.isBlank()) return base
    val tm = Tls.PinnedTrustManager(certPin, Tls.systemTrustManager())
    val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(tm), null) }
    return base.newBuilder()
        .sslSocketFactory(ctx.socketFactory, tm)
        .hostnameVerifier(Tls.pinnedHostnameVerifier(certPin, host))
        .build()
}

/**
 * What a login may do, from the `acls` rpcd returns with `session.login`: ubus objects and their
 * methods, uci configs (read / write) and file paths (read, write, exec, …). Names can hold `*` and
 * `?` wildcards, matched like rpcd does (fnmatch). Root's login has `*` everywhere.
 */
data class RouterAcl(
    val ubus: Map<String, List<String>> = emptyMap(),
    val uci: Map<String, List<String>> = emptyMap(),
    val file: Map<String, List<String>> = emptyMap(),
    val cgiIo: List<String> = emptyList(),
) {
    fun canCall(obj: String, method: String): Boolean =
        ubus.any { (o, methods) -> glob(o, obj) && methods.any { glob(it, method) } }

    fun canReadUci(config: String): Boolean =
        uci.any { (c, perms) -> glob(c, config) && ("read" in perms || "write" in perms) }

    fun canWriteUci(config: String): Boolean =
        uci.any { (c, perms) -> glob(c, config) && "write" in perms }

    /** Whether [command] (a program path plus arguments, as LuCI writes it) may run through `file exec`. */
    fun canExec(command: String): Boolean =
        canCall("file", "exec") && file.any { (path, perms) -> glob(path, command) && "exec" in perms }

    /** Saving and applying uci changes (with rollback): every config change goes through these. */
    val canApply: Boolean get() = canCall("uci", "set") && canCall("uci", "apply") && canCall("uci", "confirm")

    /** Upgrading packages through LuCI's package-manager-call wrapper (apk or opkg). */
    val canUpgradePackages: Boolean get() = canExec("$PACKAGE_MANAGER upgrade luci-base")

    /** Nothing this login may do changes the router (listing packages through `file exec` doesn't count). */
    val isReadOnly: Boolean
        get() = !canApply && !canCall("system", "reboot") && !canCall("rc", "init") &&
            !canCall("network.interface.wan", "up") && !canUpgradePackages

    companion object {
        val NONE = RouterAcl()

        /** LuCI's wrapper around apk or opkg, so the same commands work with either package manager. */
        const val PACKAGE_MANAGER = "/usr/libexec/package-manager-call"

        fun parse(acls: JsonObject?): RouterAcl {
            if (acls == null) return NONE
            fun section(name: String): Map<String, List<String>> =
                (acls[name] as? JsonObject)?.mapValues { (_, v) -> strings(v) } ?: emptyMap()
            val cgi = when (val v = acls["cgi-io"]) {
                is JsonObject -> v.keys.toList()
                else -> strings(v)
            }
            return RouterAcl(ubus = section("ubus"), uci = section("uci"), file = section("file"), cgiIo = cgi)
        }

        private fun strings(e: JsonElement?): List<String> =
            (e as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

        /** fnmatch-style match: `*` is any run of characters, `?` one character, everything else literal. */
        fun glob(pattern: String, value: String): Boolean {
            if (pattern == "*") return true
            val regex = buildString {
                pattern.forEach { ch ->
                    when (ch) {
                        '*' -> append(".*")
                        '?' -> append('.')
                        else -> append(Regex.escape(ch.toString()))
                    }
                }
            }
            return Regex(regex).matches(value)
        }
    }
}

/** A successful `session.login`. */
class LoginResult(val session: String, val timeout: Long, val acl: RouterAcl)

/** ubus status codes (ubus_msg_status), for messages. */
object UbusStatus {
    const val OK = 0
    const val INVALID_COMMAND = 1
    const val INVALID_ARGUMENT = 2
    const val METHOD_NOT_FOUND = 3
    const val NOT_FOUND = 4
    const val NO_DATA = 5
    const val PERMISSION_DENIED = 6
    const val TIMEOUT = 7
    const val NOT_SUPPORTED = 8
}

/** rpcd answered "Access denied": the session ended, or this login's ACL doesn't allow the call. */
private class AccessDenied(message: String?) : IOException(message)

/**
 * OpenWrt's ubus JSON-RPC API at <router>/ubus (uhttpd-mod-ubus + rpcd), the API LuCI uses.
 *
 * Every call carries the ubus_rpc_session from `session.login`. rpcd ends a session after a few
 * minutes without use; when it does, the client signs in again with the saved password (once) and
 * repeats the call, so the user never sees it. Calls this login's ACL doesn't allow fail with
 * [RouterError.Forbidden] without being sent.
 *
 * Calls that change something ([call] with write = true) are refused in read-only mode and, with the
 * app lock's "Confirm changes" on, only sent after the fingerprint or PIN.
 */
class UbusClient(
    val router: Router,
    baseHttp: OkHttpClient,
    initial: RouterSecrets,
    /** The saved setting, not this copy of the router: a client made before read-only was switched on must not keep writing. */
    private val isReadOnly: () -> Boolean = { router.readOnly },
    private val onSecretsChanged: (RouterSecrets) -> Unit = {},
) {
    val http: OkHttpClient = httpFor(baseHttp, router.host, router.certPin)
    private val endpoint: HttpUrl = endpointFor(router.url)
    private val loginLock = Mutex()

    @Volatile
    var secrets: RouterSecrets = initial
        private set

    /** What this login may do; known after the first sign-in in this process (null until then). */
    @Volatile
    var acl: RouterAcl? = null
        private set

    /** Calls [obj].[method] with [args] and returns its reply (an empty object when it sends none). */
    suspend fun call(obj: String, method: String, args: JsonObject = EMPTY, write: Boolean = false): JsonObject {
        if (write) {
            if (isReadOnly()) throw RouterError.ReadOnly()
            if (!WriteGuard.check()) throw RouterError.Cancelled()
        }
        acl?.let { if (!it.canCall(obj, method)) throw RouterError.Forbidden("$obj.$method") }
        if (secrets.session.isBlank()) signIn(stale = "")
        val sid = secrets.session
        return try {
            rawCall(http, endpoint, sid, obj, method, args)
        } catch (e: AccessDenied) {
            // Usually an expired session: sign in again and try once more.
            signIn(stale = sid)
            if (acl?.canCall(obj, method) == false) throw RouterError.Forbidden("$obj.$method")
            try {
                rawCall(http, endpoint, secrets.session, obj, method, args)
            } catch (e: AccessDenied) {
                throw RouterError.Forbidden(e.message)
            }
        }
    }

    /** Signs in again now (to learn what the login may do, or after the password changed). */
    suspend fun refreshLogin() = signIn(stale = secrets.session, force = true)

    private suspend fun signIn(stale: String, force: Boolean = false) = loginLock.withLock {
        // Another call already signed in again while this one waited.
        if (!force && secrets.session.isNotBlank() && secrets.session != stale) return@withLock
        if (secrets.password.isEmpty()) throw RouterError.Unauthorized(null)
        val res = login(http, router.url, router.username, secrets.password)
        secrets = secrets.copy(
            session = res.session,
            sessionIssuedAt = System.currentTimeMillis(),
            sessionTimeout = res.timeout,
        )
        acl = res.acl
        runCatching { onSecretsChanged(secrets) }
    }

    companion object {
        /** The session ID rpcd expects for calls made before signing in (only session.login is allowed). */
        const val NO_SESSION = "00000000000000000000000000000000"

        val EMPTY = JsonObject(emptyMap())

        private val JSON_TYPE = "application/json".toMediaType()
        private val ids = AtomicInteger()

        fun endpointFor(baseUrl: String): HttpUrl = "${baseUrl.trimEnd('/')}/ubus".toHttpUrl()

        /** `session.login` with [username] and [password]. Wrong credentials are [RouterError.Unauthorized]. */
        suspend fun login(http: OkHttpClient, baseUrl: String, username: String, password: String): LoginResult {
            val args = buildJsonObject {
                put("username", username)
                put("password", password)
            }
            val data = try {
                rawCall(http, endpointFor(baseUrl), NO_SESSION, "session", "login", args)
            } catch (e: RouterError.Forbidden) {
                // rpcd answers a wrong username or password with "permission denied".
                throw RouterError.Unauthorized(null)
            } catch (e: AccessDenied) {
                throw RouterError.Unauthorized(null)
            }
            val session = data["ubus_rpc_session"]?.jsonPrimitive?.contentOrNull
            if (session.isNullOrBlank()) throw RouterError.NotOpenWrt(null)
            return LoginResult(
                session = session,
                timeout = data["timeout"]?.jsonPrimitive?.longOrNull ?: 0L,
                acl = RouterAcl.parse(data["acls"] as? JsonObject),
            )
        }

        private suspend fun rawCall(
            http: OkHttpClient,
            endpoint: HttpUrl,
            session: String,
            obj: String,
            method: String,
            args: JsonObject,
        ): JsonObject = withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", ids.incrementAndGet())
                put("method", "call")
                put("params", buildJsonArray {
                    add(JsonPrimitive(session))
                    add(JsonPrimitive(obj))
                    add(JsonPrimitive(method))
                    add(args)
                })
            }.toString()
            val request = Request.Builder()
                .url(endpoint)
                .post(body.toRequestBody(JSON_TYPE))
                .header("Accept", "application/json")
                .build()
            val (code, text) = try {
                http.newCall(request).execute().use { res -> res.code to (res.body?.string().orEmpty()) }
            } catch (e: IOException) {
                throw RouterError.fromIo(e)
            }
            parse(code, text)
        }

        /** Turns a JSON-RPC reply into the call's data or the matching [RouterError]. */
        private fun parse(httpCode: Int, text: String): JsonObject {
            val root = runCatching { UbusJson.parseToJsonElement(text).jsonObject }.getOrNull()
            if (root == null || (root["result"] == null && root["error"] == null)) {
                // Not the ubus API: a LuCI page, a reverse proxy, AdGuard Home on another port, …
                throw RouterError.NotOpenWrt(if (httpCode != 200) "HTTP $httpCode" else null)
            }
            (root["error"] as? JsonObject)?.let { err ->
                val code = err["code"]?.jsonPrimitive?.intOrNull ?: 0
                val message = err["message"]?.jsonPrimitive?.contentOrNull
                // -32002: the session is unknown or expired, or the ACL doesn't allow this call.
                if (code == -32002) throw AccessDenied(message)
                throw RouterError.Ubus(code, message)
            }
            val result = runCatching { root["result"]!!.jsonArray }.getOrNull()
                ?: throw RouterError.NotOpenWrt(null)
            val status = result.getOrNull(0)?.jsonPrimitive?.intOrNull ?: UbusStatus.OK
            return when (status) {
                UbusStatus.OK -> result.getOrNull(1) as? JsonObject ?: EMPTY
                UbusStatus.PERMISSION_DENIED -> throw RouterError.Forbidden(null)
                else -> throw RouterError.Ubus(status, null)
            }
        }
    }
}
