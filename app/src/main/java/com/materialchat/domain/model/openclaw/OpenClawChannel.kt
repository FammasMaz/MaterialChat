package com.materialchat.domain.model.openclaw

/**
 * Represents a communication channel connected to the OpenClaw Gateway.
 *
 * @property type The channel platform type
 * @property isConnected Whether the channel is currently connected
 * @property accountId Optional account/bot identifier on the platform
 * @property displayName Human-readable name for this channel instance
 * @property lastActivity Timestamp of last activity on this channel
 */
data class OpenClawChannel(
    val type: ChannelType,
    val isConnected: Boolean = false,
    val accountId: String? = null,
    val displayName: String = type.displayName,
    val lastActivity: Long? = null
)

/**
 * Supported channel platform types.
 */
enum class ChannelType(val displayName: String) {
    WHATSAPP("WhatsApp"),
    TELEGRAM("Telegram"),
    DISCORD("Discord"),
    SLACK("Slack"),
    MATRIX("Matrix"),
    IRC("IRC"),
    WEB("Web"),
    SMS("SMS"),
    EMAIL("Email"),
    UNKNOWN("Unknown")
}
