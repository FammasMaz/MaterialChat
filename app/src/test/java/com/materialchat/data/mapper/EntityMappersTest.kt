package com.materialchat.data.mapper

import com.materialchat.data.local.database.entity.ConversationEntity
import com.materialchat.data.local.database.entity.MessageEntity
import com.materialchat.data.local.database.entity.ProviderEntity
import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for entity mappers.
 *
 * Tests cover:
 * - Provider domain <-> entity conversions
 * - Conversation domain <-> entity conversions
 * - Message domain <-> entity conversions
 * - List conversions
 * - Edge cases (null providerId, etc.)
 */
class EntityMappersTest {

    // ============================================================================
    // Provider Mapper Tests
    // ============================================================================

    @Test
    fun `Provider toEntity - OpenAI compatible type mapped correctly`() {
        val provider = Provider(
            id = "provider-1",
            name = "OpenAI",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.openai.com",
            defaultModel = "gpt-4o",
            requiresApiKey = true,
            isActive = true
        )

        val entity = provider.toEntity()

        assertEquals("provider-1", entity.id)
        assertEquals("OpenAI", entity.name)
        assertEquals("OPENAI_COMPATIBLE", entity.type)
        assertEquals("https://api.openai.com", entity.baseUrl)
        assertEquals("gpt-4o", entity.defaultModel)
        assertTrue(entity.requiresApiKey)
        assertTrue(entity.isActive)
    }

    @Test
    fun `Provider toEntity - Ollama native type mapped correctly`() {
        val provider = Provider(
            id = "provider-2",
            name = "Local Ollama",
            type = ProviderType.OLLAMA_NATIVE,
            baseUrl = "http://localhost:11434",
            defaultModel = "llama3.2",
            requiresApiKey = false,
            isActive = false
        )

        val entity = provider.toEntity()

        assertEquals("provider-2", entity.id)
        assertEquals("Local Ollama", entity.name)
        assertEquals("OLLAMA_NATIVE", entity.type)
        assertEquals("http://localhost:11434", entity.baseUrl)
        assertEquals("llama3.2", entity.defaultModel)
        assertFalse(entity.requiresApiKey)
        assertFalse(entity.isActive)
    }

    @Test
    fun `ProviderEntity toDomain - OpenAI compatible type mapped correctly`() {
        val entity = ProviderEntity(
            id = "provider-1",
            name = "OpenAI",
            type = "OPENAI_COMPATIBLE",
            baseUrl = "https://api.openai.com",
            defaultModel = "gpt-4o",
            requiresApiKey = true,
            isActive = true
        )

        val provider = entity.toDomain()

        assertEquals("provider-1", provider.id)
        assertEquals("OpenAI", provider.name)
        assertEquals(ProviderType.OPENAI_COMPATIBLE, provider.type)
        assertEquals("https://api.openai.com", provider.baseUrl)
        assertEquals("gpt-4o", provider.defaultModel)
        assertTrue(provider.requiresApiKey)
        assertTrue(provider.isActive)
    }

    @Test
    fun `ProviderEntity toDomain - Ollama native type mapped correctly`() {
        val entity = ProviderEntity(
            id = "provider-2",
            name = "Local Ollama",
            type = "OLLAMA_NATIVE",
            baseUrl = "http://localhost:11434",
            defaultModel = "llama3.2",
            requiresApiKey = false,
            isActive = false
        )

        val provider = entity.toDomain()

        assertEquals("provider-2", provider.id)
        assertEquals("Local Ollama", provider.name)
        assertEquals(ProviderType.OLLAMA_NATIVE, provider.type)
        assertEquals("http://localhost:11434", provider.baseUrl)
        assertEquals("llama3.2", provider.defaultModel)
        assertFalse(provider.requiresApiKey)
        assertFalse(provider.isActive)
    }

    @Test
    fun `Provider roundtrip - domain to entity to domain preserves data`() {
        val original = Provider(
            id = "test-provider",
            name = "Test Provider",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://test.api.com",
            defaultModel = "test-model",
            requiresApiKey = true,
            isActive = true
        )

        val roundtripped = original.toEntity().toDomain()

        assertEquals(original.id, roundtripped.id)
        assertEquals(original.name, roundtripped.name)
        assertEquals(original.type, roundtripped.type)
        assertEquals(original.baseUrl, roundtripped.baseUrl)
        assertEquals(original.defaultModel, roundtripped.defaultModel)
        assertEquals(original.requiresApiKey, roundtripped.requiresApiKey)
        assertEquals(original.isActive, roundtripped.isActive)
    }

