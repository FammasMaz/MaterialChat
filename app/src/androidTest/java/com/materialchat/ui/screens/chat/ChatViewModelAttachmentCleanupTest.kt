package com.materialchat.ui.screens.chat

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.data.local.preferences.EncryptedPreferences
import com.materialchat.domain.model.Attachment
import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.usecase.BranchConversationUseCase
import com.materialchat.domain.usecase.ExportConversationUseCase
import com.materialchat.domain.usecase.GetConversationsUseCase
import com.materialchat.domain.usecase.ManageBookmarksUseCase
import com.materialchat.domain.usecase.ManagePersonasUseCase
import com.materialchat.domain.usecase.ManageProvidersUseCase
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Picked images belong to the composer until they are sent. Leaving the chat
 * abandons them, so the backing files must be deleted instead of piling up in
 * `filesDir` forever.
 */
@RunWith(AndroidJUnit4::class)
class ChatViewModelAttachmentCleanupTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val created = mutableListOf<File>()

    @After
    fun tearDown() {
        created.forEach { it.delete() }
    }

    private fun imageFile(): File =
        File(context.filesDir, "${AttachmentFiles.PICKED_DIR}/vm-${UUID.randomUUID()}.jpg").apply {
            requireNotNull(parentFile).mkdirs()
            writeText("image bytes")
            created += this
        }

    private fun buildViewModel(conversationId: String): Pair<ChatViewModel, ViewModelStore> {
        val conversation = Conversation(
            id = conversationId,
            title = "Chat",
            providerId = "p1",
            modelName = "m1",
            createdAt = 1L,
            updatedAt = 1L
        )
        val provider = Provider(
            id = "p1",
            name = "Provider",
            type = com.materialchat.domain.model.ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.test.com",
            defaultModel = "m1",
            requiresApiKey = false,
            isActive = true
        )
        val getConversations = mockk<GetConversationsUseCase>(relaxed = true)
        coEvery { getConversations.getConversation(conversationId) } returns conversation
        coEvery { getConversations.getMessages(conversationId) } returns emptyList()
        every { getConversations.observeConversation(conversationId) } returns flowOf(conversation)
        every { getConversations.observeMessages(conversationId) } returns flowOf(emptyList())
        every { getConversations.observeSiblingBranches(any(), any()) } returns flowOf(emptyList())

        val providers = mockk<ManageProvidersUseCase>(relaxed = true)
        every { providers.observeProviders() } returns flowOf(listOf(provider))

        val prefs = mockk<AppPreferences>(relaxed = true)
        every { prefs.systemPrompt } returns flowOf("")
        every { prefs.hapticsEnabled } returns flowOf(true)
        every { prefs.chatHapticsEnabled } returns flowOf(true)
        every { prefs.reasoningEffort } returns flowOf(com.materialchat.domain.model.ReasoningEffort.HIGH)
        every { prefs.beautifulModelNamesEnabled } returns flowOf(true)
        every { prefs.alwaysShowThinking } returns flowOf(false)
        every { prefs.showTokenCounter } returns flowOf(false)
        every { prefs.webSearchEnabled } returns flowOf(false)
        every { prefs.webSearchProvider } returns flowOf("EXA")
        every { prefs.searxngBaseUrl } returns flowOf("")
        every { prefs.webSearchMaxResults } returns flowOf(3)

        val savedState = SavedStateHandle(
            mapOf(Screen.Chat.ARG_CONVERSATION_ID to conversationId)
        )

        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                ChatViewModel(
                    savedStateHandle = savedState,
                    getConversationsUseCase = getConversations,
                    sendMessageUseCase = mockk<SendMessageUseCase>(relaxed = true),
                    regenerateResponseUseCase = mockk<RegenerateResponseUseCase>(relaxed = true),
                    exportConversationUseCase = mockk<ExportConversationUseCase>(relaxed = true),
                    conversationRepository = mockk<ConversationRepository>(relaxed = true),
                    branchConversationUseCase = mockk<BranchConversationUseCase>(relaxed = true),
                    redoWithModelUseCase = mockk<RedoWithModelUseCase>(relaxed = true),
                    manageBookmarksUseCase = mockk<ManageBookmarksUseCase>(relaxed = true),
                    runFusionUseCase = mockk<RunFusionUseCase>(relaxed = true),
                    manageProvidersUseCase = providers,
                    managePersonasUseCase = mockk<ManagePersonasUseCase>(relaxed = true),
                    appPreferences = prefs,
                    encryptedPreferences = mockk<EncryptedPreferences>(relaxed = true),
                    appNotificationManager = mockk<AppNotificationManager>(relaxed = true),
                    ioDispatcher = Dispatchers.IO,
                    applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                    appContext = context.applicationContext
                ) as T
        }

        val viewModel = ViewModelProvider(store, factory)[ChatViewModel::class.java]
        return viewModel to store
    }

    private fun awaitSendableState(viewModel: ChatViewModel) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (viewModel.uiState.value is ChatUiState.Success) return
            Thread.sleep(20)
        }
        throw AssertionError("Chat state never became ready: ${viewModel.uiState.value}")
    }

    @Test
    fun leavingChatWithoutSendingDeletesPickedImage() {
        val file = imageFile()
        val (viewModel, store) = buildViewModel("conv-${UUID.randomUUID()}")
        awaitSendableState(viewModel)

        viewModel.addAttachment(
            Attachment(uri = Uri.fromFile(file).toString(), mimeType = "image/jpeg")
        )
        assertTrue("Attachment must be pending before cleanup", file.exists())

        store.clear()

        assertFalse("Abandoned attachment leaked: ${file.name}", file.exists())
    }

    @Test
    fun cleanupLeavesFilesOutsideTheAttachmentDirectoryAlone() {
        val foreign = File(context.cacheDir, "shared-${UUID.randomUUID()}.jpg").apply {
            writeText("not ours")
            created += this
        }
        val (viewModel, store) = buildViewModel("conv-${UUID.randomUUID()}")
        awaitSendableState(viewModel)

        viewModel.addAttachment(
            Attachment(uri = Uri.fromFile(foreign).toString(), mimeType = "image/jpeg")
        )
        store.clear()

        assertTrue("Only app-owned attachment files may be deleted", foreign.exists())
    }
}
