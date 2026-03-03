package com.materialchat.domain.model.openclaw

/**
 * Configuration for the OpenClaw Gateway connection.
 *
 * @property gatewayUrl The URL of the OpenClaw Gateway (e.g., "http://localhost:18789")
 * @property agentId The target agent ID (default: "main")
 * @property isEnabled Whether the OpenClaw integration is enabled
 * @property autoConnect Whether to automatically connect on app launch
 * @property allowSelfSignedCerts Whether to allow self-signed TLS certificates (for VPN/Tailscale)
 */
data class OpenClawConfig(
    val gatewayUrl: String = "",
    val agentId: String = "main",
    val isEnabled: Boolean = false,
    val autoConnect: Boolean = false,
    val allowSelfSignedCerts: Boolean = false
) {
    /** Whether this config has a valid gateway URL set. */
    val isConfigured: Boolean
        get() = gatewayUrl.isNotBlank()

    /** The WebSocket URL derived from the gateway URL. */
    val webSocketUrl: String
        get() {
            val trimmed = gatewayUrl.trimEnd('/')
            return when {
                trimmed.startsWith("ws://") || trimmed.startsWith("wss://") -> trimmed
                trimmed.startsWith("https://") -> trimmed.replaceFirst("https://", "wss://")
                trimmed.startsWith("http://") -> trimmed.replaceFirst("http://", "ws://")
                else -> "ws://$trimmed"
            }
        }

    /** The HTTP URL for REST API fallback. */
    val httpUrl: String
        get() {
            val trimmed = gatewayUrl.trimEnd('/')
            return when {
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                trimmed.startsWith("wss://") -> trimmed.replaceFirst("wss://", "https://")
                trimmed.startsWith("ws://") -> trimmed.replaceFirst("ws://", "http://")
                else -> "http://$trimmed"
            }
        }
}
