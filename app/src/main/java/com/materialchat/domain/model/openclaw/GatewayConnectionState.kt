package com.materialchat.domain.model.openclaw

/**
 * Represents the connection state to the OpenClaw Gateway.
 */
sealed interface GatewayConnectionState {

    /** Not connected to the gateway. */
    data object Disconnected : GatewayConnectionState

    /** Attempting to connect (WebSocket handshake in progress). */
    data object Connecting : GatewayConnectionState

    /**
     * Successfully connected and authenticated.
     *
     * @property connId The unique connection ID assigned by the gateway
     * @property protocol The negotiated protocol version
     */
    data class Connected(
        val connId: String,
        val protocol: Int = 3
    ) : GatewayConnectionState

    /**
     * Connection failed or was terminated with an error.
     *
     * @property message Human-readable error description
     * @property cause The underlying exception, if any
     * @property isRetryable Whether the connection can be retried
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val isRetryable: Boolean = true
    ) : GatewayConnectionState
}
