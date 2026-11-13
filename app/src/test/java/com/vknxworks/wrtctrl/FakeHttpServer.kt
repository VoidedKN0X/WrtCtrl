package com.vknxworks.wrtctrl

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.util.Collections
import kotlin.concurrent.thread

/**
 * A tiny HTTP/1.1 server on 127.0.0.1 for unit tests: answers each request with what [handler] returns
 * (recorded API responses), and remembers every request it saw. Nothing leaves the machine.
 */
class FakeHttpServer(private val handler: (Request) -> Response) : AutoCloseable {
    data class Request(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>, val body: String) {
        fun header(name: String): String? = headers[name.lowercase()]
    }

    data class Response(val code: Int, val body: String = "", val headers: Map<String, String> = emptyMap())

    val requests: MutableList<Request> = Collections.synchronizedList(mutableListOf())
    private val socket = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    val port: Int get() = socket.localPort
    val url: String get() = "http://127.0.0.1:$port"

    init {
        thread(isDaemon = true, name = "fake-http-$port") {
            while (!socket.isClosed) {
                val s = try { socket.accept() } catch (_: SocketException) { break }
                thread(isDaemon = true) { s.use { serve(it) } }
            }
        }
    }

    private fun serve(s: Socket) {
        val input = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
        val requestLine = input.readLine() ?: return
        val parts = requestLine.split(' ')
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = input.readLine() ?: break
            if (line.isEmpty()) break
            headers[line.substringBefore(':').trim().lowercase()] = line.substringAfter(':').trim()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val body = CharArray(length).let { buf -> var read = 0; while (read < length) { val n = input.read(buf, read, length - read); if (n < 0) break; read += n }; String(buf, 0, read) }
        val target = parts.getOrElse(1) { "/" }
        val query = target.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }
        val req = Request(parts[0], target.substringBefore('?'), query, headers, body)
        requests += req
        val r = handler(req)
        val bytes = r.body.toByteArray(Charsets.UTF_8)
        val out = s.getOutputStream()
        val head = buildString {
            append("HTTP/1.1 ${r.code} X\r\n")
            append("Content-Type: application/json\r\n")
            r.headers.forEach { (k, v) -> append("$k: $v\r\n") }
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(head.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    override fun close() = socket.close()
}
