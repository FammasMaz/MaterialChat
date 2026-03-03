package com.materialchat.domain.usecase.openclaw

import com.materialchat.data.remote.api.StreamingEvent
import com.materialchat.domain.model.openclaw.GatewayEvent
import com.materialchat.domain.repository.OpenClawRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import javax.inject.Inject

/**
 * Use case for streaming chat with an OpenClaw agent.
 */
class OpenClawChatUseCase @Inject constructor(
    private val repository: OpenClawRepository
) {
    /**
     * Send a chat message via WebSocket and observe responses via events.
     * Returns the run ID for correlation.
     */
    suspend fun sendMessage(sessionKey: String?, message: String): String {
        return repository.sendChat(sessionKey, message)
    }

    /** Abort an active chat run. */
    suspend fun abort(sessionKey: String, runId: String? = null) {
        repository.abortChat(sessionKey, runId)
    }

    /**
     * Stream a chat response via HTTP SSE.
     * Returns a Flow of StreamingEvents compatible with the existing chat infrastructure.
     */
    fun streamChat(message: String, sessionKey: String? = null): Flow<StreamingEvent> {
        return repository.streamChat(message, sessionKey)
    }

    /** Observe chat events from the WebSocket connection. */
    fun observeChatEvents(): Flow<GatewayEvent.ChatEvent> {
        return repository.gatewayEvents.filterIsInstance()
    }

    /** Observe agent events (thinking, tool calls) from WebSocket. */
    fun observeAgentEvents(): Flow<GatewayEvent.AgentEvent> {
        return repository.gatewayEvents.filterIsInstance()
    }
}
