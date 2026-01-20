package com.materialchat.data.mapper

import com.materialchat.data.local.database.entity.ConversationEntity
import com.materialchat.data.local.database.entity.MessageEntity
import com.materialchat.data.local.database.entity.ProviderEntity
import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ProviderType

// ============================================================================
// Provider Mappers
// ============================================================================

/**
 * Converts a [Provider] domain model to a [ProviderEntity] for Room database storage.
 */
fun Provider.toEntity(): ProviderEntity = ProviderEntity(
    id = id,
    name = name,
    type = type.name,
    baseUrl = baseUrl,
    defaultModel = defaultModel,
    requiresApiKey = requiresApiKey,
    isActive = isActive
)

/**
 * Converts a [ProviderEntity] from the database to a [Provider] domain model.
 */
fun ProviderEntity.toDomain(): Provider = Provider(
    id = id,
    name = name,
    type = ProviderType.valueOf(type),
    baseUrl = baseUrl,
    defaultModel = defaultModel,
    requiresApiKey = requiresApiKey,
    isActive = isActive
)

/**
 * Converts a list of [ProviderEntity] to a list of [Provider] domain models.
 */
fun List<ProviderEntity>.toProviderDomainList(): List<Provider> = map { it.toDomain() }

/**
 * Converts a list of [Provider] domain models to a list of [ProviderEntity].
 */
fun List<Provider>.toProviderEntityList(): List<ProviderEntity> = map { it.toEntity() }

// ============================================================================
// Conversation Mappers
// ============================================================================

/**
 * Converts a [Conversation] domain model to a [ConversationEntity] for Room database storage.
 */
fun Conversation.toEntity(): ConversationEntity = ConversationEntity(
    id = id,
    title = title,
    providerId = providerId,
    modelName = modelName,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/**
 * Converts a [ConversationEntity] from the database to a [Conversation] domain model.
 *
 * Note: If providerId is null (provider was deleted), an empty string is used.
 */
fun ConversationEntity.toDomain(): Conversation = Conversation(
    id = id,
    title = title,
    providerId = providerId ?: "",
    modelName = modelName,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/**
 * Converts a list of [ConversationEntity] to a list of [Conversation] domain models.
 */
fun List<ConversationEntity>.toConversationDomainList(): List<Conversation> = map { it.toDomain() }

/**
 * Converts a list of [Conversation] domain models to a list of [ConversationEntity].
 */
fun List<Conversation>.toConversationEntityList(): List<ConversationEntity> = map { it.toEntity() }

// ============================================================================
// Message Mappers
// ============================================================================

/**
 * Converts a [Message] domain model to a [MessageEntity] for Room database storage.
 */
fun Message.toEntity(): MessageEntity = MessageEntity(
    id = id,
    conversationId = conversationId,
    role = role.name,
    content = content,
    isStreaming = isStreaming,
    createdAt = createdAt
)

/**
 * Converts a [MessageEntity] from the database to a [Message] domain model.
 */
fun MessageEntity.toDomain(): Message = Message(
    id = id,
    conversationId = conversationId,
    role = MessageRole.valueOf(role),
    content = content,
    isStreaming = isStreaming,
    createdAt = createdAt
)

/**
 * Converts a list of [MessageEntity] to a list of [Message] domain models.
 */
fun List<MessageEntity>.toMessageDomainList(): List<Message> = map { it.toDomain() }

/**
 * Converts a list of [Message] domain models to a list of [MessageEntity].
 */
fun List<Message>.toMessageEntityList(): List<MessageEntity> = map { it.toEntity() }
