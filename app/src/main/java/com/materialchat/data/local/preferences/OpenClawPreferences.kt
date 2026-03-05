package com.materialchat.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.openClawDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "openclaw_preferences"
)

/**
 * DataStore-based preferences for OpenClaw Gateway configuration.
 *
 * Stores non-sensitive configuration like gateway URL, agent ID, and feature toggles.
 * Sensitive data (token) is stored in [EncryptedPreferences].
 */
class OpenClawPreferences(context: Context) {

    private val dataStore = context.openClawDataStore

    companion object {
        private val KEY_GATEWAY_URL = stringPreferencesKey("gateway_url")
        private val KEY_AGENT_ID = stringPreferencesKey("agent_id")
        private val KEY_AGENT_HISTORY = stringSetPreferencesKey("agent_history")
        private val KEY_IS_ENABLED = booleanPreferencesKey("is_enabled")
        private val KEY_AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        private val KEY_ALLOW_SELF_SIGNED = booleanPreferencesKey("allow_self_signed_certs")

        const val DEFAULT_AGENT_ID = "main"
    }

    // ========== Gateway URL ==========

    val gatewayUrl: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_GATEWAY_URL] ?: ""
    }

    suspend fun setGatewayUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[KEY_GATEWAY_URL] = url.trimEnd('/')
        }
    }

    // ========== Agent ID ==========

    val agentId: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_AGENT_ID] ?: DEFAULT_AGENT_ID
    }

    val agentHistory: Flow<List<String>> = dataStore.data.map { prefs ->
        val history = prefs[KEY_AGENT_HISTORY] ?: setOf(DEFAULT_AGENT_ID)
        history
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .sorted()
    }

    suspend fun setAgentId(agentId: String) {
        val normalizedAgentId = agentId.ifBlank { DEFAULT_AGENT_ID }
        dataStore.edit { prefs ->
            prefs[KEY_AGENT_ID] = normalizedAgentId
            val history = (prefs[KEY_AGENT_HISTORY] ?: emptySet()).toMutableSet()
            history.add(normalizedAgentId)
            prefs[KEY_AGENT_HISTORY] = history
        }
    }

    suspend fun addAgentsToHistory(agentIds: List<String>) {
        dataStore.edit { prefs ->
            val merged = (prefs[KEY_AGENT_HISTORY] ?: emptySet()).toMutableSet()
            agentIds
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { merged.add(it) }

            if (merged.isNotEmpty()) {
                prefs[KEY_AGENT_HISTORY] = merged
            }
        }
    }

    // ========== Enabled ==========

    val isEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_IS_ENABLED] ?: false
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_IS_ENABLED] = enabled
        }
    }

    // ========== Auto-Connect ==========

    val autoConnect: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_CONNECT] ?: false
    }

    suspend fun setAutoConnect(autoConnect: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTO_CONNECT] = autoConnect
        }
    }

    // ========== Self-Signed Certs ==========

    val allowSelfSignedCerts: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ALLOW_SELF_SIGNED] ?: false
    }

    suspend fun setAllowSelfSignedCerts(allow: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ALLOW_SELF_SIGNED] = allow
        }
    }

    // ========== Bulk Config ==========

    /**
     * Observes the full OpenClaw config as a combined flow.
     */
    val config: Flow<com.materialchat.domain.model.openclaw.OpenClawConfig> = dataStore.data.map { prefs ->
        com.materialchat.domain.model.openclaw.OpenClawConfig(
            gatewayUrl = prefs[KEY_GATEWAY_URL] ?: "",
            agentId = prefs[KEY_AGENT_ID] ?: DEFAULT_AGENT_ID,
            isEnabled = prefs[KEY_IS_ENABLED] ?: false,
            autoConnect = prefs[KEY_AUTO_CONNECT] ?: false,
            allowSelfSignedCerts = prefs[KEY_ALLOW_SELF_SIGNED] ?: false
        )
    }

    /**
     * Updates multiple config values at once.
     */
    suspend fun updateConfig(config: com.materialchat.domain.model.openclaw.OpenClawConfig) {
        val resolvedAgentId = config.agentId.ifBlank { DEFAULT_AGENT_ID }
        dataStore.edit { prefs ->
            prefs[KEY_GATEWAY_URL] = config.gatewayUrl.trimEnd('/')
            prefs[KEY_AGENT_ID] = resolvedAgentId
            val history = (prefs[KEY_AGENT_HISTORY] ?: emptySet()).toMutableSet()
            history.add(resolvedAgentId)
            prefs[KEY_AGENT_HISTORY] = history
            prefs[KEY_IS_ENABLED] = config.isEnabled
            prefs[KEY_AUTO_CONNECT] = config.autoConnect
            prefs[KEY_ALLOW_SELF_SIGNED] = config.allowSelfSignedCerts
        }
    }

    /**
     * Clears all OpenClaw preferences.
     */
    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
