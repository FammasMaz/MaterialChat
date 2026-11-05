package com.materialchat.ui.screens.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.model.AiModel
import com.materialchat.domain.model.Attachment
import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.FusionResult
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ProviderType
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.usecase.GetConversationsUseCase
import com.materialchat.domain.usecase.ManageProvidersUseCase
import com.materialchat.domain.usecase.RunFusionUseCase
import com.materialchat.domain.usecase.SendMessageUseCase
import com.materialchat.ui.navigation.Screen
import com.materialchat.util.AttachmentImporter
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.coroutines.CoroutineContext

/** Pauses the actual observer's off-Main estimate, not a detached state-merge helper. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class ChatViewModelDraftStateTest(private val fusion: Boolean) {
    private val conversation = Conversation(
        id = "conversation", title = "Saved chat", providerId = "provider", modelName = "model"
    )
    private val conversations = MutableStateFlow<Conversation?>(conversation)
    private val messages = MutableStateFlow<List<Message>>(emptyList())
    private val repository = mockk<ConversationRepository>(relaxed = true)
    private val providers = mockk<ManageProvidersUseCase>(relaxed = true)
    private val preferences = mockk<AppPreferences>(relaxUnitFun = true)
    private val send = mockk<SendMessageUseCase>()
    private val runFusion = mockk<RunFusionUseCase>()
    private val context = mockk<Context>(relaxed = true)
    private val store = ViewModelStore()
    private val image = Attachment(uri = "file:///private/chat_attachments/draft.jpg", mimeType = "image/jpeg")
    private val saved = mutableListOf<Message>()
    private var saveGate = CompletableDeferred(Unit)
    private var refuseSave = false

    @Before
    fun setUp() {
        mockkObject(AttachmentImporter)
        every { AttachmentImporter.discard(any(), any()) } returns Unit
        coEvery { repository.getConversation(conversation.id) } returns conversation
        coEvery { repository.getMessages(conversation.id) } answers { messages.value }
        every { repository.observeConversation(conversation.id) } returns conversations
        every { repository.observeMessages(conversation.id) } returns messages
        every { providers.observeProviders() } returns flowOf(listOf(Provider(
            id = "provider", name = "Test", type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://unused.invalid", defaultModel = "model", requiresApiKey = false
        )))
        every { preferences.systemPrompt } returns flowOf(AppPreferences.DEFAULT_SYSTEM_PROMPT)
        every { preferences.hapticsEnabled } returns flowOf(true)
        every { preferences.chatHapticsEnabled } returns flowOf(true)
        every { preferences.reasoningEffort } returns flowOf(ReasoningEffort.HIGH)
        every { preferences.beautifulModelNamesEnabled } returns flowOf(true)
        every { preferences.alwaysShowThinking } returns flowOf(false)
        every { preferences.showTokenCounter } returns flowOf(true)
        every { preferences.webSearchEnabled } returns flowOf(false)
        every { preferences.webSearchProvider } returns flowOf("EXA")
        every { preferences.searxngBaseUrl } returns flowOf("https://unused.invalid")
        every { preferences.webSearchMaxResults } returns flowOf(5)
        every { preferences.rememberLastModel } returns flowOf(false)
        every { send(any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            val content = secondArg<String>()
            val attachments = thirdArg<List<Attachment>>()
            val acknowledge = lastArg<() -> Unit>()
            flow<StreamingState> {
                saveDraft(content, attachments, acknowledge)
            }
        }
        every { runFusion(any(), any(), any(), any(), any(), any(), any()) } answers {
            val content = secondArg<String>()
            val attachments = arg<List<Attachment>>(5)
            val acknowledge = lastArg<() -> Unit>()
            flow<FusionResult> {
                saveDraft(content, attachments, acknowledge)
            }
        }
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
        unmockkObject(AttachmentImporter)
    }

    @Test
    fun `delayed observation cannot return committed images to draft cleanup`() = runTest {
        val (viewModel, io) = createViewModel()
        viewModel.addAttachment(image)
        viewModel.updateInputText("saved draft")
        val fusionConfig = state(viewModel).fusionConfig

        pauseObservation(io)
        viewModel.sendMessage()
        runCurrent()
        assertEquals(listOf(image), saved.single().attachments)
        assertTrue(state(viewModel).pendingAttachments.isEmpty())
        viewModel.updateInputText("new typing")

        io.resume()
        runCurrent()

        assertEquals("Updated title", state(viewModel).conversationTitle)
        assertEquals("new typing", state(viewModel).inputText)
        assertTrue("An acknowledged image is no longer a draft", state(viewModel).pendingAttachments.isEmpty())
        assertEquals(fusionConfig, state(viewModel).fusionConfig)
        assertTrue(state(viewModel).streamingState is StreamingState.Error)
        store.clear()
        verify(exactly = 0) { AttachmentImporter.discard(context, image) }
    }

    @Test
    fun `delayed observation cannot erase a draft restored after a refused save`() = runTest {
        saveGate = CompletableDeferred()
        refuseSave = true
        val (viewModel, io) = createViewModel()
        viewModel.addAttachment(image)
        viewModel.updateInputText("refused draft")
        viewModel.sendMessage()
        runCurrent()
        assertTrue(state(viewModel).pendingAttachments.isEmpty())

        pauseObservation(io)
        saveGate.complete(Unit)
        runCurrent()
        assertEquals(listOf(image), state(viewModel).pendingAttachments)
        viewModel.updateInputText("new typing")

        io.resume()
        runCurrent()

        assertEquals("Updated title", state(viewModel).conversationTitle)
        assertEquals("new typing", state(viewModel).inputText)
        assertEquals(listOf(image), state(viewModel).pendingAttachments)
        assertTrue(state(viewModel).streamingState is StreamingState.Error)
        assertTrue(saved.isEmpty())
        store.clear()
        verify(exactly = 1) { AttachmentImporter.discard(context, image) }
    }

    @Test
    fun `delayed observation preserves current model quote and editing controls`() = runTest {
        val quote = Message(id = "quote", conversationId = conversation.id, role = MessageRole.ASSISTANT, content = "quoted")
        messages.value = listOf(quote)
        val (viewModel, io) = createViewModel()
        pauseObservation(io)

        viewModel.updateInputText("new draft")
        viewModel.addAttachment(image)
        viewModel.quoteMessage(quote.id)
        viewModel.startEditingMessage(quote.id)
        viewModel.updateEditingText("new edit")
        viewModel.updateReasoningEffort(ReasoningEffort.LOW)
        viewModel.showExportOptions()
        viewModel.changeModel(AiModel("new-model", providerId = "provider"))
        runCurrent()
        val current = state(viewModel)
        assertEquals("new-model", current.modelName)

        io.resume()
        runCurrent()

        val latest = state(viewModel)
        assertEquals(current.modelName, latest.modelName)
        assertEquals(current.inputText, latest.inputText)
        assertEquals(current.pendingAttachments, latest.pendingAttachments)
        assertEquals(current.quotedMessage, latest.quotedMessage)
        assertEquals(current.editingMessageId, latest.editingMessageId)
        assertEquals(current.editingText, latest.editingText)
        assertEquals(current.reasoningEffort, latest.reasoningEffort)
        assertEquals(current.showExportSheet, latest.showExportSheet)
        assertEquals(current.contextWindowUsage, latest.contextWindowUsage)
    }

    private suspend fun saveDraft(content: String, attachments: List<Attachment>, acknowledge: () -> Unit) {
        saveGate.await()
        check(!refuseSave) { "Save refused" }
        saved += Message(conversationId = conversation.id, role = MessageRole.USER, content = content, attachments = attachments)
        acknowledge()
        // End before provider work without sending a second database emission that
        // could accidentally mask the delayed observer's stale assignment.
        error("Controlled post-save failure")
    }

    private fun TestScope.pauseObservation(io: PausingDispatcher) {
        io.pauseNext()
        conversations.value = conversation.copy(title = "Updated title")
        runCurrent()
        assertTrue("The real observer must be suspended in its estimate", io.isPaused)
    }

    private fun TestScope.createViewModel(): Pair<ChatViewModel, PausingDispatcher> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val io = PausingDispatcher(dispatcher)
        val viewModel = ChatViewModel(
            savedStateHandle = SavedStateHandle(mapOf(Screen.Chat.ARG_CONVERSATION_ID to conversation.id)),
            getConversationsUseCase = GetConversationsUseCase(repository),
            sendMessageUseCase = send,
            regenerateResponseUseCase = mockk(relaxed = true),
            exportConversationUseCase = mockk(relaxed = true),
            conversationRepository = repository,
            branchConversationUseCase = mockk(relaxed = true),
            redoWithModelUseCase = mockk(relaxed = true),
            manageBookmarksUseCase = mockk(relaxed = true),
            runFusionUseCase = runFusion,
            manageProvidersUseCase = providers,
            managePersonasUseCase = mockk(relaxed = true),
            appPreferences = preferences,
            encryptedPreferences = mockk(relaxed = true),
            appNotificationManager = mockk(relaxed = true),
            ioDispatcher = io,
            applicationScope = backgroundScope,
            appContext = context
        )
        store.put("chat", viewModel)
        runCurrent()
        assertTrue(viewModel.uiState.value is ChatUiState.Success)
        if (fusion) {
            viewModel.toggleFusionModel(AiModel("a", providerId = "provider"))
            viewModel.toggleFusionModel(AiModel("b", providerId = "provider"))
            viewModel.setFusionJudgeModel(AiModel("judge", providerId = "provider"))
            viewModel.confirmFusionConfig()
        }
        return viewModel to io
    }

    private fun state(viewModel: ChatViewModel): ChatUiState.Success = viewModel.uiState.value as ChatUiState.Success

    private class PausingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        private var pauseNext = false
        private var held: Pair<CoroutineContext, Runnable>? = null
        val isPaused: Boolean get() = held != null

        fun pauseNext() {
            check(!pauseNext && held == null)
            pauseNext = true
        }

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (pauseNext) {
                pauseNext = false
                held = context to block
            } else {
                delegate.dispatch(context, block)
            }
        }

        fun resume() {
            val (context, block) = checkNotNull(held)
            held = null
            delegate.dispatch(context, block)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "fusion={0}")
        fun pipelines(): List<Array<Boolean>> = listOf(arrayOf(false), arrayOf(true))
    }
}
