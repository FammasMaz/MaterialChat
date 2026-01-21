package com.materialchat.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.model.AiModel
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.usecase.ExportConversationUseCase
import com.materialchat.domain.usecase.GetConversationsUseCase
import com.materialchat.domain.usecase.ManageProvidersUseCase
import com.materialchat.domain.usecase.RegenerateResponseUseCase
import com.materialchat.domain.usecase.SendMessageUseCase
import com.materialchat.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Chat screen.
 *
 * Manages the chat state, message sending, and streaming responses.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getConversationsUseCase: GetConversationsUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val regenerateResponseUseCase: RegenerateResponseUseCase,
    private val exportConversationUseCase: ExportConversationUseCase,
    private val manageProvidersUseCase: ManageProvidersUseCase,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val conversationId: String = checkNotNull(
        savedStateHandle[Screen.Chat.ARG_CONVERSATION_ID]
    )

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>()
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    private var streamingJob: Job? = null
    private var currentSystemPrompt: String = AppPreferences.DEFAULT_SYSTEM_PROMPT
    private var currentHapticsEnabled: Boolean = AppPreferences.DEFAULT_HAPTICS_ENABLED

    init {
        loadConversation()
        loadSystemPrompt()
        loadHapticsPreference()
    }

    /**
     * Loads the conversation and observes message updates.
     */
    private fun loadConversation() {
        viewModelScope.launch {
            try {
                // Get the conversation details
                val conversation = getConversationsUseCase.getConversation(conversationId)
                if (conversation == null) {
                    _uiState.value = ChatUiState.Error("Conversation not found")
                    return@launch
                }

                // Get provider info
                val providers = manageProvidersUseCase.observeProviders().first()
                val provider = providers.find { it.id == conversation.providerId }
                val providerName = provider?.name ?: "Unknown Provider"

                // Observe messages
                getConversationsUseCase.observeMessages(conversationId)
                    .catch { e ->
                        _uiState.value = ChatUiState.Error(
                            message = e.message ?: "Failed to load messages"
                        )
                    }
                    .collect { messages ->
                        val currentState = _uiState.value
                        val inputText = if (currentState is ChatUiState.Success) {
                            currentState.inputText
                        } else {
                            ""
                        }
                        val streamingState = if (currentState is ChatUiState.Success) {
                            currentState.streamingState
                        } else {
                            StreamingState.Idle
                        }
                        val availableModels = if (currentState is ChatUiState.Success) {
                            currentState.availableModels
                        } else {
                            emptyList()
                        }
                        val isLoadingModels = if (currentState is ChatUiState.Success) {
                            currentState.isLoadingModels
                        } else {
                            false
                        }

                        // Find the last assistant message index
                        val lastAssistantIndex = messages.indexOfLast {
                            it.role == MessageRole.ASSISTANT
                        }

                        val messageItems = messages.mapIndexed { index, message ->
                            MessageUiItem(
                                message = message,
                                isLastAssistantMessage = index == lastAssistantIndex &&
                                        message.role == MessageRole.ASSISTANT,
                                showActions = message.role == MessageRole.ASSISTANT ||
                                        message.role == MessageRole.USER
                            )
                        }

                        _uiState.value = ChatUiState.Success(
                            conversationId = conversationId,
                            conversationTitle = conversation.title,
                            providerName = providerName,
                            modelName = conversation.modelName,
                            messages = messageItems,
                            inputText = inputText,
                            streamingState = streamingState,
                            availableModels = availableModels,
                            isLoadingModels = isLoadingModels,
                            hapticsEnabled = currentHapticsEnabled
                        )

                        // Only scroll to bottom when a NEW message is added, not during streaming updates
                        // Track message count to detect new messages
                        val previousMessageCount = (currentState as? ChatUiState.Success)?.messages?.size ?: 0
                        val newMessageAdded = messages.size > previousMessageCount
                        
                        if (messages.isNotEmpty() && newMessageAdded) {
                            _events.emit(ChatEvent.ScrollToBottom)
                        }
                    }
            } catch (e: Exception) {
                _uiState.value = ChatUiState.Error(
                    message = e.message ?: "Failed to load conversation"
                )
            }
        }
    }

    /**
     * Loads the system prompt from preferences.
     */
    private fun loadSystemPrompt() {
        viewModelScope.launch {
            appPreferences.systemPrompt.collect { prompt ->
                currentSystemPrompt = prompt
            }
        }
    }

    /**
     * Loads the haptics preference and updates UI state when it changes.
     */
    private fun loadHapticsPreference() {
        viewModelScope.launch {
            appPreferences.hapticsEnabled.collect { enabled ->
                currentHapticsEnabled = enabled
                // Update UI state if we're in Success state
                val currentState = _uiState.value
                if (currentState is ChatUiState.Success) {
                    _uiState.value = currentState.copy(hapticsEnabled = enabled)
                }
            }
        }
    }

    /**
     * Updates the input text.
     */
    fun updateInputText(text: String) {
        val currentState = _uiState.value
        if (currentState is ChatUiState.Success) {
            _uiState.value = currentState.copy(inputText = text)
        }
    }

    /**
     * Sends the current message.
     */
    fun sendMessage() {
        val currentState = _uiState.value
        if (currentState !is ChatUiState.Success) return
        if (!currentState.canSend) return

        val messageContent = currentState.inputText.trim()

        // Clear input immediately
        _uiState.value = currentState.copy(
            inputText = "",
            streamingState = StreamingState.Starting
        )

        streamingJob = viewModelScope.launch {
            try {
                sendMessageUseCase(
                    conversationId = conversationId,
                    userContent = messageContent,
                    systemPrompt = currentSystemPrompt
                ).collect { state ->
                    updateStreamingState(state)
                }
            } catch (e: Exception) {
                updateStreamingState(
                    StreamingState.Error(
                        error = e,
                        partialContent = null,
                        messageId = null
                    )
                )
                _events.emit(
                    ChatEvent.ShowSnackbar(
                        message = e.message ?: "Failed to send message"
                    )
                )
            }
        }
    }

    /**
     * Updates the streaming state in the UI.
     */
    private fun updateStreamingState(state: StreamingState) {
        val currentState = _uiState.value
        if (currentState is ChatUiState.Success) {
            _uiState.value = currentState.copy(streamingState = state)
        }
    }

    /**
     * Cancels the current streaming request.
     */
    fun cancelStreaming() {
        streamingJob?.cancel()
        streamingJob = null
        sendMessageUseCase.cancel()

        val currentState = _uiState.value
        if (currentState is ChatUiState.Success) {
            _uiState.value = currentState.copy(
                streamingState = StreamingState.Idle
            )
        }
    }

    /**
     * Copies a message content to clipboard.
     */
    fun copyMessage(content: String) {
        viewModelScope.launch {
            // The actual clipboard operation will be handled by the UI
            _events.emit(ChatEvent.MessageCopied)
        }
    }

    /**
     * Navigates back to the conversations list.
     */
    fun navigateBack() {
        viewModelScope.launch {
            _events.emit(ChatEvent.NavigateBack)
        }
    }

    /**
     * Loads available models from the provider.
     */
    fun loadModels() {
        val currentState = _uiState.value
        if (currentState !is ChatUiState.Success) return
        if (currentState.isLoadingModels) return

        _uiState.value = currentState.copy(isLoadingModels = true)

        viewModelScope.launch {
            try {
                val conversation = getConversationsUseCase.getConversation(conversationId)
                if (conversation == null) {
                    _uiState.value = currentState.copy(isLoadingModels = false)
                    return@launch
                }

                val modelsResult = manageProvidersUseCase.fetchModels(conversation.providerId)
                val models = modelsResult.getOrElse { emptyList() }

                val updatedState = _uiState.value
                if (updatedState is ChatUiState.Success) {
                    _uiState.value = updatedState.copy(
                        availableModels = models,
                        isLoadingModels = false
                    )
                }

                // Show error if fetching failed
                modelsResult.exceptionOrNull()?.let { error ->
                    _events.emit(
                        ChatEvent.ShowSnackbar(
                            message = "Failed to load models: ${error.message}"
                        )
                    )
                }
            } catch (e: Exception) {
                val updatedState = _uiState.value
                if (updatedState is ChatUiState.Success) {
                    _uiState.value = updatedState.copy(isLoadingModels = false)
                }
                _events.emit(
                    ChatEvent.ShowSnackbar(
                        message = "Failed to load models: ${e.message}"
                    )
                )
            }
        }
    }

    /**
     * Changes the model for this conversation.
     */
    fun changeModel(model: AiModel) {
        viewModelScope.launch {
            try {
                getConversationsUseCase.updateConversationModel(conversationId, model.id)

                val currentState = _uiState.value
                if (currentState is ChatUiState.Success) {
                    _uiState.value = currentState.copy(modelName = model.id)
                }

                _events.emit(ChatEvent.ModelChanged(model.name))
            } catch (e: Exception) {
                _events.emit(
                    ChatEvent.ShowSnackbar(
                        message = "Failed to change model: ${e.message}"
                    )
                )
            }
        }
    }

    /**
     * Regenerates the last AI response.
     */
    fun regenerateResponse() {
        val currentState = _uiState.value
        if (currentState !is ChatUiState.Success) return
        if (currentState.isStreaming) return

        _uiState.value = currentState.copy(streamingState = StreamingState.Starting)

        streamingJob = viewModelScope.launch {
            try {
                regenerateResponseUseCase(
                    conversationId = conversationId,
                    systemPrompt = currentSystemPrompt
                ).collect { state ->
                    updateStreamingState(state)
                }
            } catch (e: Exception) {
                updateStreamingState(
                    StreamingState.Error(
                        error = e,
                        partialContent = null,
                        messageId = null
                    )
                )
                _events.emit(
                    ChatEvent.ShowSnackbar(
                        message = e.message ?: "Failed to regenerate response"
                    )
                )
            }
        }
    }

    /**
     * Shows export options for the conversation.
     */
    fun showExportOptions() {
        val currentState = _uiState.value
        if (currentState is ChatUiState.Success) {
            _uiState.value = currentState.copy(showExportSheet = true)
        }
    }

    /**
     * Hides the export options bottom sheet.
     */
    fun hideExportOptions() {
        val currentState = _uiState.value
        if (currentState is ChatUiState.Success) {
            _uiState.value = currentState.copy(showExportSheet = false, isExporting = false)
        }
    }

    /**
     * Exports the conversation in the specified format.
     *
     * @param format The export format to use
     */
    fun exportConversation(format: ExportConversationUseCase.ExportFormat) {
        val currentState = _uiState.value
        if (currentState !is ChatUiState.Success) return
        if (currentState.isExporting) return

        _uiState.value = currentState.copy(isExporting = true)

        viewModelScope.launch {
            try {
                val result = exportConversationUseCase(conversationId, format)
                result.fold(
                    onSuccess = { exportResult ->
                        // Hide the export sheet first
                        val updatedState = _uiState.value
                        if (updatedState is ChatUiState.Success) {
                            _uiState.value = updatedState.copy(
                                showExportSheet = false,
                                isExporting = false
                            )
                        }
                        // Emit share event
                        _events.emit(
                            ChatEvent.ShareContent(
                                content = exportResult.content,
                                filename = exportResult.filename,
                                mimeType = exportResult.mimeType
                            )
                        )
                    },
                    onFailure = { error ->
                        val updatedState = _uiState.value
                        if (updatedState is ChatUiState.Success) {
                            _uiState.value = updatedState.copy(isExporting = false)
                        }
                        _events.emit(
                            ChatEvent.ShowSnackbar(
                                message = "Export failed: ${error.message}"
                            )
                        )
                    }
                )
            } catch (e: Exception) {
                val updatedState = _uiState.value
                if (updatedState is ChatUiState.Success) {
                    _uiState.value = updatedState.copy(isExporting = false)
                }
                _events.emit(
                    ChatEvent.ShowSnackbar(
                        message = "Export failed: ${e.message}"
                    )
                )
            }
        }
    }

    /**
     * Retries loading the conversation.
     */
    fun retry() {
        _uiState.value = ChatUiState.Loading
        loadConversation()
    }

    override fun onCleared() {
        super.onCleared()
        streamingJob?.cancel()
    }
}
