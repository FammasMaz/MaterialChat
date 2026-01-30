package com.materialchat.domain.usecase

import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.model.Conversation
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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 *
 * Tests cover:
 * - Successful message sending and streaming
 * - Error handling
 * - Message persistence
 * - Title generation
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SendMessageUseCaseTest {

    private lateinit var chatRepository: ChatRepository
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var providerRepository: ProviderRepository
    private lateinit var appPreferences: AppPreferences
    private lateinit var generateConversationTitleUseCase: GenerateConversationTitleUseCase
    private lateinit var applicationScope: CoroutineScope
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
        title = "New Chat",
        providerId = "provider-1",
        modelName = "test-model",
        createdAt = 1000L,
        updatedAt = 2000L
    )

    private val testMessages = listOf(
        Message(
            id = "msg-1",
            conversationId = "conv-1",
            role = MessageRole.USER,
            content = "Hello!"
        )
    )

    private val testReasoningEffort = ReasoningEffort.HIGH

    @Before
    fun setup() {
        chatRepository = mockk()
        conversationRepository = mockk()
        providerRepository = mockk()
        appPreferences = mockk()
        generateConversationTitleUseCase = mockk()
        applicationScope = TestScope()

        // Default mock for appPreferences
        every { appPreferences.aiGeneratedTitlesEnabled } returns flowOf(false)
        every { appPreferences.titleGenerationModel } returns flowOf("")

        sendMessageUseCase = SendMessageUseCase(
            chatRepository = chatRepository,
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            appPreferences = appPreferences,
            generateConversationTitleUseCase = generateConversationTitleUseCase,
            applicationScope = applicationScope
        )
    }

    @Test
    fun `invoke - emits Starting state first`() = runTest {
        // Given
        setupSuccessfulFlow()

        // When
        val results = sendMessageUseCase(
            conversationId = "conv-1",
            userContent = "Hello!",
            systemPrompt = "You are helpful.",
            reasoningEffort = testReasoningEffort
        ).toList()

        // Then
        assertTrue(results.first() is StreamingState.Starting)
    }

    @Test
    fun `invoke - saves user message to repository`() = runTest {
        // Given
        coEvery { conversationRepository.getConversation("conv-1") } returns testConversation
        coEvery { providerRepository.getProvider("provider-1") } returns testProvider
        coEvery { conversationRepository.getMessages("conv-1") } returns testMessages
        coEvery { conversationRepository.updateMessageContent(any(), any()) } returns Unit
        coEvery { conversationRepository.setMessageStreaming(any(), any()) } returns Unit
        coEvery { conversationRepository.updateConversationTitle(any(), any()) } returns Unit
        coEvery {
            chatRepository.sendMessage(any(), any(), any(), any(), any())
        } returns flowOf(
            StreamingState.Streaming("Hello", messageId = "msg-assistant"),
            StreamingState.Completed("Hello!", messageId = "msg-assistant")
        )

        val capturedMessages = mutableListOf<Message>()
        coEvery { conversationRepository.addMessage(capture(capturedMessages)) } returnsMany listOf("msg-user", "msg-assistant")

        // When
        sendMessageUseCase(
            conversationId = "conv-1",
            userContent = "Hello there!",
            systemPrompt = "You are helpful.",
            reasoningEffort = testReasoningEffort
        ).toList()

        // Then
        coVerify(exactly = 2) { conversationRepository.addMessage(any()) }
        val userMessage = capturedMessages.first { it.role == MessageRole.USER }
        assertEquals(MessageRole.USER, userMessage.role)
        assertEquals("Hello there!", userMessage.content)
        assertEquals("conv-1", userMessage.conversationId)
    }

    @Test
    fun `invoke - creates assistant placeholder message`() = runTest {
        // Given
        setupSuccessfulFlow()
        val messages = mutableListOf<Message>()
        coEvery { conversationRepository.addMessage(capture(messages)) } returnsMany listOf("msg-user", "msg-assistant")

        // When
        sendMessageUseCase(
            conversationId = "conv-1",
            userContent = "Hello!",
            systemPrompt = "You are helpful.",
            reasoningEffort = testReasoningEffort
        ).toList()

        // Then
        assertEquals(2, messages.size)
        val assistantMessage = messages[1]
        assertEquals(MessageRole.ASSISTANT, assistantMessage.role)
        assertEquals("", assistantMessage.content)
        assertTrue(assistantMessage.isStreaming)
    }

    @Test
    fun `invoke - sends message to chat repository with correct parameters`() = runTest {
        // Given
        setupSuccessfulFlow()
        val providerSlot = slot<Provider>()
        val messagesSlot = slot<List<Message>>()
        val modelSlot = slot<String>()
        val reasoningEffortSlot = slot<ReasoningEffort>()
        val systemPromptSlot = slot<String?>()

        coEvery {
            chatRepository.sendMessage(
                capture(providerSlot),
                capture(messagesSlot),
                capture(modelSlot),
                capture(reasoningEffortSlot),
                captureNullable(systemPromptSlot)
            )
        } returns flowOf(
            StreamingState.Streaming("Hello", messageId = "msg-assistant"),
            StreamingState.Completed("Hello!", messageId = "msg-assistant")
        )

        // When
        sendMessageUseCase(
            conversationId = "conv-1",
            userContent = "Hello!",
            systemPrompt = "Be helpful.",
            reasoningEffort = testReasoningEffort
        ).toList()

        // Then
        assertEquals(testProvider.id, providerSlot.captured.id)
        assertEquals("test-model", modelSlot.captured)
        assertEquals("Be helpful.", systemPromptSlot.captured)
        assertEquals(testReasoningEffort, reasoningEffortSlot.captured)
    }

    @Test
    fun `invoke - updates message content during streaming`() = runTest {
        // Given
        setupSuccessfulFlow()
        coEvery {
            chatRepository.sendMessage(any(), any(), any(), any(), any())
        } returns flowOf(
            StreamingState.Streaming("He", messageId = "msg-assistant"),
            StreamingState.Streaming("Hell", messageId = "msg-assistant"),
            StreamingState.Streaming("Hello", messageId = "msg-assistant"),
            StreamingState.Completed("Hello!", messageId = "msg-assistant")
        )

        // When
        sendMessageUseCase(
            conversationId = "conv-1",
            userContent = "Hi",
            systemPrompt = "System",
            reasoningEffort = testReasoningEffort
        ).toList()

        // Then
        coVerify(atLeast = 3) { conversationRepository.updateMessageContent("msg-assistant", any()) }
    }

    @Test
    fun `invoke - sets streaming to false on completion`() = runTest {
        // Given
        setupSuccessfulFlow()

        // When
        sendMessageUseCase(
            conversationId = "conv-1",
            userContent = "Hi",
            systemPrompt = "System",
            reasoningEffort = testReasoningEffort
        ).toList()

        // Then
        coVerify { conversationRepository.setMessageStreaming("msg-assistant", false) }
    }

    @Test
    fun `invoke - emits Streaming and Completed states`() = runTest {
        // Given
        setupSuccessfulFlow()
        coEvery {
            chatRepository.sendMessage(any(), any(), any(), any(), any())
        } returns flowOf(
            StreamingState.Streaming("Hello", messageId = "msg-assistant"),
            StreamingState.Completed("Hello!", messageId = "msg-assistant")
        )

        // When
        val results = sendMessageUseCase(
            conversationId = "conv-1",
            userContent = "Hi",
            systemPrompt = "System",
            reasoningEffort = testReasoningEffort
        ).toList()

        // Then
        assertTrue(results[0] is StreamingState.Starting)
        assertTrue(results[1] is StreamingState.Streaming)
        assertTrue(results[2] is StreamingState.Completed)
    }

    @Test
    fun `invoke - updates conversation title for first message`() = runTest {
        // Given
        setupSuccessfulFlow()

        // When
        sendMessageUseCase(
            conversationId = "conv-1",
            userContent = "What is the capital of France?",
            systemPrompt = "System",
            reasoningEffort = testReasoningEffort
        ).toList()

        // Then
        coVerify { conversationRepository.updateConversationTitle("conv-1", any()) }
    }

    @Test
    fun `invoke - does not update title if not default`() = runTest {
        // Given
        val existingConversation = testConversation.copy(title = "Existing Title")
        coEvery { conversationRepository.getConversation("conv-1") } returns existingConversation
        coEvery { providerRepository.getProvider("provider-1") } returns testProvider
        coEvery { conversationRepository.addMessage(any()) } returnsMany listOf("msg-user", "msg-assistant")
        coEvery { conversationRepository.getMessages("conv-1") } returns testMessages
        coEvery { conversationRepository.updateMessageContent(any(), any()) } returns Unit
        coEvery { conversationRepository.setMessageStreaming(any(), any()) } returns Unit
        coEvery {
            chatRepository.sendMessage(any(), any(), any(), any(), any())
        } returns flowOf(StreamingState.Completed("Done", messageId = "msg-assistant"))

        // When
        sendMessageUseCase(
            conversationId = "conv-1",
            userContent = "Hello!",
            systemPrompt = "System",
            reasoningEffort = testReasoningEffort
        ).toList()

        // Then
        coVerify(exactly = 0) { conversationRepository.updateConversationTitle(any(), any()) }
    }

    @Test
    fun `invoke - handles error state correctly`() = runTest {
        // Given
        setupSuccessfulFlow()
        val error = RuntimeException("Network error")
        coEvery {
            chatRepository.sendMessage(any(), any(), any(), any(), any())
        } returns flowOf(
            StreamingState.Streaming("Par", messageId = "msg-assistant"),
            StreamingState.Error(error, "Par", messageId = "msg-assistant")
        )

        // When
        val results = sendMessageUseCase(
            conversationId = "conv-1",
            userContent = "Hi",
            systemPrompt = "System",
            reasoningEffort = testReasoningEffort
        ).toList()

        // Then
        assertTrue(results.last() is StreamingState.Error)
        coVerify { conversationRepository.updateMessageContent("msg-assistant", "Par") }
        coVerify { conversationRepository.setMessageStreaming("msg-assistant", false) }
    }

    @Test
    fun `invoke - handles cancelled state correctly`() = runTest {
        // Given
        setupSuccessfulFlow()
        coEvery {
            chatRepository.sendMessage(any(), any(), any(), any(), any())
        } returns flowOf(
            StreamingState.Streaming("Partial", messageId = "msg-assistant"),
            StreamingState.Cancelled("Partial", messageId = "msg-assistant")
        )

        // When
        val results = sendMessageUseCase(
            conversationId = "conv-1",
            userContent = "Hi",
            systemPrompt = "System",
            reasoningEffort = testReasoningEffort
        ).toList()

        // Then
        assertTrue(results.last() is StreamingState.Cancelled)
        coVerify { conversationRepository.updateMessageContent("msg-assistant", "Partial") }
        coVerify { conversationRepository.setMessageStreaming("msg-assistant", false) }
    }

    @Test(expected = IllegalStateException::class)
    fun `invoke - throws exception when conversation not found`() = runTest {
        // Given
        coEvery { conversationRepository.getConversation("conv-invalid") } returns null

        // When
        sendMessageUseCase(
            conversationId = "conv-invalid",
            userContent = "Hi",
            systemPrompt = "System",
            reasoningEffort = testReasoningEffort
        ).toList()

        // Then - exception thrown
    }

    @Test(expected = IllegalStateException::class)
    fun `invoke - throws exception when provider not found`() = runTest {
        // Given
        coEvery { conversationRepository.getConversation("conv-1") } returns testConversation
        coEvery { providerRepository.getProvider("provider-1") } returns null

        // When
        sendMessageUseCase(
            conversationId = "conv-1",
            userContent = "Hi",
            systemPrompt = "System",
            reasoningEffort = testReasoningEffort
        ).toList()

        // Then - exception thrown
    }

    @Test
    fun `invoke - maps message ID correctly in emitted states`() = runTest {
        // Given
        setupSuccessfulFlow()
        coEvery { conversationRepository.addMessage(any()) } returnsMany listOf("user-123", "assistant-456")
        coEvery {
            chatRepository.sendMessage(any(), any(), any(), any(), any())
        } returns flowOf(
            StreamingState.Streaming("Hello", messageId = "original-id"),
            StreamingState.Completed("Hello!", messageId = "original-id")
        )

        // When
        val results = sendMessageUseCase(
            conversationId = "conv-1",
            userContent = "Hi",
            systemPrompt = "System",
            reasoningEffort = testReasoningEffort
        ).toList()

        // Then - states should have the correct assistant message ID
        val streamingState = results[1] as StreamingState.Streaming
        assertEquals("assistant-456", streamingState.messageId)

        val completedState = results[2] as StreamingState.Completed
        assertEquals("assistant-456", completedState.messageId)
    }

    @Test
    fun `cancel - delegates to chat repository`() {
        // Given
        every { chatRepository.cancelStreaming() } returns Unit

        // When
        sendMessageUseCase.cancel()

        // Then
        coVerify { chatRepository.cancelStreaming() }
    }

    // ============================================================================
    // Title Generation Tests
    // ============================================================================

    @Test
    fun `invoke - truncates long messages for title`() = runTest {
        // Given
        setupSuccessfulFlow()
        val longMessage = "A".repeat(100)
        val titleSlot = slot<String>()
        coEvery { conversationRepository.updateConversationTitle("conv-1", capture(titleSlot)) } returns Unit

        // When
        sendMessageUseCase(
            conversationId = "conv-1",
            userContent = longMessage,
            systemPrompt = "System",
            reasoningEffort = testReasoningEffort
        ).toList()

        // Then
        assertTrue(titleSlot.captured.length <= 40)
        assertTrue(titleSlot.captured.endsWith("..."))
    }

    @Test
    fun `invoke - removes newlines from title`() = runTest {
        // Given
        setupSuccessfulFlow()
        val titleSlot = slot<String>()
        coEvery { conversationRepository.updateConversationTitle("conv-1", capture(titleSlot)) } returns Unit

        // When
        sendMessageUseCase(
            conversationId = "conv-1",
            userContent = "Line 1\nLine 2\nLine 3",
            systemPrompt = "System",
            reasoningEffort = testReasoningEffort
        ).toList()

        // Then
        assertTrue(!titleSlot.captured.contains("\n"))
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private fun setupSuccessfulFlow() {
        coEvery { conversationRepository.getConversation("conv-1") } returns testConversation
        coEvery { providerRepository.getProvider("provider-1") } returns testProvider
        coEvery { conversationRepository.addMessage(any()) } returnsMany listOf("msg-user", "msg-assistant")
        coEvery { conversationRepository.getMessages("conv-1") } returns testMessages
        coEvery { conversationRepository.updateMessageContent(any(), any()) } returns Unit
        coEvery { conversationRepository.setMessageStreaming(any(), any()) } returns Unit
        coEvery { conversationRepository.updateConversationTitle(any(), any()) } returns Unit
        coEvery {
            chatRepository.sendMessage(any(), any(), any(), any(), any())
        } returns flowOf(
            StreamingState.Streaming("Hello", messageId = "msg-assistant"),
            StreamingState.Completed("Hello!", messageId = "msg-assistant")
        )
    }
}
