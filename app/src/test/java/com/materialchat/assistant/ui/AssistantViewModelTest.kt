package com.materialchat.assistant.ui

import androidx.lifecycle.ViewModelStore
import com.materialchat.assistant.actions.DeviceAction
import com.materialchat.assistant.actions.DeviceActionHandler
import com.materialchat.assistant.actions.DeviceActionOutcome
import com.materialchat.assistant.voice.*
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.data.local.preferences.AssistantScreenContextSettings
import com.materialchat.data.local.preferences.AssistantVoiceSettings
import com.materialchat.domain.model.*
import com.materialchat.domain.repository.*
import com.materialchat.domain.usecase.CreateConversationUseCase
import com.materialchat.domain.usecase.GenerateConversationTitleUseCase
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Exercises production orchestration, with explicit gates instead of wall-clock sleeps. */
@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModelTest {
    private val store = ViewModelStore()
    private val recognition = mockk<SpeechRecognitionManager>(relaxed = true)
    private val android = mockk<TextToSpeechManager>(relaxed = true)
    private val chat = mockk<ChatRepository>(relaxed = true)
    private val providers = mockk<ProviderRepository>(relaxed = true)
    private val preferences = mockk<AppPreferences>()
    private val conversations = mockk<ConversationRepository>(relaxed = true)
    private val create = mockk<CreateConversationUseCase>()
    private val title = mockk<GenerateConversationTitleUseCase>(relaxed = true)
    private val runtime = mockk<AssistantSpeechRuntime>()
    private val deviceActions = mockk<DeviceActionHandler>(relaxed = true)
    private val settings = MutableStateFlow(AssistantVoiceSettings())
    private val active = Provider("active", "Active", ProviderType.OPENAI_COMPATIBLE,
        "https://unused.invalid", "default", false)
    private val override = active.copy(id = "voice", defaultModel = "voice-default")
    private val states = Channel<StreamingState>(Channel.UNLIMITED)
    private val utterances = mutableListOf<String>()
    private val callbacks = mutableListOf<(TtsEvent) -> Unit>()
    private var cancelled = false
    private var speechReleased = false

    private fun TestScope.createViewModel(): AssistantViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        every { recognition.amplitudeData } returns MutableStateFlow(AudioAmplitudeData.Empty)
        every { recognition.isAvailable() } returns true
        every { android.initialize() } returns flowOf(TtsInitResult.Success)
        every { preferences.assistantVoiceEnabled } returns flowOf(true)
        every { preferences.assistantTtsEnabled } returns flowOf(true)
        every { preferences.assistantVoiceSettings } returns settings
        every { preferences.systemPrompt } returns flowOf("system")
        every { preferences.reasoningEffort } returns flowOf(ReasoningEffort.HIGH)
        every { preferences.assistantScreenContextSettings } returns flowOf(AssistantScreenContextSettings())
        coEvery { providers.getActiveProvider() } returns active
        coEvery { providers.getProvider("voice") } returns override
        coEvery { create.withProviderAndModel(any(), any()) } returns "conversation"
        coEvery { conversations.getMessages(any()) } returns emptyList()
        coEvery { conversations.getConversation(any()) } returns null
        every { chat.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) } returns flow {
            try { for (state in states) emit(state) } finally { cancelled = true }
        }
        coEvery { runtime.speak(any(), any(), any()) } coAnswers {
            utterances += firstArg<String>()
            val callback = thirdArg<(TtsEvent) -> Unit>()
            callbacks += callback
            callback(TtsEvent.Started)
            try { awaitCancellation() } finally { speechReleased = true }
        }
        return AssistantViewModel(recognition, android, chat, providers, preferences, create,
            conversations, title, backgroundScope, runtime, deviceActions).also {
            store.put("assistant", it)
            runCurrent()
        }
    }

    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test fun `clock handoff bypasses provider and rejects stale results`() = runTest {
        val vm = createViewModel()
        coEvery { providers.getActiveProvider() } returns null
        val events = mutableListOf<AssistantEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        vm.sendQuery("set alarm at 7 am")
        runCurrent()
        val event = events.single() as AssistantEvent.OpenClock
        assertEquals(com.materialchat.assistant.actions.ClockCommand.Alarm(7, 0), event.command)
        coVerify(exactly = 0) { providers.getActiveProvider() }
        coVerify(exactly = 0) { providers.getProvider(any()) }
        verify(exactly = 0) { chat.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) }
        vm.onClockHandoff(event.token, "No clock app")
        assertEquals("No clock app", vm.uiState.value.error)
        vm.startNewChat()
        vm.onClockHandoff(event.token, null)
        assertEquals("", vm.uiState.value.response)
    }

    @Test fun `model directive performs the device action and never reaches the user`() = runTest {
        val vm = createViewModel()
        every { deviceActions.perform(DeviceAction.SetTorch(true)) } returns
            DeviceActionOutcome("Flashlight on.", succeeded = true)
        vm.sendQuery("first question")
        runCurrent()
        states.send(StreamingState.Completed("Some answer.", messageId = "a"))
        runCurrent()
        // Second turn of the same conversation: the model requests an action.
        vm.sendQuery("its dark, can you help")
        runCurrent()
        states.send(StreamingState.Streaming("Turning on the", messageId = "b"))
        runCurrent()
        states.send(StreamingState.Streaming("Turning on the flashlight.\n<<action:{\"type\":\"set_to", messageId = "b"))
        runCurrent()
        assertEquals("Turning on the flashlight.", vm.uiState.value.response.trim())
        verify(exactly = 0) { deviceActions.perform(any()) }
        states.send(StreamingState.Completed("Turning on the flashlight.\n<<action:{\"type\":\"set_torch\",\"on\":true}>>", messageId = "b"))
        runCurrent()
        verify(exactly = 1) { deviceActions.perform(DeviceAction.SetTorch(true)) }
        assertEquals("Turning on the flashlight.", vm.uiState.value.response)
        assertTrue(vm.messages.value.none { it.content.contains("<<") || it.content.contains("action") })
        assertEquals("Turning on the flashlight.", vm.messages.value.last().content)
        coVerify(exactly = 1) { conversations.addMessage(match {
            it.role == MessageRole.ASSISTANT && it.content == "Turning on the flashlight."
        }) }
        assertTrue(utterances.none { it.contains("<<") })
        assertEquals(listOf("Turning on the flashlight."), utterances.filter { it.contains("flashlight") })
    }

    @Test fun `failed directive outcome is appended and invalid directives are dropped`() = runTest {
        val vm = createViewModel()
        // Speech completes immediately here so the queue reaches the appended outcome.
        coEvery { runtime.speak(any(), any(), any()) } coAnswers {
            utterances += firstArg<String>()
            val callback = thirdArg<(TtsEvent) -> Unit>()
            callback(TtsEvent.Started)
            callback(TtsEvent.Completed)
        }
        every { deviceActions.perform(DeviceAction.SetTorch(true)) } returns
            DeviceActionOutcome("This device has no flash.", succeeded = false)
        vm.sendQuery("flashlight")
        runCurrent()
        states.send(StreamingState.Completed(
            "Turning on the flashlight.\n<<action:{\"type\":\"set_torch\",\"on\":true}>>\n<<action:{\"type\":\"set_alarm\",\"hour\":99}>>\n<<action:garbage>>",
            messageId = "m"
        ))
        runCurrent()
        verify(exactly = 1) { deviceActions.perform(any()) }
        assertEquals("Turning on the flashlight. This device has no flash.", vm.uiState.value.response)
        assertFalse(vm.messages.value.last().content.contains("<<"))
        assertTrue(utterances.contains("This device has no flash."))
    }

    @Test fun `model clock directive hands off to the clock with the model's own words`() = runTest {
        val vm = createViewModel()
        val events = mutableListOf<AssistantEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        vm.sendQuery("i should get up early for the gym tomorrow, maybe 6:30")
        runCurrent()
        states.send(StreamingState.Completed(
            "Opening the clock to set a 6:30 alarm for the gym.\n<<action:{\"type\":\"set_alarm\",\"hour\":6,\"minute\":30,\"label\":\"Gym\"}>>",
            messageId = "m"
        ))
        runCurrent()
        val event = events.single() as AssistantEvent.OpenClock
        assertEquals(com.materialchat.assistant.actions.ClockCommand.Alarm(6, 30, "Gym"), event.command)
        vm.onClockHandoff(event.token, null)
        assertEquals("Opening the clock to set a 6:30 alarm for the gym.", vm.uiState.value.response)
        assertFalse(vm.messages.value.last().content.contains("<<"))
    }

    @Test fun `local reminder and device parsers run on later turns too`() = runTest {
        val vm = createViewModel()
        every { deviceActions.perform(DeviceAction.SetTorch(false)) } returns
            DeviceActionOutcome("Flashlight off.", succeeded = true)
        val events = mutableListOf<AssistantEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        vm.sendQuery("hello")
        runCurrent()
        states.send(StreamingState.Completed("Hi.", messageId = "a"))
        runCurrent()
        vm.sendQuery("ok now remind me in 20 minutes to check the oven")
        runCurrent()
        val event = events.single() as AssistantEvent.OpenClock
        assertEquals(com.materialchat.assistant.actions.ClockCommand.Timer(1200, "Check the oven"), event.command)
        vm.onClockHandoff(event.token, null)
        vm.sendQuery("and turn the flashlight off")
        runCurrent()
        verify(exactly = 1) { deviceActions.perform(DeviceAction.SetTorch(false)) }
        assertEquals("Flashlight off.", vm.uiState.value.response)
        verify(exactly = 1) { chat.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    /**
     * The screenshot case: an earlier turn of the same conversation ended in a refusal, then the
     * user asks the exact utterance again. The local first pass must run the reminder as a
     * labelled timer, and no model call (which could repeat the refusal) may happen.
     */
    @Test fun `reminder after an earlier refusal runs locally without any model call`() = runTest {
        val vm = createViewModel()
        val events = mutableListOf<AssistantEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        vm.sendQuery("can you set reminders")
        runCurrent()
        states.send(StreamingState.Completed("I can't set reminders or timers, so I can't do that for you.", messageId = "a"))
        runCurrent()
        verify(exactly = 1) { chat.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) }
        assertEquals(2, vm.messages.value.size)
        vm.sendQuery("remind me to call someone in 10 minutes")
        runCurrent()
        val event = events.single() as AssistantEvent.OpenClock
        assertEquals(com.materialchat.assistant.actions.ClockCommand.Timer(600, "Call someone"), event.command)
        verify(exactly = 1) { chat.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) }
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(null, vm.uiState.value.error)
        vm.onClockHandoff(event.token, null)
        assertTrue(vm.messages.value.last().content.contains("Opened the clock"))
        assertFalse(vm.messages.value.last().content.contains("can't"))
    }

    @Test fun `provider model and reasoning overrides are independent`() = runTest {
        val vm = createViewModel()
        val cases = listOf(
            AssistantVoiceSettings(providerId = "voice") to Triple(override, "voice-default", ReasoningEffort.HIGH),
            AssistantVoiceSettings(model = "only-model") to Triple(active, "only-model", ReasoningEffort.HIGH),
            AssistantVoiceSettings(reasoningEffort = ReasoningEffort.LOW) to Triple(active, "default", ReasoningEffort.LOW),
            AssistantVoiceSettings("voice", "all-model", ReasoningEffort.MAX) to Triple(override, "all-model", ReasoningEffort.MAX)
        )
        for ((config, expected) in cases) {
            settings.value = config
            vm.sendQuery("query")
            runCurrent()
            // The user's prompt is kept first; the action protocol is appended so the model
            // knows which phone actions it can request and how.
            verify(exactly = 1) { chat.sendMessage(expected.first, any(), expected.second,
                expected.third, match { it.startsWith("system") && it.contains("<<action:") && it.contains("set_alarm") },
                false, false, "assistant") }
            coVerify { conversations.updateConversationProviderAndModel("conversation", expected.first.id, expected.second) }
        }
    }

    @Test fun `speech starts before completion and Stop cancels only owned collection ignoring stale speech`() = runTest {
        val vm = createViewModel()
        vm.sendQuery("first")
        runCurrent()
        states.send(StreamingState.Streaming("First sentence. More", messageId = "first"))
        runCurrent()
        assertEquals(listOf("First sentence."), utterances)
        assertTrue(vm.uiState.value.isStreaming)
        assertTrue(vm.uiState.value.isSpeaking)
        coVerify(exactly = 0) { conversations.addMessage(match { it.role == MessageRole.ASSISTANT }) }
        vm.cancelStreaming()
        runCurrent()
        assertTrue(cancelled)
        assertTrue(speechReleased)
        assertFalse(vm.uiState.value.isStreaming)
        assertFalse(vm.uiState.value.isSpeaking)
        assertTrue(vm.messages.value.none { it.isStreaming })
        verify(exactly = 0) { chat.cancelStreaming(any()) }
        vm.sendQuery("replacement")
        runCurrent()
        val before = vm.uiState.value
        callbacks.single()(TtsEvent.Started)
        callbacks.single()(TtsEvent.Error("stale"))
        assertEquals(before, vm.uiState.value)
        assertEquals(VoiceState.Thinking("replacement"), vm.voiceState.value)
    }

    @Test fun `reported speech failure leaves text generation alive through completion`() = runTest {
        val vm = createViewModel()
        coEvery { runtime.speak(any(), any(), any()) } coAnswers {
            thirdArg<(TtsEvent) -> Unit>()(TtsEvent.Error("speech failed"))
        }
        vm.sendQuery("query")
        runCurrent()
        states.send(StreamingState.Streaming("First sentence. More", messageId = "m"))
        runCurrent()
        assertEquals("speech failed", vm.uiState.value.error)
        assertFalse(cancelled)
        assertTrue(vm.uiState.value.isStreaming)
        states.send(StreamingState.Completed("First sentence. More text.", messageId = "m"))
        runCurrent()
        assertEquals("First sentence. More text.", vm.uiState.value.response)
        assertFalse(vm.uiState.value.isStreaming)
        coVerify(exactly = 1) { runtime.speak(any(), any(), any()) }
        coVerify(exactly = 1) { conversations.addMessage(match {
            it.role == MessageRole.ASSISTANT && it.content == "First sentence. More text."
        }) }
    }

    @Test fun `auto listen is consumed once per visible invocation`() = runTest {
        val vm = createViewModel()
        every { recognition.startListening() } returns flow { awaitCancellation() }
        vm.onOverlayShown()
        val invocation = vm.showTrigger.value
        vm.requestAutoListen(invocation, true)
        vm.requestAutoListen(invocation, true)
        vm.onOverlayShown()
        runCurrent()
        assertEquals(invocation, vm.showTrigger.value)
        verify(exactly = 1) { recognition.startListening() }
        vm.onOverlayHidden()
        vm.onOverlayShown()
        vm.requestAutoListen(vm.showTrigger.value, true)
        runCurrent()
        verify(exactly = 2) { recognition.startListening() }
    }

    @Test fun `hide during preference loading prevents hidden and stale auto listen`() = runTest {
        val vm = createViewModel()
        val gate = CompletableDeferred<Boolean>()
        every { preferences.assistantVoiceEnabled } returns flow { emit(gate.await()) }
        every { recognition.startListening() } returns flow { awaitCancellation() }
        vm.onOverlayShown()
        val oldInvocation = vm.showTrigger.value
        val pending = launch { vm.requestAutoListen(oldInvocation, true) }
        runCurrent()
        vm.onOverlayHidden()
        vm.onOverlayShown()
        gate.complete(true)
        pending.join()
        runCurrent()
        verify(exactly = 0) { recognition.startListening() }
        vm.requestAutoListen(vm.showTrigger.value, false)
        runCurrent()
        verify(exactly = 0) { recognition.startListening() }
        vm.requestAutoListen(vm.showTrigger.value, true)
        runCurrent()
        verify(exactly = 1) { recognition.startListening() }
    }

    @Test fun `hiding without reinvocation prevents deferred microphone startup`() = runTest {
        val vm = createViewModel()
        val gate = CompletableDeferred<Boolean>()
        every { preferences.assistantVoiceEnabled } returns flow { emit(gate.await()) }
        every { recognition.startListening() } returns flow { awaitCancellation() }
        vm.onOverlayShown()
        val pending = launch { vm.requestAutoListen(vm.showTrigger.value, true) }
        runCurrent()
        vm.clearConversation()
        gate.complete(true)
        pending.join()
        runCurrent()
        verify(exactly = 0) { recognition.startListening() }
    }

    @Test fun `disabled voice preference never starts automatic recognition`() = runTest {
        val vm = createViewModel()
        every { preferences.assistantVoiceEnabled } returns flowOf(false)
        vm.onOverlayShown()
        vm.requestAutoListen(vm.showTrigger.value, true)
        runCurrent()
        verify(exactly = 0) { recognition.startListening() }
    }

    @Test fun `finish speaking retains final transcript while cancel rejects it`() = runTest {
        val vm = createViewModel()
        val results = Channel<SpeechRecognitionResult>(Channel.UNLIMITED)
        every { recognition.startListening() } returns results.receiveAsFlow()
        vm.startVoiceInput()
        runCurrent()
        results.send(SpeechRecognitionResult.Ready)
        runCurrent()
        clearMocks(recognition, answers = false, recordedCalls = true)
        vm.finishVoiceInput()
        runCurrent()
        verify(exactly = 1) { recognition.stopListening() }
        verify(exactly = 0) { recognition.cancel() }
        results.send(SpeechRecognitionResult.FinalResult("finish this utterance"))
        runCurrent()
        assertEquals("finish this utterance", vm.uiState.value.userQuery)
        vm.cancelStreaming()
        runCurrent()
    }

    @Test fun `voice input waits for cancelled turn cleanup and final recognition sends query`() = runTest {
        val vm = createViewModel()
        val cleanup = CompletableDeferred<Unit>()
        var released = false
        every { chat.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) } returns flow {
            try { awaitCancellation() } finally {
                withContext(NonCancellable) { cleanup.await(); released = true }
            }
        }
        val results = Channel<SpeechRecognitionResult>(Channel.UNLIMITED)
        every { recognition.startListening() } answers {
            assertTrue("Recognition cannot overlap old turn cleanup", released)
            results.receiveAsFlow()
        }
        vm.sendQuery("old")
        runCurrent()
        vm.startVoiceInput()
        runCurrent()
        verify(exactly = 0) { recognition.startListening() }
        cleanup.complete(Unit)
        runCurrent()
        results.send(SpeechRecognitionResult.Ready)
        runCurrent()
        assertEquals(VoiceState.Listening, vm.voiceState.value)
        results.send(SpeechRecognitionResult.PartialResult("partial"))
        runCurrent()
        assertEquals("partial", vm.uiState.value.textInput)
        results.send(SpeechRecognitionResult.FinalResult("recognized"))
        runCurrent()
        assertEquals("recognized", vm.uiState.value.userQuery)
        assertEquals(2, vm.messages.value.count { it.role == MessageRole.USER })
        vm.cancelStreaming()
        runCurrent()
        results.send(SpeechRecognitionResult.FinalResult("stale"))
        runCurrent()
        assertEquals("recognized", vm.uiState.value.userQuery)
        assertEquals(VoiceState.Idle, vm.voiceState.value)
    }

    // W6 sequence walk: the sequences, not the states.

    @Test fun `a failed stream settles the partial answer and the retry reuses the saved question`() = runTest {
        val vm = createViewModel()
        val saved = mutableListOf<Message>()
        coEvery { conversations.addMessage(any()) } answers { saved += firstArg<Message>(); firstArg<Message>().id }
        coEvery { conversations.getMessages(any()) } answers { saved.toList() }
        vm.sendQuery("question")
        runCurrent()
        states.send(StreamingState.Streaming("partial", messageId = "m"))
        runCurrent()
        assertTrue(vm.messages.value.last().isStreaming)
        states.send(StreamingState.Error(java.io.IOException("down")))
        runCurrent()
        assertEquals(AssistantCopy.NO_CONNECTION, vm.uiState.value.error)
        assertEquals(VoiceState.Error(AssistantCopy.NO_CONNECTION), vm.voiceState.value)
        assertFalse(vm.uiState.value.isStreaming)
        assertFalse(vm.uiState.value.answerInFlight)
        // The partial answer stays on the surface, without the typing dots beside the notice.
        assertEquals("partial", vm.messages.value.last().content)
        assertTrue(vm.messages.value.none { it.isStreaming })
        assertEquals(1, saved.size)
        vm.retryLastQuery()
        runCurrent()
        // The question's bubble and the half answer leave together; the exchange is redrawn once.
        assertEquals(listOf(MessageRole.USER), vm.messages.value.map { it.role })
        assertNull(vm.uiState.value.error)
        assertEquals(VoiceState.Thinking("question"), vm.voiceState.value)
        assertEquals("the retry reuses the saved question row", 1, saved.size)
        verify(exactly = 2) { chat.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) }
        states.send(StreamingState.Completed("answer", messageId = "m2"))
        runCurrent()
        assertEquals(listOf(MessageRole.USER, MessageRole.ASSISTANT), vm.messages.value.map { it.role })
        assertEquals(listOf(MessageRole.USER, MessageRole.ASSISTANT), saved.map { it.role })
    }

    @Test fun `a new listening attempt clears the stale speech-output error so a timeout is reported`() = runTest {
        val vm = createViewModel()
        coEvery { runtime.speak(any(), any(), any()) } coAnswers {
            thirdArg<(TtsEvent) -> Unit>()(TtsEvent.Error("Audio playback failed."))
        }
        vm.sendQuery("question")
        runCurrent()
        states.send(StreamingState.Completed("Answer.", messageId = "m"))
        runCurrent()
        assertEquals("Audio playback failed.", vm.uiState.value.error)
        val results = Channel<SpeechRecognitionResult>(Channel.UNLIMITED)
        every { recognition.startListening() } returns results.receiveAsFlow()
        vm.startVoiceInput()
        runCurrent()
        assertNull(vm.uiState.value.error)
        results.send(SpeechRecognitionResult.Ready)
        results.send(SpeechRecognitionResult.Error("No speech", SpeechRecognitionErrors.ERROR_SPEECH_TIMEOUT))
        runCurrent()
        // The recognizer's failure is the one notice: nothing stale outranks it.
        assertEquals(
            VoiceState.Error("No speech heard. Try again.", SpeechRecognitionErrors.ERROR_SPEECH_TIMEOUT),
            vm.voiceState.value
        )
        assertNull(vm.uiState.value.error)
        assertNull(vm.uiState.value.notice)
    }

    @Test fun `a rate limit names the wait and the retry resends the question once`() = runTest {
        val vm = createViewModel()
        vm.sendQuery("question")
        runCurrent()
        states.send(StreamingState.Error(IllegalStateException("{}"), httpStatus = 429))
        runCurrent()
        assertEquals(AssistantCopy.RATE_LIMITED, vm.uiState.value.error)
        assertEquals(VoiceState.Error(AssistantCopy.RATE_LIMITED), vm.voiceState.value)
        assertFalse(vm.uiState.value.answerInFlight)
        assertTrue(vm.messages.value.none { it.isStreaming })
        vm.retryLastQuery()
        runCurrent()
        assertNull(vm.uiState.value.error)
        assertEquals(VoiceState.Thinking("question"), vm.voiceState.value)
        verify(exactly = 2) { chat.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) }
        assertEquals(1, vm.messages.value.count { it.role == MessageRole.USER })
    }

    @Test fun `a rejected key opens settings and the hide clears the failure`() = runTest {
        val vm = createViewModel()
        val events = mutableListOf<AssistantEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        vm.onOverlayShown()
        vm.sendQuery("question")
        runCurrent()
        states.send(StreamingState.Error(IllegalStateException("{}"), httpStatus = 401))
        runCurrent()
        assertEquals(AssistantCopy.PROVIDER_REJECTED, vm.uiState.value.error)
        assertFalse(vm.uiState.value.answerInFlight)
        vm.openSettings()
        runCurrent()
        assertEquals(listOf<AssistantEvent>(AssistantEvent.OpenSettings), events)
        // The session hides to open the app; nothing of the failure survives it.
        vm.onOverlayHidden()
        assertNull(vm.uiState.value.error)
        assertEquals(VoiceState.Idle, vm.voiceState.value)
        assertEquals("", vm.uiState.value.userQuery)
        // Back from settings: a fresh invocation starts clean (continue window is 0).
        vm.onOverlayShown()
        assertTrue(vm.messages.value.isEmpty())
        assertNull(vm.uiState.value.error)
    }

    @Test fun `new chat clears the screen hint and a later screen question shows it again`() = runTest {
        val vm = createViewModel()
        vm.onOverlayShown()
        // No capture arrived; the question asks for the screen (automatic mode is the default).
        vm.sendQuery("what is this page about")
        runCurrent()
        assertTrue(vm.uiState.value.screenContextUnavailable)
        vm.startNewChat()
        assertFalse("the hint leaves with the exchange it was shown under", vm.uiState.value.screenContextUnavailable)
        vm.sendQuery("summarise this page")
        runCurrent()
        assertTrue("the next screen question in the same invocation is told again", vm.uiState.value.screenContextUnavailable)
        // Within one chat the hint is given once: the next screen question in the same chat
        // goes out without it (the send clears the marker and the once-flag stays consumed).
        vm.sendQuery("and this tweet")
        runCurrent()
        assertFalse("the hint is shown once per chat", vm.uiState.value.screenContextUnavailable)
    }

    @Test fun `a photo goes out again on retry through the saved question row`() = runTest {
        val vm = createViewModel()
        val saved = mutableListOf<Message>()
        val sent = mutableListOf<List<Message>>()
        coEvery { conversations.addMessage(any()) } answers { saved += firstArg<Message>(); firstArg<Message>().id }
        coEvery { conversations.getMessages(any()) } answers { saved.toList() }
        every { chat.sendMessage(any(), capture(sent), any(), any(), any(), any(), any(), any()) } returns flow {
            for (state in states) emit(state)
        }
        val photo = Attachment(uri = "file:///photo.jpg", mimeType = "image/jpeg")
        vm.addAttachment(photo)
        vm.sendQuery("describe this photo")
        runCurrent()
        assertTrue(vm.pendingAttachments.value.isEmpty())
        states.send(StreamingState.Error(IllegalStateException("{}"), httpStatus = 429))
        runCurrent()
        assertEquals(AssistantCopy.RATE_LIMITED, vm.uiState.value.error)
        vm.retryLastQuery()
        runCurrent()
        // The row the first attempt saved carries the photo and is reused, so the retry's
        // history has the photo exactly once.
        assertEquals(1, saved.size)
        assertEquals(listOf(photo.uri), saved.single().attachments.map { it.uri })
        assertEquals(2, sent.size)
        assertEquals(listOf(photo.uri), sent.last().single().attachments.map { it.uri })
        assertTrue(vm.pendingAttachments.value.isEmpty())
    }

    @Test fun `a photo returns to the tray when the turn fails before the question is saved`() = runTest {
        val vm = createViewModel()
        val saved = mutableListOf<Message>()
        coEvery { conversations.addMessage(any()) } answers { saved += firstArg<Message>(); firstArg<Message>().id }
        coEvery { conversations.getMessages(any()) } answers { saved.toList() }
        coEvery { create.withProviderAndModel(any(), any()) } throws java.io.IOException("down")
        val photo = Attachment(uri = "file:///photo.jpg", mimeType = "image/jpeg")
        vm.addAttachment(photo)
        vm.sendQuery("describe this photo")
        runCurrent()
        assertEquals(AssistantCopy.NO_CONNECTION, vm.uiState.value.error)
        assertTrue(saved.isEmpty())
        // Nothing was saved, so the photo is back where the user can see it is still attached.
        assertEquals(listOf(photo.uri), vm.pendingAttachments.value.map { it.uri })
        coEvery { create.withProviderAndModel(any(), any()) } returns "conversation"
        vm.retryLastQuery()
        runCurrent()
        assertEquals(listOf(photo.uri), saved.single().attachments.map { it.uri })
        assertTrue(vm.pendingAttachments.value.isEmpty())
        assertEquals(VoiceState.Thinking("describe this photo"), vm.voiceState.value)
    }

    @Test fun `a spoken action result stops when the overlay hides for Open in app`() = runTest {
        val vm = createViewModel()
        every { deviceActions.perform(DeviceAction.SetTorch(true)) } returns
            DeviceActionOutcome("Flashlight on.", succeeded = true)
        val events = mutableListOf<AssistantEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        vm.onOverlayShown()
        vm.sendQuery("turn the flashlight on")
        runCurrent()
        assertEquals("Flashlight on.", vm.uiState.value.response)
        assertTrue(vm.uiState.value.isSpeaking)
        assertEquals(listOf("Flashlight on."), utterances)
        // Open in app: the session hides, and nothing may keep talking behind the app.
        vm.openInApp()
        runCurrent()
        assertEquals(listOf<AssistantEvent>(AssistantEvent.OpenInApp(conversationId = null)), events)
        vm.onOverlayHidden()
        runCurrent()
        assertFalse(vm.uiState.value.isSpeaking)
        assertTrue("the action's speech job is cancelled with the hide", speechReleased)
        verify(atLeast = 1) { android.stop() }
        assertEquals(VoiceState.Idle, vm.voiceState.value)
    }

    @Test fun `open in app is withheld while the answer is awaited or arriving`() {
        assertTrue(AssistantUiState(isLoading = true).answerInFlight)
        assertTrue(AssistantUiState(isStreaming = true).answerInFlight)
        assertFalse(AssistantUiState(response = "done").answerInFlight)
        assertFalse(AssistantUiState(error = AssistantCopy.REQUEST_FAILED).answerInFlight)
        assertFalse(AssistantUiState(isLoading = true, conversationSaved = true).openInAppAvailable)
        assertFalse(AssistantUiState(isStreaming = true, conversationSaved = true).openInAppAvailable)
        assertTrue(AssistantUiState(response = "done", conversationSaved = true).openInAppAvailable)
        assertFalse("no conversation row, nothing to open", AssistantUiState(response = "done").openInAppAvailable)
    }

    @Test fun `a device-action-only exchange creates no conversation and withholds Open in app`() = runTest {
        val vm = createViewModel()
        every { deviceActions.perform(DeviceAction.SetTorch(true)) } returns
            DeviceActionOutcome("Flashlight on.", succeeded = true)
        vm.onOverlayShown()
        vm.sendQuery("turn the flashlight on")
        runCurrent()
        assertEquals("Flashlight on.", vm.uiState.value.response)
        assertTrue(vm.messages.value.isNotEmpty())
        // W8 ruling: the local action never creates a conversation, so the toolbar has nothing
        // for Open in app to land on and withholds it.
        coVerify(exactly = 0) { create.withProviderAndModel(any(), any()) }
        coVerify(exactly = 0) { conversations.addMessage(any()) }
        assertFalse(vm.uiState.value.conversationSaved)
        assertFalse(vm.uiState.value.openInAppAvailable)
        // A model turn in the same chat creates the row; the chip is offered on completion only.
        vm.sendQuery("what time is the sunset")
        runCurrent()
        assertTrue(vm.uiState.value.conversationSaved)
        assertFalse("withheld while the answer is awaited", vm.uiState.value.openInAppAvailable)
        states.send(StreamingState.Completed("About seven.", messageId = "a"))
        runCurrent()
        assertTrue(vm.uiState.value.openInAppAvailable)
        // New chat drops the row reference: the next exchange starts unsaved again.
        vm.startNewChat()
        assertFalse(vm.uiState.value.conversationSaved)
        assertFalse(vm.uiState.value.openInAppAvailable)
    }
}
