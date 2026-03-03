package com.materialchat.data.repository

import com.materialchat.data.local.preferences.EncryptedPreferences
import com.materialchat.data.local.preferences.OpenClawPreferences
import com.materialchat.data.remote.api.StreamingEvent
import com.materialchat.data.remote.openclaw.OpenClawGatewayClient
import com.materialchat.domain.model.openclaw.*
import com.materialchat.domain.repository.OpenClawRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [OpenClawRepository].
 *
 * Delegates to [OpenClawGatewayClient] for networking,
 * [OpenClawPreferences] for non-sensitive config,
 * and [EncryptedPreferences] for the gateway token.
 */
@Singleton
class OpenClawRepositoryImpl @Inject constructor(
    private val gatewayClient: OpenClawGatewayClient,
    private val openClawPreferences: OpenClawPreferences,
    private val encryptedPreferences: EncryptedPreferences
) : OpenClawRepository {

    // ========== Connection ==========

    override val connectionState: StateFlow<GatewayConnectionState>
        get() = gatewayClient.connectionState

    override val gatewayEvents: Flow<GatewayEvent>
        get() = gatewayClient.events

    override val latencyMs: StateFlow<Long?>
        get() = gatewayClient.latencyMs

    override suspend fun connect() {
        val config = openClawPreferences.config.first()
        val token = encryptedPreferences.getOpenClawToken()
            ?: throw IllegalStateException("No gateway token configured")

        gatewayClient.connect(config, token)
    }

    override fun disconnect() {
        gatewayClient.disconnect()
    }

    // ========== Configuration ==========

    override fun observeConfig(): Flow<OpenClawConfig> {
        return openClawPreferences.config
    }

    override suspend fun updateConfig(config: OpenClawConfig) {
        openClawPreferences.updateConfig(config)
    }

    override suspend fun setToken(token: String) {
        encryptedPreferences.setOpenClawToken(token)
    }

    override suspend fun getToken(): String? {
        return encryptedPreferences.getOpenClawToken()
    }

    override suspend fun hasToken(): Boolean {
        return encryptedPreferences.hasOpenClawToken()
    }

    override suspend fun deleteToken() {
        encryptedPreferences.deleteOpenClawToken()
    }

    // ========== Status ==========

    override suspend fun getGatewayStatus(): GatewayStatus {
        return gatewayClient.getStatus()
    }

    // ========== Sessions ==========

    override suspend fun listSessions(): List<OpenClawSession> {
        return gatewayClient.listSessions()
    }

    override suspend fun deleteSession(sessionKey: String) {
        gatewayClient.deleteSession(sessionKey)
    }

    override suspend fun getChatHistory(sessionKey: String, limit: Int): List<OpenClawChatMessage> {
        return gatewayClient.getChatHistory(sessionKey, limit)
    }

    // ========== Channels ==========

    override suspend fun listChannels(): List<OpenClawChannel> {
        return gatewayClient.listChannels()
    }

    // ========== Chat ==========

    override suspend fun sendChat(sessionKey: String?, message: String): String {
        return gatewayClient.sendChatMessage(sessionKey, message)
    }

    override suspend fun abortChat(sessionKey: String, runId: String?) {
        gatewayClient.abortChat(sessionKey, runId)
    }

    override fun streamChat(message: String, sessionKey: String?): Flow<StreamingEvent> {
        return flow {
            val openClawConfig = openClawPreferences.config.first()
            val token = encryptedPreferences.getOpenClawToken()
                ?: throw IllegalStateException("No gateway token configured")

            emitAll(gatewayClient.streamChatHttp(openClawConfig, token, message, sessionKey))
        }
    }
}
