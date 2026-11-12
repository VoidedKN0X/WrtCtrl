package com.vknxworks.wrtctrl.data.router

import com.vknxworks.wrtctrl.data.api.RouterError
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import java.util.concurrent.ConcurrentHashMap

/**
 * Plain HTTP is allowed by the platform (OpenWrt's web server, uhttpd, usually only speaks HTTP),
 * so WrtCtrl enforces it itself: an unencrypted request only goes out to a router address the user
 * chose after the "unencrypted connection" warning. That is a saved router with an http:// address,
 * or the address being set up right now once the user tapped **Use HTTP**. Everything else must be
 * HTTPS.
 *
 * Installed as a network interceptor on the shared client, so every hop is checked (also background
 * checks and widgets): an HTTP hop is only allowed when the call itself was
 * started for an allowed HTTP address, so an HTTPS request can never end up on plain HTTP.
 */
object CleartextPolicy {
    /** Origins of saved routers with an http:// address. Replaced whenever the router list changes. */
    @Volatile
    private var saved: Set<String> = emptySet()

    /** Origins accepted in the setup screen that aren't saved (yet). */
    private val pending: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private fun origin(url: HttpUrl) = "${url.host.lowercase()}:${url.port}"

    /** Called by [RouterStore] with the saved routers, every time they change. */
    fun syncSaved(routers: List<Router>) {
        saved = routers.mapNotNull { s -> s.url.toHttpUrlOrNull()?.takeIf { !it.isHttps }?.let(::origin) }.toSet()
    }

    /** The user accepted plain HTTP for [url] in the setup screen. No-op for https addresses. */
    fun allow(url: HttpUrl) {
        if (!url.isHttps) pending += origin(url)
    }

    /** Forgets what was accepted in the setup screen (saved routers stay allowed). */
    fun clearPending() = pending.clear()

    fun isAllowed(url: HttpUrl): Boolean = url.isHttps || origin(url).let { it in saved || it in pending }

    val interceptor = Interceptor { chain ->
        val url = chain.request().url
        if (!url.isHttps) {
            val original = chain.call().request().url
            if (original.isHttps || !isAllowed(original) || !isAllowed(url)) throw RouterError.CleartextBlocked(url.host)
        }
        chain.proceed(chain.request())
    }
}
