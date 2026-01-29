package com.materialchat.assistant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.assistant.voice.AudioAmplitudeData
import com.materialchat.assistant.voice.SpeechRecognitionManager
import com.materialchat.assistant.voice.SpeechRecognitionResult
import com.materialchat.assistant.voice.TextToSpeechManager
import com.materialchat.assistant.voice.TtsEvent
import com.materialchat.assistant.voice.TtsInitResult
import com.materialchat.assistant.voice.VoiceState
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.model.Attachment
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.repository.ProviderRepository
import com.materialchat.domain.usecase.CreateConversationUseCase
import com.materialchat.domain.usecase.GenerateConversationTitleUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the MaterialChat Assistant overlay.
 *
 * Manages:
 * - Voice input/output state
 * - AI response streaming
 * - Persistent conversation handling (saved to database)
 * - UI state coordination
 * - Image attachments
 *
 * Note: This ViewModel is not annotated with @HiltViewModel because it's
 * instantiated manually in VoiceInteractionSession using Hilt EntryPoints.
 */
class AssistantViewModel(
    private val speechRecognitionManager: SpeechRecognitionManager,
    private val textToSpeechManager: TextToSpeechManager,
    private val chatRepository: ChatRepository,
    private val providerRepository: ProviderRepository,
    private val appPreferences: AppPreferences,
    private val createConversationUseCase: CreateConversationUseCase,
    private val conversationRepository: ConversationRepository,
    private val generateConversationTitleUseCase: GenerateConversationTitleUseCase,
    private val applicationScope: CoroutineScope
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _amplitudeData = MutableStateFlow(AudioAmplitudeData.Empty)
    val amplitudeData: StateFlow<AudioAmplitudeData> = _amplitudeData.asStateFlow()

    private val _events = MutableSharedFlow<AssistantEvent>()
    val events: SharedFlow<AssistantEvent> = _events.asSharedFlow()

    private val _pendingAttachments = MutableStateFlow<List<Attachment>>(emptyList())
    val pendingAttachments: StateFlow<List<Attachment>> = _pendingAttachments.asStateFlow()

    /** Message history for display in the overlay */
    private val _messages = MutableStateFlow<List<AssistantMessage>>(emptyList())
    val messages: StateFlow<List<AssistantMessage>> = _messages.asStateFlow()

    private var streamingJob: Job? = null
    private var voiceInputJob: Job? = null
    private var ttsJob: Job? = null

    /** The persistent conversation ID - created on first query */
    private var currentConversationId: String? = null

    /** Timestamp of last activity for auto-reset after 1 minute */
    private var lastActivityTimestamp: Long = System.currentTimeMillis()

    /** Timeout in milliseconds (1 minute) */
    private companion object {
        const val SESSION_TIMEOUT_MS = 60_000L
    }

    init {
        // Initialize TTS
        viewModelScope.launch {
            textToSpeechManager.initialize().collect { result ->
                when (result) {
                    is TtsInitResult.Success -> {
                        _uiState.update { it.copy(isTtsAvailable = true) }
                    }
                    is TtsInitResult.Error -> {
                        _uiState.update { it.copy(isTtsAvailable = false) }
                    }
                }
            }
        }

        // Observe amplitude data from speech recognition
        viewModelScope.launch {
            speechRecognitionManager.amplitudeData.collect { data ->
                _amplitudeData.value = data
            }
        }

        // Check assistant settings
        viewModelScope.launch {
            appPreferences.assistantVoiceEnabled.collect { enabled ->
                _uiState.update { it.copy(isVoiceInputEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            appPreferences.assistantTtsEnabled.collect { enabled ->
                _uiState.update { it.copy(isTtsEnabled = enabled) }
            }
        }
    }

    /**
     * Called when the overlay is shown. Checks for session timeout and resets if needed.
     */
    fun onOverlayShown() {
        val now = System.currentTimeMillis()
        val timeSinceLastActivity = now - lastActivityTimestamp

        // Auto-reset if more than 1 minute has passed
        if (timeSinceLastActivity > SESSION_TIMEOUT_MS && _messages.value.isNotEmpty()) {
            startNewChat()
        }

        // Update last activity
        lastActivityTimestamp = now
    }

    /**
     * Start a new chat session, clearing all messages.
     */
    fun startNewChat() {
        cancelStreaming()
        stopSpeaking()
        currentConversationId = null
        _pendingAttachments.value = emptyList()
        _messages.value = emptyList()
        _uiState.update {
            it.copy(
                textInput = "",
                userQuery = "",
                response = "",
                isLoading = false,
                isStreaming = false,
                error = null
            )
        }
        _voiceState.value = VoiceState.Idle
        lastActivityTimestamp = System.currentTimeMillis()
    }

    /**
     * Update the text input.
     */
    fun updateTextInput(text: String) {
        _uiState.update { it.copy(textInput = text) }
        lastActivityTimestamp = System.currentTimeMillis()
    }

    /**
     * Start voice input.
     */
    fun startVoiceInput() {
        if (!speechRecognitionManager.isAvailable()) {
            _voiceState.value = VoiceState.Error("Speech recognition not available")
            return
        }

        voiceInputJob?.cancel()
        voiceInputJob = viewModelScope.launch {
            speechRecognitionManager.startListening().collect { result ->
                when (result) {
                    is SpeechRecognitionResult.Ready -> {
                        _voiceState.value = VoiceState.Listening
                    }
                    is SpeechRecognitionResult.SpeechStarted -> {
                        // Keep listening state
                    }
                    is SpeechRecognitionResult.SpeechEnded -> {
                        _voiceState.value = VoiceState.Processing()
                    }
                    is SpeechRecognitionResult.PartialResult -> {
                        _voiceState.value = VoiceState.Processing(result.text)
                        _uiState.update { it.copy(textInput = result.text) }
                    }
                    is SpeechRecognitionResult.FinalResult -> {
                        _uiState.update { it.copy(textInput = result.text) }
                        sendQuery(result.text)
                    }
                    is SpeechRecognitionResult.Error -> {
                        _voiceState.value = VoiceState.Error(result.message, result.errorCode)
                    }
                }
            }
        }
    }

    /**
     * Stop voice input.
     */
    fun stopVoiceInput() {
        voiceInputJob?.cancel()
        speechRecognitionManager.stopListening()
        _voiceState.value = VoiceState.Idle
    }

    /**
     * Send a text query to the AI.
     */
    fun sendQuery(query: String = _uiState.value.textInput) {
        if (query.isBlank()) return

        val trimmedQuery = query.trim()
        val attachments = _pendingAttachments.value

        // Add user message to the message list
        _messages.update { it + AssistantMessage(role = MessageRole.USER, content = trimmedQuery) }

        _uiState.update {
            it.copy(
                textInput = "",
                userQuery = trimmedQuery,
                response = "",
                isLoading = true,
                isStreaming = false
            )
        }
        _pendingAttachments.value = emptyList()
        _voiceState.value = VoiceState.Thinking(trimmedQuery)
        lastActivityTimestamp = System.currentTimeMillis()

        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            try {
                // Get active provider
                val provider = providerRepository.getActiveProvider()
                if (provider == null) {
                    handleError("No active AI provider. Please configure one in Settings.")
                    return@launch
                }

                // Create persistent conversation on first query
                if (currentConversationId == null) {
                    try {
                        currentConversationId = createConversationUseCase()
                    } catch (e: Exception) {
                        handleError("Failed to create conversation: ${e.message}")
                        return@launch
                    }
                }

                val conversationId = currentConversationId!!

                // Get system prompt
                val systemPrompt = appPreferences.systemPrompt.first()
                val reasoningEffort = appPreferences.reasoningEffort.first()

                // Create and save user message
                val userMessage = Message(
                    conversationId = conversationId,
                    role = MessageRole.USER,
                    content = trimmedQuery,
                    attachments = attachments
                )
                conversationRepository.addMessage(userMessage)

                // Get all messages for this conversation
                val messages = conversationRepository.getMessages(conversationId)

                // Stream the response
                chatRepository.sendMessage(
                    provider = provider,
                    messages = messages,
                    model = provider.defaultModel,
                    reasoningEffort = reasoningEffort,
                    systemPrompt = systemPrompt
                ).collect { state ->
                    when (state) {
                        is StreamingState.Starting -> {
                            _uiState.update { it.copy(isLoading = true) }
                        }
                        is StreamingState.Streaming -> {
                            _voiceState.value = VoiceState.Responding(
                                content = state.content,
                                userQuery = trimmedQuery
                            )
                            _uiState.update {
                                it.copy(
                                    response = state.content,
                                    isLoading = false,
                                    isStreaming = true
                                )
                            }
                            // Update streaming response in message list
                            updateStreamingResponse(state.content)
                        }
                        is StreamingState.Completed -> {
                            _voiceState.value = VoiceState.Complete(
                                content = state.finalContent,
                                userQuery = trimmedQuery
                            )
                            _uiState.update {
                                it.copy(
                                    response = state.finalContent,
                                    isLoading = false,
                                    isStreaming = false
                                )
                            }

                            // Finalize assistant message in message list
                            finalizeAssistantResponse(state.finalContent)
                            lastActivityTimestamp = System.currentTimeMillis()

                            // Save assistant message to database
                            val assistantMessage = Message(
                                conversationId = conversationId,
                                role = MessageRole.ASSISTANT,
                                content = state.finalContent
                            )
                            conversationRepository.addMessage(assistantMessage)

                            // Trigger title generation (non-blocking)
                            applicationScope.launch {
                                generateTitleIfNeeded(
                                    conversationId = conversationId,
                                    userMessage = trimmedQuery,
                                    assistantResponse = state.finalContent
                                )
                            }

                            // Speak the response if TTS is enabled
                            speakResponseIfEnabled(state.finalContent)
                        }
                        is StreamingState.Error -> {
                            handleError(state.error.message ?: "An error occurred")
                        }
                        is StreamingState.Cancelled -> {
                            _uiState.update {
                                it.copy(
                                    response = state.partialContent ?: "",
                                    isLoading = false,
                                    isStreaming = false
                                )
                            }
                            _voiceState.value = VoiceState.Idle
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                handleError(e.message ?: "An error occurred")
            }
        }
    }

    /**
     * Cancel the current streaming request.
     */
    fun cancelStreaming() {
        streamingJob?.cancel()
        chatRepository.cancelStreaming()
        _uiState.update { it.copy(isLoading = false, isStreaming = false) }
        _voiceState.value = VoiceState.Idle
    }

    /**
     * Speak the response using TTS if enabled.
     */
    private fun speakResponseIfEnabled(text: String) {
        if (!_uiState.value.isTtsEnabled || !_uiState.value.isTtsAvailable) return

        ttsJob?.cancel()
        ttsJob = viewModelScope.launch {
            _voiceState.value = VoiceState.Speaking(text)
            textToSpeechManager.speak(text).collect { event ->
                when (event) {
                    is TtsEvent.Started -> {
                        _uiState.update { it.copy(isSpeaking = true) }
                    }
                    is TtsEvent.Completed, is TtsEvent.Stopped -> {
                        _uiState.update { it.copy(isSpeaking = false) }
                        _voiceState.value = VoiceState.Idle
                    }
                    is TtsEvent.Error -> {
                        _uiState.update { it.copy(isSpeaking = false) }
                        _voiceState.value = VoiceState.Idle
                    }
                }
            }
        }
    }

    /**
     * Stop TTS playback.
     */
    fun stopSpeaking() {
        ttsJob?.cancel()
        textToSpeechManager.stop()
        _uiState.update { it.copy(isSpeaking = false) }
        _voiceState.value = VoiceState.Idle
    }

    /**
     * Update the streaming response in the message list.
     * Adds or updates the assistant message as it streams.
     */
    private fun updateStreamingResponse(content: String) {
        _messages.update { messages ->
            val lastMessage = messages.lastOrNull()
            if (lastMessage?.role == MessageRole.ASSISTANT && lastMessage.isStreaming) {
                // Update existing streaming message
                messages.dropLast(1) + lastMessage.copy(content = content)
            } else {
                // Add new streaming assistant message
                messages + AssistantMessage(
                    role = MessageRole.ASSISTANT,
                    content = content,
                    isStreaming = true
                )
            }
        }
    }

    /**
     * Finalize the assistant response in the message list.
     */
    private fun finalizeAssistantResponse(content: String) {
        _messages.update { messages ->
            val lastMessage = messages.lastOrNull()
            if (lastMessage?.role == MessageRole.ASSISTANT) {
                messages.dropLast(1) + lastMessage.copy(content = content, isStreaming = false)
            } else {
                messages + AssistantMessage(role = MessageRole.ASSISTANT, content = content)
            }
        }
    }

    /**
     * Generate conversation title if it's still "New Chat".
     */
    private suspend fun generateTitleIfNeeded(
        conversationId: String,
        userMessage: String,
        assistantResponse: String
    ) {
        try {
            val conversation = conversationRepository.getConversation(conversationId)
            if (conversation?.title == "New Chat") {
                generateConversationTitleUseCase(
                    conversationId = conversationId,
                    userMessage = userMessage,
                    assistantResponse = assistantResponse
                )
            }
        } catch (_: Exception) {
            // Silent failure - title generation is non-critical
        }
    }

    /**
     * Handle errors.
     */
    private fun handleError(message: String) {
        _voiceState.value = VoiceState.Error(message)
        _uiState.update {
            it.copy(
                isLoading = false,
                isStreaming = false,
                error = message
            )
        }
    }

    /**
     * Clear the current conversation (used when dismissing).
     * Note: This clears state but does NOT reset messages - use startNewChat() for that.
     */
    fun clearConversation() {
        cancelStreaming()
        stopSpeaking()
        _uiState.update {
            it.copy(
                textInput = "",
                userQuery = "",
                response = "",
                isLoading = false,
                isStreaming = false,
                error = null
            )
        }
        _voiceState.value = VoiceState.Idle
    }

    /**
     * Request to open the conversation in the main app.
     */
    fun openInApp() {
        viewModelScope.launch {
            _events.emit(AssistantEvent.OpenInApp(conversationId = currentConversationId))
        }
    }

    /**
     * Add an image attachment.
     */
    fun addAttachment(attachment: Attachment) {
        _pendingAttachments.update { it + attachment }
    }

    /**
     * Remove an image attachment.
     */
    fun removeAttachment(attachment: Attachment) {
        _pendingAttachments.update { attachments ->
            attachments.filter { it.uri != attachment.uri }
        }
    }

    /**
     * Dismiss the assistant overlay.
     */
    fun dismiss() {
        clearConversation()
        viewModelScope.launch {
            _events.emit(AssistantEvent.Dismiss)
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognitionManager.release()
        textToSpeechManager.release()
    }
}

/**
 * UI state for the assistant overlay.
 */
data class AssistantUiState(
    val textInput: String = "",
    val userQuery: String = "",
    val response: String = "",
    val isLoading: Boolean = false,
    val isStreaming: Boolean = false,
    val isSpeaking: Boolean = false,
    val isVoiceInputEnabled: Boolean = true,
    val isTtsEnabled: Boolean = true,
    val isTtsAvailable: Boolean = false,
    val error: String? = null
)

/**
 * Events emitted by the assistant ViewModel.
 */
sealed class AssistantEvent {
    data class OpenInApp(val conversationId: String?) : AssistantEvent()
    data object Dismiss : AssistantEvent()
}

/**
 * A message in the assistant conversation for display purposes.
 */
data class AssistantMessage(
    val role: MessageRole,
    val content: String,
    val isStreaming: Boolean = false
)
