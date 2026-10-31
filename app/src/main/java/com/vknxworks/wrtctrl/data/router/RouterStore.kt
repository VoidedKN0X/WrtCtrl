package com.vknxworks.wrtctrl.data.router

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.vknxworks.wrtctrl.data.lock.LockVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Saved routers and the active one. Router settings are plain JSON; every secret (password and
 * ubus session) is encrypted with an AES-GCM key that lives in the Android Keystore
 * and never leaves it. Nothing here is backed up or moved to another phone (see data_extraction_rules).
 */
class RouterStore(
    context: Context,
    private val vault: LockVault,
    /** Whether new secrets are also sealed with the app lock's key (app lock on, background checks off). */
    private val sealing: () -> Boolean,
) {
    private val sp = context.getSharedPreferences("routers", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listSerializer = ListSerializer(Router.serializer())

    private val _routers = MutableStateFlow(load().also(CleartextPolicy::syncSaved))
    val routers: StateFlow<List<Router>> = _routers.asStateFlow()

    private val _activeId = MutableStateFlow(sp.getString(KEY_ACTIVE, null)?.takeIf { id -> _routers.value.any { it.id == id } }
        ?: _routers.value.firstOrNull()?.id)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    private fun load(): List<Router> = try {
        sp.getString(KEY_ROUTERS, null)?.let { json.decodeFromString(listSerializer, it) } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    @Synchronized
    fun save(router: Router) {
        val list = _routers.value.filterNot { it.id == router.id } + router
        val sorted = list.sortedBy { s -> _routers.value.indexOfFirst { it.id == s.id }.let { if (it < 0) Int.MAX_VALUE else it } }
        sp.edit().putString(KEY_ROUTERS, json.encodeToString(listSerializer, sorted)).apply()
        CleartextPolicy.syncSaved(sorted)
        _routers.value = sorted
    }

    @Synchronized
    fun update(id: String, transform: (Router) -> Router) {
        val current = _routers.value.firstOrNull { it.id == id } ?: return
        save(transform(current))
    }

    @Synchronized
    fun remove(id: String) {
        val list = _routers.value.filterNot { it.id == id }
        sp.edit()
            .putString(KEY_ROUTERS, json.encodeToString(listSerializer, list))
            .remove(secretKey(id, "password")).remove(secretKey(id, "session"))
            .remove(secretKey(id, "session_at")).remove(secretKey(id, "session_timeout"))
            .apply()
        CleartextPolicy.syncSaved(list)
        _routers.value = list
        if (_activeId.value == id) setActive(list.firstOrNull()?.id)
    }

    fun setActive(id: String?) {
        sp.edit().putString(KEY_ACTIVE, id).apply()
        _activeId.value = id
    }

    fun secrets(id: String): RouterSecrets = RouterSecrets(
        password = decrypt(sp.getString(secretKey(id, "password"), null)),
        session = decrypt(sp.getString(secretKey(id, "session"), null)),
        sessionIssuedAt = sp.getLong(secretKey(id, "session_at"), 0),
        sessionTimeout = sp.getLong(secretKey(id, "session_timeout"), 0),
    )

    fun saveSecrets(id: String, s: RouterSecrets) = saveSecrets(id, s, sealing())

    @Synchronized
    private fun saveSecrets(id: String, s: RouterSecrets, seal: Boolean) {
        // Locked: the key isn't in memory, so nothing can be sealed. Keep what's stored rather than
        // writing a login that isn't sealed (or losing it).
        if (seal && !vault.isOpen) return
        sp.edit()
            .putString(secretKey(id, "password"), encrypt(s.password, seal))
            .putString(secretKey(id, "session"), encrypt(s.session, seal))
            .putLong(secretKey(id, "session_at"), s.sessionIssuedAt)
            .putLong(secretKey(id, "session_timeout"), s.sessionTimeout)
            .commit()
    }

    /**
     * Stores every router's secrets again, sealed with the app lock's key or not. Only while it's unlocked
     * (or nothing is sealed), so every secret can be read first.
     */
    @Synchronized
    fun resealAll(seal: Boolean) {
        if (vault.enabled && !vault.isOpen) return
        _routers.value.forEach { router ->
            // Already as asked: leave it alone (this also runs at every unlock).
            if (!sealedAs(router.id, seal)) saveSecrets(router.id, secrets(router.id), seal)
        }
    }

    /** Whether every stored secret of [id] is already sealed (or not) as [seal] says. */
    private fun sealedAs(id: String, seal: Boolean): Boolean = listOf("password", "session").all { what ->
        val inner = keystoreOpen(sp.getString(secretKey(id, what), null)) ?: return@all true
        LockVault.isSealed(inner) == seal
    }

    /** Deletes every router and its secrets ("Forgot PIN?"). */
    @Synchronized
    fun removeAll() {
        sp.edit().clear().commit()
        CleartextPolicy.syncSaved(emptyList())
        _routers.value = emptyList()
        _activeId.value = null
    }

    private fun secretKey(id: String, what: String) = "secret_${id}_$what"

    // --- Keystore AES-GCM ---

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String, seal: Boolean): String? {
        if (plain.isEmpty()) return null
        return try {
            // With the app lock's key on top, the secret can't be read while the app is locked.
            val inner = if (seal) vault.seal(plain) ?: return null else plain
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key())
            Base64.encodeToString(c.iv + c.doFinal(inner.toByteArray()), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    /** The Keystore layer only: the secret, or the app lock's sealed form of it. Null when there's none. */
    private fun keystoreOpen(stored: String?): String? {
        if (stored.isNullOrEmpty()) return null
        return try {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
            String(c.doFinal(raw, 12, raw.size - 12))
        } catch (_: Exception) {
            null
        }
    }

    private fun decrypt(stored: String?): String {
        val inner = keystoreOpen(stored) ?: return ""
        // Sealed with the app lock's key: readable only while unlocked.
        return if (LockVault.isSealed(inner)) vault.open(inner).orEmpty() else inner
    }

    private companion object {
        const val KEY_ROUTERS = "routers"
        const val KEY_ACTIVE = "active"
        const val KEY_ALIAS = "wrtctrl_secrets"
    }
}