    @Test
    fun `ProviderEntity list toDomainList - multiple entities converted`() {
        val entities = listOf(
            ProviderEntity(
                id = "p1",
                name = "Provider 1",
                type = "OPENAI_COMPATIBLE",
                baseUrl = "https://api1.com",
                defaultModel = "model1",
                requiresApiKey = true,
                isActive = true
            ),
            ProviderEntity(
                id = "p2",
                name = "Provider 2",
                type = "OLLAMA_NATIVE",
                baseUrl = "http://localhost:11434",
                defaultModel = "model2",
                requiresApiKey = false,
                isActive = false
            )
        )

        val providers = entities.toProviderDomainList()

        assertEquals(2, providers.size)
        assertEquals("p1", providers[0].id)
        assertEquals(ProviderType.OPENAI_COMPATIBLE, providers[0].type)
        assertEquals("p2", providers[1].id)
        assertEquals(ProviderType.OLLAMA_NATIVE, providers[1].type)
    }

    @Test
    fun `Provider list toEntityList - multiple providers converted`() {
        val providers = listOf(
            Provider(
                id = "p1",
                name = "Provider 1",
                type = ProviderType.OPENAI_COMPATIBLE,
                baseUrl = "https://api1.com",
                defaultModel = "model1",
                requiresApiKey = true,
                isActive = true
            ),
            Provider(
                id = "p2",
                name = "Provider 2",
                type = ProviderType.OLLAMA_NATIVE,
                baseUrl = "http://localhost:11434",
                defaultModel = "model2",
                requiresApiKey = false,
                isActive = false
            )
        )

        val entities = providers.toProviderEntityList()

        assertEquals(2, entities.size)
        assertEquals("p1", entities[0].id)
        assertEquals("OPENAI_COMPATIBLE", entities[0].type)
        assertEquals("p2", entities[1].id)
        assertEquals("OLLAMA_NATIVE", entities[1].type)
    }

    // ============================================================================
    // Conversation Mapper Tests
    // ============================================================================

