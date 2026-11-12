package com.vknxworks.wrtctrl.ui.components

import android.content.Context
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.data.api.RouterError

/** A message for [e] the user can act on, from strings.xml. [host] names the router in the message. */
fun errorMessage(context: Context, e: Throwable, host: String): String {
    val base = when (e) {
        is RouterError.Unreachable -> context.getString(R.string.error_unreachable, host)
        is RouterError.UntrustedCertificate -> context.getString(R.string.error_untrusted_cert, host)
        is RouterError.PinMismatch -> context.getString(R.string.error_pin_mismatch, host)
        is RouterError.Unauthorized -> context.getString(R.string.error_bad_password)
        is RouterError.Forbidden -> context.getString(R.string.error_forbidden)
        is RouterError.NotOpenWrt -> context.getString(R.string.error_not_openwrt, host)
        is RouterError.Ubus -> context.getString(R.string.error_ubus, e.code)
        is RouterError.ReadOnly -> context.getString(R.string.error_read_only)
        is RouterError.Cancelled -> context.getString(R.string.error_cancelled)
        is RouterError.CleartextBlocked -> context.getString(R.string.error_cleartext_blocked, e.host)
        else -> context.getString(R.string.error_generic)
    }
    // The router's own wording helps for ubus errors; network errors' detail is noise.
    val detail = (e as? RouterError.Ubus)?.detail
    return if (detail.isNullOrBlank()) base else context.getString(R.string.error_with_detail, base, detail)
}
