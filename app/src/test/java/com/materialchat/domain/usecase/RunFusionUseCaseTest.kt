package com.materialchat.domain.usecase

import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.model.Attachment
import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.FusionConfig
import com.materialchat.domain.model.FusionModelSelection
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ProviderType
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.repository.ProviderRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [RunFusionUseCase].
 *
 * Images attached to a fusion prompt must survive the pipeline: they have to be
 * persisted on the saved user message (so the thread renders them and the files
 * stay referenced) and reach the models in phase 1.
 */
class RunFusionUseCaseTest {

    private lateinit var chatRepository: ChatRepository
    private lateinit var providerRepository: ProviderRepository
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var appPreferences: AppPreferences
    private lateinit var runFusionUseCase: RunFusionUseCase

    private val testProvider = Provider(
        id = "provider-1",
        name = "Test Provider",
        type = ProviderType.OPENAI_COMPATIBLE,
        baseUrl = "https://api.test.com",
        defaultModel = "test-model",
        requiresApiKey = true,
        isActive = true
    )

    private val testConversation = Conversation(
        id = "conv-1",
        title = "Test",
        providerId = "provider-1",
        modelName = "test-model",
        createdAt = 1000L,
        updatedAt = 2000L
    )

    private val fusionConfig = FusionConfig(
        selectedModels = listOf(
            FusionModelSelection("model-a", "provider-1"),
            FusionModelSelection("model-b", "provider-1")
        ),
        judgeModel = FusionModelSelection("judge", "provider-1"),
        isEnabled = true
    )

    private val image = Attachment(
        id = "att-1",
        uri = "file:///data/user/0/app/files/chat_attachments/a.jpg",
        mimeType = "image/jpeg"
    )

    @Before
    fun setup() {
        chatRepository = mockk(relaxUnitFun = true)
        providerRepository = mockk()
        conversationRepository = mockk(relaxUnitFun = true)
        appPreferences = mockk(relaxed = true)

        every { appPreferences.systemPrompt } returns flowOf("")
        coEvery { conversationRepository.getConversation("conv-1") } returns testConversation
        coEvery { conversationRepository.getMessages("conv-1") } returns emptyList()
        coEvery { conversationRepository.addMessage(any()) } returnsMany
            listOf("msg-user", "msg-assistant")
        coEvery { providerRepository.getProvider("provider-1") } returns testProvider
        coEvery { chatRepository.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            flowOf(StreamingState.Completed("answer", "answer", "msg-assistant"))

        runFusionUseCase = RunFusionUseCase(
            chatRepository = chatRepository,
            providerRepository = providerRepository,
            conversationRepository = conversationRepository,
            appPreferences = appPreferences
        )
    }

    private fun invokeUseCase(attachments: List<Attachment>) = runFusionUseCase(
        conversationId = "conv-1",
        userContent = "describe this",
        fusionConfig = fusionConfig,
        systemPrompt = "",
        reasoningEffort = ReasoningEffort.NONE,
        attachments = attachments
    )

    @Test
    fun `attachments are saved on the user message`() = runTest {
        val saved = mutableListOf<Message>()
        coEvery { conversationRepository.addMessage(capture(saved)) } returnsMany
            listOf("msg-user", "msg-assistant")

        invokeUseCase(listOf(image)).toList()

        val userMessage = saved.first { it.role == MessageRole.USER }
        assertEquals(listOf(image), userMessage.attachments)
    }

    @Test
    fun `attachments reach the models queried in phase 1`() = runTest {
        // Echo whatever the use case persisted back out of the repository, so the
        // assertion covers the real save-then-read-history chain.
        val persisted = mutableListOf<Message>()
        coEvery { conversationRepository.addMessage(any()) } answers {
            persisted.add(firstArg())
            "msg-${persisted.size}"
        }
        coEvery { conversationRepository.getMessages("conv-1") } answers { persisted.toList() }

        // Capture every call: phase 1 (per-model) and phase 2 (judge synthesis).
        val sentHistories = mutableListOf<List<Message>>()
        coEvery {
            chatRepository.sendMessage(any(), capture(sentHistories), any(), any(), any(), any(), any(), any())
        } returns flowOf(StreamingState.Completed("answer", "answer", "msg-2"))

        invokeUseCase(listOf(image)).toList()

        assertTrue(
            "Phase 1 history must include the attached image",
            sentHistories.any { history ->
                history.any { message ->
                    message.role == MessageRole.USER && message.attachments.contains(image)
                }
            }
        )
    }

    @Test
    fun `prompts without attachments are unaffected`() = runTest {
        val saved = mutableListOf<Message>()
        coEvery { conversationRepository.addMessage(capture(saved)) } returnsMany
            listOf("msg-user", "msg-assistant")

        invokeUseCase(emptyList()).toList()

        val userMessage = saved.first { it.role == MessageRole.USER }
        assertTrue(userMessage.attachments.isEmpty())
    }
}
