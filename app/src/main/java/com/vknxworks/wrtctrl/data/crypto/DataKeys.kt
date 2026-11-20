package com.vknxworks.wrtctrl.data.crypto

import android.content.Context

/**
 * The one [DataKeyStore] and [SealedCache] of this process. The data key is kept in `data.key` in the app's
 * private folder, wrapped by the Keystore key [ALIAS]. That key is separate from the keys that protect the
 * saved passwords and from the app lock's keys, which are never touched here.
 */
object DataKeys {
    private const val ALIAS = "wrtctrl_data_at_rest"

    @Volatile
    private var keyStore: DataKeyStore? = null

    @Volatile
    private var sealedCache: SealedCache? = null

    fun keys(context: Context): DataKeyStore = keyStore ?: synchronized(this) {
        keyStore ?: DataKeyStore(context.applicationContext.filesDir, KeystoreWrapper(ALIAS)).also { keyStore = it }
    }

    fun cache(context: Context): SealedCache = sealedCache ?: synchronized(this) {
        sealedCache ?: SealedCache(keys(context)).also { sealedCache = it }
    }
}
