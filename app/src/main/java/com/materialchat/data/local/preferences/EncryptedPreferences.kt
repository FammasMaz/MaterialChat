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

    // ============================================================================
    // OAuth Token Storage Methods
    // ============================================================================

    /**
     * Store an encrypted OAuth access token for a provider.
     *
     * @param providerId The unique identifier of the provider
     * @param token The access token to encrypt and store
     */
    suspend fun setAccessToken(providerId: String, token: String) = withContext(Dispatchers.IO) {
        encryptAndStore(oauthAccessTokenKey(providerId), token)
    }

    /**
     * Retrieve and decrypt an OAuth access token for a provider.
     *
     * @param providerId The unique identifier of the provider
     * @return The decrypted access token, or null if not found
     */
    suspend fun getAccessToken(providerId: String): String? = withContext(Dispatchers.IO) {
        decryptAndRetrieve(oauthAccessTokenKey(providerId))
    }

    /**
     * Store an encrypted OAuth refresh token for a provider.
     *
     * @param providerId The unique identifier of the provider
     * @param token The refresh token to encrypt and store
     */
    suspend fun setRefreshToken(providerId: String, token: String) = withContext(Dispatchers.IO) {
        encryptAndStore(oauthRefreshTokenKey(providerId), token)
    }

    /**
     * Retrieve and decrypt an OAuth refresh token for a provider.
     *
     * @param providerId The unique identifier of the provider
     * @return The decrypted refresh token, or null if not found
     */
    suspend fun getRefreshToken(providerId: String): String? = withContext(Dispatchers.IO) {
        decryptAndRetrieve(oauthRefreshTokenKey(providerId))
    }

    /**
     * Store the OAuth token expiry timestamp for a provider.
     *
     * @param providerId The unique identifier of the provider
     * @param expiresAt Token expiry timestamp in milliseconds since epoch
     */
    suspend fun setTokenExpiry(providerId: String, expiresAt: Long) = withContext(Dispatchers.IO) {
        prefs.edit().putLong(oauthExpiryKey(providerId), expiresAt).apply()
    }

    /**
     * Retrieve the OAuth token expiry timestamp for a provider.
     *
     * @param providerId The unique identifier of the provider
     * @return Token expiry timestamp, or null if not found
     */
    suspend fun getTokenExpiry(providerId: String): Long? = withContext(Dispatchers.IO) {
        val key = oauthExpiryKey(providerId)
        if (prefs.contains(key)) prefs.getLong(key, 0L) else null
    }

    /**
     * Store the OAuth user email for a provider.
     *
     * @param providerId The unique identifier of the provider
     * @param email The authenticated user's email
     */
    suspend fun setOAuthEmail(providerId: String, email: String) = withContext(Dispatchers.IO) {
        encryptAndStore(oauthEmailKey(providerId), email)
    }

    /**
     * Retrieve the OAuth user email for a provider.
     *
     * @param providerId The unique identifier of the provider
     * @return The user's email, or null if not found
     */
    suspend fun getOAuthEmail(providerId: String): String? = withContext(Dispatchers.IO) {
        decryptAndRetrieve(oauthEmailKey(providerId))
    }

    /**
     * Store the OAuth project ID for a provider (e.g., for Antigravity).
     *
     * @param providerId The unique identifier of the provider
     * @param projectId The provider-specific project ID
     */
    suspend fun setOAuthProjectId(providerId: String, projectId: String) = withContext(Dispatchers.IO) {
        encryptAndStore(oauthProjectIdKey(providerId), projectId)
    }

    /**
     * Retrieve the OAuth project ID for a provider.
     *
     * @param providerId The unique identifier of the provider
     * @return The project ID, or null if not found
     */
    suspend fun getOAuthProjectId(providerId: String): String? = withContext(Dispatchers.IO) {
        decryptAndRetrieve(oauthProjectIdKey(providerId))
    }

    /**
     * Clear all OAuth tokens and metadata for a provider.
     *
     * @param providerId The unique identifier of the provider
     */
    suspend fun clearOAuthTokens(providerId: String) = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(oauthAccessTokenKey(providerId))
            .remove(oauthRefreshTokenKey(providerId))
            .remove(oauthExpiryKey(providerId))
            .remove(oauthEmailKey(providerId))
            .remove(oauthProjectIdKey(providerId))
            .apply()
    }

    /**
     * Check if valid OAuth tokens exist for a provider.
     *
     * @param providerId The unique identifier of the provider
     * @return True if access token exists and hasn't expired
     */
    suspend fun hasValidTokens(providerId: String): Boolean = withContext(Dispatchers.IO) {
        val accessToken = getAccessToken(providerId)
        val expiry = getTokenExpiry(providerId)

        if (accessToken.isNullOrEmpty()) return@withContext false
        if (expiry == null) return@withContext true // No expiry means token doesn't expire

        // Check if token is still valid (with 60 second buffer)
        System.currentTimeMillis() < expiry - 60_000L
    }

    // ============================================================================
    // Private Helper Methods
    // ============================================================================

    /**
     * Encrypt a string value and store it in preferences.
     */
    private fun encryptAndStore(key: String, value: String) {
        val associatedData = key.toByteArray(StandardCharsets.UTF_8)
        val plaintext = value.toByteArray(StandardCharsets.UTF_8)
        val ciphertext = aead.encrypt(plaintext, associatedData)
        val encoded = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        prefs.edit().putString(key, encoded).apply()
    }

    /**
     * Decrypt and retrieve a string value from preferences.
     */
    private fun decryptAndRetrieve(key: String): String? {
        val encoded = prefs.getString(key, null) ?: return null
        return try {
            val ciphertext = Base64.decode(encoded, Base64.NO_WRAP)
            val associatedData = key.toByteArray(StandardCharsets.UTF_8)
            val plaintext = aead.decrypt(ciphertext, associatedData)
            String(plaintext, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    // OAuth preference key generators
    private fun oauthAccessTokenKey(providerId: String) = "${OAUTH_ACCESS_PREFIX}$providerId"
    private fun oauthRefreshTokenKey(providerId: String) = "${OAUTH_REFRESH_PREFIX}$providerId"
    private fun oauthExpiryKey(providerId: String) = "${OAUTH_EXPIRY_PREFIX}$providerId"
    private fun oauthEmailKey(providerId: String) = "${OAUTH_EMAIL_PREFIX}$providerId"
    private fun oauthProjectIdKey(providerId: String) = "${OAUTH_PROJECT_PREFIX}$providerId"

    companion object {
        private const val PREFS_NAME = "materialchat_encrypted_prefs"
        private const val KEYSET_NAME = "materialchat_keyset"
        private const val MASTER_KEY_URI = "android-keystore://materialchat_master_key"
        private const val API_KEY_PREFIX = "api_key_"

        // OAuth token prefixes
        private const val OAUTH_ACCESS_PREFIX = "oauth_access_"
        private const val OAUTH_REFRESH_PREFIX = "oauth_refresh_"
        private const val OAUTH_EXPIRY_PREFIX = "oauth_expiry_"
        private const val OAUTH_EMAIL_PREFIX = "oauth_email_"
        private const val OAUTH_PROJECT_PREFIX = "oauth_project_"
    }
}
