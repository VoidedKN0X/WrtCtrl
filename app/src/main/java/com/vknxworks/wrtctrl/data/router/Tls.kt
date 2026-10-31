package com.vknxworks.wrtctrl.data.router

import java.net.InetSocketAddress
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Trust-on-first-use certificate pinning for servers with a self-signed certificate.
 * The user confirms the SHA-256 fingerprint of the server's certificate once; after that only
 * that exact certificate (or one the phone already trusts) is accepted.
 */
object Tls {

    /** SHA-256 of the DER-encoded certificate, as colon-separated upper-case hex. */
    fun fingerprint(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(":") { "%02X".format(it) }

    /** The platform trust manager (system CAs plus user CAs, per network_security_config). */
    fun systemTrustManager(): X509TrustManager {
        val f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        f.init(null as KeyStore?)
        return f.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    class PinMismatchException : CertificateException(
        "pinned certificate changed"
    )

    /** Accepts the chain only if its leaf matches [pin]; otherwise falls back to normal validation. */
    class PinnedTrustManager(private val pin: String, private val system: X509TrustManager) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
            system.checkClientTrusted(chain, authType)

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull() ?: throw CertificateException("Empty certificate chain")
            if (fingerprint(leaf).equals(pin, ignoreCase = true)) return
            try {
                system.checkServerTrusted(chain, authType)
            } catch (e: CertificateException) {
                throw PinMismatchException()
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers
    }

    /**
     * Skips the hostname check only for the configured server [host] presenting the pinned
     * certificate (an OpenWrt self-signed certificate names the router, not the address you type). Everything
     * else gets the normal check.
     */
    fun pinnedHostnameVerifier(pin: String, host: String): HostnameVerifier {
        val default = HttpsURLConnection.getDefaultHostnameVerifier()
        return HostnameVerifier { h, session ->
            val leaf = try { session.peerCertificates.firstOrNull() as? X509Certificate } catch (_: Exception) { null }
            if (h.equals(host, ignoreCase = true) && leaf != null && fingerprint(leaf).equals(pin, ignoreCase = true)) true
            else default.verify(h, session)
        }
    }

    private class Captured : CertificateException("captured")

    /**
     * Reads the certificate chain a server presents, without trusting it: the handshake is
     * aborted as soon as the chain arrives, so nothing (no login, no cookie) is ever sent.
     */
    fun fetchLeaf(host: String, port: Int): X509Certificate {
        var chain: Array<out X509Certificate>? = null
        val capture = object : X509TrustManager {
            override fun checkClientTrusted(c: Array<out X509Certificate>?, authType: String?) {
                throw CertificateException()
            }
            override fun checkServerTrusted(c: Array<out X509Certificate>?, authType: String?) {
                chain = c
                throw Captured()
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<javax.net.ssl.TrustManager>(capture), null)
        val raw = java.net.Socket()
        try {
            raw.connect(InetSocketAddress(host, port), 10_000)
            raw.soTimeout = 10_000
            val s = ctx.socketFactory.createSocket(raw, host, port, true) as SSLSocket
            try { s.startHandshake() } catch (e: Exception) { if (chain == null) throw e } finally { runCatching { s.close() } }
        } finally {
            runCatching { raw.close() }
        }
        return chain?.firstOrNull() ?: throw CertificateException("The server sent no certificate")
    }

    private fun causes(e: Throwable): Sequence<Throwable> = generateSequence(e) { it.cause.takeIf { c -> c !== it } }

    /** True when the connection failed because the certificate wasn't trusted (not a network error). */
    fun isCertificateError(e: Throwable): Boolean = causes(e).any {
        it is CertificateException || it is CertPathValidatorException || it is SSLPeerUnverifiedException
    }

    /** True when the server presented a different certificate from the one the user pinned. */
    fun isPinMismatch(e: Throwable): Boolean = causes(e).any { it is PinMismatchException }
}
