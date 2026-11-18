package com.materialchat.assistant.ui

import androidx.lifecycle.ViewModel
import com.materialchat.assistant.actions.ActionDirectives
import com.materialchat.assistant.actions.AssistantAction
import com.materialchat.assistant.actions.AssistantCommandParser
import com.materialchat.assistant.actions.ClockCommand
import com.materialchat.assistant.actions.DeviceActionHandler
import com.materialchat.assistant.actions.DeviceActionOutcome
import com.materialchat.assistant.attach.AssistantAttachMode
import com.materialchat.assistant.attach.AssistantAttachmentInbox
import com.materialchat.assistant.screen.ScreenContext
import com.materialchat.util.AttachmentLimits
import java.time.LocalDateTime
import androidx.lifecycle.viewModelScope
import com.materialchat.assistant.voice.AudioAmplitudeData
import com.materialchat.assistant.voice.RecognitionOptions
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
import com.materialchat.data.local.preferences.shouldContinueAssistantChat
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
    private val deviceActionHandler: DeviceActionHandler,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
    /** Where the attach activity leaves photos; the production instance unless a test supplies one. */
    private val attachmentInbox: AssistantAttachmentInbox = AssistantAttachmentInbox.shared,
    /** Deletes the file behind an attachment the user removed or that could not be taken. */
    private val discardAttachment: (Attachment) -> Unit = {}
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

    /**
     * The screen the assistant was opened over, as the platform delivered it for this
     * invocation (see [ScreenContext]); null until something arrives and after the session
     * hides. The session owns and releases the bitmap; this is a view of it.
     */
    private val _screenContext = MutableStateFlow<ScreenContext?>(null)
    val screenContext: StateFlow<ScreenContext?> = _screenContext.asStateFlow()

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

    /** The overlay left for the picker or camera; the typed question survives the hide. */
    private var attachInProgress = false

    /** The persistent conversation ID - created on first query */
    private var currentConversationId: String? = null

    /** Timestamp of last assistant activity; decides whether an invocation resumes the chat. */
    private var lastActivityTimestamp: Long = System.currentTimeMillis()

    /** Cached "continue previous assistant chat" window, in minutes (0 = always start fresh). */
    @Volatile private var continueChatMinutes: Int = 0

    init {
        viewModelScope.launch {
            appPreferences.assistantVoiceSettings.collect { continueChatMinutes = it.continueChatMinutes }
        }
        // Initialize TTS with the engine the user chose (null = system default).
        viewModelScope.launch {
            val configuredEngine = appPreferences.assistantVoiceSettings.first().ttsEnginePackage
            textToSpeechManager.setEnginePackage(configuredEngine)
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

        // Photos the attach activity delivered: first anything already waiting (the session
        // that started the picker may have been destroyed meanwhile, so this ViewModel is the
        // first to see them), then everything that arrives while this ViewModel lives.
        takeDeliveredAttachments()
        viewModelScope.launch {
            attachmentInbox.pending.collect { waiting -> if (waiting.isNotEmpty()) takeDeliveredAttachments() }
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
    fun onOverlayShown(resumeAfterAttach: Boolean = false) {
        if (overlayVisible) return
        overlayVisible = true
        val now = System.currentTimeMillis()

        if (resumeAfterAttach) {
            // Back from the picker or camera: the same turn continues with its typed text and
            // its photos, and the microphone does not open on its own over them.
            attachInProgress = false
            lastActivityTimestamp = now
            autoListenConsumed = _showTrigger.value + 1
            _showTrigger.value++
            return
        }
        attachInProgress = false

        // Start fresh unless the user chose to continue recent chats. The saved conversation
        // stays in the chat list either way; only the assistant's view of it resets.
        if (!shouldContinueAssistantChat(continueChatMinutes, lastActivityTimestamp, now) &&
            (_messages.value.isNotEmpty() || currentConversationId != null)
        ) {
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
        lastActivityTimestamp = System.currentTimeMillis()
    }

    /**
     * Start a new chat session, clearing all messages.
     */
    fun startNewChat() {
        cancelStreaming()
        stopSpeaking()
        currentConversationId = null
        // Photos that were never sent are not kept on disk.
        _pendingAttachments.value.forEach(discardAttachment)
        _pendingAttachments.value = emptyList()
        _messages.value = emptyList()
        _uiState.update {
            it.copy(
                textInput = "",
                userQuery = "",
                response = "",
                isLoading = false,
                isStreaming = false,
                error = null,
                notice = null
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
            val recognitionSettings = appPreferences.assistantVoiceSettings.first()
            speechRecognitionManager.startListening(
                RecognitionOptions(
                    languageTag = recognitionSettings.recognitionLanguageTag,
                    preferredEngine = recognitionSettings.recognitionEngine
                )
            ).collect { result ->
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
                error = null,
                notice = null
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
                // First pass, on every turn: obvious commands are recognised locally, so they
                // cost no model round trip and work without a provider. Anything missed goes
                // to the model, which can request the same actions through a directive.
                val localAction = if (attachments.isEmpty()) AssistantCommandParser.parse(trimmedQuery, now()) else null
                if (localAction != null) {
                    when (localAction) {
                        is AssistantAction.Clock -> {
                            _uiState.update { it.copy(isLoading = false) }
                            _voiceState.value = VoiceState.Idle
                            _events.emit(AssistantEvent.OpenClock(localAction.command, token))
                        }
                        is AssistantAction.Device -> {
                            // Performed here and reported with the real outcome, so the
                            // assistant never claims a change it did not make.
                            val outcome = deviceActionHandler.perform(localAction.action)
                            val result = outcome.message
                            _uiState.update { it.copy(isLoading = false, isStreaming = false, response = result) }
                            finalizeAssistantResponse(result)
                            _voiceState.value = VoiceState.Complete(result, trimmedQuery)
                            // Another app is in front after a launch; the overlay hides.
                            if (!outcome.launchedActivity) speakActionResult(result, token)
                        }
                    }
                    return@launch
                }
                coroutineScope {
                    val settings = appPreferences.assistantVoiceSettings.first().normalized()
                    val chunker = StreamingSpeechChunker()
                    lateinit var queue: AssistantSpeechQueue
                    queue = AssistantSpeechQueue(this, onDrained = {
                        // All queued speech finished: only now leave the speaking state.
                        if (token == turn && _uiState.value.isSpeaking) {
                            _uiState.update { it.copy(isSpeaking = false) }
                            if (!_uiState.value.isStreaming && !_uiState.value.isLoading) {
                                _voiceState.value = VoiceState.Complete(_uiState.value.response, trimmedQuery)
                            }
                        }
                    }) { chunk ->
                        if (token == turn && !speechStopped && _uiState.value.isTtsEnabled) {
                            speechRuntime.speak(chunk, settings) { event ->
                                if (token == turn && !speechStopped) {
                                    when (event) {
                                        TtsEvent.Started -> {
                                            _uiState.update { it.copy(isSpeaking = true) }
                                            _voiceState.value = VoiceState.Speaking(chunk)
                                        }
                                        is TtsEvent.Notice -> _uiState.update { it.copy(notice = event.message) }
                                        is TtsEvent.Error -> {
                                            speechStopped = true
                                            _uiState.update { it.copy(isSpeaking = false, error = event.message) }
                                            if (!_uiState.value.isStreaming && !_uiState.value.isLoading) {
                                                _voiceState.value = VoiceState.Complete(_uiState.value.response, trimmedQuery)
                                            }
                                        }
                                        else -> {
                                            // A sentence finished. Stay in the speaking state while more
                                            // speech is queued or the answer is still streaming, so the
                                            // pill does not flicker between sentences; onDrained ends it.
                                            val moreComing = queue.pending > 1 ||
                                                _uiState.value.isStreaming || _uiState.value.isLoading
                                            if (!moreComing) {
                                                _uiState.update { it.copy(isSpeaking = false) }
                                                _voiceState.value = VoiceState.Complete(_uiState.value.response, trimmedQuery)
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
                    // Append the action protocol so the model knows which phone actions it can
                    // request and how; the user's own prompt still applies first.
                    val notice = ActionDirectives.capabilityNotice(now())
                    val effectiveSystemPrompt = if (systemPrompt.isBlank()) notice else systemPrompt + "\n\n" + notice
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
                                // Action directives are never shown or spoken; stripping is
                                // monotone so the append-only speech chunker keeps working.
                                val shown = ActionDirectives.strip(state.content)
                                if (!_uiState.value.isSpeaking) _voiceState.value = VoiceState.Responding(
                                    content = shown,
                                    userQuery = trimmedQuery
                                )
                                _uiState.update {
                                    it.copy(
                                        response = shown,
                                        isLoading = false,
                                        isStreaming = true
                                    )
                                }
                                // Update streaming response in message list
                                updateStreamingResponse(shown)
                                if (!speechStopped && _uiState.value.isTtsEnabled)
                                    chunker.update(shown).forEach { queue.enqueue(it) }
                            }
                            is StreamingState.Completed -> {
                                // The model's action requests: run them now, keep them out of
                                // what is shown, spoken and saved, and append the real outcome
                                // whenever it failed or the model said nothing. A clock request
                                // hands off to the clock app last, since that hides the overlay.
                                val extraction = ActionDirectives.extract(state.finalContent, now())
                                val spoken = extraction.text
                                val outcomes = mutableListOf<DeviceActionOutcome>()
                                var clockCommand: ClockCommand? = null
                                for (action in extraction.actions) {
                                    when (action) {
                                        is AssistantAction.Clock -> if (clockCommand == null) clockCommand = action.command
                                        is AssistantAction.Device -> outcomes += deviceActionHandler.perform(action.action)
                                    }
                                }
                                val appended = outcomes
                                    .filter { !it.succeeded || spoken.isBlank() }
                                    .joinToString(" ") { it.message }
                                val finalText = listOf(spoken.trim(), appended).filter { it.isNotBlank() }.joinToString(" ")

                                if (!_uiState.value.isSpeaking) _voiceState.value = VoiceState.Complete(
                                    content = finalText,
                                    userQuery = trimmedQuery
                                )
                                _uiState.update {
                                    it.copy(
                                        response = finalText,
                                        isLoading = false,
                                        isStreaming = false
                                    )
                                }

                                // Finalize assistant message in message list
                                finalizeAssistantResponse(finalText)
                                lastActivityTimestamp = System.currentTimeMillis()

                                // Save assistant message to database
                                val assistantMessage = Message(
                                    conversationId = conversationId,
                                    role = MessageRole.ASSISTANT,
                                    content = finalText
                                )
                                conversationRepository.addMessage(assistantMessage)

                                // Trigger title generation (non-blocking)
                                applicationScope.launch {
                                    generateTitleIfNeeded(
                                        conversationId = conversationId,
                                        userMessage = trimmedQuery,
                                        assistantResponse = finalText
                                    )
                                }

                                // Speak the response if TTS is enabled. The chunker needs the
                                // untrimmed stripped text, of which every streamed snapshot is
                                // a prefix; the outcome is spoken after it.
                                if (!speechStopped && _uiState.value.isTtsEnabled) {
                                    chunker.update(spoken, isFinal = true).forEach { queue.enqueue(it) }
                                    if (appended.isNotBlank() && outcomes.none { it.launchedActivity }) queue.enqueue(appended)
                                }
                                queue.finish()
                                clockCommand?.let { _events.emit(AssistantEvent.OpenClock(it, token)) }
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
                            is TtsEvent.Notice -> _uiState.update { it.copy(notice = event.message) }
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
        // Hidden for the picker or camera: the question being typed comes back with the photo.
        val keepTyped = attachInProgress
        _uiState.update {
            it.copy(
                textInput = if (keepTyped) it.textInput else "",
                userQuery = "",
                response = "",
                isLoading = false,
                isStreaming = false,
                error = null,
                notice = null
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
        // A model that requested the clock already described what it is doing; the local
        // first pass has no text of its own.
        val response = _uiState.value.response.ifBlank {
            "Opened the clock app. Review and confirm there; nothing is confirmed here."
        }
        _uiState.update { it.copy(response = response, isLoading = false, isStreaming = false) }
        finalizeAssistantResponse(response)
        _voiceState.value = VoiceState.Complete(response, _uiState.value.userQuery)
    }

    fun openInApp() {
        viewModelScope.launch {
            _events.emit(AssistantEvent.OpenInApp(conversationId = currentConversationId))
        }
    }

    /** The session delivers each half of the capture as it arrives, and null when it hides. */
    fun onScreenContext(context: ScreenContext?) {
        _screenContext.value = context
    }

    /**
     * Add an image attachment.
     */
    fun addAttachment(attachment: Attachment) {
        _pendingAttachments.update { it + attachment }
    }

    /**
     * Remove an image attachment and delete its file.
     */
    fun removeAttachment(attachment: Attachment) {
        var removed = false
        _pendingAttachments.update { attachments ->
            attachments.filter { keep -> (keep.uri != attachment.uri).also { if (!it) removed = true } }
        }
        if (removed) discardAttachment(attachment)
    }

    /**
     * Asks the session to open the camera or the photo picker for this question. The picker
     * runs in a separate activity (the overlay cannot receive an activity result), so the
     * request is an event the session acts on; the photos come back through the inbox.
     * Refused with the composer's wording once the per-message limit is reached.
     */
    fun requestAttach(mode: AssistantAttachMode) {
        val room = AttachmentLimits.MAX_PER_MESSAGE - _pendingAttachments.value.size
        if (room <= 0) {
            _uiState.update { it.copy(notice = AttachmentLimits.limitNotice()) }
            return
        }
        stopVoiceInput()
        attachInProgress = true
        lastActivityTimestamp = System.currentTimeMillis()
        viewModelScope.launch { _events.emit(AssistantEvent.Attach(mode, room)) }
    }

    /** The session could not open the attach activity; the overlay stays and says why. */
    fun onAttachLaunchFailed(error: String) {
        attachInProgress = false
        _uiState.update { it.copy(notice = error) }
    }

    /**
     * Takes everything the attach activity published, exactly once. Photos beyond the
     * per-message limit (two deliveries racing a full tray) are deleted and reported with the
     * composer's wording rather than silently dropped.
     */
    private fun takeDeliveredAttachments() {
        val delivered = attachmentInbox.drain()
        if (delivered.isEmpty()) return
        val room = (AttachmentLimits.MAX_PER_MESSAGE - _pendingAttachments.value.size).coerceAtLeast(0)
        val kept = delivered.take(room)
        val dropped = delivered.drop(room)
        if (kept.isNotEmpty()) _pendingAttachments.update { it + kept }
        dropped.forEach(discardAttachment)
        if (dropped.isNotEmpty()) _uiState.update { it.copy(notice = AttachmentLimits.limitNotice()) }
        lastActivityTimestamp = System.currentTimeMillis()
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
    val error: String? = null,
    /** Informational, non-error message (e.g. neural voice unavailable, Android voice used instead). */
    val notice: String? = null
)

/**
 * Events emitted by the assistant ViewModel.
 */
sealed class AssistantEvent {
    data class OpenClock(val command: ClockCommand, val token: Long) : AssistantEvent()
    data class OpenInApp(val conversationId: String?) : AssistantEvent()
    /** Open the camera or photo picker; [room] is how many more images this message can take. */
    data class Attach(val mode: AssistantAttachMode, val room: Int) : AssistantEvent()
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
