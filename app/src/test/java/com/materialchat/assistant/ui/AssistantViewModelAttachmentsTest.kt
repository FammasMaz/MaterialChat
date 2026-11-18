package com.materialchat.assistant.ui

import androidx.lifecycle.ViewModelStore
import com.materialchat.assistant.actions.DeviceActionHandler
import com.materialchat.assistant.attach.AssistantAttachMode
import com.materialchat.assistant.attach.AssistantAttachmentInbox
import com.materialchat.assistant.voice.AssistantSpeechRuntime
import com.materialchat.assistant.voice.AudioAmplitudeData
import com.materialchat.assistant.voice.SpeechRecognitionManager
import com.materialchat.assistant.voice.TextToSpeechManager
import com.materialchat.assistant.voice.TtsInitResult
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.data.local.preferences.AssistantScreenContextSettings
import com.materialchat.data.local.preferences.AssistantVoiceSettings
import com.materialchat.domain.model.Attachment
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.repository.ProviderRepository
import com.materialchat.domain.usecase.CreateConversationUseCase
import com.materialchat.domain.usecase.GenerateConversationTitleUseCase
import com.materialchat.util.AttachmentLimits
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The inbox hand-off between the attach activity and the overlay: nothing is lost when the
 * session (and its ViewModel) is destroyed while the picker is on top, and nothing is taken
 * twice when two ViewModels overlap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModelAttachmentsTest {
    private val stores = mutableListOf<ViewModelStore>()
    private val recognition = mockk<SpeechRecognitionManager>(relaxed = true)
    private val tts = mockk<TextToSpeechManager>(relaxed = true)
    private val preferences = mockk<AppPreferences>()
    private val discarded = mutableListOf<Attachment>()

    private fun photo(name: String) = Attachment(uri = "file:///data/chat_attachments/$name.jpg", mimeType = "image/jpeg")

    private fun TestScope.createViewModel(inbox: AssistantAttachmentInbox): AssistantViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        every { recognition.amplitudeData } returns MutableStateFlow(AudioAmplitudeData.Empty)
        every { recognition.isAvailable() } returns true
        every { tts.initialize() } returns flowOf(TtsInitResult.Success)
        every { preferences.assistantVoiceEnabled } returns flowOf(true)
        every { preferences.assistantTtsEnabled } returns flowOf(true)
        every { preferences.assistantVoiceSettings } returns MutableStateFlow(AssistantVoiceSettings())
        every { preferences.systemPrompt } returns flowOf("system")
        every { preferences.reasoningEffort } returns flowOf(ReasoningEffort.HIGH)
        every { preferences.assistantScreenContextSettings } returns flowOf(AssistantScreenContextSettings())
        val store = ViewModelStore().also { stores += it }
        return AssistantViewModel(
            speechRecognitionManager = recognition,
            textToSpeechManager = tts,
            chatRepository = mockk<ChatRepository>(relaxed = true),
            providerRepository = mockk<ProviderRepository>(relaxed = true),
            appPreferences = preferences,
            createConversationUseCase = mockk<CreateConversationUseCase>(relaxed = true),
            conversationRepository = mockk<ConversationRepository>(relaxed = true),
            generateConversationTitleUseCase = mockk<GenerateConversationTitleUseCase>(relaxed = true),
            applicationScope = backgroundScope,
            speechRuntime = mockk<AssistantSpeechRuntime>(relaxed = true),
            deviceActionHandler = mockk<DeviceActionHandler>(relaxed = true),
            attachmentInbox = inbox,
            discardAttachment = { discarded += it }
        ).also {
            store.put("assistant", it)
            runCurrent()
        }
    }

    @After fun tearDown() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
    }

    @Test fun `photos delivered while no ViewModel exists are taken by the next one`() = runTest {
        val inbox = AssistantAttachmentInbox()
        // The session that opened the picker was destroyed; the activity still publishes.
        inbox.publish(listOf(photo("a"), photo("b")))
        val vm = createViewModel(inbox)
        assertEquals(listOf(photo("a").uri, photo("b").uri), vm.pendingAttachments.value.map { it.uri })
        assertTrue(inbox.pending.value.isEmpty())
        assertTrue(discarded.isEmpty())
    }

    @Test fun `photos delivered while the ViewModel lives are taken as they arrive`() = runTest {
        val inbox = AssistantAttachmentInbox()
        val vm = createViewModel(inbox)
        assertTrue(vm.pendingAttachments.value.isEmpty())
        inbox.publish(listOf(photo("a")))
        runCurrent()
        assertEquals(listOf(photo("a").uri), vm.pendingAttachments.value.map { it.uri })
        inbox.publish(listOf(photo("b")))
        runCurrent()
        assertEquals(2, vm.pendingAttachments.value.size)
        assertTrue(inbox.pending.value.isEmpty())
    }

    @Test fun `a delivery is drained exactly once across a recreated ViewModel`() = runTest {
        val inbox = AssistantAttachmentInbox()
        inbox.publish(listOf(photo("a")))
        val first = createViewModel(inbox)
        assertEquals(1, first.pendingAttachments.value.size)
        // Session destroyed and recreated: the new ViewModel must not see the same photo again.
        stores.removeAt(0).clear()
        val second = createViewModel(inbox)
        assertTrue(second.pendingAttachments.value.isEmpty())
        inbox.publish(listOf(photo("b")))
        runCurrent()
        assertEquals(listOf(photo("b").uri), second.pendingAttachments.value.map { it.uri })
    }

    @Test fun `two live ViewModels never both take the same photo`() = runTest {
        val inbox = AssistantAttachmentInbox()
        val first = createViewModel(inbox)
        val second = createViewModel(inbox)
        inbox.publish(listOf(photo("a")))
        runCurrent()
        val total = first.pendingAttachments.value.size + second.pendingAttachments.value.size
        assertEquals(1, total)
    }

    @Test fun `deliveries beyond the per-message limit are discarded and reported`() = runTest {
        val inbox = AssistantAttachmentInbox()
        val vm = createViewModel(inbox)
        inbox.publish((1..AttachmentLimits.MAX_PER_MESSAGE).map { photo("p$it") })
        runCurrent()
        assertEquals(AttachmentLimits.MAX_PER_MESSAGE, vm.pendingAttachments.value.size)
        assertNull(vm.uiState.value.notice)
        inbox.publish(listOf(photo("extra")))
        runCurrent()
        assertEquals(AttachmentLimits.MAX_PER_MESSAGE, vm.pendingAttachments.value.size)
        assertEquals(listOf(photo("extra").uri), discarded.map { it.uri })
        assertEquals(AttachmentLimits.limitNotice(), vm.uiState.value.notice)
    }

    @Test fun `attach request carries the remaining room and is refused when full`() = runTest {
        val inbox = AssistantAttachmentInbox()
        val vm = createViewModel(inbox)
        val events = mutableListOf<AssistantEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        inbox.publish(listOf(photo("a")))
        runCurrent()
        vm.requestAttach(AssistantAttachMode.Gallery)
        runCurrent()
        val attach = events.single() as AssistantEvent.Attach
        assertEquals(AssistantAttachMode.Gallery, attach.mode)
        assertEquals(AttachmentLimits.MAX_PER_MESSAGE - 1, attach.room)
        inbox.publish((2..AttachmentLimits.MAX_PER_MESSAGE).map { photo("p$it") })
        runCurrent()
        vm.requestAttach(AssistantAttachMode.Camera)
        runCurrent()
        assertEquals(1, events.size)
        assertEquals(AttachmentLimits.limitNotice(), vm.uiState.value.notice)
    }

    @Test fun `typed text survives the hide for the picker and returns with the photo`() = runTest {
        val inbox = AssistantAttachmentInbox()
        val vm = createViewModel(inbox)
        vm.onOverlayShown()
        vm.updateTextInput("what is this")
        vm.requestAttach(AssistantAttachMode.Camera)
        runCurrent()
        // The session hides while the camera is up.
        vm.onOverlayHidden()
        assertEquals("what is this", vm.uiState.value.textInput)
        inbox.publish(listOf(photo("shot")))
        runCurrent()
        vm.onOverlayShown(resumeAfterAttach = true)
        assertEquals("what is this", vm.uiState.value.textInput)
        assertEquals(1, vm.pendingAttachments.value.size)
        // A plain hide (no attach in progress) still clears the draft.
        vm.onOverlayHidden()
        assertEquals("", vm.uiState.value.textInput)
    }

    @Test fun `removing an attachment deletes its file and a new chat discards the rest`() = runTest {
        val inbox = AssistantAttachmentInbox()
        val vm = createViewModel(inbox)
        inbox.publish(listOf(photo("a"), photo("b")))
        runCurrent()
        vm.removeAttachment(photo("a"))
        assertEquals(listOf(photo("b").uri), vm.pendingAttachments.value.map { it.uri })
        assertEquals(listOf(photo("a").uri), discarded.map { it.uri })
        vm.removeAttachment(photo("missing"))
        assertEquals(1, discarded.size)
        vm.startNewChat()
        assertTrue(vm.pendingAttachments.value.isEmpty())
        assertEquals(listOf(photo("a").uri, photo("b").uri), discarded.map { it.uri })
    }
}
