package com.vknxworks.wrtctrl.ui.components

import android.content.Context
import android.content.Intent
import com.vknxworks.wrtctrl.data.lock.AppLock

/** AdCtrl's package: "Open in AdCtrl" shows when it's installed. */
const val ADCTRL = "com.vknxworks.adctrl"

/** Whether the app [pkg] is installed (it has to be listed under `queries` in the manifest). */
fun isInstalled(context: Context, pkg: String): Boolean =
    runCatching { context.packageManager.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)

/** Opens AdCtrl; coming back from it doesn't count as leaving WrtCtrl for the app lock. */
fun openAdCtrl(context: Context) {
    context.packageManager.getLaunchIntentForPackage(ADCTRL)?.let { intent ->
        AppLock.expectReturn()
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
