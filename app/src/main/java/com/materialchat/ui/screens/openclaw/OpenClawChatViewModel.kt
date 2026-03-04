package com.materialchat.ui.screens.openclaw

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.domain.model.openclaw.GatewayConnectionState
import com.materialchat.domain.model.openclaw.GatewayEvent
import com.materialchat.domain.model.openclaw.OpenClawChatMessage
import com.materialchat.domain.model.openclaw.OpenClawChatRole
import com.materialchat.domain.usecase.openclaw.ConnectGatewayUseCase
import com.materialchat.domain.usecase.openclaw.ManageOpenClawSessionsUseCase
import com.materialchat.domain.usecase.openclaw.OpenClawChatUseCase
import com.materialchat.data.local.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the OpenClaw Chat screen.
 *
 * Manages chat messages, streaming state, agent status indicators,
 * and session lifecycle for an OpenClaw agent conversation.
 */
@HiltViewModel
class OpenClawChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val openClawChatUseCase: OpenClawChatUseCase,
    private val manageOpenClawSessionsUseCase: ManageOpenClawSessionsUseCase,
    private val connectGatewayUseCase: ConnectGatewayUseCase,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<OpenClawChatUiState>(OpenClawChatUiState.Loading)
    val uiState: StateFlow<OpenClawChatUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<OpenClawChatEvent>()
    val events: SharedFlow<OpenClawChatEvent> = _events.asSharedFlow()

    /** Session key from navigation arguments. Treat "new" as null (new session). */
    private val initialSessionKey: String? = savedStateHandle.get<String>("sessionKey")
        ?.takeIf { it != "new" }

    /** Current active run ID for correlating streaming events. */
    private var activeRunId: String? = null

    /** Job for the streaming event collection. */
    private var streamJob: Job? = null

    init {
        initializeChat()
        observeConnectionState()
        observeChatEvents()
        observeAgentEvents()
        observeHapticsPreference()
    }

    /**
     * Initializes the chat by loading history or resuming the most recent session.
     * Includes defensive checks for gateway connectivity.
     */
    private fun initializeChat() {
        viewModelScope.launch {
            try {
                var sessionKey = initialSessionKey
                var messages: List<OpenClawChatMessage> = emptyList()

                if (sessionKey != null) {
                    // Explicit session key — load its history
                    messages = try {
                        manageOpenClawSessionsUseCase.getChatHistory(sessionKey)
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else {
                    // No session key — try to resume the most recent session
                    // Only attempt if the gateway is connected
                    val connectionState = connectGatewayUseCase.connectionState.value
                    if (connectionState is GatewayConnectionState.Connected) {
                        try {
                            val sessions = manageOpenClawSessionsUseCase.listSessions()
                            val latestSession = sessions.firstOrNull()
                            if (latestSession != null) {
                                sessionKey = latestSession.key
                                messages = try {
                                    manageOpenClawSessionsUseCase.getChatHistory(latestSession.key)
                                } catch (e: Exception) {
                                    emptyList()
                                }
                            }
                        } catch (_: Exception) {
                            // Can't list sessions — start fresh
                        }
                    }
                }

                _uiState.value = OpenClawChatUiState.Active(
                    messages = messages,
                    sessionKey = sessionKey,
                    connectionState = connectGatewayUseCase.connectionState.value
                )
            } catch (e: Exception) {
                _uiState.value = OpenClawChatUiState.Error(
                    message = e.message ?: "Failed to load chat"
                )
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
                    if (state is OpenClawChatUiState.Active) {
                        state.copy(connectionState = connectionState)
                    } else state
                }
            }
        }
    }

    /**
     * Observes the haptics preference from app settings.
     */
    private fun observeHapticsPreference() {
        viewModelScope.launch {
            appPreferences.hapticsEnabled.collect { enabled ->
                _uiState.update { state ->
                    if (state is OpenClawChatUiState.Active) {
                        state.copy(hapticsEnabled = enabled)
                    } else state
                }
            }
        }
    }

    /**
     * Observes chat events from the WebSocket for streaming responses.
     */
    private fun observeChatEvents() {
        viewModelScope.launch {
            openClawChatUseCase.observeChatEvents().collect { event ->
                val currentState = _uiState.value
                if (currentState !is OpenClawChatUiState.Active) return@collect
                if (!currentState.isStreaming) return@collect

                // Accept events if activeRunId hasn't been set yet (race with RPC response)
                // or if it matches the event's runId
                val runId = activeRunId
                if (runId != null && runId.isNotEmpty() && event.runId.isNotEmpty() && event.runId != runId) return@collect

                // Capture runId from the first event if we don't have one yet
                if (runId == null && event.runId.isNotEmpty()) {
                    activeRunId = event.runId
                }

                when (event.state) {
                    "delta" -> {
                        val updatedMessages = appendOrUpdateAssistantMessage(
                            currentState.messages,
                            event.content ?: "",
                            event.runId
                        )
                        _uiState.update { state ->
                            if (state is OpenClawChatUiState.Active) {
                                state.copy(messages = updatedMessages)
                            } else state
                        }
                    }
                    "final" -> {
                        val updatedMessages = finalizeAssistantMessage(
                            currentState.messages,
                            event.content,
                            event.toolCalls,
                            event.runId
                        )
                        _uiState.update { state ->
                            if (state is OpenClawChatUiState.Active) {
                                state.copy(
                                    messages = updatedMessages,
                                    isStreaming = false,
                                    agentStatus = AgentStatus.IDLE,
                                    sessionKey = state.sessionKey ?: event.sessionKey
                                )
                            } else state
                        }
                        activeRunId = null
                    }
                    "aborted" -> {
                        _uiState.update { state ->
                            if (state is OpenClawChatUiState.Active) {
                                state.copy(
                                    isStreaming = false,
                                    agentStatus = AgentStatus.IDLE
                                )
                            } else state
                        }
                        activeRunId = null
                    }
                    "error" -> {
                        _events.emit(
                            OpenClawChatEvent.ShowSnackbar(
                                event.errorMessage ?: "Agent error"
                            )
                        )
                        _uiState.update { state ->
                            if (state is OpenClawChatUiState.Active) {
                                state.copy(
                                    isStreaming = false,
                                    agentStatus = AgentStatus.IDLE
                                )
                            } else state
                        }
                        activeRunId = null
                    }
                }
            }
        }
    }

    /**
     * Observes agent events (thinking, tool calls) from WebSocket.
     */
    private fun observeAgentEvents() {
        viewModelScope.launch {
            openClawChatUseCase.observeAgentEvents().collect { event ->
                val currentState = _uiState.value
                if (currentState !is OpenClawChatUiState.Active) return@collect
                if (!currentState.isStreaming) return@collect

                val runId = activeRunId
                if (runId != null && runId.isNotEmpty() && event.runId.isNotEmpty() && event.runId != runId) return@collect

                val agentStatus = when (event.stream) {
                    "thinking" -> AgentStatus.THINKING
                    "tool_call" -> AgentStatus.EXECUTING
                    "tool_result" -> AgentStatus.WAITING
                    else -> AgentStatus.THINKING
                }

                _uiState.update { state ->
                    if (state is OpenClawChatUiState.Active) {
                        state.copy(agentStatus = agentStatus)
                    } else state
                }
            }
        }
    }

    /**
     * Sends a message to the OpenClaw agent.
     */
    fun sendMessage() {
        val currentState = _uiState.value
        if (currentState !is OpenClawChatUiState.Active) return
        if (!currentState.canSend) return

        val messageText = currentState.currentInput.trim()

        // Add user message to the list
        val userMessage = OpenClawChatMessage(
            role = OpenClawChatRole.USER,
            content = messageText
        )
        val updatedMessages = currentState.messages + userMessage

        _uiState.update { state ->
            if (state is OpenClawChatUiState.Active) {
                state.copy(
                    messages = updatedMessages,
                    isStreaming = true,
                    agentStatus = AgentStatus.THINKING,
                    currentInput = ""
                )
            } else state
        }

        viewModelScope.launch {
            try {
                val runId = openClawChatUseCase.sendMessage(
                    sessionKey = currentState.sessionKey,
                    message = messageText
                )
                activeRunId = runId
            } catch (e: Exception) {
                _uiState.update { state ->
                    if (state is OpenClawChatUiState.Active) {
                        state.copy(
                            isStreaming = false,
                            agentStatus = AgentStatus.IDLE
                        )
                    } else state
                }
                _events.emit(
                    OpenClawChatEvent.ShowSnackbar(
                        "Failed to send message: ${e.message}"
                    )
                )
            }
        }
    }

    /**
     * Aborts the current streaming response.
     */
    fun abortStream() {
        val currentState = _uiState.value
        if (currentState !is OpenClawChatUiState.Active) return
        val sessionKey = currentState.sessionKey ?: return

        viewModelScope.launch {
            try {
                openClawChatUseCase.abort(sessionKey, activeRunId)
                _uiState.update { state ->
                    if (state is OpenClawChatUiState.Active) {
                        state.copy(
                            isStreaming = false,
                            agentStatus = AgentStatus.IDLE
                        )
                    } else state
                }
                activeRunId = null
            } catch (e: Exception) {
                _events.emit(
                    OpenClawChatEvent.ShowSnackbar(
                        "Failed to abort: ${e.message}"
                    )
                )
            }
        }
    }

    /**
     * Loads chat history for a session.
     */
    fun loadHistory(sessionKey: String) {
        _uiState.value = OpenClawChatUiState.Loading

        viewModelScope.launch {
            try {
                val messages = manageOpenClawSessionsUseCase.getChatHistory(sessionKey)
                _uiState.value = OpenClawChatUiState.Active(
                    messages = messages,
                    sessionKey = sessionKey,
                    connectionState = connectGatewayUseCase.connectionState.value
                )
            } catch (e: Exception) {
                _uiState.value = OpenClawChatUiState.Error(
                    message = e.message ?: "Failed to load history"
                )
            }
        }
    }

    /**
     * Starts a new chat session by clearing messages and resetting state.
     */
    fun startNewSession() {
        activeRunId = null
        _uiState.value = OpenClawChatUiState.Active(
            messages = emptyList(),
            sessionKey = null,
            connectionState = connectGatewayUseCase.connectionState.value
        )
    }

    /**
     * Updates the current input text.
     */
    fun updateInput(text: String) {
        _uiState.update { state ->
            if (state is OpenClawChatUiState.Active) {
                state.copy(currentInput = text)
            } else state
        }
    }

    /**
     * Appends delta content to the current assistant message or creates a new one.
     */
    private fun appendOrUpdateAssistantMessage(
        messages: List<OpenClawChatMessage>,
        delta: String,
        runId: String
    ): List<OpenClawChatMessage> {
        val lastMessage = messages.lastOrNull()
        return if (lastMessage?.role == OpenClawChatRole.ASSISTANT && lastMessage.runId == runId) {
            // Replace content — server sends full accumulated text, not just the new chunk
            messages.dropLast(1) + lastMessage.copy(
                content = delta
            )
        } else {
            messages + OpenClawChatMessage(
                role = OpenClawChatRole.ASSISTANT,
                content = delta,
                runId = runId
            )
        }
    }

    /**
     * Finalizes the assistant message with final content and tool calls.
     */
    private fun finalizeAssistantMessage(
        messages: List<OpenClawChatMessage>,
        finalContent: String?,
        toolCalls: List<com.materialchat.domain.model.openclaw.ToolCallInfo>?,
        runId: String
    ): List<OpenClawChatMessage> {
        val lastMessage = messages.lastOrNull()
        return if (lastMessage?.role == OpenClawChatRole.ASSISTANT && lastMessage.runId == runId) {
            messages.dropLast(1) + lastMessage.copy(
                content = finalContent ?: lastMessage.content,
                toolCalls = toolCalls ?: lastMessage.toolCalls
            )
        } else if (finalContent != null) {
            messages + OpenClawChatMessage(
                role = OpenClawChatRole.ASSISTANT,
                content = finalContent,
                toolCalls = toolCalls ?: emptyList(),
                runId = runId
            )
        } else {
            messages
        }
    }

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
    }
}
