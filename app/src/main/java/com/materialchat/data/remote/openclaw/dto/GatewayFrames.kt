package com.materialchat.data.remote.openclaw.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ============================================================================
// WebSocket Frame DTOs
// ============================================================================

/**
 * Request frame sent from client to gateway.
 */
@Serializable
data class RequestFrame(
    val type: String = "req",
    val id: String,
    val method: String,
    val params: JsonObject? = null
)

/**
 * Response frame received from gateway.
 */
@Serializable
data class ResponseFrame(
    val type: String = "res",
    val id: String,
    val ok: Boolean,
    val payload: JsonObject? = null,
    val error: GatewayError? = null
)

/**
 * Event frame pushed from gateway.
 */
@Serializable
data class EventFrame(
    val type: String = "event",
    val event: String,
    val payload: JsonObject? = null,
    val seq: Int? = null
)

/**
 * Gateway error details.
 */
@Serializable
data class GatewayError(
    val code: String? = null,
    val message: String? = null,
    val details: JsonObject? = null,
    val retryable: Boolean? = null,
    @SerialName("retryAfterMs")
    val retryAfterMs: Long? = null
)

// ============================================================================
// Generic frame wrapper for parsing incoming messages
// ============================================================================

/**
 * Generic frame for initial parsing — determines frame type before full deserialization.
 */
@Serializable
data class GenericFrame(
    val type: String,
    // Response fields
    val id: String? = null,
    val ok: Boolean? = null,
    val payload: JsonObject? = null,
    val error: GatewayError? = null,
    // Event fields
    val event: String? = null,
    val seq: Int? = null
)

// ============================================================================
// Connect handshake DTOs
// ============================================================================

/**
 * Parameters for the 'connect' method.
 */
@Serializable
data class ConnectParams(
    val minProtocol: Int = 3,
    val maxProtocol: Int = 3,
    val client: ClientInfo,
    val role: String = "operator",
    val scopes: List<String> = listOf("operator.admin"),
    val auth: ConnectAuth? = null
)

@Serializable
data class ClientInfo(
    val id: String = "openclaw-android",
    val displayName: String = "MaterialChat",
    val version: String = "1.0.0",
    val platform: String = "android",
    val mode: String = "backend"
)

@Serializable
data class ConnectAuth(
    val token: String? = null
)

/**
 * Hello-OK response payload.
 */
@Serializable
data class HelloOkPayload(
    val type: String? = null,
    val protocol: Int? = null,
    val server: ServerInfo? = null,
    val features: GatewayFeatures? = null,
    val auth: AuthResult? = null,
    val policy: GatewayPolicy? = null
)

@Serializable
data class ServerInfo(
    val version: String? = null,
    val connId: String? = null
)

@Serializable
data class GatewayFeatures(
    val methods: List<String> = emptyList(),
    val events: List<String> = emptyList()
)

@Serializable
data class AuthResult(
    val deviceToken: String? = null,
    val role: String? = null,
    val scopes: List<String> = emptyList()
)

@Serializable
data class GatewayPolicy(
    val maxPayload: Long? = null,
    val maxBufferedBytes: Long? = null,
    val tickIntervalMs: Long? = null
)

// ============================================================================
// Chat DTOs
// ============================================================================

/**
 * Parameters for the 'chat.send' method.
 */
@Serializable
data class ChatSendParams(
    val sessionKey: String? = null,
    val message: String,
    val thinking: String = "enabled",
    val timeoutMs: Long? = null,
    val idempotencyKey: String? = null
)

/**
 * Chat event payload from the gateway.
 */
@Serializable
data class ChatEventPayload(
    val runId: String? = null,
    val sessionKey: String? = null,
    val seq: Int = 0,
    val state: String? = null,
    val message: ChatMessagePayload? = null,
    val errorMessage: String? = null,
    val usage: JsonObject? = null,
    val stopReason: String? = null
)

@Serializable
data class ChatMessagePayload(
    val role: String? = null,
    val content: JsonElement? = null,
    val toolCalls: List<ToolCallPayload>? = null
)

@Serializable
data class ToolCallPayload(
    val name: String? = null,
    val arguments: String? = null,
    val result: String? = null
)

/**
 * Agent event payload.
 */
@Serializable
data class AgentEventPayload(
    val runId: String? = null,
    val seq: Int = 0,
    val stream: String? = null,
    val ts: Long? = null,
    val data: JsonElement? = null
)

// ============================================================================
// Session/Channel DTOs
// ============================================================================

@Serializable
data class SessionListPayload(
    val sessions: List<SessionPayload> = emptyList()
)

@Serializable
data class SessionPayload(
    val key: String,
    val agentId: String? = null,
    val channel: String? = null,
    val label: String? = null,
    val startedAt: Long? = null,
    val lastActivity: Long? = null,
    val messageCount: Int? = null,
    val title: String? = null
)

@Serializable
data class ChannelStatusPayload(
    val channels: List<ChannelPayload> = emptyList()
)

@Serializable
data class ChannelPayload(
    val type: String? = null,
    val connected: Boolean = false,
    val accountId: String? = null,
    val displayName: String? = null,
    val lastActivity: Long? = null
)

@Serializable
data class ChatHistoryPayload(
    val messages: List<ChatHistoryMessage> = emptyList()
)

@Serializable
data class ChatHistoryMessage(
    val role: String? = null,
    val content: JsonElement? = null,
    val thinking: String? = null,
    val toolCalls: List<ToolCallPayload>? = null,
    val ts: Long? = null,
    val runId: String? = null
)

// ============================================================================
// Status DTO
// ============================================================================

@Serializable
data class StatusPayload(
    val online: Boolean = true,
    val version: String? = null,
    val uptime: String? = null,
    val agentId: String? = null,
    val activeChannels: Int = 0,
    val activeSessions: Int = 0
)
