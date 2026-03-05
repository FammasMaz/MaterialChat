package com.materialchat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.data.local.preferences.OpenClawPreferences
import com.materialchat.domain.model.Persona
import com.materialchat.domain.model.openclaw.GatewayConnectionState
import com.materialchat.domain.usecase.CreateConversationUseCase
import com.materialchat.domain.usecase.ManagePersonasUseCase
import com.materialchat.domain.usecase.openclaw.ConnectGatewayUseCase
import com.materialchat.domain.usecase.openclaw.ManageOpenClawConfigUseCase
import com.materialchat.domain.usecase.openclaw.OpenClawChatUseCase
import com.materialchat.notifications.AppNotificationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App-level ViewModel for actions accessible from the floating toolbar.
 * Handles conversation creation (New Chat FAB) independent of screen scope.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val createConversationUseCase: CreateConversationUseCase,
    private val managePersonasUseCase: ManagePersonasUseCase,
    private val connectGatewayUseCase: ConnectGatewayUseCase,
    private val openClawChatUseCase: OpenClawChatUseCase,
    private val manageOpenClawConfigUseCase: ManageOpenClawConfigUseCase,
    private val appPreferences: AppPreferences,
    private val appNotificationManager: AppNotificationManager
) : ViewModel() {

    /** Whether the OpenClaw Gateway is currently connected. */
    val isOpenClawConnected: StateFlow<Boolean> = connectGatewayUseCase.connectionState
        .map { it is GatewayConnectionState.Connected }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _navigateToChat = MutableSharedFlow<String>()
    val navigateToChat = _navigateToChat.asSharedFlow()

    private val _showError = MutableSharedFlow<String>()
    val showError = _showError.asSharedFlow()

    /** Whether the persona picker bottom sheet is visible. */
    private val _showPersonaPicker = MutableStateFlow(false)
    val showPersonaPicker: StateFlow<Boolean> = _showPersonaPicker.asStateFlow()

    /** Reactive list of all personas for the picker. */
    val personas: Flow<List<Persona>> = managePersonasUseCase.observeAllPersonas()

    private var notificationsEnabled: Boolean = false
    private var defaultOpenClawAgentId: String = OpenClawPreferences.DEFAULT_AGENT_ID
    private val recentlyNotifiedRuns = ArrayDeque<String>()

    init {
        maintainGatewayConnectionWhileAppRuns()
        observeNotificationPreference()
        observeOpenClawConfig()
        observeOpenClawEvents()
    }

    fun showPersonaPicker() { _showPersonaPicker.value = true }
    fun hidePersonaPicker() { _showPersonaPicker.value = false }

    private fun maintainGatewayConnectionWhileAppRuns() {
        viewModelScope.launch {
            combine(
                manageOpenClawConfigUseCase.observeConfig(),
                connectGatewayUseCase.connectionState
            ) { config, connectionState ->
                config to connectionState
            }.collect { (config, connectionState) ->
                val shouldConnect = config.isEnabled &&
                    config.isConfigured &&
                    manageOpenClawConfigUseCase.hasToken()

                val alreadyConnected = connectionState is GatewayConnectionState.Connected ||
                    connectionState is GatewayConnectionState.Connecting

                if (shouldConnect && !alreadyConnected) {
                    try {
                        connectGatewayUseCase.connect()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun observeNotificationPreference() {
        viewModelScope.launch {
            appPreferences.notificationsEnabled
                .catch { emit(false) }
                .collect { enabled ->
                    notificationsEnabled = enabled
                }
        }
    }

    private fun observeOpenClawConfig() {
        viewModelScope.launch {
            manageOpenClawConfigUseCase.observeConfig()
                .catch { }
                .collect { config ->
                    defaultOpenClawAgentId = config.agentId.ifBlank { OpenClawPreferences.DEFAULT_AGENT_ID }
                }
        }
    }

    private fun observeOpenClawEvents() {
        viewModelScope.launch {
            openClawChatUseCase.observeChatEvents()
                .catch { }
                .collect { event ->
                    if (!notificationsEnabled) return@collect
                    if (event.state != "final") return@collect
                    val content = event.content?.takeIf { it.isNotBlank() } ?: return@collect

                    val dedupeKey = "${event.runId}:${event.sessionKey}:${event.seq}"
                    if (dedupeKey in recentlyNotifiedRuns) return@collect
                    recentlyNotifiedRuns.addLast(dedupeKey)
                    if (recentlyNotifiedRuns.size > 100) {
                        recentlyNotifiedRuns.removeFirst()
                    }

                    appNotificationManager.notifyOpenClawResponse(
                        sessionKey = event.sessionKey.ifBlank { null },
                        agentId = defaultOpenClawAgentId,
                        content = content
                    )
                }
        }
    }

    fun createNewConversation() {
        viewModelScope.launch {
            try {
                val conversationId = createConversationUseCase()
                _navigateToChat.emit(conversationId)
            } catch (e: IllegalStateException) {
                _showError.emit("No provider configured")
            } catch (e: Exception) {
                _showError.emit(e.message ?: "Failed to create conversation")
            }
        }
    }

    fun createNewConversationWithPersona(personaId: String) {
        viewModelScope.launch {
            try {
                val conversationId = createConversationUseCase.withPersona(personaId)
                _navigateToChat.emit(conversationId)
            } catch (e: IllegalStateException) {
                _showError.emit("No provider configured")
            } catch (e: Exception) {
                _showError.emit(e.message ?: "Failed to create conversation")
            }
            _showPersonaPicker.value = false
        }
    }
}
