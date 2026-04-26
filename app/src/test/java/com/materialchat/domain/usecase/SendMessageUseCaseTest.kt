package com.materialchat.domain.usecase

import android.content.Context
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ProviderType
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.model.WebSearchConfig
import com.materialchat.domain.model.WebSearchMetadata
import com.materialchat.domain.model.WebSearchProvider
import com.materialchat.domain.model.WebSearchResult
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.repository.PersonaRepository
import com.materialchat.domain.repository.ProviderRepository
import com.materialchat.domain.repository.WebSearchRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SendMessageUseCase].
 */
class SendMessageUseCaseTest {

    private lateinit var chatRepository: ChatRepository
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var providerRepository: ProviderRepository
    private lateinit var personaRepository: PersonaRepository
    private lateinit var webSearchRepository: WebSearchRepository
    private lateinit var appPreferences: AppPreferences
    private lateinit var generateConversationTitleUseCase: GenerateConversationTitleUseCase
    private lateinit var applicationScope: TestScope
    private lateinit var context: Context
    private lateinit var sendMessageUseCase: SendMessageUseCase

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
        title = Conversation.generateDefaultTitle(),
        providerId = "provider-1",
        modelName = "test-model",
        createdAt = 1000L,
        updatedAt = 2000L
    )

    @Before
    fun setup() {
        chatRepository = mockk(relaxUnitFun = true)
        conversationRepository = mockk(relaxUnitFun = true)
        providerRepository = mockk()
        personaRepository = mockk()
        webSearchRepository = mockk()
        appPreferences = mockk()
        generateConversationTitleUseCase = mockk()
        applicationScope = TestScope()
        context = mockk(relaxed = true)

        every { appPreferences.aiGeneratedTitlesEnabled } returns flowOf(false)
        every { appPreferences.defaultImageGenerationModel } returns flowOf(AppPreferences.DEFAULT_IMAGE_GENERATION_MODEL)
        every { appPreferences.defaultImageOutputFormat } returns flowOf("png")
        every { appPreferences.titleGenerationModel } returns flowOf("")

        sendMessageUseCase = SendMessageUseCase(
            chatRepository = chatRepository,
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            personaRepository = personaRepository,
            webSearchRepository = webSearchRepository,
            appPreferences = appPreferences,
            generateConversationTitleUseCase = generateConversationTitleUseCase,
            applicationScope = applicationScope,
            context = context
        )
    }

    @Test
    fun `invoke - emits Starting state first`() = runTest {
        setupSuccessfulFlow()

        val results = invokeUseCase().toList()

        assertTrue(results.first() is StreamingState.Starting)
    }

    @Test
    fun `invoke - saves user message and assistant placeholder`() = runTest {
        setupSuccessfulFlow()
        val capturedMessages = mutableListOf<Message>()
        coEvery { conversationRepository.addMessage(capture(capturedMessages)) } returnsMany listOf("msg-user", "msg-assistant")

        invokeUseCase(userContent = "Hello there!").toList()

        assertEquals(2, capturedMessages.size)
        val userMessage = capturedMessages[0]
        assertEquals(MessageRole.USER, userMessage.role)
        assertEquals("Hello there!", userMessage.content)
        assertEquals("conv-1", userMessage.conversationId)

        val assistantMessage = capturedMessages[1]
        assertEquals(MessageRole.ASSISTANT, assistantMessage.role)
        assertEquals("", assistantMessage.content)
        assertTrue(assistantMessage.isStreaming)
    }

    @Test
    fun `invoke - sends current conversation to chat repository`() = runTest {
        setupSuccessfulFlow()
        val providerSlot = slot<Provider>()
        val modelSlot = slot<String>()
        val reasoningSlot = slot<ReasoningEffort>()
        val systemPromptSlot = slot<String>()

        every {
            chatRepository.sendMessage(
                provider = capture(providerSlot),
                messages = any(),
                model = capture(modelSlot),
                reasoningEffort = capture(reasoningSlot),
                systemPrompt = capture(systemPromptSlot),
                disableTools = any()
            )
        } returns completedFlow("Hello!")

        invokeUseCase(systemPrompt = "Be helpful.").toList()

        assertEquals(testProvider.id, providerSlot.captured.id)
        assertEquals("test-model", modelSlot.captured)
        assertEquals(ReasoningEffort.HIGH, reasoningSlot.captured)
        assertEquals("Be helpful.", systemPromptSlot.captured)
    }

    @Test
    fun `invoke - updates final content and marks streaming false on completion`() = runTest {
        setupSuccessfulFlow()

        invokeUseCase().toList()

        coVerify { conversationRepository.updateMessageContent("msg-assistant", "Hello!") }
        coVerify { conversationRepository.setMessageStreaming("msg-assistant", false) }
        coVerify { conversationRepository.updateMessageDurations("msg-assistant", null, any()) }
    }

    @Test
    fun `invoke - emits Streaming and Completed states with assistant message id`() = runTest {
        setupSuccessfulFlow()

        val results = invokeUseCase().toList()

        assertTrue(results[0] is StreamingState.Starting)
        val streamingState = results[1] as StreamingState.Streaming
        assertEquals("msg-assistant", streamingState.messageId)
        val completedState = results[2] as StreamingState.Completed
        assertEquals("msg-assistant", completedState.messageId)
    }

    @Test
    fun `invoke - falls back to simple title when AI titles are disabled`() = runTest {
        setupSuccessfulFlow()

        invokeUseCase(userContent = "What is the capital of France?").toList()

        coVerify { conversationRepository.updateConversationTitle("conv-1", "What is the capital of France?") }
    }

    @Test
    fun `invoke - does not update title if conversation already has custom title`() = runTest {
        setupSuccessfulFlow(conversation = testConversation.copy(title = "Existing Title"))

        invokeUseCase().toList()

        coVerify(exactly = 0) { conversationRepository.updateConversationTitle(any(), any()) }
        coVerify(exactly = 0) { generateConversationTitleUseCase(any(), any(), any()) }
    }

    @Test
    fun `invoke - handles error state correctly`() = runTest {
        val error = RuntimeException("Network error")
        setupSuccessfulFlow(
            stream = flowOf(
                StreamingState.Streaming(content = "Par", messageId = "provider-id"),
                StreamingState.Error(error = error, partialContent = "Par", messageId = "provider-id")
            )
        )

        val results = invokeUseCase().toList()

        assertTrue(results.last() is StreamingState.Error)
        coVerify { conversationRepository.updateMessageContent("msg-assistant", "Par") }
        coVerify { conversationRepository.setMessageStreaming("msg-assistant", false) }
    }

    @Test
    fun `invoke - handles cancelled state correctly`() = runTest {
        setupSuccessfulFlow(
            stream = flowOf(
                StreamingState.Streaming(content = "Partial", messageId = "provider-id"),
                StreamingState.Cancelled(partialContent = "Partial", messageId = "provider-id")
            )
        )

        val results = invokeUseCase().toList()

        assertTrue(results.last() is StreamingState.Cancelled)
        coVerify { conversationRepository.updateMessageContent("msg-assistant", "Partial") }
        coVerify { conversationRepository.setMessageStreaming("msg-assistant", false) }
    }

    @Test
    fun `invoke - web search injects results, persists sources, and disables provider tools`() = runTest {
        val query = "What changed in Kotlin recently?"
        val messages = listOf(
            Message(
                id = "msg-user",
                conversationId = "conv-1",
                role = MessageRole.USER,
                content = query
            )
        )
        val config = WebSearchConfig(
            isEnabled = true,
            provider = WebSearchProvider.SEARXNG,
            maxResults = 1,
            searxngBaseUrl = "https://search.example.com"
        )
        val metadata = WebSearchMetadata(
            query = query,
            provider = WebSearchProvider.SEARXNG,
            results = listOf(
                WebSearchResult(
                    index = 1,
                    url = "https://kotlinlang.org/news",
                    title = "Kotlin News",
                    snippet = "Kotlin released new tooling updates.",
                    domain = "kotlinlang.org"
                )
            ),
            searchDurationMs = 42L
        )
        val systemPromptSlot = slot<String>()
        val disableToolsSlot = slot<Boolean>()

        setupSuccessfulFlow(messages = messages)
        coEvery { webSearchRepository.search(query, config) } returns Result.success(metadata)
        every {
            chatRepository.sendMessage(
                provider = any(),
                messages = any(),
                model = any(),
                reasoningEffort = any(),
                systemPrompt = capture(systemPromptSlot),
                disableTools = capture(disableToolsSlot)
            )
        } returns completedFlow("Kotlin released new tooling updates. [1]")

        invokeUseCase(userContent = query, webSearchConfig = config).toList()

        assertTrue(systemPromptSlot.captured.orEmpty().contains("[MATERIALCHAT_WEB_SEARCH]"))
        assertTrue(systemPromptSlot.captured.orEmpty().contains("Kotlin News"))
        assertTrue(disableToolsSlot.captured)
        coVerify { conversationRepository.updateMessageWebSearchMetadata("msg-assistant", any()) }
    }

    @Test(expected = IllegalStateException::class)
    fun `invoke - throws exception when conversation not found`() = runTest {
        coEvery { conversationRepository.getConversation("conv-invalid") } returns null

        sendMessageUseCase(
            conversationId = "conv-invalid",
            userContent = "Hi",
            systemPrompt = "System",
            reasoningEffort = ReasoningEffort.HIGH
        ).toList()
    }

    @Test(expected = IllegalStateException::class)
    fun `invoke - throws exception when provider not found`() = runTest {
        coEvery { conversationRepository.getConversation("conv-1") } returns testConversation
        coEvery { providerRepository.getProvider("provider-1") } returns null

        invokeUseCase().toList()
    }

    @Test
    fun `cancel - delegates to chat repository`() {
        every { chatRepository.cancelStreaming() } returns Unit

        sendMessageUseCase.cancel()

        verify { chatRepository.cancelStreaming() }
    }

    private fun invokeUseCase(
        userContent: String = "Hello!",
        systemPrompt: String = "System",
        webSearchConfig: WebSearchConfig = WebSearchConfig()
    ) = sendMessageUseCase(
        conversationId = "conv-1",
        userContent = userContent,
        systemPrompt = systemPrompt,
        reasoningEffort = ReasoningEffort.HIGH,
        webSearchConfig = webSearchConfig
    )

    private fun setupSuccessfulFlow(
        conversation: Conversation = testConversation,
        messages: List<Message> = listOf(
            Message(
                id = "msg-user",
                conversationId = "conv-1",
                role = MessageRole.USER,
                content = "Hello!"
            )
        ),
        stream: kotlinx.coroutines.flow.Flow<StreamingState> = completedFlow("Hello!")
    ) {
        coEvery { conversationRepository.getConversation("conv-1") } returns conversation
        coEvery { providerRepository.getProvider("provider-1") } returns testProvider
        coEvery { conversationRepository.addMessage(any()) } returnsMany listOf("msg-user", "msg-assistant")
        coEvery { conversationRepository.getMessages("conv-1") } returns messages
        coEvery { conversationRepository.updateMessageContent(any(), any()) } returns Unit
        coEvery { conversationRepository.updateMessageContentWithThinking(any(), any(), any()) } returns Unit
        coEvery { conversationRepository.setMessageStreaming(any(), any()) } returns Unit
        coEvery { conversationRepository.updateMessageDurations(any(), any(), any()) } returns Unit
        coEvery { conversationRepository.updateConversationTitle(any(), any()) } returns Unit
        coEvery { conversationRepository.updateConversationTitleAndIcon(any(), any(), any()) } returns Unit
        coEvery { conversationRepository.updateMessageWebSearchMetadata(any(), any()) } returns Unit
        coEvery { generateConversationTitleUseCase(any(), any(), any()) } returns Result.success("Generated Title")
        every {
            chatRepository.sendMessage(
                provider = any(),
                messages = any(),
                model = any(),
                reasoningEffort = any(),
                systemPrompt = any(),
                disableTools = any()
            )
        } returns stream
    }

    private fun completedFlow(content: String) = flowOf(
        StreamingState.Streaming(content = content.take(5), messageId = "provider-id"),
        StreamingState.Completed(finalContent = content, messageId = "provider-id")
    )
}
