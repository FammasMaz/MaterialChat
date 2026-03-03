package com.materialchat.ui.screens.openclaw

import com.materialchat.domain.model.openclaw.GatewayConnectionState
import com.materialchat.domain.model.openclaw.OpenClawChatMessage

/**
 * UI state for the OpenClaw Chat screen.
 */
sealed interface OpenClawChatUiState {

    /**
     * Loading state while chat history is being fetched.
     */
    data object Loading : OpenClawChatUiState

    /**
     * Active chat state with messages and streaming info.
     *
     * @property messages The list of chat messages
     * @property isStreaming Whether the agent is currently streaming a response
     * @property sessionKey The current session key (null for new sessions)
     * @property agentStatus Current agent execution status
     * @property connectionState Current gateway connection state
     * @property currentInput Current text in the input field
     */
    data class Active(
        val messages: List<OpenClawChatMessage> = emptyList(),
        val isStreaming: Boolean = false,
        val sessionKey: String? = null,
        val agentStatus: AgentStatus = AgentStatus.IDLE,
        val connectionState: GatewayConnectionState = GatewayConnectionState.Disconnected,
        val currentInput: String = ""
    ) : OpenClawChatUiState {

        /** Whether a message can be sent. */
        val canSend: Boolean
            get() = currentInput.isNotBlank() &&
                    !isStreaming &&
                    connectionState is GatewayConnectionState.Connected
    }

    /**
     * Error state when the chat fails to load.
     *
     * @property message Human-readable error message
     */
    data class Error(val message: String) : OpenClawChatUiState
}

/**
 * Agent execution status for UI display.
 */
enum class AgentStatus(val displayName: String) {
    IDLE("Idle"),
    THINKING("Thinking..."),
    EXECUTING("Executing tool..."),
    WAITING("Waiting...")
}

/**
 * One-time events for the OpenClaw Chat screen.
 */
sealed interface OpenClawChatEvent {
    data class ShowSnackbar(val message: String) : OpenClawChatEvent
}
