package com.materialchat.assistant.ui

import androidx.lifecycle.ViewModelStore
import com.materialchat.assistant.actions.DeviceAction
import com.materialchat.assistant.actions.DeviceActionHandler
import com.materialchat.assistant.voice.*
import com.materialchat.data.local.preferences.AppPreferences
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
            verify(exactly = 1) { chat.sendMessage(expected.first, any(), expected.second,
                expected.third, "system", false, false, "assistant") }
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
}
