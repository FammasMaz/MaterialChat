package com.materialchat.domain.model.openclaw

/**
 * A chat message in an OpenClaw session.
 *
 * @property role The role of the message sender
 * @property content The text content of the message
 * @property thinkingContent Agent's thinking/reasoning content (if available)
 * @property toolCalls Tool calls made by the agent in this message
 * @property timestamp Message timestamp (epoch millis)
 * @property runId The run ID this message was part of
 */
data class OpenClawChatMessage(
    val role: OpenClawChatRole,
    val content: String,
    val thinkingContent: String? = null,
    val toolCalls: List<ToolCallInfo> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val runId: String? = null
)

/**
 * Roles for OpenClaw chat messages.
 */
enum class OpenClawChatRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}
