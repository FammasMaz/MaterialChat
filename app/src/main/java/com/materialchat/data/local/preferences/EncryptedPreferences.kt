package com.materialchat.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

/**
 * Encrypted preferences manager for sensitive data.
 *
 * This class uses Google Tink to encrypt sensitive data (like API keys) before
 * storing them in SharedPreferences. The encryption key is stored in Android Keystore,
 * providing hardware-backed security on supported devices.
 *
 * Security features:
 * - AES-256-GCM encryption via Tink
 * - Key stored in Android Keystore (hardware-backed when available)
 * - Associated data for authenticated encryption
 */
class EncryptedPreferences(context: Context) {

    private val prefs: SharedPreferences
    private val aead: Aead

    init {
        // Register Tink AEAD configuration
        AeadConfig.register()

        // Create or load keyset from Android Keystore
        val keysetManager = AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, PREFS_NAME)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()

        aead = keysetManager.keysetHandle.getPrimitive(Aead::class.java)

        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Store an encrypted API key for a provider.
     *
     * @param providerId The unique identifier of the provider
     * @param apiKey The API key to encrypt and store
     */
    suspend fun setApiKey(providerId: String, apiKey: String) = withContext(Dispatchers.IO) {
        val key = apiKeyPrefKey(providerId)
        val associatedData = key.toByteArray(StandardCharsets.UTF_8)
        val plaintext = apiKey.toByteArray(StandardCharsets.UTF_8)

        val ciphertext = aead.encrypt(plaintext, associatedData)
        val encoded = Base64.encodeToString(ciphertext, Base64.NO_WRAP)

        prefs.edit().putString(key, encoded).apply()
    }

    /**
     * Retrieve and decrypt an API key for a provider.
     *
     * @param providerId The unique identifier of the provider
     * @return The decrypted API key, or null if not found or decryption fails
     */
    suspend fun getApiKey(providerId: String): String? = withContext(Dispatchers.IO) {
        val key = apiKeyPrefKey(providerId)
        val encoded = prefs.getString(key, null) ?: return@withContext null

        try {
            val ciphertext = Base64.decode(encoded, Base64.NO_WRAP)
            val associatedData = key.toByteArray(StandardCharsets.UTF_8)
            val plaintext = aead.decrypt(ciphertext, associatedData)
            String(plaintext, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            // Log error in production, return null for now
            null
        }
    }

    /**
     * Delete an API key for a provider.
     *
     * @param providerId The unique identifier of the provider
     */
    suspend fun deleteApiKey(providerId: String) = withContext(Dispatchers.IO) {
        val key = apiKeyPrefKey(providerId)
        prefs.edit().remove(key).apply()
    }

    /**
     * Check if an API key exists for a provider.
     *
     * @param providerId The unique identifier of the provider
     * @return True if an API key is stored for this provider
     */
    suspend fun hasApiKey(providerId: String): Boolean = withContext(Dispatchers.IO) {
        val key = apiKeyPrefKey(providerId)
        prefs.contains(key)
    }

    /**
     * Get all provider IDs that have stored API keys.
     *
     * @return Set of provider IDs with stored API keys
     */
    suspend fun getAllProviderIdsWithApiKeys(): Set<String> = withContext(Dispatchers.IO) {
        prefs.all.keys
            .filter { it.startsWith(API_KEY_PREFIX) }
            .map { it.removePrefix(API_KEY_PREFIX) }
            .toSet()
    }

    /**
     * Clear all stored API keys.
     * Use with caution - this cannot be undone.
     */
    suspend fun clearAllApiKeys() = withContext(Dispatchers.IO) {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(API_KEY_PREFIX) }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    /**
     * Generate the preference key for an API key.
     */
    private fun apiKeyPrefKey(providerId: String): String {
        return "$API_KEY_PREFIX$providerId"
    }

    companion object {
        private const val PREFS_NAME = "materialchat_encrypted_prefs"
        private const val KEYSET_NAME = "materialchat_keyset"
        private const val MASTER_KEY_URI = "android-keystore://materialchat_master_key"
        private const val API_KEY_PREFIX = "api_key_"
    }
}
