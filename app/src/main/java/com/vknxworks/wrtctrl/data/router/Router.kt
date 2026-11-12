package com.vknxworks.wrtctrl.data.router

import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A saved OpenWrt router (the main router, or a dumb access point). Secrets (password, ubus session)
 * are never part of this object: they are stored separately, encrypted with a Keystore key.
 */
@Serializable
data class Router(
    val id: String,
    /** What the user calls it; defaults to the router's hostname. */
    val name: String,
    /** Base address without a path, e.g. http://192.168.1.1. The ubus API is at <url>/ubus. */
    val url: String,
    /** The rpcd login (a dedicated least-privilege user, or root). */
    val username: String = "",
    /** SHA-256 fingerprint of the pinned self-signed certificate; empty when the certificate is trusted normally. */
    val certPin: String = "",
    /** Hide every action that changes something on this router. */
    val readOnly: Boolean = false,
    /** The router's hostname (system.board), typed to confirm a reboot. */
    val hostname: String = "",
    /** Last seen board model, e.g. "GL.iNet GL-MT6000". */
    val model: String = "",
    /** Last seen OpenWrt release, e.g. "24.10.2". */
    val openWrtVersion: String = "",
) {
    val host: String get() = url.toHttpUrlOrNull()?.host ?: url

    /** Plain HTTP: the login and everything else travel unencrypted. */
    val isPlainHttp: Boolean get() = url.startsWith("http://", ignoreCase = true)

    /** First letter shown in the profile picture. */
    val initial: String get() = name.ifBlank { host }.take(1)
}

/** Secrets for one router. Held in memory only while needed; persisted encrypted by [RouterStore]. */
data class RouterSecrets(
    val password: String = "",
    /** The ubus_rpc_session ID from session.login. */
    val session: String = "",
    /** When [session] was issued (ms since epoch). */
    val sessionIssuedAt: Long = 0,
    /** The session timeout rpcd reported, in seconds. */
    val sessionTimeout: Long = 0,
) {
    /** Never prints the secrets themselves, should this object end up in a log or error message. */
    override fun toString(): String = "RouterSecrets(redacted)"
}

object RouterAddress {
    /**
     * Turns what the user typed into a base URL. No scheme means http:// on port 80, because OpenWrt's
     * web server serves LuCI and /ubus over plain HTTP unless HTTPS was set up; the app still asks
     * before the first unencrypted connection (see [CleartextPolicy]). An explicit https:// address
     * without a port uses 443. Paths are dropped. Returns null for anything that isn't a usable address.
     */
    fun normalize(input: String): HttpUrl? {
        val text = input.trim().trimEnd('/')
        if (text.isEmpty()) return null
        val https = text.startsWith("https://", ignoreCase = true)
        val hasScheme = https || isPlainHttp(text)
        if (!hasScheme && text.contains("://")) return null
        val withScheme = if (hasScheme) text else "http://$text"
        val parsed = withScheme.toHttpUrlOrNull() ?: return null
        return HttpUrl.Builder().scheme(if (https) "https" else "http").host(parsed.host).port(parsed.port).build()
    }

    /** The base URL as stored: no trailing slash. */
    fun base(url: HttpUrl): String = url.toString().trimEnd('/')

    fun isPlainHttp(input: String): Boolean = input.trim().startsWith("http://", ignoreCase = true)
}
