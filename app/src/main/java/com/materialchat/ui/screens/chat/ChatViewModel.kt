package com.materialchat.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.data.local.preferences.EncryptedPreferences
import com.materialchat.di.IoDispatcher
import com.materialchat.domain.model.AiModel
import com.materialchat.domain.model.Attachment
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.Persona
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.model.BookmarkCategory
import com.materialchat.domain.model.FusionConfig
import com.materialchat.domain.model.FusionModelSelection
import com.materialchat.domain.model.WebSearchConfig
import com.materialchat.domain.model.WebSearchProvider
import com.materialchat.domain.usecase.ExportConversationUseCase
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.usecase.BranchConversationUseCase
import com.materialchat.domain.usecase.GetConversationsUseCase
import com.materialchat.domain.usecase.ManagePersonasUseCase
import com.materialchat.domain.usecase.ManageBookmarksUseCase
import com.materialchat.domain.usecase.ManageProvidersUseCase
import com.materialchat.domain.usecase.RedoWithModelUseCase
import com.materialchat.domain.usecase.RegenerateResponseUseCase
import com.materialchat.domain.usecase.RunFusionUseCase
import com.materialchat.domain.usecase.SendMessageUseCase
import com.materialchat.notifications.AppNotificationManager
import com.materialchat.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import com.materialchat.di.ApplicationScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

private const val MESSAGE_GROUP_TIME_WINDOW_MS = 5 * 60 * 1000L
private const val STREAMING_UI_UPDATE_INTERVAL_MS = 120L

