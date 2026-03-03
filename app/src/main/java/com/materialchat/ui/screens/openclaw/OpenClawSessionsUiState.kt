package com.materialchat.ui.screens.openclaw

import com.materialchat.domain.model.openclaw.OpenClawSession

/**
 * UI state for the OpenClaw Sessions screen.
 */
sealed interface OpenClawSessionsUiState {

    /**
     * Loading state while sessions are being fetched.
     */
    data object Loading : OpenClawSessionsUiState

    /**
     * Success state with a list of sessions.
     *
     * @property sessions The list of active sessions
     * @property isRefreshing Whether a refresh is in progress
     */
    data class Success(
        val sessions: List<OpenClawSession> = emptyList(),
        val isRefreshing: Boolean = false
    ) : OpenClawSessionsUiState

    /**
     * Empty state when no sessions exist.
     */
    data object Empty : OpenClawSessionsUiState

    /**
     * Error state when loading fails.
     *
     * @property message Human-readable error message
     */
    data class Error(val message: String) : OpenClawSessionsUiState
}

/**
 * One-time events for the OpenClaw Sessions screen.
 */
sealed interface OpenClawSessionsEvent {
    data class ShowSnackbar(val message: String) : OpenClawSessionsEvent
    data class NavigateToChat(val sessionKey: String) : OpenClawSessionsEvent
}
