package com.vknxworks.wrtctrl.data.router

import android.content.Context
import java.util.zip.GZIPInputStream

/**
 * Device vendors by MAC prefix, from the IEEE registry (bundled as assets/oui.tsv.gz, from the
 * oui-data project). Looked up on the phone only: no MAC address ever leaves it.
 */
object Oui {
    @Volatile
    private var table: Map<String, String>? = null

    /** Loads the table (about 40,000 vendors) once; call off the main thread. */
    fun load(context: Context): Map<String, String> {
        table?.let { return it }
        synchronized(this) {
            table?.let { return it }
            val map = HashMap<String, String>(48_000)
            runCatching {
                GZIPInputStream(context.assets.open("oui.tsv.gz")).bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val tab = line.indexOf('\t')
                        if (tab == 6) map[line.substring(0, 6)] = line.substring(7)
                    }
                }
            }
            table = map
            return map
        }
    }

    /**
     * The vendor of [mac], or null. A locally administered (randomised) address, as phones use for
     * privacy on Wi-Fi, has no vendor.
     */
    fun vendor(context: Context, mac: String): String? {
        val hex = mac.filter { it.isLetterOrDigit() }.uppercase()
        if (hex.length < 6) return null
        if (isRandomised(mac)) return null
        return load(context)[hex.substring(0, 6)]?.let(::tidy)
    }

    /** The locally administered bit is set: a private (random) address. */
    fun isRandomised(mac: String): Boolean {
        val first = mac.filter { it.isLetterOrDigit() }.take(2).toIntOrNull(16) ?: return false
        return first and 0x02 != 0
    }

    /** "SAMSUNG ELECTRONICS CO.,LTD" → "Samsung Electronics". */
    private fun tidy(name: String): String {
        val cleaned = name
            .replace(Regex("[,.]?\\s*(CO\\.?,?\\s*LTD|CORPORATION|CORP|INC|LTD|LIMITED|GMBH|LLC|S\\.?A\\.?|B\\.?V\\.?|AG|CO)\\.?$", RegexOption.IGNORE_CASE), "")
            .trim()
            .trimEnd(',', '.')
        return if (cleaned == cleaned.uppercase() && cleaned.length > 3) {
            cleaned.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }
        } else {
            cleaned
        }
    }
}
