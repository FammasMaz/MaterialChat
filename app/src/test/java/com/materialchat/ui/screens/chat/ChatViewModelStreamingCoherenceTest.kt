package com.materialchat.ui.screens.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ProviderHttpException
import com.materialchat.domain.model.ProviderType
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.usecase.BranchConversationUseCase
import com.materialchat.domain.usecase.GetConversationsUseCase
import com.materialchat.domain.usecase.ManageProvidersUseCase
import com.materialchat.domain.usecase.RegenerateResponseUseCase
import com.materialchat.domain.usecase.SendMessageUseCase
import com.materialchat.ui.navigation.Screen
import com.materialchat.ui.screens.chat.components.ChatErrorCopy
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
 * Streaming / cancel / error coherence at the view-model seam (W6 chat part 4): the bubble,
 * action row, composer, masthead pulse and scroll FAB all read `streamingState` (through
 * `isStreaming`) and the one `ShowSnackbar` per failure, so these transitions are what the
 * surfaces agree on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelStreamingCoherenceTest {
    private val conversation = Conversation(
        id = "conversation", title = "Saved chat", providerId = "provider", modelName = "model"
    )
    private val user = Message(id = "u1", conversationId = conversation.id, role = MessageRole.USER, content = "question")
    private val assistant = Message(id = "a1", conversationId = conversation.id, role = MessageRole.ASSISTANT, content = "answer")
    /** A reply whose stream delivered thinking tokens and nothing else (W8 chat part 4). */
    private val thinkingOnly = Message(
        id = "a2", conversationId = conversation.id, role = MessageRole.ASSISTANT,
        content = "", thinkingContent = "reasoning so far", isStreaming = true
    )
    private val conversations = MutableStateFlow<Conversation?>(conversation)
    private val messages = MutableStateFlow(listOf(user, assistant))
    private val repository = mockk<ConversationRepository>(relaxed = true)
    private val providers = mockk<ManageProvidersUseCase>(relaxed = true)
    private val preferences = mockk<AppPreferences>(relaxUnitFun = true)
    // Unit-returning calls (`cancel(conversationId)` from Stop) need no answer; the streams do.
    private val send = mockk<SendMessageUseCase>(relaxUnitFun = true)
    private val regenerate = mockk<RegenerateResponseUseCase>(relaxUnitFun = true)
    private val branch = mockk<BranchConversationUseCase>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val store = ViewModelStore()

    /** A reply that starts and then stays open until Stop cancels it. */
    private val openStream = flow<StreamingState> {
        emit(StreamingState.Streaming(content = "", thinkingContent = null, messageId = "a2"))
        awaitCancellation()
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `Stop during regenerate returns to Idle with no errored state and no snackbar`() = runTest {
        every { regenerate(any(), any(), any(), any(), any()) } returns openStream
        val (viewModel, events) = createViewModel()

        viewModel.regenerateResponse()
        runCurrent()
        assertTrue(state(viewModel).isStreaming)

        viewModel.cancelStreaming()
        runCurrent()

        assertEquals(StreamingState.Idle, state(viewModel).streamingState)
        assertTrue("no snackbar for a user Stop: $events", events.none { it is ChatEvent.ShowSnackbar })
    }

    @Test
    fun `a failed regenerate raises exactly one classified snackbar and types the error`() = runTest {
        every { regenerate(any(), any(), any(), any(), any()) } returns flow {
            throw ProviderHttpException(429, "raw provider body")
        }
        val (viewModel, events) = createViewModel()

        viewModel.regenerateResponse()
        runCurrent()

        val error = state(viewModel).streamingState as StreamingState.Error
        assertEquals(429, error.httpStatus)
        assertEquals(ChatErrorCopy.RATE_LIMITED, ChatErrorCopy.notice(error))
        val snackbars = events.filterIsInstance<ChatEvent.ShowSnackbar>()
        assertEquals("one snackbar, the class line: $snackbars", listOf(ChatErrorCopy.RATE_LIMITED), snackbars.map { it.message })
    }

    @Test
    fun `a failed edit-and-resend hands the edited text back and raises one classified line`() = runTest {
        val followUp = Message(id = "u2", conversationId = conversation.id, role = MessageRole.USER, content = "second question")
        messages.value = listOf(user, assistant, followUp)
        coEvery { branch(any(), any(), any()) } throws IllegalStateException("Source conversation not found")
        val (viewModel, events) = createViewModel()

        viewModel.startEditingMessage(followUp.id)
        viewModel.updateEditingText("second question, edited")
        viewModel.submitEditedMessage()
        runCurrent()

        val state = state(viewModel)
        assertEquals("the editor reopens on the same message", followUp.id, state.editingMessageId)
        assertEquals("second question, edited", state.editingText)
        assertTrue("no navigation on a failed branch: $events", events.none { it is ChatEvent.NavigateToBranch })
        val snackbars = events.filterIsInstance<ChatEvent.ShowSnackbar>().map { it.message }
        assertEquals(listOf(ChatErrorCopy.COULD_NOT_RESEND_EDIT), snackbars)
        assertTrue(snackbars.none { it.contains("Source conversation") })
    }

    @Test
    fun `a failed branch with a typed status shows the class line and never the exception text`() = runTest {
        coEvery { branch(any(), any(), any()) } throws ProviderHttpException(503, "upstream body")
        val (viewModel, events) = createViewModel()

        viewModel.branchFromMessage(assistant.id)
        runCurrent()

        assertEquals(
            listOf(ChatErrorCopy.SERVER_ERROR),
            events.filterIsInstance<ChatEvent.ShowSnackbar>().map { it.message }
        )
        assertTrue(events.none { it is ChatEvent.NavigateToBranch })
    }

    @Test
    fun `editing cannot start while a reply is streaming`() = runTest {
        every { send(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns openStream
        val (viewModel, _) = createViewModel()
        viewModel.updateInputText("follow-up")
        viewModel.sendMessage()
        runCurrent()
        assertTrue(state(viewModel).isStreaming)

        viewModel.startEditingMessage(user.id)
        runCurrent()

        assertNull(state(viewModel).editingMessageId)
    }

    @Test
    fun `submitting an edit opened before a stream keeps the draft and notices`() = runTest {
        every { send(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns openStream
        val (viewModel, events) = createViewModel()
        viewModel.startEditingMessage(user.id)
        viewModel.updateEditingText("edited question")
        viewModel.updateInputText("follow-up")
        viewModel.sendMessage()
        runCurrent()
        assertTrue(state(viewModel).isStreaming)

        viewModel.submitEditedMessage()
        runCurrent()

        assertEquals(user.id, state(viewModel).editingMessageId)
        assertEquals("edited question", state(viewModel).editingText)
        assertEquals(
            listOf(EDIT_WHILE_STREAMING_NOTICE),
            events.filterIsInstance<ChatEvent.ShowSnackbar>().map { it.message }
        )
        assertTrue(events.none { it is ChatEvent.NavigateToBranch })
    }

    @Test
    fun `Stop on a reply that holds only thinking leaves a cancelled reply, not a failed one`() = runTest {
        every { send(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns openStream
        val (viewModel, events) = createViewModel()
        viewModel.updateInputText("follow-up")
        viewModel.sendMessage()
        runCurrent()
        messages.value = listOf(user, assistant, thinkingOnly)
        runCurrent()
        assertTrue(state(viewModel).isStreaming)

        viewModel.cancelStreaming()
        runCurrent()
        // The use case's Room write (isStreaming = false) lands after the Stop.
        messages.value = listOf(user, assistant, thinkingOnly.copy(isStreaming = false))
        runCurrent()

        val item = state(viewModel).messages.last()
        assertEquals(thinkingOnly.id, item.message.id)
        assertFalse(item.message.isStreaming)
        assertFalse("a Stop is a cancelled reply, never a failed one: $item", item.isErrored)
        assertEquals(StreamingState.Idle, state(viewModel).streamingState)
        assertTrue("no snackbar for a user Stop: $events", events.none { it is ChatEvent.ShowSnackbar })
    }

    @Test
    fun `a reply that failed after only thinking is errored whichever of the Room write and the error lands last`() = runTest {
        every { send(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns flow {
            emit(StreamingState.Streaming(content = "", thinkingContent = thinkingOnly.thinkingContent, messageId = thinkingOnly.id))
            throw ProviderHttpException(503, "raw provider body")
        }
        val (viewModel, events) = createViewModel()

        // Order A: the error lands while Room still marks the reply streaming, the write follows.
        messages.value = listOf(user, assistant, thinkingOnly)
        runCurrent()
        viewModel.updateInputText("follow-up")
        viewModel.sendMessage()
        runCurrent()
        assertTrue(state(viewModel).streamingState is StreamingState.Error)
        messages.value = listOf(user, assistant, thinkingOnly.copy(isStreaming = false))
        runCurrent()
        val afterWrite = state(viewModel).messages.last()
        assertEquals(thinkingOnly.id, afterWrite.message.id)
        assertTrue("errored once the write lands: $afterWrite", afterWrite.isErrored)
        assertFalse("the older reply never turns red", state(viewModel).messages.first { it.message.id == assistant.id }.isErrored)

        // Order B: Room already holds the stopped reply when the error lands (the use case
        // writes isStreaming = false before it emits the Error, so this is the production order).
        val gate = CompletableDeferred<Unit>()
        val second = thinkingOnly.copy(id = "a3")
        every { send(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns flow {
            emit(StreamingState.Streaming(content = "", thinkingContent = second.thinkingContent, messageId = second.id))
            gate.await()
            throw ProviderHttpException(503, "raw provider body")
        }
        viewModel.updateInputText("again")
        viewModel.sendMessage()
        runCurrent()
        assertTrue(state(viewModel).isStreaming)
        messages.value = listOf(user, assistant, thinkingOnly.copy(isStreaming = false), second.copy(isStreaming = false))
        runCurrent()
        assertFalse("not errored while the stream is still open", state(viewModel).messages.last().isErrored)
        gate.complete(Unit)
        runCurrent()
        val afterError = state(viewModel).messages.last()
        assertEquals(second.id, afterError.message.id)
        assertTrue("errored once the error lands: $afterError", afterError.isErrored)

        val snackbars = events.filterIsInstance<ChatEvent.ShowSnackbar>().map { it.message }
        assertEquals(listOf(ChatErrorCopy.SERVER_ERROR, ChatErrorCopy.SERVER_ERROR), snackbars)
    }

    private fun TestScope.createViewModel(): Pair<ChatViewModel, List<ChatEvent>> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
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
        val viewModel = ChatViewModel(
            savedStateHandle = SavedStateHandle(mapOf(Screen.Chat.ARG_CONVERSATION_ID to conversation.id)),
            getConversationsUseCase = GetConversationsUseCase(repository),
            sendMessageUseCase = send,
            regenerateResponseUseCase = regenerate,
            exportConversationUseCase = mockk(relaxed = true),
            conversationRepository = repository,
            branchConversationUseCase = branch,
            redoWithModelUseCase = mockk(relaxed = true),
            manageBookmarksUseCase = mockk(relaxed = true),
            runFusionUseCase = mockk(relaxed = true),
            manageProvidersUseCase = providers,
            managePersonasUseCase = mockk(relaxed = true),
            appPreferences = preferences,
            encryptedPreferences = mockk(relaxed = true),
            appNotificationManager = mockk(relaxed = true),
            ioDispatcher = dispatcher,
            applicationScope = backgroundScope,
            appContext = context
        )
        store.put("chat", viewModel)
        val events = mutableListOf<ChatEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        runCurrent()
        assertTrue(viewModel.uiState.value is ChatUiState.Success)
        return viewModel to events
    }

    private fun state(viewModel: ChatViewModel): ChatUiState.Success = viewModel.uiState.value as ChatUiState.Success
}
