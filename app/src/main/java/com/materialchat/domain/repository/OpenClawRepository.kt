package com.materialchat.domain.repository

import com.materialchat.data.remote.api.StreamingEvent
import com.materialchat.domain.model.openclaw.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for the OpenClaw Gateway subsystem.
 *
 * Provides access to gateway connection, configuration, sessions, channels, and chat.
 */
interface OpenClawRepository {

    // ========== Connection ==========

    /** Current gateway connection state. */
    val connectionState: StateFlow<GatewayConnectionState>

    /** Stream of real-time gateway events. */
    val gatewayEvents: Flow<GatewayEvent>

    /** Current connection latency in milliseconds. */
    val latencyMs: StateFlow<Long?>

    /** Connect to the gateway. */
    suspend fun connect()

    /** Disconnect from the gateway. */
    fun disconnect()

    // ========== Configuration ==========

    /** Observe the current OpenClaw config. */
    fun observeConfig(): Flow<OpenClawConfig>

    /** Update the OpenClaw config. */
    suspend fun updateConfig(config: OpenClawConfig)

    /** Set the gateway authentication token. */
    suspend fun setToken(token: String)

    /** Get the gateway authentication token. */
    suspend fun getToken(): String?

    /** Check if a gateway token is stored. */
    suspend fun hasToken(): Boolean

    /** Delete the gateway token. */
    suspend fun deleteToken()

    // ========== Status ==========

    /** Get the gateway status (requires active connection). */
    suspend fun getGatewayStatus(): GatewayStatus

    // ========== Sessions ==========

    /** List all active sessions. */
    suspend fun listSessions(): List<OpenClawSession>

    /** Ensure a gateway agent exists. Returns true if created. */
    suspend fun ensureAgentExists(agentId: String, workspaceDir: String): Boolean

    /** Delete a session by key. */
    suspend fun deleteSession(sessionKey: String)

    /** Get chat history for a session. */
    suspend fun getChatHistory(sessionKey: String, limit: Int = 100): List<OpenClawChatMessage>

    // ========== Channels ==========

    /** List all connected channels. */
    suspend fun listChannels(): List<OpenClawChannel>

    // ========== Chat ==========

    /** Send a chat message via WebSocket. Returns the run ID. */
    suspend fun sendChat(sessionKey: String?, message: String): String

    /** Abort an active chat run. */
    suspend fun abortChat(sessionKey: String, runId: String? = null)

    /**
     * Stream a chat response via HTTP SSE.
     * Used for simple one-shot chat or when WebSocket is unavailable.
     */
    fun streamChat(message: String, sessionKey: String? = null): Flow<StreamingEvent>
}
