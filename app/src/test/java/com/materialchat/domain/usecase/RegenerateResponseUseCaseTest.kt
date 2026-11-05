package com.materialchat.domain.usecase

import android.content.Context
import com.materialchat.domain.model.Attachment
import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ProviderType
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.repository.PersonaRepository
import com.materialchat.domain.repository.ProviderRepository
import com.materialchat.domain.repository.WebSearchRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Exercises the production regeneration flow with a stateful persistence boundary. */
class RegenerateResponseUseCaseTest {
    private val repository = mockk<ConversationRepository>(relaxUnitFun = true)
    private val chat = mockk<ChatRepository>()
    private val providers = mockk<ProviderRepository>()
    private val personas = mockk<PersonaRepository>()
    private val search = mockk<WebSearchRepository>()
    private val rows = linkedMapOf<String, Message>()
    private val writes = mutableListOf<String>()
    private val provider = Provider(
        id = "provider", name = "Test", type = ProviderType.OPENAI_COMPATIBLE,
        baseUrl = "https://example.com", defaultModel = "model", requiresApiKey = false
    )
    private val conversation = Conversation(
        id = "conversation", title = "Saved chat", providerId = provider.id, modelName = "model"
    )
    private val user = Message(
        id = "user", conversationId = conversation.id, role = MessageRole.USER,
        content = "Explain this equation", createdAt = 1L
    )
    private val original = Message(
        id = "original", conversationId = conversation.id, role = MessageRole.ASSISTANT,
        content = "Saved answer: \\(E = mc^2\\)", thinkingContent = "Original reasoning",
        attachments = listOf(Attachment(uri = "file:///saved/original.png", mimeType = "image/png")),
        modelName = "old-model", webSearchMetadata = "original-sources",
        memoryMetadata = "original-memory", firstTokenLatencyMs = 50L,
        generatedTokenCount = 25, totalDurationMs = 500L, createdAt = 2L
    )
    private lateinit var useCase: RegenerateResponseUseCase
    private var sentMessages: List<Message> = emptyList()
    private var sentModel: String? = null

    @Before
    fun setUp() {
        rows[user.id] = user
        rows[original.id] = original
        coEvery { repository.getConversation(conversation.id) } returns conversation
        coEvery { providers.getProvider(provider.id) } returns provider
        coEvery { repository.getMessages(conversation.id) } answers { rows.values.toList() }
        coEvery { repository.addMessage(any()) } answers {
            val message = firstArg<Message>()
            rows[message.id] = message
            writes += "add:${message.id}"
            message.id
        }
        coEvery { repository.deleteMessage(any()) } answers {
            val id = firstArg<String>()
            rows.remove(id)
            writes += "delete:$id"
            Unit
        }
        coEvery { repository.updateMessageContent(any(), any()) } answers {
            val id = firstArg<String>()
            rows[id]?.let { rows[id] = it.copy(content = secondArg()) }
            writes += "content:$id"
            Unit
        }
        coEvery { repository.updateMessageContentWithThinking(any(), any(), any()) } answers {
            val id = firstArg<String>()
            rows[id]?.let { rows[id] = it.copy(content = secondArg(), thinkingContent = thirdArg()) }
            writes += "content:$id"
            Unit
        }
        coEvery { repository.setMessageStreaming(any(), any()) } answers {
            val id = firstArg<String>()
            rows[id]?.let { rows[id] = it.copy(isStreaming = secondArg()) }
            Unit
        }
        coEvery { repository.updateMessageWebSearchMetadata(any(), any()) } answers {
            val id = firstArg<String>()
            rows[id]?.let { rows[id] = it.copy(webSearchMetadata = secondArg()) }
            Unit
        }
        useCase = RegenerateResponseUseCase(
            chat, repository, providers, personas, search, mockk<Context>(relaxed = true)
        )
    }

    @Test
    fun `network error leaves original answer and attachments unchanged without an empty retry`() = runTest {
        respondWith(flowOf(StreamingState.Error(IllegalStateException("Offline"))))

        val states = regenerate().toList()

        assertTrue(states.last() is StreamingState.Error)
        assertEquals(original, rows[original.id])
        assertEquals(listOf(user, original), rows.values.toList())
        coVerify(exactly = 0) { repository.deleteMessage(original.id) }
    }

