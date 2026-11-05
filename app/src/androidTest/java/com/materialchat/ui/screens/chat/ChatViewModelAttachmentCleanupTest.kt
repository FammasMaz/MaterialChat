package com.materialchat.ui.screens.chat

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.materialchat.data.local.database.MaterialChatDatabase
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.data.local.preferences.EncryptedPreferences
import com.materialchat.data.repository.BookmarkRepositoryImpl
import com.materialchat.data.repository.ConversationRepositoryImpl
import com.materialchat.data.repository.MemoryRepositoryImpl
import com.materialchat.data.repository.PersonaRepositoryImpl
import com.materialchat.data.repository.ProviderRepositoryImpl
import com.materialchat.domain.model.AiModel
import com.materialchat.domain.model.Attachment
import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ProviderType
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.repository.LocalModelRepository
import com.materialchat.domain.repository.WebSearchRepository
import com.materialchat.domain.usecase.BranchConversationUseCase
import com.materialchat.domain.usecase.ExportConversationUseCase
import com.materialchat.domain.usecase.ExtractMemoriesUseCase
import com.materialchat.domain.usecase.GenerateConversationTitleUseCase
import com.materialchat.domain.usecase.GetConversationsUseCase
import com.materialchat.domain.usecase.ManageBookmarksUseCase
import com.materialchat.domain.usecase.ManagePersonasUseCase
import com.materialchat.domain.usecase.ManageProvidersUseCase
import com.materialchat.domain.usecase.RecallMemoriesUseCase
import com.materialchat.domain.usecase.RedoWithModelUseCase
import com.materialchat.domain.usecase.RegenerateResponseUseCase
import com.materialchat.domain.usecase.RunFusionUseCase
import com.materialchat.domain.usecase.SendMessageUseCase
import com.materialchat.notifications.AppNotificationManager
import com.materialchat.ui.navigation.Screen
import com.materialchat.util.AttachmentFiles
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real ViewModel, use cases, Room and private files on every supported Android API. */
@RunWith(AndroidJUnit4::class)
class ChatViewModelAttachmentCleanupTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val files = mutableListOf<File>()
    private val stores = mutableListOf<ViewModelStore>()
    private val saveGates = mutableListOf<CompletableDeferred<Unit>>()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val conversationId = "conv-${UUID.randomUUID()}"
    private val provider = Provider(
        id = "provider",
        name = "Test provider",
        type = ProviderType.OPENAI_COMPATIBLE,
        baseUrl = "https://unused.invalid",
        defaultModel = "model",
        requiresApiKey = false,
        isActive = true
    )
    private lateinit var database: MaterialChatDatabase
    private lateinit var repository: ConversationRepositoryImpl
    private lateinit var preferences: AppPreferences
    private lateinit var encryptedPreferences: EncryptedPreferences
    private lateinit var providers: ProviderRepositoryImpl
    private val chat = mockk<ChatRepository>(relaxUnitFun = true)
    private val localModels = mockk<LocalModelRepository>(relaxUnitFun = true)
    private val search = mockk<WebSearchRepository>()
    private var previousWebSearchEnabled = false

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, MaterialChatDatabase::class.java).build()
        repository = ConversationRepositoryImpl(database.conversationDao(), database.messageDao(), context)
        preferences = AppPreferences(context)
        previousWebSearchEnabled = preferences.webSearchEnabled.first()
        preferences.setWebSearchEnabled(false)
        encryptedPreferences = EncryptedPreferences(context)
        providers = ProviderRepositoryImpl(database.providerDao(), encryptedPreferences)
        providers.addProvider(provider)
        repository.createConversation(
            Conversation(id = conversationId, title = "Saved chat", providerId = provider.id, modelName = "model")
        )
        coEvery { chat.fetchModels(any(), any()) } returns Result.success(emptyList())
        every { chat.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            flowOf(StreamingState.Error(IllegalStateException("Controlled provider failure")))
    }

    @After
    fun tearDown() = runBlocking {
        saveGates.forEach { it.complete(Unit) }
        onMain { stores.forEach { it.clear() } }
        val job = applicationScope.coroutineContext[Job]!!
        applicationScope.cancel()
        withTimeout(TIMEOUT_MS) { job.join() }
        database.close()
        preferences.setWebSearchEnabled(previousWebSearchEnabled)
        files.forEach { it.delete() }
    }

    @Test
    fun leavingChatWithoutSendingDeletesPickedImage() = runBlocking {
        val (viewModel, store) = buildViewModel()
        val (file, image) = pickedImage()
        onMain { viewModel.addAttachment(image) }
        assertEquals(listOf(image), success(viewModel).pendingAttachments)

        onMain { store.clear() }

        assertFalse("Abandoned attachment leaked", file.exists())
        assertTrue(repository.getMessages(conversationId).isEmpty())
    }

    @Test
    fun editingAMessageKeepsItsAttachedImages() = runBlocking {
        val (file, image) = pickedImage()
        repository.addMessage(Message(conversationId = conversationId, role = MessageRole.USER, content = "first", createdAt = 1L))
        val original = Message(
            conversationId = conversationId,
            role = MessageRole.USER,
            content = "look at this",
            attachments = listOf(image),
            createdAt = 2L
        )
        repository.addMessage(original)
        val (viewModel, store) = buildViewModel()
        awaitState(viewModel) { it.messages.size == 2 }
        onMain {
            viewModel.startEditingMessage(original.id)
            viewModel.updateEditingText("look at this closely")
            viewModel.submitEditedMessage()
        }
        val branch = withTimeout(TIMEOUT_MS) {
            repository.observeBranches(conversationId).first { it.isNotEmpty() }.single()
        }
        val messages = withTimeout(TIMEOUT_MS) {
            repository.observeMessages(branch.id).first { rows -> rows.any { it.content == "look at this closely" } }
        }
        onMain { store.clear() }

        assertEquals(listOf(image), messages.last().attachments)
        assertEquals(original, repository.getMessages(conversationId).last())
        assertTrue("Both conversations still reference the image", file.exists())
    }

    @Test
    fun rejectedOnDeviceSendRestoresTextQuoteAndImage() = runBlocking {
        providers.updateProvider(provider.copy(type = ProviderType.LITERT_LM_LOCAL))
        val quoted = Message(conversationId = conversationId, role = MessageRole.ASSISTANT, content = "Quoted answer")
        repository.addMessage(quoted)
        val (viewModel, store) = buildViewModel()
        awaitState(viewModel) { it.messages.any { row -> row.message.id == quoted.id } }
        val (file, image) = pickedImage()
        onMain {
            viewModel.addAttachment(image)
            viewModel.updateInputText("describe this")
            viewModel.quoteMessage(quoted.id)
            viewModel.sendMessage()
        }
        val restored = awaitState(viewModel) {
            it.streamingState is StreamingState.Error && it.pendingAttachments == listOf(image)
        }

        assertEquals("describe this", restored.inputText)
        assertEquals(quoted, restored.quotedMessage)
        assertEquals(listOf(quoted), repository.getMessages(conversationId))
        assertTrue(file.exists())
        onMain { store.clear() }
        assertFalse("Unsent images are deleted only when the restored draft is abandoned", file.exists())
    }

    @Test
    fun failedSaveRestoresTheDraftWithoutPartialDatabaseRows() = runBlocking {
        val (viewModel, _) = buildViewModel()
        val (file, image) = pickedImage()
        rejectTimestampUpdates()
        onMain {
            viewModel.addAttachment(image)
            viewModel.updateInputText("keep this draft")
            viewModel.sendMessage()
        }
        val restored = awaitState(viewModel) {
            it.streamingState is StreamingState.Error && it.pendingAttachments == listOf(image)
        }

        assertEquals("keep this draft", restored.inputText)
        assertTrue(repository.getMessages(conversationId).isEmpty())
        assertTrue(file.exists())
    }

    @Test
    fun failedFusionSaveRestoresItsDraftToo() = runBlocking {
        val (viewModel, _) = buildViewModel()
        val (file, image) = pickedImage()
        rejectTimestampUpdates()
        onMain {
            enableFusion(viewModel)
            viewModel.addAttachment(image)
            viewModel.updateInputText("fusion draft")
            viewModel.sendMessage()
        }
        val restored = awaitState(viewModel) {
            it.streamingState is StreamingState.Error && it.pendingAttachments == listOf(image)
        }

        assertEquals("fusion draft", restored.inputText)
        assertFalse(restored.isFusionRunning)
        assertTrue(repository.getMessages(conversationId).isEmpty())
        assertTrue(file.exists())
    }

    @Test
    fun stopBeforeSaveRestoresTheDraftWithoutOverwritingNewTyping() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = saveGate()
        val controlled = object : ConversationRepository by repository {
            override suspend fun addMessage(message: Message): String {
                if (message.role == MessageRole.USER) {
                    entered.complete(Unit)
                    release.await()
                    throw IllegalStateException("Save refused")
                }
                return repository.addMessage(message)
            }
        }
        val (viewModel, store) = buildViewModel(controlled)
        val (file, image) = pickedImage()
        val sendJob = startGatedSend(viewModel) {
            viewModel.addAttachment(image)
            viewModel.updateInputText("original draft")
        }
        withTimeout(TIMEOUT_MS) { entered.await() }
        onMain {
            viewModel.updateInputText("new typing")
            viewModel.cancelStreaming()
        }
        release.complete(Unit)
        awaitSendCompletion(sendJob)
        val restored = awaitState(viewModel) { it.pendingAttachments == listOf(image) }

        assertEquals("new typing", restored.inputText)
        assertFalse(restored.isStreaming)
        assertTrue(repository.getMessages(conversationId).isEmpty())
        assertTrue(file.exists())
        onMain { store.clear() }
        assertFalse(file.exists())
    }

    @Test
    fun stopDuringCommittedSaveNeverRestoresOrDeletesSavedImages() = runBlocking {
        assertCommittedSaveSurvivesStop(fusion = false)
    }

    @Test
    fun fusionStopDuringCommittedSaveNeverRestoresOrDeletesSavedImages() = runBlocking {
        assertCommittedSaveSurvivesStop(fusion = true)
    }

    private suspend fun assertCommittedSaveSurvivesStop(fusion: Boolean) {
        val committed = CompletableDeferred<Unit>()
        val release = saveGate()
        val controlled = object : ConversationRepository by repository {
            override suspend fun addMessage(message: Message): String {
                val id = repository.addMessage(message)
                if (message.role == MessageRole.USER) {
                    committed.complete(Unit)
                    release.await()
                }
                return id
            }
        }
        val (viewModel, store) = buildViewModel(controlled)
        val (file, image) = pickedImage()
        val sendJob = startGatedSend(viewModel, fusion) {
            if (fusion) enableFusion(viewModel)
            viewModel.addAttachment(image)
            viewModel.updateInputText("persist this")
        }
        withTimeout(TIMEOUT_MS) { committed.await() }
        onMain { viewModel.cancelStreaming() }
        release.complete(Unit)
        awaitSendCompletion(sendJob)
        // Clearing the real ViewModel runs abandoned-draft deletion. A wrongly
        // restored committed image would now be removed from private storage.
        onMain { store.clear() }
        assertTrue("Committed files must survive Stop and ViewModel cleanup", file.exists())
        val saved = repository.getMessages(conversationId).single()
        assertEquals(MessageRole.USER, saved.role)
        assertEquals(listOf(image), saved.attachments)
        assertTrue(success(viewModel).pendingAttachments.isEmpty())
    }

    @Test
    fun postSaveReadFailureCannotReturnSavedImagesToDestructiveCleanup() = runBlocking {
        val committed = CompletableDeferred<Unit>()
        val controlled = object : ConversationRepository by repository {
            override suspend fun addMessage(message: Message): String {
                val id = repository.addMessage(message)
                if (message.role == MessageRole.USER) committed.complete(Unit)
                return id
            }

            override suspend fun getMessages(conversationId: String): List<Message> {
                check(!committed.isCompleted) { "Controlled post-save database read failure" }
                return repository.getMessages(conversationId)
            }
        }
        val (viewModel, store) = buildViewModel(controlled)
        val (file, image) = pickedImage()
        onMain {
            viewModel.addAttachment(image)
            viewModel.updateInputText("saved image")
            viewModel.sendMessage()
        }
        awaitState(viewModel) { it.streamingState is StreamingState.Error }
        onMain { store.clear() }

        assertTrue(success(viewModel).pendingAttachments.isEmpty())
        assertEquals(listOf(image), repository.getMessages(conversationId).single().attachments)
        assertTrue("A later read failure must not delete the committed file", file.exists())
    }

    @Test
    fun imageGenerationRefusesPickedImagesWithoutClearingTheDraft() = runBlocking {
        val (viewModel, _) = buildViewModel()
        val (file, image) = pickedImage()
        onMain {
            viewModel.addAttachment(image)
            viewModel.updateInputText("change this image")
            viewModel.generateImage()
        }

        assertEquals(listOf(image), success(viewModel).pendingAttachments)
        assertEquals("change this image", success(viewModel).inputText)
        assertFalse(success(viewModel).isStreaming)
        assertTrue(repository.getMessages(conversationId).isEmpty())
        assertTrue(file.exists())
    }

    @Test
    fun cleanupLeavesFilesOutsideTheAttachmentDirectoryAlone() = runBlocking {
        val foreign = File(context.cacheDir, "shared-${UUID.randomUUID()}.jpg").apply {
            writeText("not ours")
            files += this
        }
        val (viewModel, store) = buildViewModel()
        onMain { viewModel.addAttachment(Attachment(uri = Uri.fromFile(foreign).toString(), mimeType = "image/jpeg")) }

        onMain { store.clear() }

        assertTrue("Only app-owned attachment files may be deleted", foreign.exists())
    }

    private suspend fun buildViewModel(
        persistence: ConversationRepository = repository
    ): Pair<ChatViewModel, ViewModelStore> {
        val personas = PersonaRepositoryImpl(database.personaDao())
        val memory = MemoryRepositoryImpl(database.memoryDao(), Dispatchers.IO)
        val branches = BranchConversationUseCase(persistence)
        val title = GenerateConversationTitleUseCase(chat, persistence, providers, localModels, preferences)
        val send = SendMessageUseCase(
            chatRepository = chat,
            conversationRepository = persistence,
            providerRepository = providers,
            personaRepository = personas,
            webSearchRepository = search,
            localModelRepository = localModels,
            appPreferences = preferences,
            generateConversationTitleUseCase = title,
            recallMemoriesUseCase = RecallMemoriesUseCase(memory),
            extractMemoriesUseCase = ExtractMemoriesUseCase(chat, memory, localModels, providers, preferences, Json),
            applicationScope = applicationScope,
            context = context
        )
        val store = ViewModelStore().also { stores += it }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(
                savedStateHandle = SavedStateHandle(mapOf(Screen.Chat.ARG_CONVERSATION_ID to conversationId)),
                getConversationsUseCase = GetConversationsUseCase(persistence),
                sendMessageUseCase = send,
                regenerateResponseUseCase = RegenerateResponseUseCase(chat, persistence, providers, personas, search, context),
                exportConversationUseCase = ExportConversationUseCase(persistence),
                conversationRepository = persistence,
                branchConversationUseCase = branches,
                redoWithModelUseCase = RedoWithModelUseCase(persistence, branches),
                manageBookmarksUseCase = ManageBookmarksUseCase(BookmarkRepositoryImpl(database.bookmarkDao())),
                runFusionUseCase = RunFusionUseCase(chat, providers, persistence, preferences),
                manageProvidersUseCase = ManageProvidersUseCase(providers, chat),
                managePersonasUseCase = ManagePersonasUseCase(personas),
                appPreferences = preferences,
                encryptedPreferences = encryptedPreferences,
                appNotificationManager = AppNotificationManager(context),
                ioDispatcher = Dispatchers.IO,
                applicationScope = applicationScope,
                appContext = context
            ) as T
        }
        lateinit var viewModel: ChatViewModel
        onMain { viewModel = ViewModelProvider(store, factory)[ChatViewModel::class.java] }
        awaitState(viewModel) { true }
        return viewModel to store
    }

    private fun saveGate(): CompletableDeferred<Unit> =
        CompletableDeferred<Unit>().also { saveGates += it }

    /** Capture the real owner job while the repository gate guarantees it is live. */
    private fun startGatedSend(
        viewModel: ChatViewModel,
        fusion: Boolean = false,
        prepare: () -> Unit
    ): Job {
        lateinit var sendJob: Job
        onMain {
            prepare()
            val parent = requireNotNull(
                (if (fusion) viewModel.viewModelScope else applicationScope).coroutineContext[Job]
            )
            val existing = parent.children.toSet()
            viewModel.sendMessage()
            sendJob = parent.children.single { it !in existing }
        }
        return sendJob
    }

    private suspend fun awaitSendCompletion(job: Job) {
        withTimeout(TIMEOUT_MS) { job.join() }
        instrumentation.waitForIdleSync()
    }

    private fun enableFusion(viewModel: ChatViewModel) {
        viewModel.toggleFusionModel(AiModel("model-a", providerId = provider.id))
        viewModel.toggleFusionModel(AiModel("model-b", providerId = provider.id))
        viewModel.setFusionJudgeModel(AiModel("judge", providerId = provider.id))
        viewModel.confirmFusionConfig()
        assertTrue(success(viewModel).fusionConfig.isEnabled)
    }

    private fun pickedImage(): Pair<File, Attachment> {
        val file = File(context.filesDir, "${AttachmentFiles.PICKED_DIR}/vm-${UUID.randomUUID()}.jpg").apply {
            requireNotNull(parentFile).mkdirs()
            writeText("image bytes")
            files += this
        }
        return file to Attachment(uri = Uri.fromFile(file).toString(), mimeType = "image/jpeg")
    }

    private fun rejectTimestampUpdates() {
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER reject_timestamp BEFORE UPDATE OF updated_at ON conversations
            BEGIN
                SELECT RAISE(ABORT, 'timestamp update rejected by regression test');
            END
            """.trimIndent()
        )
    }

    private suspend fun awaitState(
        viewModel: ChatViewModel,
        predicate: (ChatUiState.Success) -> Boolean
    ): ChatUiState.Success = withTimeout(TIMEOUT_MS) {
        viewModel.uiState.first { it is ChatUiState.Success && predicate(it) } as ChatUiState.Success
    }

    private fun success(viewModel: ChatViewModel): ChatUiState.Success =
        viewModel.uiState.value as ChatUiState.Success

    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
