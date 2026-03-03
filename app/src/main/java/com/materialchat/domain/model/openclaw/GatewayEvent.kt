package com.materialchat.domain.model.openclaw

/**
 * Events received from the OpenClaw Gateway via WebSocket.
 */
sealed interface GatewayEvent {

    /** Server heartbeat tick. */
    data class Tick(val timestamp: Long) : GatewayEvent

    /** Gateway shutdown notification. */
    data class ShutdownEvent(
        val reason: String,
        val restartExpectedMs: Long? = null
    ) : GatewayEvent

    /**
     * Chat stream event (delta, final, aborted, error).
     *
     * @property runId Unique run identifier
     * @property sessionKey The session this chat belongs to
     * @property seq Sequence number within the run
     * @property state The state of this event: "delta", "final", "aborted", "error"
     * @property content Text content (for delta/final)
     * @property errorMessage Error message (for error state)
     * @property toolCalls Tool call information (if agent invoked tools)
     */
    data class ChatEvent(
        val runId: String,
        val sessionKey: String,
        val seq: Int = 0,
        val state: String,
        val content: String? = null,
        val errorMessage: String? = null,
        val toolCalls: List<ToolCallInfo>? = null
    ) : GatewayEvent

    /**
     * Agent execution event (thinking, executing tools, etc.).
     *
     * @property runId Unique run identifier
     * @property stream Stream name (e.g., "thinking", "tool_call", "tool_result")
     * @property data Raw event data
     */
    data class AgentEvent(
        val runId: String,
        val seq: Int = 0,
        val stream: String,
        val data: String? = null
    ) : GatewayEvent

    /**
     * Health/presence event.
     *
     * @property latencyMs Round-trip latency in milliseconds
     */
    data class HealthEvent(
        val latencyMs: Long? = null
    ) : GatewayEvent

    /**
     * Generic/unhandled event.
     *
     * @property eventName The event type name
     * @property payload Raw JSON payload
     */
    data class UnknownEvent(
        val eventName: String,
        val payload: String? = null
    ) : GatewayEvent
}

/**
 * Information about a tool call made by the agent.
 *
 * @property name The tool/function name
 * @property arguments JSON string of arguments passed to the tool
 * @property result The tool execution result (null if pending)
 */
data class ToolCallInfo(
    val name: String,
    val arguments: String? = null,
    val result: String? = null
)
