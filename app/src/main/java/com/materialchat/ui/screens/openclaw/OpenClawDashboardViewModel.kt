package com.materialchat.ui.screens.openclaw

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.data.local.preferences.OpenClawPreferences
import com.materialchat.domain.model.openclaw.GatewayConnectionState
import com.materialchat.domain.model.openclaw.GatewayEvent
import com.materialchat.domain.model.openclaw.OpenClawChannel
import com.materialchat.domain.model.openclaw.OpenClawConfig
import com.materialchat.domain.usecase.openclaw.ConnectGatewayUseCase
import com.materialchat.domain.usecase.openclaw.GetGatewayStatusUseCase
import com.materialchat.domain.usecase.openclaw.ManageOpenClawAgentsUseCase
import com.materialchat.domain.usecase.openclaw.ManageOpenClawConfigUseCase
import com.materialchat.domain.usecase.openclaw.ManageOpenClawSessionsUseCase
import com.materialchat.notifications.OpenClawNotificationScheduler
import com.materialchat.notifications.OpenClawPushSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the OpenClaw Dashboard screen.
 *
 * Manages gateway connection state, status polling, channel health,
 * and initial setup configuration.
 */
@HiltViewModel
class OpenClawDashboardViewModel @Inject constructor(
    private val connectGatewayUseCase: ConnectGatewayUseCase,
    private val getGatewayStatusUseCase: GetGatewayStatusUseCase,
    private val manageOpenClawAgentsUseCase: ManageOpenClawAgentsUseCase,
    private val manageOpenClawConfigUseCase: ManageOpenClawConfigUseCase,
    private val manageOpenClawSessionsUseCase: ManageOpenClawSessionsUseCase,
    private val openClawNotificationScheduler: OpenClawNotificationScheduler,
    private val openClawPreferences: OpenClawPreferences,
    private val openClawPushSyncManager: OpenClawPushSyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<OpenClawDashboardUiState>(OpenClawDashboardUiState.Loading)
    val uiState: StateFlow<OpenClawDashboardUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<OpenClawDashboardEvent>()
    val events: SharedFlow<OpenClawDashboardEvent> = _events.asSharedFlow()

    init {
        observeConfig()
        observeAgentHistory()
        observeConnectionState()
        observeLatency()
        observeGatewayEvents()
    }

    /**
     * Observes the OpenClaw config and initializes the UI state.
     */
    private fun observeConfig() {
        viewModelScope.launch {
            manageOpenClawConfigUseCase.observeConfig()
                .catch { e ->
                    _uiState.value = OpenClawDashboardUiState.Error(
                        message = e.message ?: "Failed to load configuration"
                    )
                }
                .collect { config ->
                    val isConfigured = manageOpenClawConfigUseCase.isConfigured()
                    val currentState = _uiState.value
                    if (currentState is OpenClawDashboardUiState.Success) {
                        _uiState.update { state ->
                            if (state is OpenClawDashboardUiState.Success) {
                                state.copy(
                                    config = config,
                                    agentHistory = mergeAgentHistory(
                                        state.agentHistory,
                                        listOf(config.agentId)
                                    ),
                                    showSetupSheet = !isConfigured && state.showSetupSheet
                                )
                            } else state
                        }
                    } else {
                        _uiState.value = OpenClawDashboardUiState.Success(
                            config = config,
                            agentHistory = mergeAgentHistory(listOf(config.agentId)),
                            showSetupSheet = !isConfigured
                        )
                    }
                }
        }
    }

    /**
     * Observes the gateway connection state.
     */
    private fun observeConnectionState() {
        viewModelScope.launch {
            connectGatewayUseCase.connectionState.collect { connectionState ->
                _uiState.update { state ->
                    if (state is OpenClawDashboardUiState.Success) {
                        state.copy(connectionState = connectionState)
                    } else state
                }

                // Auto-fetch status on connection
                if (connectionState is GatewayConnectionState.Connected) {
                    refreshStatus()
                }
            }
        }
    }

    private fun observeAgentHistory() {
        viewModelScope.launch {
            openClawPreferences.agentHistory.collect { history ->
                _uiState.update { state ->
                    if (state is OpenClawDashboardUiState.Success) {
                        state.copy(
                            agentHistory = mergeAgentHistory(
                                listOf(state.config.agentId),
                                history,
                                state.agentHistory
                            )
                        )
                    } else state
                }
            }
        }
    }

    /**
     * Observes connection latency updates.
     */
    private fun observeLatency() {
        viewModelScope.launch {
            getGatewayStatusUseCase.latencyMs.collect { latency ->
                _uiState.update { state ->
                    if (state is OpenClawDashboardUiState.Success) {
                        state.copy(latencyMs = latency)
                    } else state
                }
            }
        }
    }

    /**
     * Observes gateway events for health and status updates.
     */
    private fun observeGatewayEvents() {
        viewModelScope.launch {
            getGatewayStatusUseCase.events
                .catch { /* Silently handle event stream errors */ }
                .collect { event ->
                    when (event) {
                        is GatewayEvent.ShutdownEvent -> {
                            _events.emit(
                                OpenClawDashboardEvent.ShowSnackbar(
                                    "Gateway shutting down: ${event.reason}"
                                )
                            )
                        }
                        else -> { /* Other events handled elsewhere */ }
                    }
                }
        }
    }

    /**
     * Connects to the OpenClaw Gateway.
     */
    fun connect() {
        viewModelScope.launch {
            try {
                connectGatewayUseCase.connect()
            } catch (e: Exception) {
                _events.emit(
                    OpenClawDashboardEvent.ShowSnackbar(
                        "Failed to connect: ${e.message}"
                    )
                )
            }
        }
    }

    /**
     * Disconnects from the OpenClaw Gateway.
     */
    fun disconnect() {
        connectGatewayUseCase.disconnect()
    }

    /**
     * Refreshes the gateway status.
     */
    fun refreshStatus() {
        val currentState = _uiState.value
        if (currentState !is OpenClawDashboardUiState.Success) return

        _uiState.update { state ->
            if (state is OpenClawDashboardUiState.Success) {
                state.copy(isRefreshing = true)
            } else state
        }

        viewModelScope.launch {
            try {
                val status = getGatewayStatusUseCase.getStatus()
                val sessionAgents = try {
                    manageOpenClawSessionsUseCase.listSessions().map { it.agentId }
                } catch (_: Exception) {
                    emptyList()
                }
                openClawPreferences.addAgentsToHistory(
                    sessionAgents + listOf(status.agentId)
                )

                _uiState.update { state ->
                    if (state is OpenClawDashboardUiState.Success) {
                        state.copy(
                            status = status,
                            agentHistory = mergeAgentHistory(
                                state.agentHistory,
                                sessionAgents,
                                listOf(status.agentId, state.config.agentId)
                            ),
                            isRefreshing = false
                        )
                    } else state
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    if (state is OpenClawDashboardUiState.Success) {
                        state.copy(isRefreshing = false)
                    } else state
                }
                _events.emit(
                    OpenClawDashboardEvent.ShowSnackbar(
                        "Failed to fetch status: ${e.message}"
                    )
                )
            }
        }
    }

    /**
     * Shows the setup bottom sheet.
     */
    fun showSetup() {
        _uiState.update { state ->
            if (state is OpenClawDashboardUiState.Success) {
                state.copy(showSetupSheet = true)
            } else state
        }
    }

    /**
     * Hides the setup bottom sheet.
     */
    fun hideSetup() {
        _uiState.update { state ->
            if (state is OpenClawDashboardUiState.Success) {
                state.copy(showSetupSheet = false)
            } else state
        }
    }

    /**
     * Saves the OpenClaw Gateway configuration.
     *
     * @param url The gateway URL
     * @param token The authentication token
     * @param agentId The target agent ID
     * @param selfSigned Whether to allow self-signed certificates
     */
    fun saveConfig(url: String, token: String, agentId: String, selfSigned: Boolean) {
        viewModelScope.launch {
            try {
                val resolvedAgentId = agentId.ifBlank { "main" }
                val config = OpenClawConfig(
                    gatewayUrl = url.trim(),
                    agentId = resolvedAgentId,
                    isEnabled = true,
                    autoConnect = true,
                    allowSelfSignedCerts = selfSigned
                )
                manageOpenClawConfigUseCase.updateConfig(config)

                if (token.isNotBlank()) {
                    manageOpenClawConfigUseCase.setToken(token.trim())
                }

                if (token.isNotBlank() || manageOpenClawConfigUseCase.hasToken()) {
                    connectGatewayUseCase.connect()
                }

                openClawNotificationScheduler.scheduleImmediate()
                openClawPushSyncManager.syncRegistration()

                _uiState.update { state ->
                    if (state is OpenClawDashboardUiState.Success) {
                        state.copy(
                            showSetupSheet = false,
                            agentHistory = mergeAgentHistory(
                                state.agentHistory,
                                listOf(resolvedAgentId)
                            )
                        )
                    } else state
                }

                _events.emit(
                    OpenClawDashboardEvent.ShowSnackbar("Configuration saved")
                )
            } catch (e: Exception) {
                _events.emit(
                    OpenClawDashboardEvent.ShowSnackbar(
                        "Failed to save config: ${e.message}"
                    )
                )
            }
        }
    }

    /**
     * Navigates to the agent chat.
     */
    fun navigateToChat() {
        viewModelScope.launch {
            _events.emit(OpenClawDashboardEvent.NavigateToChat())
        }
    }

    /**
     * Navigates to the sessions list.
     */
    fun navigateToSessions() {
        viewModelScope.launch {
            _events.emit(OpenClawDashboardEvent.NavigateToSessions)
        }
    }

    private fun mergeAgentHistory(vararg agentGroups: List<String>): List<String> {
        return agentGroups
            .flatMap { it }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }
}
