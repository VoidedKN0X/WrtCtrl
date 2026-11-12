package com.vknxworks.wrtctrl.data.api

import com.vknxworks.wrtctrl.data.router.Tls
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * Everything that can go wrong talking to a router, sorted into what the user can act on.
 * The UI turns these into messages from strings.xml; [detail] is the router's own wording when it gave one.
 */
sealed class RouterError(val detail: String? = null, cause: Throwable? = null) : IOException(detail, cause) {
    /** No route, DNS failure, refused or timed out. */
    class Unreachable(cause: Throwable?) : RouterError(cause?.message, cause)

    /** The certificate isn't trusted by the phone and hasn't been pinned. */
    class UntrustedCertificate(cause: Throwable?) : RouterError(cause?.message, cause)

    /** The router now presents a different certificate from the pinned one. */
    class PinMismatch(cause: Throwable?) : RouterError(cause?.message, cause)

    /**
     * WrtCtrl refused to send a request over plain HTTP to [host]: only router addresses the user
     * chose after the "unencrypted connection" warning may use HTTP. Nothing was sent.
     */
    class CleartextBlocked(val host: String) : RouterError(null)

    /** Wrong username or password, or the session ended and signing in again failed. */
    class Unauthorized(detail: String?) : RouterError(detail)

    /** Signed in, but this login's rpcd ACL doesn't allow the object or method. */
    class Forbidden(detail: String?) : RouterError(detail)

    /** The address answered, but not like OpenWrt's ubus JSON-RPC API (e.g. a different web page). */
    class NotOpenWrt(detail: String?) : RouterError(detail)

    /**
     * The router is in read-only mode, so WrtCtrl refused to send a request that could change
     * something. Raised before anything is sent, whatever the screen that asked for it.
     */
    class ReadOnly : RouterError(null)

    /** The user cancelled the fingerprint or PIN check before a change; nothing was sent. */
    class Cancelled : RouterError(null)

    /** Any other ubus error status (not found, invalid argument, …) with its numeric [code]. */
    class Ubus(val code: Int, detail: String?) : RouterError(detail)

    companion object {
        /** Sorts a network-level failure from OkHttp. */
        fun fromIo(e: IOException): RouterError = when {
            e is RouterError -> e
            Tls.isPinMismatch(e) -> PinMismatch(e)
            Tls.isCertificateError(e) -> UntrustedCertificate(e)
            e is SSLHandshakeException -> UntrustedCertificate(e)
            e is UnknownHostException || e is ConnectException || e is NoRouteToHostException || e is SocketTimeoutException -> Unreachable(e)
            else -> Unreachable(e)
        }
    }
}
