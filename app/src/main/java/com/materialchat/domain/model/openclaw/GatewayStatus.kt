package com.materialchat.domain.model.openclaw

/**
 * Status information from the OpenClaw Gateway.
 *
 * @property isOnline Whether the gateway is online and responding
 * @property version The gateway software version
 * @property uptime Human-readable uptime string
 * @property agentId The primary agent ID configured on the gateway
 * @property activeChannels Number of active communication channels
 * @property activeSessions Number of active chat sessions
 * @property connId The current WebSocket connection ID
 */
data class GatewayStatus(
    val isOnline: Boolean,
    val version: String = "",
    val uptime: String = "",
    val agentId: String = "main",
    val activeChannels: Int = 0,
    val activeSessions: Int = 0,
    val connId: String = ""
)