/**
 * ViewModel for the Chat screen.
 *
 * Manages the chat state, message sending, and streaming responses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getConversationsUseCase: GetConversationsUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val regenerateResponseUseCase: RegenerateResponseUseCase,
    private val exportConversationUseCase: ExportConversationUseCase,
    private val conversationRepository: ConversationRepository,
    private val branchConversationUseCase: BranchConversationUseCase,
    private val redoWithModelUseCase: RedoWithModelUseCase,
    private val manageBookmarksUseCase: ManageBookmarksUseCase,
    private val runFusionUseCase: RunFusionUseCase,
    private val manageProvidersUseCase: ManageProvidersUseCase,
    private val managePersonasUseCase: ManagePersonasUseCase,
    private val appPreferences: AppPreferences,
    private val encryptedPreferences: EncryptedPreferences,
    private val appNotificationManager: AppNotificationManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {

    private val conversationId: String = checkNotNull(
        savedStateHandle[Screen.Chat.ARG_CONVERSATION_ID]
    )

    private val autoSend: Boolean = savedStateHandle[Screen.Chat.ARG_AUTO_SEND] ?: false

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>()
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    private var streamingJob: Job? = null
    private var fusionJob: Job? = null
    private var lastStreamingUiUpdateMs: Long = 0L
    private var lastStreamingUiMessageId: String? = null
    private var currentSystemPrompt: String = AppPreferences.DEFAULT_SYSTEM_PROMPT
    private var currentHapticsEnabled: Boolean = AppPreferences.DEFAULT_HAPTICS_ENABLED
    private var currentReasoningEffort: ReasoningEffort = AppPreferences.DEFAULT_REASONING_EFFORT
    private var currentBeautifulModelNamesEnabled: Boolean = AppPreferences.DEFAULT_BEAUTIFUL_MODEL_NAMES
    private var currentAlwaysShowThinking: Boolean = false
    private var currentShowTokenCounter: Boolean = false
    private var currentWebSearchEnabled: Boolean = false
    private var currentWebSearchProvider: WebSearchProvider = WebSearchProvider.EXA
    private var currentSearxngBaseUrl: String = AppPreferences.DEFAULT_SEARXNG_BASE_URL
    private var currentWebSearchMaxResults: Int = AppPreferences.DEFAULT_WEB_SEARCH_MAX_RESULTS
    private var autoSendTriggered: Boolean = false
    private var siblingInfo: SiblingInfo? = null
    private var currentPersona: Persona? = null
    private val activeConversationId = MutableStateFlow(conversationId)
    private val overrideModel: String? = savedStateHandle[Screen.Chat.ARG_OVERRIDE_MODEL]
    // Tracks the message ID being regenerated to prevent overlap artifacts
    private var regeneratingMessageId: String? = null

    init {
        cleanUpStuckStreamingMessages()
        loadConversation()
        loadSystemPrompt()
        loadHapticsPreference()
        loadReasoningEffort()
        loadBeautifulModelNamesPreference()
        loadAlwaysShowThinkingPreference()
        loadShowTokenCounterPreference()
        loadWebSearchPreferences()
        loadSiblings()
        loadPersona()
        loadBookmarkStates()
    }

    /**
     * Cleans up messages stuck with isStreaming=true from a previous session.
     * This happens when the app was killed mid-stream or after a crash.
     * Only cleans messages older than 5 minutes to avoid interfering with
     * active background streaming (which runs on applicationScope).
     */
    private fun cleanUpStuckStreamingMessages() {
        viewModelScope.launch(ioDispatcher) {
            try {
                val messages = conversationRepository.getMessages(conversationId)
                val staleThreshold = System.currentTimeMillis() - 5 * 60 * 1000L
                for (message in messages) {
                    if (message.isStreaming && message.createdAt < staleThreshold) {
                        conversationRepository.setMessageStreaming(message.id, false)
                    }
                }
            } catch (_: Exception) {
                // Best-effort cleanup
            }
        }
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

                // Observe conversation and messages reactively, switching on activeConversationId
                activeConversationId.flatMapLatest { currentId ->
                    combine(
                        getConversationsUseCase.observeConversation(currentId),
                        getConversationsUseCase.observeMessages(currentId)
                    ) { conversation, messages ->
                        Pair(conversation, messages)
                    }
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
                        val isSameConversation = currentState is ChatUiState.Success &&
                            currentState.conversationId == activeConversationId.value
                        val inputText = if (isSameConversation) {
                            (currentState as ChatUiState.Success).inputText
                        } else {
                            ""
                        }
                        val pendingAttachments = if (isSameConversation) {
                            (currentState as ChatUiState.Success).pendingAttachments
                        } else {
                            emptyList()
                        }
                        val streamingState = if (isSameConversation) {
                            (currentState as ChatUiState.Success).streamingState
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
                        val currentModelName = if (currentState is ChatUiState.Success &&
                            currentState.conversationId == activeConversationId.value) {
                            // Same conversation — preserve user's model picker selection
                            currentState.modelName
                        } else {
                            // New/switched conversation — use its actual model
                            conversation.modelName
                        }
                        val reasoningEffort = if (currentState is ChatUiState.Success) {
                            currentState.reasoningEffort
                        } else {
                            currentReasoningEffort
                        }

                        // Filter out the message being regenerated to prevent overlap artifacts.
                        // The DB may still contain the old message for a few frames after deleteMessage()
                        // is called in RegenerateResponseUseCase.
                        val filteredMessages = if (regeneratingMessageId != null) {
                            messages.filter { it.id != regeneratingMessageId }.also { filtered ->
                                // Clear the flag once the message is actually gone from DB
                                if (filtered.size == messages.size) {
                                    // Message was already deleted from DB, clear flag
                                } else if (streamingState !is StreamingState.Idle) {
                                    // Keep filtering while streaming
                                } else {
                                    regeneratingMessageId = null
                                }
                            }
                        } else {
                            messages
                        }

                        // Find the last assistant message index
                        val lastAssistantIndex = filteredMessages.indexOfLast {
                            it.role == MessageRole.ASSISTANT
                        }

                        val messageItems = filteredMessages.mapIndexed { index, message ->
                            val isSiblingTarget = index == lastAssistantIndex &&
                                    message.role == MessageRole.ASSISTANT
                            MessageUiItem(
                                message = message,
                                isLastAssistantMessage = index == lastAssistantIndex &&
                                        message.role == MessageRole.ASSISTANT,
                                showActions = message.role == MessageRole.ASSISTANT ||
                                        message.role == MessageRole.USER,
                                groupPosition = resolveGroupPosition(filteredMessages, index),
                                siblingInfo = if (isSiblingTarget) siblingInfo else null,
                                showModelLabel = message.modelName != null &&
                                        message.modelName != conversation.modelName,
                                isErrored = message.role == MessageRole.ASSISTANT &&
                                        message.content.isEmpty() && !message.hasAttachments && !message.isStreaming
                            )
                        }

                        val slideDirection = if (currentState is ChatUiState.Success) {
                            currentState.slideDirection
                        } else {
                            0
                        }

                        // Compute branch state inline to avoid race condition
                        // (loadBranchState() ran before state was Success, so hasBranches was always false)
                        val hasBranches = if (isSameConversation && currentState is ChatUiState.Success) {
                            currentState.hasBranches
                        } else {
                            conversation.parentId != null ||
                                conversationRepository.getBranchCount(activeConversationId.value) > 0
                        }

                        _uiState.value = ChatUiState.Success(
                            conversationId = activeConversationId.value,
                            conversationTitle = conversation.title,
                            conversationIcon = conversation.icon,
                            isEphemeral = conversation.isEphemeral,
                            isArchived = conversation.isArchived,
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
                            beautifulModelNamesEnabled = currentBeautifulModelNamesEnabled,
                            alwaysShowThinking = currentAlwaysShowThinking,
                            slideDirection = slideDirection,
                            persona = currentPersona,
                            editingMessageId = (currentState as? ChatUiState.Success)?.editingMessageId,
                            editingText = (currentState as? ChatUiState.Success)?.editingText ?: "",
                            hasBranches = hasBranches,
                            quotedMessage = (currentState as? ChatUiState.Success)?.quotedMessage,
                            showTokenCounter = currentShowTokenCounter
                        )

                        // Only scroll to bottom when a NEW message is added, not during streaming updates
                        // Track message count to detect new messages
                        val previousMessageCount = (currentState as? ChatUiState.Success)?.messages?.size ?: 0
                        val newMessageAdded = messages.size > previousMessageCount

                        if (messages.isNotEmpty() && newMessageAdded) {
                            _events.emit(ChatEvent.ScrollToBottom)
                        }

                        // Auto-send logic: if navigated with autoSend=true, trigger regenerate once
                        if (autoSend && !autoSendTriggered && _uiState.value is ChatUiState.Success) {
                            autoSendTriggered = true
                            regenerateResponse(overrideModelName = overrideModel)
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
     * Loads the persona associated with the conversation (if any).
     */
    private fun loadPersona() {
        viewModelScope.launch {
            try {
                val conversation = getConversationsUseCase.getConversation(conversationId)
                val personaId = conversation?.personaId
                if (personaId != null) {
                    currentPersona = managePersonasUseCase.getPersonaById(personaId)
                    val currentState = _uiState.value
                    if (currentState is ChatUiState.Success) {
                        _uiState.value = currentState.copy(persona = currentPersona)
                    }
                }
            } catch (_: Exception) {
                // Persona loading is non-critical
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
     * Loads the always show thinking preference and updates UI state when it changes.
     */
    private fun loadAlwaysShowThinkingPreference() {
        viewModelScope.launch {
            appPreferences.alwaysShowThinking.collect { enabled ->
                currentAlwaysShowThinking = enabled
                val currentState = _uiState.value
                if (currentState is ChatUiState.Success) {
                    _uiState.value = currentState.copy(alwaysShowThinking = enabled)
                }
            }
        }
    }

    /**
     * Loads the show token counter preference and updates UI state when it changes.
     */
    private fun loadShowTokenCounterPreference() {
        viewModelScope.launch {
            appPreferences.showTokenCounter.collect { enabled ->
                currentShowTokenCounter = enabled
                val currentState = _uiState.value
                if (currentState is ChatUiState.Success) {
                    _uiState.value = currentState.copy(showTokenCounter = enabled)
                }
            }
        }
    }

    /**
     * Loads web search preferences.
     */
    private fun loadWebSearchPreferences() {
        viewModelScope.launch {
            appPreferences.webSearchEnabled.collect { enabled ->
                currentWebSearchEnabled = enabled
            }
        }
        viewModelScope.launch {
            appPreferences.webSearchProvider.collect { provider ->
                currentWebSearchProvider = try {
                    WebSearchProvider.valueOf(provider)
                } catch (_: Exception) {
                    WebSearchProvider.EXA
                }
            }
        }
        viewModelScope.launch {
            appPreferences.searxngBaseUrl.collect { url ->
                currentSearxngBaseUrl = url
            }
        }
        viewModelScope.launch {
            appPreferences.webSearchMaxResults.collect { maxResults ->
                currentWebSearchMaxResults = maxResults
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
     * Quotes a message for reply — sets quoted message state and prefills input context.
     * The quoted text is shown as a preview above the input and prepended on send.
     */
    fun quoteMessage(messageId: String) {
        val currentState = _uiState.value
        if (currentState is ChatUiState.Success) {
            val message = currentState.messages.find { it.message.id == messageId }?.message
            if (message != null && message.content.isNotEmpty()) {
                _uiState.value = currentState.copy(quotedMessage = message)
            }
        }
    }

    /**
     * Clears the currently quoted message.
     */
    fun clearQuote() {
        val currentState = _uiState.value
        if (currentState is ChatUiState.Success) {
            _uiState.value = currentState.copy(quotedMessage = null)
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
     * If fusion mode is enabled, delegates to sendFusionMessage instead.
     */
    fun sendMessage() {
        sendCurrentInput(forceImageGeneration = false)
    }

    /**
     * Generates an image from the current input using the default image model.
     */
    fun generateImage() {
        sendCurrentInput(forceImageGeneration = true)
    }

    private fun sendCurrentInput(forceImageGeneration: Boolean) {
        val currentState = _uiState.value
        if (currentState !is ChatUiState.Success) return
        if (forceImageGeneration && currentState.inputText.isBlank()) {
            viewModelScope.launch {
                _events.emit(ChatEvent.ShowSnackbar("Describe the image you want to create first"))
            }
            return
        }
        if (!forceImageGeneration && !currentState.canSend) return
        if (forceImageGeneration && currentState.isStreaming) return

        // If fusion is enabled and configured, use fusion flow
        if (!forceImageGeneration && currentState.fusionConfig.isEnabled &&
            currentState.fusionConfig.selectedModels.size >= 2 &&
            currentState.fusionConfig.judgeModel != null
        ) {
            sendFusionMessage()
            return
        }

        val messageContent = currentState.inputText.trim()
        val attachments = if (forceImageGeneration) emptyList() else currentState.pendingAttachments.toList()
        val quotedMessage = if (forceImageGeneration) null else currentState.quotedMessage

        // Prepend quoted text if present
        val finalContent = if (quotedMessage != null) {
            val quotedLines = quotedMessage.content.lines().joinToString("\n") { "> $it" }
            "$quotedLines\n\n$messageContent"
        } else {
            messageContent
        }

        // Clear input, attachments, and quote immediately
        _uiState.value = currentState.copy(
            inputText = "",
            pendingAttachments = emptyList(),
            streamingState = StreamingState.Starting,
            quotedMessage = null
        )

        // Use applicationScope so streaming survives navigation away / ViewModel destruction.
        // The DB is updated by the use case; we only update UI state here.
        streamingJob = applicationScope.launch(ioDispatcher) {
            try {
                // Build web search config from current preferences
                val webSearchConfig = if (currentWebSearchEnabled) {
                    val apiKey = if (currentWebSearchProvider == WebSearchProvider.EXA) {
                        encryptedPreferences.getApiKey("web_search_exa") ?: ""
                    } else ""
                    WebSearchConfig(
                        isEnabled = true,
                        provider = currentWebSearchProvider,
                        apiKey = apiKey,
                        maxResults = currentWebSearchMaxResults,
                        searxngBaseUrl = currentSearxngBaseUrl
                    )
                } else {
                    WebSearchConfig()
                }

                sendMessageUseCase(
                    conversationId = activeConversationId.value,
                    userContent = finalContent,
                    attachments = attachments,
                    systemPrompt = currentSystemPrompt,
                    reasoningEffort = currentReasoningEffort,
                    webSearchConfig = webSearchConfig,
                    forceImageGeneration = forceImageGeneration
                ).collect { state ->
                    updateStreamingState(state)
                    // Notify when completed and app is in background
                    if (state is StreamingState.Completed) {
                        appNotificationManager.notifyChatResponseComplete(
                            conversationId = activeConversationId.value,
                            preview = state.finalContent
                        )
                    }
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
        // Clear regenerating ID when streaming completes or errors
        if (state is StreamingState.Completed || state is StreamingState.Error || state is StreamingState.Cancelled) {
            regeneratingMessageId = null
            lastStreamingUiUpdateMs = 0L
            lastStreamingUiMessageId = null
        }

        val stateForUi = when (state) {
            is StreamingState.Streaming -> {
                val now = System.currentTimeMillis()
                val sameMessage = lastStreamingUiMessageId == state.messageId
                if (sameMessage && now - lastStreamingUiUpdateMs < STREAMING_UI_UPDATE_INTERVAL_MS) {
                    return
                }
                lastStreamingUiUpdateMs = now
                lastStreamingUiMessageId = state.messageId
                // Message text is rendered from the throttled Room stream; keeping the
                // large accumulated string here just forces extra StateFlow equality work.
                StreamingState.Streaming(content = "", thinkingContent = null, messageId = state.messageId)
            }
            is StreamingState.Completed -> StreamingState.Idle
            is StreamingState.Error -> StreamingState.Error(
                error = state.error,
                partialContent = null,
                messageId = state.messageId
            )
            is StreamingState.Cancelled -> StreamingState.Cancelled(
                partialContent = null,
                messageId = state.messageId
            )
            else -> state
        }

        _uiState.update { currentState ->
            if (currentState is ChatUiState.Success) {
                currentState.copy(streamingState = stateForUi)
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
        val currentState = _uiState.value as? ChatUiState.Success
        streamingJob?.cancel()
        streamingJob = null
        fusionJob?.cancel()
        fusionJob = null
        sendMessageUseCase.cancel()
        regenerateResponseUseCase.cancel()
        regeneratingMessageId = null

        if (currentState != null) {
            val streamingMessages = currentState.messages
                .map { it.message }
                .filter { it.isStreaming }

            val updatedMessages = currentState.messages.mapNotNull { item ->
                val message = item.message
                when {
                    !message.isStreaming -> item
                    shouldRemoveCancelledPlaceholder(message) -> null
                    else -> item.copy(message = message.copy(isStreaming = false))
                }
            }

            _uiState.value = currentState.copy(
                messages = updatedMessages,
                streamingState = StreamingState.Idle,
                isFusionRunning = false,
                fusionResult = null
            )

            if (streamingMessages.isNotEmpty()) {
                viewModelScope.launch(ioDispatcher) {
                    streamingMessages.forEach { message ->
                        runCatching {
                            if (shouldRemoveCancelledPlaceholder(message)) {
                                conversationRepository.deleteMessage(message.id)
                            } else {
                                conversationRepository.setMessageStreaming(message.id, false)
                            }
                        }
                    }
                }
            }
            return
        }

        val currentStateFallback = _uiState.value
        if (currentStateFallback is ChatUiState.Success) {
            _uiState.value = currentStateFallback.copy(
                streamingState = StreamingState.Idle,
                isFusionRunning = false,
                fusionResult = null
            )
        }
    }

    private fun shouldRemoveCancelledPlaceholder(message: Message): Boolean {
        return message.role == MessageRole.ASSISTANT &&
            message.content.isBlank() &&
            message.thinkingContent.isNullOrBlank() &&
            !message.hasAttachments
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
            val conversation = getConversationsUseCase.getConversation(activeConversationId.value)
            if (conversation?.isEphemeral == true) {
                cancelStreaming()
                runCatching {
                    conversationRepository.deleteConversation(conversation.parentId ?: conversation.id)
                }
            }
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
                val conversation = getConversationsUseCase.getConversation(activeConversationId.value)
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
                getConversationsUseCase.updateConversationModel(activeConversationId.value, model.id)

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
     *
     * @param overrideModelName Optional model name to use instead of conversation default
     */
    fun regenerateResponse(overrideModelName: String? = null) {
        val currentState = _uiState.value
        if (currentState !is ChatUiState.Success) return
        if (currentState.isStreaming) return

        // Immediately remove the last assistant message from the UI to prevent
        // ghosting while the database deletion propagates via Flow.
        // Without this, the old reply stays visible for a few frames alongside
        // the streaming indicator, creating a "previous reply in background" artifact.
        val filteredMessages = currentState.messages.toMutableList().also { list ->
            val lastAssistantIdx = list.indexOfLast { it.message.role == MessageRole.ASSISTANT }
            if (lastAssistantIdx >= 0) {
                regeneratingMessageId = list[lastAssistantIdx].message.id
                list.removeAt(lastAssistantIdx)
            }
        }

        _uiState.value = currentState.copy(
            streamingState = StreamingState.Starting,
            messages = filteredMessages
        )

        // Use applicationScope so streaming survives navigation away / ViewModel destruction.
        streamingJob = applicationScope.launch(ioDispatcher) {
            try {
                regenerateResponseUseCase(
                    conversationId = activeConversationId.value,
                    systemPrompt = currentSystemPrompt,
                    reasoningEffort = currentReasoningEffort,
                    overrideModelName = overrideModelName
                ).collect { state ->
                    updateStreamingState(state)
                    // Notify when completed and app is in background
                    if (state is StreamingState.Completed) {
                        appNotificationManager.notifyChatResponseComplete(
                            conversationId = activeConversationId.value,
                            preview = state.finalContent
                        )
                    }
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
                    sourceConversationId = activeConversationId.value,
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

    // ========== Edit & Resend ==========

    /**
     * Starts editing a user message.
     */
    fun startEditingMessage(messageId: String) {
        val currentState = _uiState.value
        if (currentState !is ChatUiState.Success) return
        val message = currentState.messages.find { it.message.id == messageId }?.message ?: return
        _uiState.value = currentState.copy(
            editingMessageId = messageId,
            editingText = message.content
        )
    }

    /**
     * Updates the editing text.
     */
    fun updateEditingText(text: String) {
        val currentState = _uiState.value
        if (currentState is ChatUiState.Success) {
            _uiState.value = currentState.copy(editingText = text)
        }
    }

    /**
     * Cancels the current editing operation.
     */
    fun cancelEditing() {
        val currentState = _uiState.value
        if (currentState is ChatUiState.Success) {
            _uiState.value = currentState.copy(editingMessageId = null, editingText = "")
        }
    }

    /**
     * Submits the edited message by creating a branch.
     */
    fun submitEditedMessage() {
        val currentState = _uiState.value
        if (currentState !is ChatUiState.Success) return
        val editingMessageId = currentState.editingMessageId ?: return
        val editedText = currentState.editingText.trim()
        if (editedText.isEmpty()) return

        val messages = currentState.messages.map { it.message }
        val editingIndex = messages.indexOfFirst { it.id == editingMessageId }
        if (editingIndex == -1) return

        if (editingIndex == 0) {
            viewModelScope.launch {
                _events.emit(ChatEvent.ShowSnackbar(
                    message = "Cannot edit the first message"
                ))
            }
            return
        }

        val branchFromId = messages[editingIndex - 1].id

        // Clear editing state
        _uiState.value = currentState.copy(editingMessageId = null, editingText = "")

        viewModelScope.launch {
            try {
                val newConversationId = branchConversationUseCase(
                    sourceConversationId = activeConversationId.value,
                    upToMessageId = branchFromId
                )

                val editedMessage = Message(
                    id = java.util.UUID.randomUUID().toString(),
                    conversationId = newConversationId,
                    role = MessageRole.USER,
                    content = editedText
                )
                conversationRepository.addMessage(editedMessage)

                _events.emit(ChatEvent.NavigateToBranch(
                    conversationId = newConversationId,
                    autoSend = true
                ))
            } catch (e: Exception) {
                _events.emit(ChatEvent.ShowSnackbar(
                    message = e.message ?: "Failed to submit edited message"
                ))
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
                val result = exportConversationUseCase(activeConversationId.value, format)
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
     * Loads sibling branch info for redo-with-model navigation.
     * Observes sibling branches and computes SiblingInfo for the last assistant message.
     * Re-evaluates when activeConversationId changes (inline sibling switching).
     */
    private fun loadSiblings() {
        viewModelScope.launch {
            try {
                activeConversationId.collectLatest { currentId ->
                    val conversation = getConversationsUseCase.getConversation(currentId)
                    if (conversation == null) {
                        siblingInfo = null
                        refreshSiblingInfoInState()
                        return@collectLatest
                    }

                    // Only load siblings for conversations that have a branchSourceMessageId,
                    // or for root conversations that might be parents of sibling branches
                    val parentId: String
                    val branchSourceMsgId: String

                    when {
                        conversation.branchSourceMessageId != null -> {
                            // This is a branch with sibling tracking
                            parentId = conversation.parentId ?: run {
                                siblingInfo = null
                                refreshSiblingInfoInState()
                                return@collectLatest
                            }
                            branchSourceMsgId = conversation.branchSourceMessageId
                        }
                        conversation.parentId == null -> {
                            // This is a root conversation — check if any branches have it as parent
                            parentId = conversation.id
                            val messages = getConversationsUseCase.getMessages(currentId)
                            val lastUserMessage = messages.lastOrNull { it.role == MessageRole.USER }
                            if (lastUserMessage == null) {
                                siblingInfo = null
                                refreshSiblingInfoInState()
                                return@collectLatest
                            }
                            branchSourceMsgId = lastUserMessage.id
                        }
                        else -> {
                            // Branch without branchSourceMessageId (legacy branch), no sibling nav
                            siblingInfo = null
                            refreshSiblingInfoInState()
                            return@collectLatest
                        }
                    }

                    getConversationsUseCase.observeSiblingBranches(parentId, branchSourceMsgId)
                        .collect { siblings ->
                            if (siblings.isEmpty()) {
                                siblingInfo = null
                                refreshSiblingInfoInState()
                                return@collect
                            }

                            // Build sibling list: root/parent is always index 0
                            val entries = mutableListOf<SiblingEntry>()

                            if (conversation.parentId == null) {
                                // Root conversation: add itself as the first entry
                                val rootMessages = getConversationsUseCase.getMessages(currentId)
                                val rootLastAssistant = rootMessages.lastOrNull { it.role == MessageRole.ASSISTANT }
                                entries.add(SiblingEntry(
                                    conversationId = conversation.id,
                                    modelName = rootLastAssistant?.modelName ?: conversation.modelName
                                ))
                            } else {
                                // Branch: add the parent as the first entry
                                val parent = getConversationsUseCase.getConversation(parentId)
                                if (parent != null) {
                                    val parentMessages = getConversationsUseCase.getMessages(parentId)
                                    val parentLastAssistant = parentMessages.lastOrNull { it.role == MessageRole.ASSISTANT }
                                    entries.add(SiblingEntry(
                                        conversationId = parent.id,
                                        modelName = parentLastAssistant?.modelName ?: parent.modelName
                                    ))
                                }
                            }

                            // Add all sibling branches (use response model name, not conversation model)
                            for (sibling in siblings) {
                                val siblingMessages = getConversationsUseCase.getMessages(sibling.id)
                                val siblingLastAssistant = siblingMessages.lastOrNull { it.role == MessageRole.ASSISTANT }
                                entries.add(SiblingEntry(
                                    conversationId = sibling.id,
                                    modelName = siblingLastAssistant?.modelName ?: sibling.modelName
                                ))
                            }

                            val totalCount = entries.size
                            if (totalCount <= 1) {
                                siblingInfo = null
                                refreshSiblingInfoInState()
                                return@collect
                            }

                            val currentIndex = entries.indexOfFirst { it.conversationId == currentId }
                            if (currentIndex == -1) {
                                siblingInfo = null
                                refreshSiblingInfoInState()
                                return@collect
                            }

                            siblingInfo = SiblingInfo(
                                currentIndex = currentIndex,
                                totalCount = totalCount,
                                siblings = entries
                            )
                            refreshSiblingInfoInState()
                        }
                }
            } catch (_: Exception) {
                // Silently fail — sibling navigation is optional
            }
        }
    }

    /**
     * Refreshes siblingInfo on the last assistant message in current UI state.
     */
    private fun refreshSiblingInfoInState() {
        val currentState = _uiState.value
        if (currentState is ChatUiState.Success) {
            val updatedMessages = currentState.messages.map { item ->
                if (item.isLastAssistantMessage) {
                    item.copy(siblingInfo = siblingInfo)
                } else {
                    item
                }
            }
            _uiState.value = currentState.copy(messages = updatedMessages)
        }
    }

    /**
     * Shows the redo model picker for the given assistant message.
     */
    fun showRedoModelPicker(messageId: String) {
        val currentState = _uiState.value
        if (currentState !is ChatUiState.Success) return
        if (currentState.isStreaming) return

        _uiState.value = currentState.copy(
            showRedoModelPicker = true,
            redoTargetMessageId = messageId
        )

        // Ensure models are loaded
        if (currentState.availableModels.isEmpty()) {
            loadModels()
        }
    }

    /**
     * Hides the redo model picker.
     */
    fun hideRedoModelPicker() {
        val currentState = _uiState.value
        if (currentState is ChatUiState.Success) {
            _uiState.value = currentState.copy(
                showRedoModelPicker = false,
                redoTargetMessageId = null
            )
        }
    }

    /**
     * Redo the response with a different model.
     * Creates a branch and navigates to it with autoSend=true.
     */
    fun redoWithModel(model: AiModel) {
        val currentState = _uiState.value
        if (currentState !is ChatUiState.Success) return
        if (currentState.isStreaming) return

        val targetMessageId = currentState.redoTargetMessageId ?: return

        // Hide picker immediately
        _uiState.value = currentState.copy(
            showRedoModelPicker = false,
            redoTargetMessageId = null
        )

        viewModelScope.launch {
            try {
                val newConversationId = redoWithModelUseCase(
                    conversationId = activeConversationId.value,
                    targetAssistantMessageId = targetMessageId,
                    newModelName = model.id
                )
                _events.emit(ChatEvent.NavigateToBranch(
                    conversationId = newConversationId,
                    autoSend = true,
                    overrideModel = model.id
                ))
            } catch (e: Exception) {
                _events.emit(
                    ChatEvent.ShowSnackbar(
                        message = e.message ?: "Failed to redo with model"
                    )
                )
            }
        }
    }

    /**
     * Navigates to a sibling conversation inline (no page navigation).
     * Switches the active conversation and triggers a slide animation.
     *
     * @param targetConversationId The conversation ID to switch to
     * @param direction -1 for previous (slide from left), 1 for next (slide from right)
     */
    fun navigateToSibling(targetConversationId: String, direction: Int) {
        _uiState.update { currentState ->
            if (currentState is ChatUiState.Success) {
                currentState.copy(slideDirection = direction)
            } else currentState
        }
        activeConversationId.value = targetConversationId
    }

    // ========== Bookmark Operations ==========

    /**
     * Loads bookmark states for all messages in the current conversation.
     * Observes bookmark changes reactively to keep the UI in sync.
     */
    private fun loadBookmarkStates() {
        viewModelScope.launch(ioDispatcher) {
            activeConversationId.collectLatest { currentId ->
                try {
                    val messages = getConversationsUseCase.getMessages(currentId)
                    val bookmarkedIds = mutableSetOf<String>()
                    for (message in messages) {
                        if (manageBookmarksUseCase.isMessageBookmarked(message.id)) {
                            bookmarkedIds.add(message.id)
                        }
                    }
                    _uiState.update { currentState ->
                        if (currentState is ChatUiState.Success) {
                            currentState.copy(bookmarkedMessageIds = bookmarkedIds)
                        } else currentState
                    }
                } catch (_: Exception) {
                    // Bookmark state loading is best-effort
                }
            }
        }
    }

    /**
     * Toggles the bookmark state for a message (quick bookmark with default category).
     */
    fun toggleBookmark(messageId: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val isNowBookmarked = manageBookmarksUseCase.toggleBookmark(
                    messageId = messageId,
                    conversationId = activeConversationId.value
                )
                _uiState.update { currentState ->
                    if (currentState is ChatUiState.Success) {
                        val updatedIds = if (isNowBookmarked) {
                            currentState.bookmarkedMessageIds + messageId
                        } else {
                            currentState.bookmarkedMessageIds - messageId
                        }
                        currentState.copy(bookmarkedMessageIds = updatedIds)
                    } else currentState
                }
                _events.emit(
                    ChatEvent.ShowSnackbar(
                        message = if (isNowBookmarked) "Message bookmarked" else "Bookmark removed"
                    )
                )
            } catch (e: Exception) {
                _events.emit(
                    ChatEvent.ShowSnackbar(
                        message = "Failed to toggle bookmark: ${e.message}"
                    )
                )
            }
        }
    }

    /**
     * Shows the add bookmark bottom sheet for a specific message.
     */
    fun showAddBookmarkSheet(messageId: String) {
        val currentState = _uiState.value
        if (currentState is ChatUiState.Success) {
            _uiState.value = currentState.copy(
                showAddBookmarkSheet = true,
                bookmarkTargetMessageId = messageId
            )
        }
    }

    /**
     * Hides the add bookmark bottom sheet.
     */
    fun hideAddBookmarkSheet() {
        val currentState = _uiState.value
        if (currentState is ChatUiState.Success) {
            _uiState.value = currentState.copy(
                showAddBookmarkSheet = false,
                bookmarkTargetMessageId = null
            )
        }
    }

    /**
     * Adds a bookmark with full metadata (category, tags, note).
     * Called from the AddBookmarkSheet.
     */
    fun addBookmarkWithDetails(
        category: BookmarkCategory,
        tags: List<String>,
        note: String?
    ) {
        val currentState = _uiState.value
        if (currentState !is ChatUiState.Success) return
        val messageId = currentState.bookmarkTargetMessageId ?: return

        // Hide sheet immediately
        _uiState.value = currentState.copy(
            showAddBookmarkSheet = false,
            bookmarkTargetMessageId = null
        )

        viewModelScope.launch(ioDispatcher) {
            try {
                // Remove existing bookmark first if present
                manageBookmarksUseCase.removeBookmarkByMessageId(messageId)

                // Add new bookmark with details
                val isNowBookmarked = manageBookmarksUseCase.toggleBookmark(
                    messageId = messageId,
                    conversationId = activeConversationId.value,
                    category = category,
                    tags = tags,
                    note = note
                )
                _uiState.update { state ->
                    if (state is ChatUiState.Success) {
                        state.copy(
                            bookmarkedMessageIds = state.bookmarkedMessageIds + messageId
                        )
                    } else state
                }
                _events.emit(ChatEvent.ShowSnackbar(message = "Message bookmarked"))
            } catch (e: Exception) {
                _events.emit(
                    ChatEvent.ShowSnackbar(
                        message = "Failed to add bookmark: ${e.message}"
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
        // Streaming now runs on applicationScope and will complete in the background.
        // We only cancel fusion jobs (which are less critical) and UI cleanup.
        fusionJob?.cancel()
        // NOTE: We intentionally do NOT cancel streamingJob here — it runs on
        // applicationScope so it can complete even after the user navigates away.
        // The use case handles DB cleanup (setMessageStreaming(false)) on completion,
        // error, or cancellation within its own flow.
    }

    // ========== Fusion Methods ==========

    /**
     * Toggles fusion mode on/off.
     * When enabling, shows the model selector. When disabling, clears the config.
     */
    fun toggleFusionMode() {
        val currentState = _uiState.value
        if (currentState !is ChatUiState.Success) return

        val isCurrentlyEnabled = currentState.fusionConfig.isEnabled

        if (isCurrentlyEnabled) {
            // Disable fusion
            _uiState.value = currentState.copy(
                fusionConfig = FusionConfig(),
                showFusionModelSelector = false,
                fusionResult = null
            )
        } else {
            // Show the model selector to configure fusion
            _uiState.value = currentState.copy(
                showFusionModelSelector = true
            )
            // Ensure models are loaded
            if (currentState.availableModels.isEmpty()) {
                loadModels()
            }
        }
    }

    /**
     * Shows the fusion model selector bottom sheet.
     */
    fun showFusionModelSelector() {
        val currentState = _uiState.value
        if (currentState !is ChatUiState.Success) return

        _uiState.value = currentState.copy(showFusionModelSelector = true)

        if (currentState.availableModels.isEmpty()) {
            loadModels()
        }
    }

    /**
     * Hides the fusion model selector bottom sheet.
     */
    fun hideFusionModelSelector() {
        val currentState = _uiState.value
        if (currentState is ChatUiState.Success) {
            _uiState.value = currentState.copy(showFusionModelSelector = false)
        }
    }

    /**
     * Toggles a model's selection for fusion.
     */
    fun toggleFusionModel(model: AiModel) {
        val currentState = _uiState.value
        if (currentState !is ChatUiState.Success) return

        val currentConfig = currentState.fusionConfig
        val currentSelections = currentConfig.selectedModels.toMutableList()
        val existing = currentSelections.find { it.modelName == model.id }

        if (existing != null) {
            currentSelections.remove(existing)
        } else if (currentSelections.size < 3) {
            // Look up provider for this model
            val conversation = null // We'll use the conversation's provider
            currentSelections.add(
                FusionModelSelection(
                    modelName = model.id,
                    providerId = model.providerId
                )
            )
        }

        _uiState.value = currentState.copy(
            fusionConfig = currentConfig.copy(selectedModels = currentSelections)
        )
    }

    /**
     * Sets the judge model for fusion synthesis.
     */
    fun setFusionJudgeModel(model: AiModel) {
        val currentState = _uiState.value
        if (currentState !is ChatUiState.Success) return

        _uiState.value = currentState.copy(
            fusionConfig = currentState.fusionConfig.copy(
                judgeModel = FusionModelSelection(
                    modelName = model.id,
                    providerId = model.providerId
                )
            )
        )
    }

    /**
     * Confirms the fusion configuration and enables fusion mode.
     */
    fun confirmFusionConfig() {
        val currentState = _uiState.value
        if (currentState !is ChatUiState.Success) return

        val config = currentState.fusionConfig
        if (config.selectedModels.size < 2 || config.judgeModel == null) return

        _uiState.value = currentState.copy(
            fusionConfig = config.copy(isEnabled = true),
            showFusionModelSelector = false
        )
    }

    /**
     * Sends the current message using the fusion pipeline.
     * Queries multiple models in parallel, then synthesizes via judge model.
     */
    private fun sendFusionMessage() {
        val currentState = _uiState.value
        if (currentState !is ChatUiState.Success) return
        if (!currentState.canSend) return

        val messageContent = currentState.inputText.trim()

        // Clear input immediately
        _uiState.value = currentState.copy(
            inputText = "",
            pendingAttachments = emptyList(),
            streamingState = StreamingState.Starting,
            isFusionRunning = true,
            fusionResult = null
        )

        fusionJob = viewModelScope.launch(ioDispatcher) {
            try {
                runFusionUseCase(
                    conversationId = activeConversationId.value,
                    userContent = messageContent,
                    fusionConfig = currentState.fusionConfig,
                    systemPrompt = currentSystemPrompt,
                    reasoningEffort = currentReasoningEffort
                ).collect { result ->
                    _uiState.update { state ->
                        if (state is ChatUiState.Success) {
                            state.copy(
                                fusionResult = result,
                                streamingState = if (result.synthesizedResponse != null && !result.isSynthesizing) {
                                    StreamingState.Idle
                                } else {
                                    StreamingState.Starting
                                },
                                isFusionRunning = result.isSynthesizing || result.synthesizedResponse == null
                            )
                        } else state
                    }
                }

                // Fusion complete
                _uiState.update { state ->
                    if (state is ChatUiState.Success) {
                        state.copy(
                            streamingState = StreamingState.Idle,
                            isFusionRunning = false
                        )
                    } else state
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    if (state is ChatUiState.Success) {
                        state.copy(
                            streamingState = StreamingState.Error(
                                error = e,
                                partialContent = null,
                                messageId = null
                            ),
                            isFusionRunning = false
                        )
                    } else state
                }
                _events.emit(
                    ChatEvent.ShowSnackbar(
                        message = e.message ?: "Fusion failed"
                    )
                )
            }
        }
    }
}
