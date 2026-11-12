package com.materialchat.assistant.ui

import androidx.lifecycle.ViewModel
import com.materialchat.assistant.actions.ClockCommand
import com.materialchat.assistant.actions.ClockCommandParser
import com.materialchat.assistant.actions.DeviceActionHandler
import com.materialchat.assistant.actions.DeviceActionParser
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
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.repository.ProviderRepository
import com.materialchat.domain.usecase.CreateConversationUseCase
import com.materialchat.domain.usecase.GenerateConversationTitleUseCase
import com.materialchat.assistant.voice.AssistantSpeechRuntime
import com.materialchat.assistant.voice.AssistantSpeechQueue
import com.materialchat.assistant.voice.StreamingSpeechChunker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
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
    val appPreferences: AppPreferences,
    private val createConversationUseCase: CreateConversationUseCase,
    private val conversationRepository: ConversationRepository,
    private val generateConversationTitleUseCase: GenerateConversationTitleUseCase,
    private val applicationScope: CoroutineScope,
    private val speechRuntime: AssistantSpeechRuntime,
    private val deviceActionHandler: DeviceActionHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _amplitudeData = MutableStateFlow(AudioAmplitudeData.Empty)
    val amplitudeData: StateFlow<AudioAmplitudeData> = _amplitudeData.asStateFlow()

    private val _events = MutableSharedFlow<AssistantEvent>()
    val events: SharedFlow<AssistantEvent> = _events.asSharedFlow()

    /** Trigger that increments each time overlay is shown - used to reset animations */
    private val _showTrigger = MutableStateFlow(0)
    val showTrigger: StateFlow<Int> = _showTrigger.asStateFlow()

    private val _pendingAttachments = MutableStateFlow<List<Attachment>>(emptyList())
    val pendingAttachments: StateFlow<List<Attachment>> = _pendingAttachments.asStateFlow()

    /** Message history for display in the overlay */
    private val _messages = MutableStateFlow<List<AssistantMessage>>(emptyList())
    val messages: StateFlow<List<AssistantMessage>> = _messages.asStateFlow()

    private var streamingJob: Job? = null
    private var voiceInputJob: Job? = null
    private var ttsJob: Job? = null
    private var turn = 0L
    private var recognition = 0L
    private var overlayVisible = false
    private var autoListenConsumed = -1
    private var speechQueue: AssistantSpeechQueue? = null
    private var speechStopped = false

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
                if (!enabled) stopSpeaking()
            }
        }
    }

    /**
     * Called when the overlay is shown. Checks for session timeout and resets if needed.
     * Also triggers animation reset in the UI.
     */
    fun onOverlayShown() {
        if (overlayVisible) return
        overlayVisible = true
        val now = System.currentTimeMillis()
        val timeSinceLastActivity = now - lastActivityTimestamp

        // Auto-reset if more than 1 minute has passed
        if (timeSinceLastActivity > SESSION_TIMEOUT_MS && _messages.value.isNotEmpty()) {
            startNewChat()
        }

        // Update last activity
        lastActivityTimestamp = now

        // Increment show trigger to reset animations in UI
        _showTrigger.value++
    }

    /** Recheck retained invocation ownership after preferences suspend. Main-thread only. */
    suspend fun requestAutoListen(invocation: Int, hasPermission: Boolean) {
        if (!hasPermission || !overlayVisible || invocation != _showTrigger.value ||
            autoListenConsumed == invocation) return
        val enabled = appPreferences.assistantVoiceEnabled.first()
        if (!enabled || !overlayVisible || invocation != _showTrigger.value ||
            autoListenConsumed == invocation) return
        autoListenConsumed = invocation
        startVoiceInput()
    }

    fun onOverlayHidden() {
        overlayVisible = false
        clearConversation()
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
        val previousTurn = streamingJob
        cancelStreaming()
        val token = ++recognition
        if (!speechRecognitionManager.isAvailable()) {
            _voiceState.value = VoiceState.Error("Speech recognition not available")
            return
        }

        voiceInputJob?.cancel()
        voiceInputJob = viewModelScope.launch {
            previousTurn?.join()
            if (token != recognition) return@launch
            speechRecognitionManager.startListening().collect { result ->
                if (token != recognition) return@collect
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

    /** Keep the collector alive so Android's final result is submitted normally. */
    fun finishVoiceInput() {
        speechRecognitionManager.stopListening()
    }

    fun stopVoiceInput() {
        recognition++
        voiceInputJob?.cancel()
        speechRecognitionManager.cancel()
        _voiceState.value = VoiceState.Idle
    }

    /**
     * Send a text query to the AI.
     */
    fun sendQuery(query: String = _uiState.value.textInput) {
        if (query.isBlank()) return

        val previousTurn = streamingJob
        cancelStreaming()
        val token = turn
        speechStopped = false
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
                isStreaming = false,
                error = null
            )
        }
        _pendingAttachments.value = emptyList()
        _voiceState.value = VoiceState.Thinking(trimmedQuery)
        lastActivityTimestamp = System.currentTimeMillis()

        streamingJob = viewModelScope.launch {
            try {
                previousTurn?.join()
                currentCoroutineContext().ensureActive()
                if (token != turn) return@launch
                val clockCommand = if (attachments.isEmpty()) ClockCommandParser.parse(trimmedQuery) else null
                if (clockCommand != null) {
                    _uiState.update { it.copy(isLoading = false) }
                    _voiceState.value = VoiceState.Idle
                    _events.emit(AssistantEvent.OpenClock(clockCommand, token))
                    return@launch
                }
                // Device actions are performed here and reported with their real outcome, so
                // the assistant never claims a change it did not make. This runs before any
                // provider call: no model can control the device.
                val deviceAction = if (attachments.isEmpty()) DeviceActionParser.parse(trimmedQuery) else null
                if (deviceAction != null) {
                    val result = deviceActionHandler.perform(deviceAction)
                    _uiState.update { it.copy(isLoading = false, isStreaming = false, response = result) }
                    finalizeAssistantResponse(result)
                    _voiceState.value = VoiceState.Complete(result, trimmedQuery)
                    speakActionResult(result, token)
                    return@launch
                }
                coroutineScope {
                    val settings = appPreferences.assistantVoiceSettings.first().normalized()
                    val chunker = StreamingSpeechChunker()
                    val queue = AssistantSpeechQueue(this) { chunk ->
                        if (token == turn && !speechStopped && _uiState.value.isTtsEnabled) {
                            speechRuntime.speak(chunk, settings) { event ->
                                if (token == turn && !speechStopped) {
                                    when (event) {
                                        TtsEvent.Started -> {
                                            _uiState.update { it.copy(isSpeaking = true) }
                                            _voiceState.value = VoiceState.Speaking(chunk)
                                        }
                                        else -> {
                                            _uiState.update { it.copy(isSpeaking = false) }
                                            if (!_uiState.value.isStreaming && !_uiState.value.isLoading) {
                                                _voiceState.value = VoiceState.Complete(_uiState.value.response, trimmedQuery)
                                            }
                                            if (event is TtsEvent.Error) {
                                                speechStopped = true
                                                _uiState.update { it.copy(error = event.message) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    speechQueue = queue
                    ttsJob = queue.job
                    // Voice overrides are independent of normal chat preferences.
                    val provider = settings.providerId?.let { providerRepository.getProvider(it) }
                        ?: if (settings.providerId == null) providerRepository.getActiveProvider() else null
                    if (provider == null) {
                        handleError("No active AI provider. Please configure one in Settings.")
                        queue.finish()
                        return@coroutineScope
                    }

                    val model = settings.model ?: provider.defaultModel
                    if (currentConversationId == null) {
                        val created = createConversationUseCase.withProviderAndModel(provider.id, model)
                        currentCoroutineContext().ensureActive()
                        if (token != turn) return@coroutineScope
                        currentConversationId = created
                    }
                    val conversationId = currentConversationId!!
                    conversationRepository.updateConversationProviderAndModel(conversationId, provider.id, model)
                    val systemPrompt = appPreferences.systemPrompt.first()
                    // Append the capability notice so the model cannot claim device actions it
                    // cannot perform; the user's own prompt still applies first.
                    val effectiveSystemPrompt = if (systemPrompt.isBlank()) {
                        ASSISTANT_CAPABILITY_NOTICE
                    } else {
                        systemPrompt + "\n\n" + ASSISTANT_CAPABILITY_NOTICE
                    }
                    val reasoningEffort = settings.reasoningEffort ?: appPreferences.reasoningEffort.first()

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
                        model = model,
                        reasoningEffort = reasoningEffort,
                        systemPrompt = effectiveSystemPrompt,
                        ownerId = ASSISTANT_STREAM_OWNER
                    ).collect { state ->
                        currentCoroutineContext().ensureActive()
                        if (token != turn) return@collect
                        when (state) {
                            is StreamingState.Starting -> {
                                _uiState.update { it.copy(isLoading = true) }
                            }
                            is StreamingState.Streaming -> {
                                if (!_uiState.value.isSpeaking) _voiceState.value = VoiceState.Responding(
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
                                if (!speechStopped && _uiState.value.isTtsEnabled)
                                    chunker.update(state.content).forEach { queue.enqueue(it) }
                            }
                            is StreamingState.Completed -> {
                                if (!_uiState.value.isSpeaking) _voiceState.value = VoiceState.Complete(
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
                                if (!speechStopped && _uiState.value.isTtsEnabled)
                                    chunker.update(state.finalContent, isFinal = true).forEach { queue.enqueue(it) }
                                queue.finish()
                            }
                            is StreamingState.Error -> {
                                queue.cancel()
                                handleError(state.error.message ?: "An error occurred")
                            }
                            is StreamingState.Cancelled -> {
                                queue.cancel()
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
                    queue.finish()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (token == turn) handleError("Assistant request failed. Check provider and voice settings.")
            }
        }
    }

    /**
     * Cancel the current streaming request.
     */
    fun cancelStreaming() {
        turn++
        stopVoiceInput()
        streamingJob?.cancel()
        stopSpeaking()
        _messages.update { messages -> messages.map { it.copy(isStreaming = false) } }
        _uiState.update { it.copy(isLoading = false, isStreaming = false) }
        _voiceState.value = VoiceState.Idle
    }

    /** Stops speech for the rest of this turn without cancelling generation. */
    fun stopSpeaking() {
        speechStopped = true
        speechQueue?.cancel()
        speechQueue = null
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
     * Speak a device-action result so a voice-only turn still gets an audible answer.
     *
     * Unlike a generated answer this is a short fixed sentence, so it is spoken directly
     * rather than through the streaming chunker. Speech failures are contained: the result
     * has already been shown, and a speech problem must not turn it into an error.
     */
    private fun speakActionResult(result: String, token: Long) {
        if (!_uiState.value.isTtsEnabled) return
        viewModelScope.launch {
            try {
                val settings = appPreferences.assistantVoiceSettings.first().normalized()
                speechRuntime.speak(result, settings) { event ->
                    if (token == turn) {
                        when (event) {
                            TtsEvent.Started -> _uiState.update { it.copy(isSpeaking = true) }
                            is TtsEvent.Error -> {
                                _uiState.update { it.copy(isSpeaking = false, error = event.message) }
                            }
                            else -> _uiState.update { it.copy(isSpeaking = false) }
                        }
                    }
                }
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
                // Speech is optional here; the result is already on screen.
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
        } catch (cancelled: CancellationException) {
            throw cancelled
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
        overlayVisible = false
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

    /** Only reports a handoff, never that the clock saved anything. Ignore stale callbacks. */
    fun onClockHandoff(token: Long, error: String?) {
        if (token != turn) return
        if (error != null) {
            handleError(error)
            return
        }
        val response = "Opened the clock app. Review and confirm there; nothing is confirmed here."
        _uiState.update { it.copy(response = response, isLoading = false, isStreaming = false) }
        finalizeAssistantResponse(response)
        _voiceState.value = VoiceState.Complete(response, _uiState.value.userQuery)
    }

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
        cancelStreaming()
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
    data class OpenClock(val command: ClockCommand, val token: Long) : AssistantEvent()
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

/** Owner key isolating assistant streams from chat Stop requests. */
private const val ASSISTANT_STREAM_OWNER = "assistant"

/**
 * Tells the model what it genuinely cannot do.
 *
 * Local recognition catches the common phrasings, but anything it misses still reaches the
 * model, and an unconstrained model answers "I've set your alarm for 7am" without setting
 * anything. This instruction makes the honest answer the default for everything the app
 * cannot actually perform.
 */
private val ASSISTANT_CAPABILITY_NOTICE = """
You are a voice assistant inside an Android app. You cannot control the device. You cannot
set alarms, timers, reminders or calendar events, and you cannot change Wi-Fi, Bluetooth,
Do Not Disturb, brightness or other system settings. The app handles flashlight, volume and
mute itself, so those requests never reach you.

If the user asks for anything you cannot do, say plainly that you cannot do it and tell them
the shortest way to do it themselves. Never say or imply that you performed an action, and
never invent a confirmation. Keep answers short: they are spoken aloud.
""".trimIndent()
