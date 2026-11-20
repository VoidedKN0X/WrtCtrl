package com.vknxworks.wrtctrl.data.crypto

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps the data key with the Keystore key [alias] ([KeystoreAes]: AES-256-GCM, not exportable, StrongBox
 * when the phone has it, no fingerprint or PIN needed):
 *
 *     "WRTCTRL-KEY1" | IV (12, chosen by the Keystore) | ciphertext + tag
 */
class KeystoreWrapper(private val alias: String) : KeyWrapper {
    override fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(KeystoreAes.TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, KeystoreAes.key(alias))
        val iv = cipher.iv
        if (iv == null || iv.size != IV_LEN) throw IllegalStateException("unexpected IV")
        return MAGIC + iv + cipher.doFinal(plain)
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        val headerEnd = MAGIC.size + IV_LEN
        if (wrapped.size <= headerEnd || !wrapped.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw IllegalStateException("not a key file")
        }
        val cipher = Cipher.getInstance(KeystoreAes.TRANSFORMATION)
        // Only ever with the key that's already there: unwrapping must not make a new Keystore key.
        val key = KeystoreAes.existing(alias) ?: throw IllegalStateException("the Keystore key is gone")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, wrapped, MAGIC.size, IV_LEN))
        return cipher.doFinal(wrapped, headerEnd, wrapped.size - headerEnd)
    }

    private companion object {
        val MAGIC = "WRTCTRL-KEY1".toByteArray(Charsets.US_ASCII)
        const val IV_LEN = 12
    }
}
