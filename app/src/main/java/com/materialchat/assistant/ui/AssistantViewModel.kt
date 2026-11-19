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
import com.materialchat.assistant.screen.ScreenContextDecision
import com.materialchat.assistant.screen.ScreenContextPolicy
import com.materialchat.assistant.screen.ScreenContextPrompt
import com.materialchat.assistant.screen.ScreenContextTrigger
import com.materialchat.data.local.preferences.AssistantScreenContextSettings
import com.materialchat.util.AttachmentLimits
import java.time.LocalDateTime
import androidx.lifecycle.viewModelScope
import com.materialchat.assistant.voice.AudioAmplitudeData
import com.materialchat.assistant.voice.RecognitionOptions
import com.materialchat.assistant.voice.SpeechRecognitionErrors
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
    private val discardAttachment: (Attachment) -> Unit = {},
    /**
     * Writes the screen capture into the owned attachments directory (the session supplies
     * [com.materialchat.assistant.screen.AssistScreenshots.writeAttachment] on IO); null when
     * it could not be written.
     */
    private val writeScreenshot: suspend (android.graphics.Bitmap) -> Attachment? = { null }
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

    /**
     * The screen capture staged for the next question as a normal image attachment file, shown
     * as the removable "Screen" chip; separate from the photos so it is labelled and never
     * counts against the per-message photo limit. Written when the question is about the
     * screen (or always, in that mode), consumed by the send, discarded otherwise.
     */
    private val _screenAttachment = MutableStateFlow<Attachment?>(null)
    val screenAttachment: StateFlow<Attachment?> = _screenAttachment.asStateFlow()

    /** The user removed the screen chip: this question goes without the screen, whatever asks. */
    private var screenDeclined = false
    private var screenStaging = false
    /** The settings hint for a missing capture is shown once per invocation. */
    private var screenUnavailableShown = false
    @Volatile private var screenSettings = AssistantScreenContextSettings()

    /** The persistent conversation ID - created on first query */
    private var currentConversationId: String? = null

    /** The user message the last turn saved; a retry reuses it when it is still the conversation's last row. */
    private var savedQuestion: Message? = null

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

        viewModelScope.launch {
            appPreferences.assistantScreenContextSettings.collect { settings ->
                screenSettings = settings
                reconsiderScreenStaging()
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
        savedQuestion = null
        // Photos that were never sent are not kept on disk.
        _pendingAttachments.value.forEach(discardAttachment)
        _pendingAttachments.value = emptyList()
        unstageScreen()
        // W7: the settings hint for a missing capture belongs to the exchange it was shown under.
        // Before, New chat left `screenContextUnavailable` standing (the hint showed above the
        // empty pill) while the once-flag stayed consumed, so the next screen question in the
        // same invocation got no hint at all.
        screenUnavailableShown = false
        _messages.value = emptyList()
        _uiState.update {
            it.copy(
                textInput = "",
                userQuery = "",
                response = "",
                isLoading = false,
                isStreaming = false,
                error = null,
                notice = null,
                screenContextUnavailable = false,
                conversationSaved = false
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
        reconsiderScreenStaging()
    }

    /**
     * Start voice input.
     */
    fun startVoiceInput() {
        val previousTurn = streamingJob
        cancelStreaming()
        // W6: a new listening attempt supersedes what the last turn left behind. AssistantNotices
        // ranks `error` above the recognizer's own failure, so a speech-output error (or a voice
        // fallback notice) left standing here would mask a timeout's "No speech heard. Try
        // again." with a stale notice that offers no action.
        _uiState.update { it.copy(error = null, notice = null) }
        val token = ++recognition
        if (!speechRecognitionManager.isAvailable()) {
            _voiceState.value = VoiceState.Error(
                AssistantCopy.RECOGNIZER_UNAVAILABLE, SpeechRecognitionErrors.ERROR_CLIENT
            )
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
                        // The chip appears while the user is still speaking about the screen.
                        reconsiderScreenStaging()
                    }
                    is SpeechRecognitionResult.FinalResult -> {
                        _uiState.update { it.copy(textInput = result.text) }
                        sendQuery(result.text)
                    }
                    is SpeechRecognitionResult.Error -> {
                        // The window shows the copy system's phrase for the code, never the
                        // recognizer's own wording.
                        _voiceState.value = VoiceState.Error(
                            AssistantCopy.recognitionError(result.errorCode, result.message),
                            result.errorCode
                        )
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
        send(query, reuseSavedQuestion = false)
    }

    /**
     * One turn. [reuseSavedQuestion] is the notice's "Try again": the row the failed attempt
     * saved is reused when it is still the conversation's last message (see [retryLastQuery]).
     */
    private fun send(query: String, reuseSavedQuestion: Boolean) {
        if (query.isBlank()) return

        val previousTurn = streamingJob
        cancelStreaming()
        val token = turn
        speechStopped = false
        val trimmedQuery = query.trim()
        val attachments = _pendingAttachments.value
        // Screen context for this question (see ScreenContextPolicy): decided now, on the final
        // utterance, from the user's setting and whether the capture arrived. The staged chip, if
        // any, is consumed by this turn either way.
        val declinedForTurn = screenDeclined
        screenDeclined = false
        val screenPlan = screenDecision(asks = ScreenContextTrigger.matches(trimmedQuery), declined = declinedForTurn)
        val stagedScreen = _screenAttachment.value
        _screenAttachment.value = null

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
                notice = null,
                screenContextUsed = false,
                screenContextUnavailable = false
            )
        }
        _pendingAttachments.value = emptyList()
        if (screenPlan == ScreenContextDecision.UNAVAILABLE) markScreenUnavailable()
        _voiceState.value = VoiceState.Thinking(trimmedQuery)
        lastActivityTimestamp = System.currentTimeMillis()
        // W7: until the question row is saved, the photos exist only in this turn; a failure
        // before that point puts them back in the tray so "Try again" sends them.
        var questionSaved = false
        // W8: the answer as far as the pill showed it, saved under the turn's conversation when
        // the turn is cut off (a new question, a new invocation, the hide), so the chat and the
        // model's history carry what the user saw instead of two user rows in a row.
        var shownPartial = ""
        var turnConversationId: String? = null

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
                        AssistantAction.ScreenContext -> Unit // never produced by the local parsers
                    }
                    stagedScreen?.let(discardAttachment)
                    return@launch
                }
                coroutineScope {
                    val settings = appPreferences.assistantVoiceSettings.first().normalized()
                    var chunker = StreamingSpeechChunker()
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
                        restoreAttachments(attachments)
                        handleError(AssistantCopy.NO_PROVIDER)
                        stagedScreen?.let(discardAttachment)
                        queue.finish()
                        return@coroutineScope
                    }

                    val model = settings.model ?: provider.defaultModel
                    if (currentConversationId == null) {
                        val created = createConversationUseCase.withProviderAndModel(provider.id, model)
                        currentCoroutineContext().ensureActive()
                        if (token != turn) return@coroutineScope
                        currentConversationId = created
                        // W8 ruling: only a model exchange creates a conversation; the toolbar
                        // offers Open in app once there is a row for it to land on.
                        _uiState.update { it.copy(conversationSaved = true) }
                    }
                    val conversationId = currentConversationId!!
                    turnConversationId = conversationId
                    conversationRepository.updateConversationProviderAndModel(conversationId, provider.id, model)
                    val systemPrompt = appPreferences.systemPrompt.first()
                    // Append the action protocol so the model knows which phone actions it can
                    // request and how; the user's own prompt still applies first.
                    val notice = ActionDirectives.capabilityNotice(now())
                    val effectiveSystemPrompt = if (systemPrompt.isBlank()) notice else systemPrompt + "\n\n" + notice
                    val reasoningEffort = settings.reasoningEffort ?: appPreferences.reasoningEffort.first()

                    // The screen capture, when this question is about it: the screenshot becomes
                    // an attachment of this one message and the screen text goes into the system
                    // prompt, so a text-only model still works. Otherwise the model is only told
                    // a capture exists and may ask for it (one resend of the same turn).
                    var screenUse: ScreenUse? = if (screenPlan == ScreenContextDecision.ATTACH) {
                        prepareScreenUse(stagedScreen)
                    } else {
                        stagedScreen?.let(discardAttachment)
                        null
                    }
                    currentCoroutineContext().ensureActive()
                    if (token != turn) return@coroutineScope

                    // The saved question. W6: a retry of a plain failed question (no photos, no
                    // screen use this time) reuses the row the first attempt saved, as long as
                    // it is still the conversation's last message, so neither the chat nor the
                    // model's history carries the question twice. A retry that attaches the
                    // screen saves a new row (the capture is consumed per attempt).
                    val reused = if (reuseSavedQuestion && attachments.isEmpty() && screenUse == null) {
                        savedQuestion?.takeIf {
                            it.conversationId == conversationId && it.content == trimmedQuery &&
                                conversationRepository.getMessages(conversationId).lastOrNull()?.id == it.id
                        }
                    } else null
                    val userMessage = reused ?: Message(
                        conversationId = conversationId,
                        role = MessageRole.USER,
                        content = trimmedQuery,
                        attachments = attachments + listOfNotNull(screenUse?.attachment)
                    )
                    if (reused == null) conversationRepository.addMessage(userMessage)
                    savedQuestion = userMessage
                    questionSaved = true

                    var resend = true
                    while (resend) {
                    resend = false
                    val turnSystemPrompt = screenUse.let { use ->
                        when {
                            use != null -> effectiveSystemPrompt + "\n\n" + use.promptBlock
                            screenDecision(asks = true, declined = declinedForTurn) == ScreenContextDecision.ATTACH ->
                                effectiveSystemPrompt + "\n\n" + ScreenContextPrompt.availabilityNotice()
                            else -> effectiveSystemPrompt
                        }
                    }
                    _uiState.update { it.copy(screenContextUsed = screenUse != null) }
                    chunker = StreamingSpeechChunker()

                    // Get all messages for this conversation
                    val messages = conversationRepository.getMessages(conversationId)

                    // Stream the response
                    chatRepository.sendMessage(
                        provider = provider,
                        messages = messages,
                        model = model,
                        reasoningEffort = reasoningEffort,
                        systemPrompt = turnSystemPrompt,
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
                                shownPartial = shown
                                if (!speechStopped && _uiState.value.isTtsEnabled)
                                    chunker.update(shown).forEach { queue.enqueue(it) }
                            }
                            is StreamingState.Completed -> {
                                // The answer is saved whole below; a later cancellation of this
                                // turn has nothing partial left to keep.
                                shownPartial = ""
                                // The model's action requests: run them now, keep them out of
                                // what is shown, spoken and saved, and append the real outcome
                                // whenever it failed or the model said nothing. A clock request
                                // hands off to the clock app last, since that hides the overlay.
                                val extraction = ActionDirectives.extract(state.finalContent, now())
                                // The model asked for the screen it was not given: resend this
                                // same turn with the capture attached, once, instead of
                                // answering blind. When it cannot be given, say why.
                                val screenRequested = extraction.actions.any { it is AssistantAction.ScreenContext }
                                var screenFallback = ""
                                if (screenRequested && screenUse == null) {
                                    val allowed = screenDecision(asks = true, declined = declinedForTurn)
                                    val use = if (allowed == ScreenContextDecision.ATTACH) prepareScreenUse(null) else null
                                    if (use != null) {
                                        screenUse = use
                                        conversationRepository.updateMessage(
                                            userMessage.copy(attachments = attachments + listOfNotNull(use.attachment))
                                        )
                                        // The directive-only interim reply never reaches the user.
                                        _messages.update { list ->
                                            val last = list.lastOrNull()
                                            if (last != null && last.role == MessageRole.ASSISTANT && last.isStreaming) list.dropLast(1) else list
                                        }
                                        _uiState.update { it.copy(response = "", isLoading = true, isStreaming = false) }
                                        _voiceState.value = VoiceState.Thinking(trimmedQuery)
                                        resend = true
                                        return@collect
                                    }
                                    screenFallback = ScreenContextPrompt.unavailableReply(screenSettings.mode)
                                    if (allowed == ScreenContextDecision.UNAVAILABLE) markScreenUnavailable()
                                }
                                val spoken = extraction.text
                                val outcomes = mutableListOf<DeviceActionOutcome>()
                                var clockCommand: ClockCommand? = null
                                for (action in extraction.actions) {
                                    when (action) {
                                        is AssistantAction.Clock -> if (clockCommand == null) clockCommand = action.command
                                        is AssistantAction.Device -> outcomes += deviceActionHandler.perform(action.action)
                                        AssistantAction.ScreenContext -> Unit
                                    }
                                }
                                val appended = (outcomes
                                    .filter { !it.succeeded || spoken.isBlank() }
                                    .map { it.message } + screenFallback)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" ")
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
                                // The failure notice's "Try again" redraws the exchange without
                                // the half answer (retryLastQuery), so it is not saved either.
                                shownPartial = ""
                                // Never the exception's own text: the copy system names
                                // connectivity, a typed 401/403/429 when the stream carries
                                // one, the rate limit from the body, and keeps the rest generic.
                                handleError(AssistantCopy.requestFailure(state.error, httpStatus = streamingErrorStatus(state)))
                            }
                            is StreamingState.Cancelled -> {
                                queue.cancel()
                                settleStreamingMessages()
                                persistPartialAnswer(conversationId, shownPartial)
                                shownPartial = ""
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
                    } // while (resend)
                    queue.finish()
                }
            } catch (cancelled: CancellationException) {
                // The turn was cut off: keep what the user saw (see persistPartialAnswer).
                val conversationId = turnConversationId
                if (questionSaved && conversationId != null) persistPartialAnswer(conversationId, shownPartial)
                throw cancelled
            } catch (e: Exception) {
                if (token == turn) {
                    if (!questionSaved) restoreAttachments(attachments)
                    handleError(AssistantCopy.requestFailure(e))
                }
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
        settleStreamingMessages()
        _uiState.update { it.copy(isLoading = false, isStreaming = false) }
        _voiceState.value = VoiceState.Idle
    }

    /**
     * The streaming marker leaves every message: a failed, cancelled or interrupted turn keeps
     * its partial text on the surface without the typing dots under it (W6: handleError left
     * them running beside the failure notice).
     */
    private fun settleStreamingMessages() {
        _messages.update { messages -> messages.map { it.copy(isStreaming = false) } }
    }

    /**
     * W8 (sequence walk round 5): a turn cut off mid-answer (a new question, a new invocation,
     * the hide) saves the answer as far as the pill showed it, as chat's Stop does, so the next
     * question's history follows what the user saw. Before, cancelStreaming settled the partial
     * on the surface but saved nothing, and the model's history carried two user rows in a row
     * while the pill showed question, partial answer, question. Runs inside the cancelled turn,
     * not cancellable, so the next turn (which joins this one) saves its row after it.
     */
    private suspend fun persistPartialAnswer(conversationId: String, partial: String) {
        if (partial.isBlank()) return
        withContext(NonCancellable) {
            runCatching {
                conversationRepository.addMessage(
                    Message(conversationId = conversationId, role = MessageRole.ASSISTANT, content = partial)
                )
            }
        }
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
        // W7: owned like the streamed answer's speech, so stopSpeaking (Stop, Open in app, the
        // hide, New chat) cancels it; before, the job ran on untracked and a cloud voice could
        // keep speaking after the overlay was gone.
        ttsJob = viewModelScope.launch {
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
     * Re-sends the question a failed request interrupted (the notice's "Try again"). The
     * question's own bubble is reused rather than repeated; photos and the screen capture were
     * consumed by the first attempt and do not go out again.
     */
    fun retryLastQuery() {
        val question = _uiState.value.userQuery
        if (question.isBlank()) return
        _messages.update { list ->
            // W6: the interrupted answer (partial text, settled by handleError) leaves together
            // with the question's bubble, so the retry redraws the exchange once instead of
            // stacking a second question under the half answer.
            var kept = list
            val partial = kept.lastOrNull()
            val question1 = kept.getOrNull(kept.size - 2)
            if (partial != null && partial.role == MessageRole.ASSISTANT &&
                question1 != null && question1.role == MessageRole.USER && question1.content == question
            ) kept = kept.dropLast(1)
            val last = kept.lastOrNull()
            if (last != null && last.role == MessageRole.USER && last.content == question) kept.dropLast(1) else kept
        }
        send(question, reuseSavedQuestion = true)
    }

    /**
     * Handle errors.
     */
    private fun handleError(message: String) {
        _voiceState.value = VoiceState.Error(message)
        settleStreamingMessages()
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

    /** The provider rejected the key: open the app's settings (the notice's "Open settings"). */
    fun openSettings() {
        viewModelScope.launch { _events.emit(AssistantEvent.OpenSettings) }
    }

    /**
     * The typed HTTP status of a failed stream. `StreamingState.Error.httpStatus` is being added
     * by the chat delegate (W4); until it lands this is the one place the assistant reads it, so
     * the switch is a one-line change here and the copy mapping is already tested for both paths.
     */
    private fun streamingErrorStatus(state: StreamingState.Error): Int? = state.httpStatus

    /** The session delivers each half of the capture as it arrives, and null when it hides. */
    fun onScreenContext(context: ScreenContext?) {
        _screenContext.value = context
        if (context == null) {
            // The invocation ended: nothing staged survives it, and the next one starts clean.
            unstageScreen()
            screenDeclined = false
            screenUnavailableShown = false
            _uiState.update { it.copy(screenContextUnavailable = false) }
        } else {
            reconsiderScreenStaging()
        }
    }

    /** The user removed the "Screen" chip: this question goes without the screen. */
    fun removeScreenContext() {
        screenDeclined = true
        unstageScreen()
    }

    private fun screenDecision(asks: Boolean, declined: Boolean = screenDeclined): ScreenContextDecision {
        val context = _screenContext.value
        val usable = context != null && ScreenContextPolicy.usable(context, screenSettings)
        return ScreenContextPolicy.decide(screenSettings.mode, asks, usable, declined)
    }

    /**
     * Stages the screenshot as the "Screen" chip as soon as the question being typed or spoken
     * refers to the screen (or at once in the always mode), so the user sees what is about to
     * be sent and can remove it. Text needs no staging; it is read at send time.
     */
    private fun reconsiderScreenStaging() {
        val context = _screenContext.value ?: return
        val screenshot = context.screenshot ?: return
        if (_screenAttachment.value != null || screenStaging || !screenSettings.includeScreenshot) return
        val asks = ScreenContextTrigger.matches(_uiState.value.textInput)
        if (screenDecision(asks) != ScreenContextDecision.ATTACH) return
        screenStaging = true
        viewModelScope.launch {
            val written = try { writeScreenshot(screenshot) } finally { screenStaging = false }
            if (written == null) return@launch
            if (_screenContext.value === context && _screenAttachment.value == null && !screenDeclined) {
                _screenAttachment.value = written
            } else {
                discardAttachment(written)
            }
        }
    }

    private fun unstageScreen() {
        val staged = _screenAttachment.value ?: return
        _screenAttachment.value = null
        discardAttachment(staged)
    }

    private fun markScreenUnavailable() {
        if (screenUnavailableShown) return
        screenUnavailableShown = true
        _uiState.update { it.copy(screenContextUnavailable = true) }
    }

    /** What goes out with this question: the attachment (staged or written now) and the prompt block. */
    private suspend fun prepareScreenUse(staged: Attachment?): ScreenUse? {
        val context = _screenContext.value
        if (context == null) {
            staged?.let(discardAttachment)
            return null
        }
        val settings = screenSettings
        val screenshot = context.screenshot
        val attachment = when {
            !settings.includeScreenshot || screenshot == null -> {
                staged?.let(discardAttachment)
                null
            }
            staged != null -> staged
            else -> writeScreenshot(screenshot)
        }
        val text = context.text?.takeIf { settings.includeScreenText && it.isNotBlank() }
        val webUri = context.webUri?.takeIf { settings.includeScreenText }
        if (attachment == null && text == null && webUri == null) return null
        val forPrompt = if (settings.includeScreenText) context else context.copy(webUri = null)
        return ScreenUse(attachment, ScreenContextPrompt.contextBlock(forPrompt, attachment != null, text))
    }

    private class ScreenUse(val attachment: Attachment?, val promptBlock: String)

    /**
     * Add an image attachment.
     */
    fun addAttachment(attachment: Attachment) {
        _pendingAttachments.update { it + attachment }
    }

    /** Photos a turn took but never saved go back to the tray (in front of anything added since). */
    private fun restoreAttachments(attachments: List<Attachment>) {
        if (attachments.isNotEmpty()) _pendingAttachments.update { attachments + it }
    }

    /**
     * Remove an image attachment and delete its file.
     */
    fun removeAttachment(attachment: Attachment) {
        var removed = false
        _pendingAttachments.update { attachments ->
            attachments.filter { keep -> (keep.uri != attachment.uri).also { if (!it) removed = true } }
        }
        if (removed) {
            discardAttachment(attachment)
            clearLimitNotice()
        }
    }

    /**
     * W8: the limit notice asks the user to remove a photo; it leaves with that removal (or with
     * a `+` tap that has room again) instead of standing over the opened pill after it was obeyed.
     */
    private fun clearLimitNotice() {
        if (_uiState.value.notice == AttachmentLimits.limitNotice()) _uiState.update { it.copy(notice = null) }
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
        clearLimitNotice()
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
    val notice: String? = null,
    /** The current answer was produced with the screen capture (the "Using screen" marker). */
    val screenContextUsed: Boolean = false,
    /** The question wanted the screen but the platform delivered nothing: show how to enable it. */
    val screenContextUnavailable: Boolean = false,
    /**
     * A conversation row exists for this chat. A device-action-only exchange ("Flashlight on.")
     * never creates one (W8 ruling), so Open in app would land on the chat list, not on the
     * exchange; the toolbar withholds it until a model turn has saved the conversation.
     */
    val conversationSaved: Boolean = false
) {
    /**
     * The answer is awaited or still arriving. Leaving the overlay now (Open in app hides the
     * session) cancels the stream; the answer as far as it got is saved (W8), but the app would
     * open on an answer cut off mid-sentence, so the toolbar withholds Open in app until this
     * is false (W6).
     */
    val answerInFlight: Boolean get() = isLoading || isStreaming

    /** Open in app is offered: the chat is saved (W8) and nothing is in flight (W6). */
    val openInAppAvailable: Boolean get() = conversationSaved && !answerInFlight
}

/**
 * Events emitted by the assistant ViewModel.
 */
sealed class AssistantEvent {
    data class OpenClock(val command: ClockCommand, val token: Long) : AssistantEvent()
    data class OpenInApp(val conversationId: String?) : AssistantEvent()
    /** Open the app's settings: the provider rejected the request (HTTP 401/403). */
    data object OpenSettings : AssistantEvent()
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
