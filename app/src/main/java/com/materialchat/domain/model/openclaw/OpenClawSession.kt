package com.materialchat.domain.model.openclaw

/**
 * Represents a chat session on the OpenClaw Gateway.
 *
 * @property key Unique session key
 * @property agentId The agent handling this session
 * @property channelType The channel this session originated from (null for direct/API sessions)
 * @property label Optional human-readable label
 * @property startedAt Timestamp when the session started (epoch millis)
 * @property lastActivity Timestamp of last activity (epoch millis)
 * @property messageCount Number of messages in this session
 * @property title Derived or assigned session title
 */
data class OpenClawSession(
    val key: String,
    val agentId: String = "main",
    val channelType: ChannelType? = null,
    val label: String? = null,
    val startedAt: Long = 0L,
    val lastActivity: Long = 0L,
    val messageCount: Int = 0,
    val title: String? = null
)
