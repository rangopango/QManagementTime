package com.sabil.qnow.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sabil.qnow.domain.model.UserRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private val Context.secureDataStore by preferencesDataStore(name = "qnow_secure")

/**
 * Small encrypted key-value store. Values are sealed with AES-256-GCM using a key that never
 * leaves the Android Keystore, then kept in DataStore. Backup is switched off for it.
 */
@Singleton
class SecureStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val keyAlias = "qnow_secure_store_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(cipher.iv + sealed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String? = runCatching {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }.getOrNull()

    private suspend fun put(key: Preferences.Key<String>, value: String) {
        context.secureDataStore.edit { it[key] = encrypt(value) }
    }

    private suspend fun get(key: Preferences.Key<String>): String? =
        context.secureDataStore.data.first()[key]?.let(::decrypt)

    private val roleKey = stringPreferencesKey("role")

    suspend fun putRole(uid: String, role: UserRole) = put(roleKey, "$uid:${role.name}")

    /** The cached role, only if it belongs to [uid]. */
    suspend fun role(uid: String): UserRole? {
        val (owner, role) = get(roleKey)?.split(':', limit = 2)?.takeIf { it.size == 2 } ?: return null
        return if (owner == uid) UserRole.entries.firstOrNull { it.name == role } else null
    }

    suspend fun clear() {
        context.secureDataStore.edit { it.clear() }
    }
}
