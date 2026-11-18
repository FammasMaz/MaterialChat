package com.materialchat.assistant.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModelStore
import com.materialchat.assistant.actions.DeviceActionHandler
import com.materialchat.assistant.screen.ScreenContext
import com.materialchat.assistant.screen.ScreenContextPrompt
import com.materialchat.assistant.voice.AssistantSpeechRuntime
import com.materialchat.assistant.voice.AudioAmplitudeData
import com.materialchat.assistant.voice.SpeechRecognitionManager
import com.materialchat.assistant.voice.TextToSpeechManager
import com.materialchat.assistant.voice.TtsInitResult
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.data.local.preferences.AssistantScreenContextSettings
import com.materialchat.data.local.preferences.AssistantVoiceSettings
import com.materialchat.data.local.preferences.ScreenContextMode
import com.materialchat.domain.model.Attachment
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ProviderType
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.repository.ProviderRepository
import com.materialchat.domain.usecase.CreateConversationUseCase
import com.materialchat.domain.usecase.GenerateConversationTitleUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The screen capture on the send path: attached (screenshot file + delimited text block) only
 * for a question that refers to the screen, resent once when the model asks for it with the
 * directive, never sent in the Never mode, and reported once when nothing arrived.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModelScreenContextTest {
    private val store = ViewModelStore()
    private val recognition = mockk<SpeechRecognitionManager>(relaxed = true)
    private val tts = mockk<TextToSpeechManager>(relaxed = true)
    private val chat = mockk<ChatRepository>(relaxed = true)
    private val providers = mockk<ProviderRepository>(relaxed = true)
    private val preferences = mockk<AppPreferences>()
    private val conversations = mockk<ConversationRepository>(relaxed = true)
    private val create = mockk<CreateConversationUseCase>()
    private val screenSettings = MutableStateFlow(AssistantScreenContextSettings())
    private val active = Provider("active", "Active", ProviderType.OPENAI_COMPATIBLE, "https://unused.invalid", "default", false)

    /** Every request the ViewModel made: the system prompt and the user message attachments. */
    private val requests = mutableListOf<Pair<String?, List<Attachment>>>()
    private val replies = ArrayDeque<String>()
    private val saved = mutableListOf<Message>()
    private val updated = mutableListOf<Message>()
    private val discarded = mutableListOf<Attachment>()
    private var written = 0
    private val screenFile = Attachment(uri = "file:///data/chat_attachments/screen_1.jpg", mimeType = "image/jpeg")

    private fun TestScope.createViewModel(): AssistantViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        every { recognition.amplitudeData } returns MutableStateFlow(AudioAmplitudeData.Empty)
        every { recognition.isAvailable() } returns true
        every { tts.initialize() } returns flowOf(TtsInitResult.Success)
        every { preferences.assistantVoiceEnabled } returns flowOf(true)
        every { preferences.assistantTtsEnabled } returns flowOf(false)
        every { preferences.assistantVoiceSettings } returns MutableStateFlow(AssistantVoiceSettings())
        every { preferences.assistantScreenContextSettings } returns screenSettings
        every { preferences.systemPrompt } returns flowOf("system")
        every { preferences.reasoningEffort } returns flowOf(ReasoningEffort.HIGH)
        coEvery { providers.getActiveProvider() } returns active
        coEvery { create.withProviderAndModel(any(), any()) } returns "conversation"
        coEvery { conversations.addMessage(capture(slot<Message>().also { })) } answers {
            saved += firstArg<Message>(); firstArg<Message>().id
        }
        coEvery { conversations.updateMessage(any()) } answers {
            val message = firstArg<Message>()
            updated += message
            // Behave like the real repository: a later read sees the updated attachments.
            saved.replaceAll { if (it.id == message.id) message else it }
        }
        coEvery { conversations.getMessages(any()) } answers { saved.toList() }
        coEvery { conversations.getConversation(any()) } returns null
        every { chat.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            val messages = secondArg<List<Message>>()
            requests += arg<String?>(4) to messages.last().attachments
            val reply = replies.removeFirstOrNull() ?: "ok"
            flow {
                emit(StreamingState.Streaming(reply, messageId = "m${requests.size}"))
                emit(StreamingState.Completed(reply, messageId = "m${requests.size}"))
            }
        }
        return AssistantViewModel(
            speechRecognitionManager = recognition,
            textToSpeechManager = tts,
            chatRepository = chat,
            providerRepository = providers,
            appPreferences = preferences,
            createConversationUseCase = create,
            conversationRepository = conversations,
            generateConversationTitleUseCase = mockk<GenerateConversationTitleUseCase>(relaxed = true),
            applicationScope = backgroundScope,
            speechRuntime = mockk<AssistantSpeechRuntime>(relaxed = true),
            deviceActionHandler = mockk<DeviceActionHandler>(relaxed = true),
            discardAttachment = { discarded += it },
            writeScreenshot = { written++; screenFile }
        ).also {
            store.put("assistant", it)
            runCurrent()
        }
    }

    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun capture(withScreenshot: Boolean = true) = ScreenContext(
        capturedAt = 1L,
        screenshot = if (withScreenshot) mockk<Bitmap>(relaxed = true) else null,
        text = "First tweet: cats are great",
        sourcePackage = "com.twitter.android"
    )

    @Test fun `a question about the screen goes out with the screenshot file and the text block`() = runTest {
        val vm = createViewModel()
        vm.onScreenContext(capture())
        vm.sendQuery("what does this first tweet mean")
        runCurrent()
        assertEquals(1, requests.size)
        val (prompt, attachments) = requests.single()
        assertEquals(listOf(screenFile), attachments)
        assertTrue(prompt.orEmpty().contains(ScreenContextPrompt.BLOCK_START))
        assertTrue(prompt.orEmpty().contains("First tweet: cats are great"))
        assertTrue(prompt.orEmpty().contains("com.twitter.android"))
        assertFalse(prompt.orEmpty().contains(ScreenContextPrompt.directive))
        assertEquals(listOf(screenFile), saved.single { it.role == MessageRole.USER }.attachments)
        assertTrue(vm.uiState.value.screenContextUsed)
        assertFalse(vm.uiState.value.screenContextUnavailable)
        assertTrue(discarded.isEmpty())
    }

    @Test fun `an unrelated question is sent without the screen and the model is told it may ask`() = runTest {
        val vm = createViewModel()
        vm.onScreenContext(capture())
        vm.sendQuery("what is the capital of France")
        runCurrent()
        val (prompt, attachments) = requests.single()
        assertTrue(attachments.isEmpty())
        assertFalse(prompt.orEmpty().contains(ScreenContextPrompt.BLOCK_START))
        assertFalse(prompt.orEmpty().contains("cats are great"))
        assertTrue(prompt.orEmpty().contains(ScreenContextPrompt.directive))
        assertFalse(vm.uiState.value.screenContextUsed)
        assertEquals(0, written)
    }

    @Test fun `the directive resends the same turn once with the capture and hides the interim reply`() = runTest {
        val vm = createViewModel()
        vm.onScreenContext(capture())
        replies += ScreenContextPrompt.directive
        replies += "It means the cat is great."
        vm.sendQuery("explain the first one")
        runCurrent()
        assertEquals(2, requests.size)
        assertTrue(requests[0].second.isEmpty())
        assertEquals(listOf(screenFile), requests[1].second)
        assertTrue(requests[1].first.orEmpty().contains(ScreenContextPrompt.BLOCK_START))
        assertEquals(listOf(screenFile), updated.single().attachments)
        assertEquals("It means the cat is great.", vm.uiState.value.response)
        assertFalse(vm.uiState.value.response.contains("<<"))
        assertTrue(vm.uiState.value.screenContextUsed)
        // One user turn, one answer; the directive-only interim reply never reached the list.
        assertEquals(2, vm.messages.value.size)
    }

    @Test fun `never mode sends nothing and does not mention the capture`() = runTest {
        screenSettings.value = AssistantScreenContextSettings(mode = ScreenContextMode.NEVER)
        val vm = createViewModel()
        vm.onScreenContext(capture())
        replies += ScreenContextPrompt.directive
        vm.sendQuery("what does this tweet say")
        runCurrent()
        assertEquals(1, requests.size)
        assertTrue(requests.single().second.isEmpty())
        assertFalse(requests.single().first.orEmpty().contains("SCREEN"))
        assertFalse(vm.uiState.value.screenContextUsed)
        assertFalse(vm.uiState.value.screenContextUnavailable)
        assertEquals(0, written)
    }

    @Test fun `always mode attaches to an unrelated question`() = runTest {
        screenSettings.value = AssistantScreenContextSettings(mode = ScreenContextMode.ALWAYS)
        val vm = createViewModel()
        vm.onScreenContext(capture())
        vm.sendQuery("tell me a joke")
        runCurrent()
        assertEquals(listOf(screenFile), requests.single().second)
        assertTrue(vm.uiState.value.screenContextUsed)
    }

    @Test fun `screen question with nothing captured reports the settings hint once`() = runTest {
        val vm = createViewModel()
        vm.onScreenContext(ScreenContext(capturedAt = 1L))
        vm.sendQuery("what does this tweet mean")
        runCurrent()
        assertTrue(requests.single().second.isEmpty())
        assertTrue(vm.uiState.value.screenContextUnavailable)
        vm.sendQuery("and this one")
        runCurrent()
        assertFalse("the hint is shown once per invocation", vm.uiState.value.screenContextUnavailable)
        vm.onScreenContext(null)
        vm.onScreenContext(ScreenContext(capturedAt = 2L))
        vm.sendQuery("translate this")
        runCurrent()
        assertTrue("a new invocation may show it again", vm.uiState.value.screenContextUnavailable)
    }

    @Test fun `text only capture still goes out as the prompt block for a text-only model`() = runTest {
        val vm = createViewModel()
        vm.onScreenContext(capture(withScreenshot = false))
        vm.sendQuery("summarize this page")
        runCurrent()
        val (prompt, attachments) = requests.single()
        assertTrue(attachments.isEmpty())
        assertTrue(prompt.orEmpty().contains("cats are great"))
        assertTrue(vm.uiState.value.screenContextUsed)
        assertEquals(0, written)
    }

    @Test fun `removing the staged chip sends the question without the screen`() = runTest {
        val vm = createViewModel()
        vm.onScreenContext(capture())
        vm.updateTextInput("what is this tweet about")
        runCurrent()
        assertEquals(screenFile, vm.screenAttachment.value)
        vm.removeScreenContext()
        assertNull(vm.screenAttachment.value)
        assertEquals(listOf(screenFile), discarded)
        replies += ScreenContextPrompt.directive
        vm.sendQuery("what is this tweet about")
        runCurrent()
        assertEquals(1, requests.size)
        assertTrue(requests.single().second.isEmpty())
        assertFalse(vm.uiState.value.screenContextUsed)
    }

    @Test fun `hiding the session discards a staged chip`() = runTest {
        val vm = createViewModel()
        vm.onScreenContext(capture())
        vm.updateTextInput("read this message")
        runCurrent()
        assertEquals(screenFile, vm.screenAttachment.value)
        vm.onScreenContext(null)
        assertNull(vm.screenAttachment.value)
        assertEquals(listOf(screenFile), discarded)
    }
}