    @Test
    fun `Conversation toEntity - all fields mapped correctly`() {
        val conversation = Conversation(
            id = "conv-1",
            title = "Test Conversation",
            providerId = "provider-1",
            modelName = "gpt-4o",
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val entity = conversation.toEntity()

        assertEquals("conv-1", entity.id)
        assertEquals("Test Conversation", entity.title)
        assertEquals("provider-1", entity.providerId)
        assertEquals("gpt-4o", entity.modelName)
        assertEquals(1000L, entity.createdAt)
        assertEquals(2000L, entity.updatedAt)
    }

    @Test
    fun `ConversationEntity toDomain - all fields mapped correctly`() {
        val entity = ConversationEntity(
            id = "conv-1",
            title = "Test Conversation",
            providerId = "provider-1",
            modelName = "gpt-4o",
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val conversation = entity.toDomain()

        assertEquals("conv-1", conversation.id)
        assertEquals("Test Conversation", conversation.title)
        assertEquals("provider-1", conversation.providerId)
        assertEquals("gpt-4o", conversation.modelName)
        assertEquals(1000L, conversation.createdAt)
        assertEquals(2000L, conversation.updatedAt)
    }

    @Test
    fun `ConversationEntity toDomain - null providerId converted to empty string`() {
        val entity = ConversationEntity(
            id = "conv-1",
            title = "Test Conversation",
            providerId = null,
            modelName = "gpt-4o",
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val conversation = entity.toDomain()

        assertEquals("", conversation.providerId)
    }

    @Test
    fun `Conversation roundtrip - domain to entity to domain preserves data`() {
        val original = Conversation(
            id = "conv-test",
            title = "Test Chat",
            providerId = "provider-123",
            modelName = "llama3.2",
            createdAt = 5000L,
            updatedAt = 6000L
        )

        val roundtripped = original.toEntity().toDomain()

        assertEquals(original.id, roundtripped.id)
        assertEquals(original.title, roundtripped.title)
        assertEquals(original.providerId, roundtripped.providerId)
        assertEquals(original.modelName, roundtripped.modelName)
        assertEquals(original.createdAt, roundtripped.createdAt)
        assertEquals(original.updatedAt, roundtripped.updatedAt)
    }

    @Test
    fun `ConversationEntity list toDomainList - multiple entities converted`() {
        val entities = listOf(
            ConversationEntity(
                id = "c1",
                title = "Chat 1",
                providerId = "p1",
                modelName = "model1",
                createdAt = 1000L,
                updatedAt = 2000L
            ),
            ConversationEntity(
                id = "c2",
                title = "Chat 2",
                providerId = null,
                modelName = "model2",
                createdAt = 3000L,
                updatedAt = 4000L
            )
        )

        val conversations = entities.toConversationDomainList()

        assertEquals(2, conversations.size)
        assertEquals("c1", conversations[0].id)
        assertEquals("p1", conversations[0].providerId)
        assertEquals("c2", conversations[1].id)
        assertEquals("", conversations[1].providerId)
    }

    @Test
    fun `Conversation list toEntityList - multiple conversations converted`() {
        val conversations = listOf(
            Conversation(
                id = "c1",
                title = "Chat 1",
                providerId = "p1",
                modelName = "model1",
                createdAt = 1000L,
                updatedAt = 2000L
            ),
            Conversation(
                id = "c2",
                title = "Chat 2",
                providerId = "p2",
                modelName = "model2",
                createdAt = 3000L,
                updatedAt = 4000L
            )
        )

        val entities = conversations.toConversationEntityList()

        assertEquals(2, entities.size)
        assertEquals("c1", entities[0].id)
        assertEquals("c2", entities[1].id)
    }

    // ============================================================================
    // Message Mapper Tests
    // ============================================================================

    @Test
    fun `Message toEntity - USER role mapped correctly`() {
        val message = Message(
            id = "msg-1",
            conversationId = "conv-1",
            role = MessageRole.USER,
            content = "Hello!",
            isStreaming = false,
            createdAt = 1000L
        )

        val entity = message.toEntity()

        assertEquals("msg-1", entity.id)
        assertEquals("conv-1", entity.conversationId)
        assertEquals("USER", entity.role)
        assertEquals("Hello!", entity.content)
        assertFalse(entity.isStreaming)
        assertEquals(1000L, entity.createdAt)
    }

    @Test
    fun `Message toEntity - ASSISTANT role mapped correctly`() {
        val message = Message(
            id = "msg-2",
            conversationId = "conv-1",
            role = MessageRole.ASSISTANT,
            content = "Hi there!",
            isStreaming = true,
            createdAt = 2000L
        )

        val entity = message.toEntity()

        assertEquals("msg-2", entity.id)
        assertEquals("ASSISTANT", entity.role)
        assertEquals("Hi there!", entity.content)
        assertTrue(entity.isStreaming)
    }

    @Test
    fun `Message toEntity - SYSTEM role mapped correctly`() {
        val message = Message(
            id = "msg-3",
            conversationId = "conv-1",
            role = MessageRole.SYSTEM,
            content = "You are a helpful assistant.",
            isStreaming = false,
            createdAt = 500L
        )

        val entity = message.toEntity()

        assertEquals("SYSTEM", entity.role)
        assertEquals("You are a helpful assistant.", entity.content)
    }

    @Test
    fun `MessageEntity toDomain - USER role mapped correctly`() {
        val entity = MessageEntity(
            id = "msg-1",
            conversationId = "conv-1",
            role = "USER",
            content = "Hello!",
            isStreaming = false,
            createdAt = 1000L
        )

        val message = entity.toDomain()

        assertEquals("msg-1", message.id)
        assertEquals("conv-1", message.conversationId)
        assertEquals(MessageRole.USER, message.role)
        assertEquals("Hello!", message.content)
        assertFalse(message.isStreaming)
        assertEquals(1000L, message.createdAt)
    }

    @Test
    fun `MessageEntity toDomain - ASSISTANT role mapped correctly`() {
        val entity = MessageEntity(
            id = "msg-2",
            conversationId = "conv-1",
            role = "ASSISTANT",
            content = "Hi there!",
            isStreaming = true,
            createdAt = 2000L
        )

        val message = entity.toDomain()

        assertEquals(MessageRole.ASSISTANT, message.role)
        assertTrue(message.isStreaming)
    }

    @Test
    fun `MessageEntity toDomain - SYSTEM role mapped correctly`() {
        val entity = MessageEntity(
            id = "msg-3",
            conversationId = "conv-1",
            role = "SYSTEM",
            content = "You are a helpful assistant.",
            isStreaming = false,
            createdAt = 500L
        )

        val message = entity.toDomain()

        assertEquals(MessageRole.SYSTEM, message.role)
    }

    @Test
    fun `Message roundtrip - domain to entity to domain preserves data`() {
        val original = Message(
            id = "msg-test",
            conversationId = "conv-test",
            role = MessageRole.ASSISTANT,
            content = "Test response with **markdown**",
            isStreaming = false,
            createdAt = 9999L
        )

        val roundtripped = original.toEntity().toDomain()

        assertEquals(original.id, roundtripped.id)
        assertEquals(original.conversationId, roundtripped.conversationId)
        assertEquals(original.role, roundtripped.role)
        assertEquals(original.content, roundtripped.content)
        assertEquals(original.isStreaming, roundtripped.isStreaming)
        assertEquals(original.createdAt, roundtripped.createdAt)
    }

    @Test
    fun `MessageEntity list toDomainList - multiple entities converted`() {
        val entities = listOf(
            MessageEntity(
                id = "m1",
                conversationId = "c1",
                role = "USER",
                content = "Hello",
                isStreaming = false,
                createdAt = 1000L
            ),
            MessageEntity(
                id = "m2",
                conversationId = "c1",
                role = "ASSISTANT",
                content = "Hi!",
                isStreaming = false,
                createdAt = 2000L
            ),
            MessageEntity(
                id = "m3",
                conversationId = "c1",
                role = "SYSTEM",
                content = "System",
                isStreaming = false,
                createdAt = 500L
            )
        )

        val messages = entities.toMessageDomainList()

        assertEquals(3, messages.size)
        assertEquals(MessageRole.USER, messages[0].role)
        assertEquals(MessageRole.ASSISTANT, messages[1].role)
        assertEquals(MessageRole.SYSTEM, messages[2].role)
    }

    @Test
    fun `Message list toEntityList - multiple messages converted`() {
        val messages = listOf(
            Message(
                id = "m1",
                conversationId = "c1",
                role = MessageRole.USER,
                content = "Hello",
                createdAt = 1000L
            ),
            Message(
                id = "m2",
                conversationId = "c1",
                role = MessageRole.ASSISTANT,
                content = "Hi!",
                createdAt = 2000L
            )
        )

        val entities = messages.toMessageEntityList()

        assertEquals(2, entities.size)
        assertEquals("USER", entities[0].role)
        assertEquals("ASSISTANT", entities[1].role)
    }

    // ============================================================================
    // Edge Cases
    // ============================================================================

    @Test
    fun `Empty lists convert correctly`() {
        val emptyProviders = emptyList<Provider>()
        val emptyConversations = emptyList<Conversation>()
        val emptyMessages = emptyList<Message>()

        assertTrue(emptyProviders.toProviderEntityList().isEmpty())
        assertTrue(emptyConversations.toConversationEntityList().isEmpty())
        assertTrue(emptyMessages.toMessageEntityList().isEmpty())
    }

    @Test
    fun `Message with empty content maps correctly`() {
        val message = Message(
            id = "msg-empty",
            conversationId = "conv-1",
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true,
            createdAt = 1000L
        )

        val entity = message.toEntity()
        val roundtripped = entity.toDomain()

        assertEquals("", roundtripped.content)
    }

    @Test
    fun `Conversation with special characters in title maps correctly`() {
        val conversation = Conversation(
            id = "conv-special",
            title = "Test: \"Hello\" & <World> 'Chat'",
            providerId = "p1",
            modelName = "model",
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val roundtripped = conversation.toEntity().toDomain()

        assertEquals("Test: \"Hello\" & <World> 'Chat'", roundtripped.title)
    }

    @Test
    fun `Message with unicode content maps correctly`() {
        val message = Message(
            id = "msg-unicode",
            conversationId = "conv-1",
            role = MessageRole.USER,
            content = "Hello 世界 🌍 مرحبا",
            createdAt = 1000L
        )

        val roundtripped = message.toEntity().toDomain()

        assertEquals("Hello 世界 🌍 مرحبا", roundtripped.content)
    }
}
