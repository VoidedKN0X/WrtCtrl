package com.vknxworks.wrtctrl.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException
import java.io.OutputStream

/**
 * Downloads a configuration backup (what LuCI's "Generate archive" makes, `sysupgrade -b`) through
 * cgi-io's backup endpoint, with this login's session, into [out]. The login needs the cgi-io "backup"
 * permission. Returns the number of bytes written.
 */
suspend fun UbusClient.downloadBackup(out: OutputStream): Long {
    // Make sure the session is fresh: cgi-io checks it, but can't sign in again by itself.
    call("system", "board")
    val url = "${router.url.trimEnd('/')}/cgi-bin/cgi-backup".toHttpUrl()
    val body = FormBody.Builder().add("sessionid", secrets.session).build()
    val request = Request.Builder().url(url).post(body).build()
    return withContext(Dispatchers.IO) {
        try {
            http.newCall(request).execute().use { res ->
                when (res.code) {
                    200 -> Unit
                    403 -> throw RouterError.Forbidden("cgi-io backup")
                    404 -> throw RouterError.NotOpenWrt("cgi-backup: HTTP 404")
                    else -> throw RouterError.Ubus(res.code, "cgi-backup: HTTP ${res.code}")
                }
                val type = res.header("Content-Type").orEmpty()
                if (type.contains("text/html")) throw RouterError.Forbidden("cgi-io backup")
                val src = res.body?.byteStream() ?: throw RouterError.Ubus(UbusStatus.NO_DATA, null)
                src.copyTo(out)
            }
        } catch (e: RouterError) {
            throw e
        } catch (e: IOException) {
            throw RouterError.fromIo(e)
        }
    }
}
