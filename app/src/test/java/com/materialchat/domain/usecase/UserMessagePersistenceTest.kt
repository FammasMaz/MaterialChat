package com.materialchat.domain.usecase

import android.content.Context
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
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.repository.LocalModelRepository
import com.materialchat.domain.repository.PersonaRepository
import com.materialchat.domain.repository.ProviderRepository
import com.materialchat.domain.repository.WebSearchRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** The normal and fusion pipelines must make the same explicit ownership handoff. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class UserMessagePersistenceTest(private val fusion: Boolean) {
    private val repository = mockk<ConversationRepository>(relaxUnitFun = true)
    private val chat = mockk<ChatRepository>(relaxUnitFun = true)
    private val providers = mockk<ProviderRepository>()
    private val personas = mockk<PersonaRepository>()
    private val search = mockk<WebSearchRepository>()
    private val localModels = mockk<LocalModelRepository>()
    private val preferences = mockk<AppPreferences>()
    private val conversation = Conversation(
        id = "conversation", title = "Saved chat", providerId = "provider", modelName = "model"
    )
    private val provider = Provider(
        id = "provider", name = "Test", type = ProviderType.OPENAI_COMPATIBLE,
        baseUrl = "https://unused.invalid", defaultModel = "model", requiresApiKey = false
    )
    private val image = Attachment(uri = "file:///private/chat_attachments/image.jpg", mimeType = "image/jpeg")
    private val saved = mutableListOf<Message>()
    private val acknowledged = mutableListOf<Message>()
    private lateinit var send: SendMessageUseCase
    private lateinit var runFusion: RunFusionUseCase

    @Before
    fun setUp() {
        coEvery { repository.getConversation(conversation.id) } returns conversation
        coEvery { providers.getProvider(provider.id) } returns provider
        coEvery { repository.addMessage(any()) } answers {
            firstArg<Message>().also { saved += it }.id
        }
        // Stop immediately after the user save, before any provider or Android service work.
        coEvery { repository.getMessages(conversation.id) } throws IllegalStateException("Post-save read failed")
        runFusion = RunFusionUseCase(chat, providers, repository, preferences)
    }

    @Test
    fun `a refused save never transfers ownership`() = runTest {
        createSendUseCase(backgroundScope)
        coEvery { repository.addMessage(any()) } throws IllegalStateException("Save refused")

        val failure = runCatching { collect() }.exceptionOrNull()

        assertEquals("Save refused", failure?.message)
        assertTrue(saved.isEmpty())
        assertTrue(acknowledged.isEmpty())
        coVerify(exactly = 0) { repository.getMessages(any()) }
    }

    @Test
    fun `a post-save failure cannot revoke the persistence acknowledgement`() = runTest {
        createSendUseCase(backgroundScope)

        val failure = runCatching { collect() }.exceptionOrNull()

        assertEquals("Post-save read failed", failure?.message)
        assertEquals(1, saved.size)
        assertEquals(MessageRole.USER, saved.single().role)
        assertEquals(listOf(image), saved.single().attachments)
        assertEquals(saved, acknowledged)
    }

    @Test
    fun `stop while saving waits for commit and acknowledgement before finishing`() = runTest {
        createSendUseCase(backgroundScope)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { repository.addMessage(any()) } coAnswers {
            val message = firstArg<Message>()
            entered.complete(Unit)
            release.await()
            saved += message
            message.id
        }
        val job = launch { collect() }
        entered.await()

        job.cancel()
        runCurrent()
        assertFalse("The ownership handoff must finish before cancellation completes", job.isCompleted)
        assertTrue(acknowledged.isEmpty())
        release.complete(Unit)
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(1, saved.size)
        assertEquals(saved, acknowledged)
        coVerify(exactly = 0) { repository.getMessages(any()) }
    }

    @Test
    fun `stop while a save is refused keeps ownership with the draft`() = runTest {
        createSendUseCase(backgroundScope)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { repository.addMessage(any()) } coAnswers {
            entered.complete(Unit)
            release.await()
            throw IllegalStateException("Save refused")
        }
        val job = launch { runCatching { collect() } }
        entered.await()

        job.cancel()
        release.complete(Unit)
        job.join()

        assertTrue(job.isCancelled)
        assertTrue(saved.isEmpty())
        assertTrue(acknowledged.isEmpty())
    }

    @Test
    fun `stop before persistence starts does not save or acknowledge anything`() = runTest {
        createSendUseCase(backgroundScope)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { repository.getConversation(conversation.id) } coAnswers {
            entered.complete(Unit)
            release.await()
            conversation
        }
        val job = launch { collect() }
        entered.await()

        job.cancelAndJoin()
        release.complete(Unit)

        assertTrue(saved.isEmpty())
        assertTrue(acknowledged.isEmpty())
        coVerify(exactly = 0) { repository.addMessage(any()) }
    }

    private fun createSendUseCase(scope: kotlinx.coroutines.CoroutineScope) {
        send = SendMessageUseCase(
            chatRepository = chat,
            conversationRepository = repository,
            providerRepository = providers,
            personaRepository = personas,
            webSearchRepository = search,
            localModelRepository = localModels,
            appPreferences = preferences,
            generateConversationTitleUseCase = mockk(),
            recallMemoriesUseCase = mockk(),
            extractMemoriesUseCase = mockk(),
            applicationScope = scope,
            context = mockk<Context>(relaxed = true)
        )
    }

    private suspend fun collect() {
        val acknowledge = {
            assertEquals("Only a committed user message may transfer file ownership", 1, saved.size)
            acknowledged += saved.single()
            Unit
        }
        if (fusion) {
            runFusion(
                conversationId = conversation.id,
                userContent = "describe this",
                fusionConfig = FusionConfig(
                    selectedModels = listOf(
                        FusionModelSelection("model-a", provider.id),
                        FusionModelSelection("model-b", provider.id)
                    ),
                    judgeModel = FusionModelSelection("judge", provider.id),
                    isEnabled = true
                ),
                systemPrompt = "",
                reasoningEffort = ReasoningEffort.NONE,
                attachments = listOf(image),
                onUserMessageSaved = acknowledge
            ).collect()
        } else {
            send(
                conversationId = conversation.id,
                userContent = "describe this",
                attachments = listOf(image),
                systemPrompt = "",
                reasoningEffort = ReasoningEffort.NONE,
                onUserMessageSaved = acknowledge
            ).collect()
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "fusion={0}")
        fun pipelines(): List<Array<Boolean>> = listOf(arrayOf(false), arrayOf(true))
    }
}
