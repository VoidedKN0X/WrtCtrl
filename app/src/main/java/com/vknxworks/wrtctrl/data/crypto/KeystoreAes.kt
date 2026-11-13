package com.vknxworks.wrtctrl.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM keys that live in the Android Keystore and can't be exported. A new key is made in
 * StrongBox (the phone's separate security chip) when there is one and it handles AES-GCM, and in the
 * normal Keystore otherwise. A key that already exists is kept as it is, so nothing saved has to be
 * encrypted again. The key handle is looked up once and kept in memory (the key itself never leaves
 * the Keystore), so the Keystore isn't opened again for every password that's read.
 */
object KeystoreAes {
    private const val PROVIDER = "AndroidKeyStore"
    const val TRANSFORMATION = "AES/GCM/NoPadding"

    private val handles = ConcurrentHashMap<String, SecretKey>()

    /** The key called [alias], made the first time it's needed. */
    fun key(alias: String): SecretKey = handles[alias] ?: synchronized(this) {
        handles[alias] ?: loadOrCreate(alias).also { handles[alias] = it }
    }

    private fun loadOrCreate(alias: String): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        // StrongBox first. Phones without it throw StrongBoxUnavailableException; a chip that makes the
        // key but can't use it for AES-GCM is caught by the test below, and the key is made again without it.
        val strong = runCatching { generate(alias, strongBox = true) }.getOrNull()
        if (strong != null) {
            if (works(strong)) return strong
            runCatching { ks.deleteEntry(alias) }
        }
        return generate(alias, strongBox = false)
    }

    private fun generate(alias: String, strongBox: Boolean): SecretKey {
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setIsStrongBoxBacked(strongBox)
                .build(),
        )
        return gen.generateKey()
    }

    /** Encrypts and decrypts a few test bytes with [key]. */
    private fun works(key: SecretKey): Boolean = runCatching {
        val probe = byteArrayOf(0x43, 0x74, 0x72, 0x6c)
        val enc = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        val sealed = enc.doFinal(probe)
        val dec = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, enc.iv)) }
        dec.doFinal(sealed).contentEquals(probe)
    }.getOrDefault(false)
}
