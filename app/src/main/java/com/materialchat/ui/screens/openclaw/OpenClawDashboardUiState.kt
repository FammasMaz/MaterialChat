package com.materialchat.ui.screens.openclaw

import com.materialchat.domain.model.openclaw.GatewayConnectionState
import com.materialchat.domain.model.openclaw.GatewayStatus
import com.materialchat.domain.model.openclaw.OpenClawChannel
import com.materialchat.domain.model.openclaw.OpenClawConfig

/**
 * UI state for the OpenClaw Dashboard screen.
 */
sealed interface OpenClawDashboardUiState {

    /**
     * Loading state while initial data is being fetched.
     */
    data object Loading : OpenClawDashboardUiState

    /**
     * Success state with gateway status and configuration.
     *
     * @property connectionState Current gateway connection state
     * @property status Gateway status information (null if not yet fetched)
     * @property channels List of connected channels
     * @property latencyMs Current latency in milliseconds (null if unavailable)
     * @property config Current OpenClaw configuration
     * @property agentHistory Known agent IDs from previous sessions
     * @property showSetupSheet Whether to show the initial setup bottom sheet
     * @property isRefreshing Whether a status refresh is in progress
     */
    data class Success(
        val connectionState: GatewayConnectionState = GatewayConnectionState.Disconnected,
        val status: GatewayStatus? = null,
        val channels: List<OpenClawChannel> = emptyList(),
        val latencyMs: Long? = null,
        val config: OpenClawConfig = OpenClawConfig(),
        val agentHistory: List<String> = emptyList(),
        val showSetupSheet: Boolean = false,
        val isRefreshing: Boolean = false
    ) : OpenClawDashboardUiState {

        /** Whether the gateway is currently connected. */
        val isConnected: Boolean
            get() = connectionState is GatewayConnectionState.Connected

        /** Whether the config has a valid gateway URL. */
        val isConfigured: Boolean
            get() = config.isConfigured
    }

    /**
     * Error state when loading fails.
     *
     * @property message Human-readable error message
     */
    data class Error(val message: String) : OpenClawDashboardUiState
}

/**
 * One-time events for the OpenClaw Dashboard screen.
 */
sealed interface OpenClawDashboardEvent {
    data class ShowSnackbar(val message: String) : OpenClawDashboardEvent
    data class NavigateToChat(val sessionKey: String? = null) : OpenClawDashboardEvent
    data object NavigateToSessions : OpenClawDashboardEvent
}
