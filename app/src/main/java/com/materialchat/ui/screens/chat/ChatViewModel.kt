package com.materialchat.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.di.IoDispatcher
import com.materialchat.domain.model.AiModel
import com.materialchat.domain.model.Attachment
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.usecase.ExportConversationUseCase
import com.materialchat.domain.usecase.BranchConversationUseCase
import com.materialchat.domain.usecase.GetConversationsUseCase
import com.materialchat.domain.usecase.ManageProvidersUseCase
import com.materialchat.domain.usecase.RegenerateResponseUseCase
import com.materialchat.domain.usecase.SendMessageUseCase
import com.materialchat.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

private const val MESSAGE_GROUP_TIME_WINDOW_MS = 5 * 60 * 1000L

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
    private val branchConversationUseCase: BranchConversationUseCase,
    private val manageProvidersUseCase: ManageProvidersUseCase,
    private val appPreferences: AppPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
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
    private var currentReasoningEffort: ReasoningEffort = AppPreferences.DEFAULT_REASONING_EFFORT
    private var currentBeautifulModelNamesEnabled: Boolean = AppPreferences.DEFAULT_BEAUTIFUL_MODEL_NAMES

    init {
        loadConversation()
        loadSystemPrompt()
        loadHapticsPreference()
        loadReasoningEffort()
        loadBeautifulModelNamesPreference()
    }

    /**
     * Loads the conversation and observes message updates.
     */
    private fun loadConversation() {
        viewModelScope.launch {
            try {
                // Get initial conversation details for provider info
                val initialConversation = getConversationsUseCase.getConversation(conversationId)
                if (initialConversation == null) {
                    _uiState.value = ChatUiState.Error("Conversation not found")
                    return@launch
                }

                // Get provider info
                val providers = manageProvidersUseCase.observeProviders().first()
                val provider = providers.find { it.id == initialConversation.providerId }
                val providerName = provider?.name ?: "Unknown Provider"

                // Combine conversation and messages observation
                combine(
                    getConversationsUseCase.observeConversation(conversationId),
                    getConversationsUseCase.observeMessages(conversationId)
                ) { conversation, messages ->
                    Pair(conversation, messages)
                }
                    .catch { e ->
                        _uiState.value = ChatUiState.Error(
                            message = e.message ?: "Failed to load messages"
                        )
                    }
                    .collect { (conversation, messages) ->
                        if (conversation == null) {
                            _uiState.value = ChatUiState.Error("Conversation not found")
                            return@collect
                        }

                        val currentState = _uiState.value
                        val inputText = if (currentState is ChatUiState.Success) {
                            currentState.inputText
                        } else {
                            ""
                        }
                        val pendingAttachments = if (currentState is ChatUiState.Success) {
                            currentState.pendingAttachments
                        } else {
                            emptyList()
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
                        val currentModelName = if (currentState is ChatUiState.Success) {
                            currentState.modelName
                        } else {
                            conversation.modelName
                        }
                        val reasoningEffort = if (currentState is ChatUiState.Success) {
                            currentState.reasoningEffort
                        } else {
                            currentReasoningEffort
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
                                        message.role == MessageRole.USER,
                                groupPosition = resolveGroupPosition(messages, index)
                            )
                        }

                        _uiState.value = ChatUiState.Success(
                            conversationId = conversationId,
                            conversationTitle = conversation.title,
                            conversationIcon = conversation.icon,
                            providerName = providerName,
                            modelName = currentModelName,
                            messages = messageItems,
                            inputText = inputText,
                            pendingAttachments = pendingAttachments,
                            streamingState = streamingState,
                            availableModels = availableModels,
                            isLoadingModels = isLoadingModels,
                            hapticsEnabled = currentHapticsEnabled,
                            reasoningEffort = reasoningEffort,
                            beautifulModelNamesEnabled = currentBeautifulModelNamesEnabled
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
     * Loads the reasoning effort preference and updates UI state when it changes.
     */
    private fun loadReasoningEffort() {
        viewModelScope.launch {
            appPreferences.reasoningEffort.collect { effort ->
                currentReasoningEffort = effort
                val currentState = _uiState.value
                if (currentState is ChatUiState.Success) {
                    _uiState.value = currentState.copy(reasoningEffort = effort)
                }
            }
        }
    }

    /**
     * Loads the beautiful model names preference and updates UI state when it changes.
     */
    private fun loadBeautifulModelNamesPreference() {
        viewModelScope.launch {
            appPreferences.beautifulModelNamesEnabled.collect { enabled ->
                currentBeautifulModelNamesEnabled = enabled
                val currentState = _uiState.value
                if (currentState is ChatUiState.Success) {
                    _uiState.value = currentState.copy(beautifulModelNamesEnabled = enabled)
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

    private fun resolveGroupPosition(
        messages: List<Message>,
        index: Int
    ): MessageGroupPosition {
        val message = messages[index]
        if (message.role == MessageRole.SYSTEM) {
            return MessageGroupPosition.Single
        }

        val previous = messages.getOrNull(index - 1)
        val next = messages.getOrNull(index + 1)

        val groupedWithPrevious = previous?.let { isGroupedMessage(message, it) } ?: false
        val groupedWithNext = next?.let { isGroupedMessage(next, message) } ?: false

        return when {
            groupedWithPrevious && groupedWithNext -> MessageGroupPosition.Middle
            groupedWithPrevious -> MessageGroupPosition.Last
            groupedWithNext -> MessageGroupPosition.First
            else -> MessageGroupPosition.Single
        }
    }

    private fun isGroupedMessage(
        current: Message,
        other: Message
    ): Boolean {
        if (current.role != other.role || current.role == MessageRole.SYSTEM) {
            return false
        }

        val timeGap = abs(current.createdAt - other.createdAt)
        return timeGap <= MESSAGE_GROUP_TIME_WINDOW_MS
    }

    /**
     * Adds an image attachment to the pending attachments list.
     *
     * @param attachment The attachment to add
     */
    fun addAttachment(attachment: Attachment) {
        val currentState = _uiState.value
        if (currentState is ChatUiState.Success) {
            _uiState.value = currentState.copy(
                pendingAttachments = currentState.pendingAttachments + attachment
            )
        }
    }

    /**
     * Removes an image attachment from the pending attachments list.
     *
     * @param attachment The attachment to remove
     */
    fun removeAttachment(attachment: Attachment) {
        val currentState = _uiState.value
        if (currentState is ChatUiState.Success) {
            _uiState.value = currentState.copy(
                pendingAttachments = currentState.pendingAttachments.filter { it.id != attachment.id }
            )
        }
    }

    /**
     * Clears all pending attachments.
     */
    fun clearAttachments() {
        val currentState = _uiState.value
        if (currentState is ChatUiState.Success) {
            _uiState.value = currentState.copy(pendingAttachments = emptyList())
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
        val attachments = currentState.pendingAttachments.toList()

        // Clear input and attachments immediately
        _uiState.value = currentState.copy(
            inputText = "",
            pendingAttachments = emptyList(),
            streamingState = StreamingState.Starting
        )

        streamingJob = viewModelScope.launch(ioDispatcher) {
            try {
                sendMessageUseCase(
                    conversationId = conversationId,
                    userContent = messageContent,
                    attachments = attachments,
                    systemPrompt = currentSystemPrompt,
                    reasoningEffort = currentReasoningEffort
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
        _uiState.update { currentState ->
            if (currentState is ChatUiState.Success) {
                currentState.copy(streamingState = state)
            } else {
                currentState
            }
        }

        // Show error message to user when streaming error occurs
        if (state is StreamingState.Error) {
            viewModelScope.launch {
                _events.emit(
                    ChatEvent.ShowSnackbar(
                        message = state.error?.message ?: "An error occurred"
                    )
                )
            }
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

                // Save as last used model if the setting is enabled
                if (appPreferences.rememberLastModel.first()) {
                    appPreferences.setLastUsedModel(model.id)
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
     * Updates the reasoning effort preference.
     */
    fun updateReasoningEffort(effort: ReasoningEffort) {
        currentReasoningEffort = effort
        val currentState = _uiState.value
        if (currentState is ChatUiState.Success) {
            _uiState.value = currentState.copy(reasoningEffort = effort)
        }
        viewModelScope.launch {
            appPreferences.setReasoningEffort(effort)
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

        streamingJob = viewModelScope.launch(ioDispatcher) {
            try {
                regenerateResponseUseCase(
                    conversationId = conversationId,
                    systemPrompt = currentSystemPrompt,
                    reasoningEffort = currentReasoningEffort
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
     * Creates a branch from the conversation at the specified message.
     * The branch includes all messages up to and including the target message.
     *
     * @param messageId The ID of the message to branch from
     */
    fun branchFromMessage(messageId: String) {
        val currentState = _uiState.value
        if (currentState !is ChatUiState.Success) return
        if (currentState.isStreaming) return

        viewModelScope.launch {
            try {
                val newConversationId = branchConversationUseCase(
                    sourceConversationId = conversationId,
                    upToMessageId = messageId
                )
                _events.emit(ChatEvent.NavigateToBranch(newConversationId))
            } catch (e: Exception) {
                _events.emit(
                    ChatEvent.ShowSnackbar(
                        message = e.message ?: "Failed to create branch"
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