    @Test
    fun `failed retry preserves partial response as well as the saved answer`() = runTest {
        respondWith(flowOf(
            StreamingState.Streaming("Partial new answer", "New reasoning", "upstream"),
            StreamingState.Error(IllegalStateException("Disconnected"), messageId = "upstream")
        ))

        regenerate().toList()

        assertEquals(original, rows[original.id])
        val partial = rows.values.last()
        assertEquals("Partial new answer", partial.content)
        assertEquals("New reasoning", partial.thinkingContent)
        assertFalse(partial.isStreaming)
    }

    @Test
    fun `explicit cancellation before any tokens preserves only the saved exchange`() = runTest {
        respondWith(flowOf(StreamingState.Cancelled()))

        val states = regenerate().toList()

        assertTrue(states.last() is StreamingState.Cancelled)
        assertEquals(listOf(user, original), rows.values.toList())
    }

    @Test
    fun `coroutine cancellation after reasoning keeps original and finishes the partial row`() = runTest {
        val started = CompletableDeferred<Unit>()
        respondWith(flow {
            emit(StreamingState.Streaming("", "New reasoning only", "upstream"))
            started.complete(Unit)
            awaitCancellation()
        })
        val job = launch { regenerate().toList() }
        started.await()
        job.cancel()
        job.join()

        assertEquals(original, rows[original.id])
        assertEquals("New reasoning only", rows.values.last().thinkingContent)
        assertFalse(rows.values.last().isStreaming)
    }

    @Test
    fun `throw before a flow is returned still cleans up and preserves the original`() = runTest {
        every {
            chat.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
        } throws IllegalStateException("Credential lookup failed")

        val error = runCatching { regenerate().toList() }.exceptionOrNull()

        assertEquals("Credential lookup failed", error?.message)
        assertEquals(listOf(user, original), rows.values.toList())
    }

    @Test
    fun `a silent end is an error and does not erase the saved answer`() = runTest {
        respondWith(flow { })

        val states = regenerate().toList()

        assertTrue("Every request needs a terminal state", states.last() is StreamingState.Error)
        assertEquals(listOf(user, original), rows.values.toList())
    }

    @Test
    fun `successful retry deletes original only after final replacement is persisted`() = runTest {
        respondWith(flow {
            assertEquals("Original must still exist when the request starts", original, rows[original.id])
            assertEquals(listOf(user), sentMessages)
            emit(StreamingState.Streaming("New", "New reasoning", "upstream"))
            assertEquals("Original must survive until completion", original, rows[original.id])
            emit(StreamingState.Completed("Complete new answer", "New reasoning", "upstream"))
        })

        val states = regenerate("replacement-model").toList()

        val completed = states.last() as StreamingState.Completed
        assertEquals("replacement-model", sentModel)
        assertFalse(rows.containsKey(original.id))
        val replacement = requireNotNull(rows[completed.messageId])
        assertEquals("Complete new answer", replacement.content)
        assertEquals("New reasoning", replacement.thinkingContent)
        assertFalse(replacement.isStreaming)
        assertEquals("replacement-model", replacement.modelName)
        assertTrue(writes.indexOf("content:${replacement.id}") < writes.indexOf("delete:${original.id}"))
        assertEquals(2, rows.size)
    }

    @Test
    fun `empty successful response does not replace a useful saved answer`() = runTest {
        respondWith(flowOf(StreamingState.Completed("", messageId = "upstream")))

        val states = regenerate().toList()

        assertTrue(states.last() is StreamingState.Error)
        assertEquals(listOf(user, original), rows.values.toList())
    }

    @Test
    fun `failure after a trailing user message never deletes an earlier assistant answer`() = runTest {
        val nextUser = user.copy(id = "next-user", content = "Follow up", createdAt = 3L)
        rows[nextUser.id] = nextUser
        respondWith(flowOf(StreamingState.Error(IllegalStateException("Offline"))))

        regenerate().toList()

        assertEquals(listOf(user, original, nextUser), sentMessages)
        assertEquals(listOf(user, original, nextUser), rows.values.toList())
    }

    private fun respondWith(stream: Flow<StreamingState>) {
        every {
            chat.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            sentMessages = secondArg()
            sentModel = thirdArg()
            stream
        }
    }

    private fun regenerate(model: String? = null) = useCase(
        conversationId = conversation.id,
        systemPrompt = "System",
        reasoningEffort = ReasoningEffort.HIGH,
        overrideModelName = model
    )
}
