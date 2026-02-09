package com.materialchat.data.mapper

import com.materialchat.data.local.database.entity.ConversationEntity
import com.materialchat.data.local.database.entity.MessageEntity
import com.materialchat.data.local.database.entity.ProviderEntity
import com.materialchat.domain.model.Attachment
import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ProviderType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

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
    icon = icon,
    providerId = providerId,
    modelName = modelName,
    parentId = parentId,
    branchSourceMessageId = branchSourceMessageId,
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
    icon = icon,
    providerId = providerId ?: "",
    modelName = modelName,
    parentId = parentId,
    branchSourceMessageId = branchSourceMessageId,
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
    thinkingContent = thinkingContent,
    imageAttachments = serializeAttachments(attachments),
    isStreaming = isStreaming,
    thinkingDurationMs = thinkingDurationMs,
    totalDurationMs = totalDurationMs,
    modelName = modelName,
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
    thinkingContent = thinkingContent,
    attachments = deserializeAttachments(imageAttachments),
    isStreaming = isStreaming,
    thinkingDurationMs = thinkingDurationMs,
    totalDurationMs = totalDurationMs,
    modelName = modelName,
    createdAt = createdAt
)

/**
 * Serializes a list of [Attachment] domain models to a JSON string for database storage.
 */
private fun serializeAttachments(attachments: List<Attachment>): String? {
    if (attachments.isEmpty()) return null
    return try {
        val attachmentDataList = attachments.map { attachment ->
            AttachmentData(
                id = attachment.id,
                uri = attachment.uri,
                mimeType = attachment.mimeType,
                base64Data = attachment.base64Data
            )
        }
        json.encodeToString(attachmentDataList)
    } catch (e: Exception) {
        null
    }
}

/**
 * Deserializes a JSON string from the database to a list of [Attachment] domain models.
 */
private fun deserializeAttachments(jsonString: String?): List<Attachment> {
    if (jsonString.isNullOrEmpty()) return emptyList()
    return try {
        val attachmentDataList = json.decodeFromString<List<AttachmentData>>(jsonString)
        attachmentDataList.map { data ->
            Attachment(
                id = data.id,
                uri = data.uri,
                mimeType = data.mimeType,
                base64Data = data.base64Data
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Converts a list of [MessageEntity] to a list of [Message] domain models.
 */
fun List<MessageEntity>.toMessageDomainList(): List<Message> = map { it.toDomain() }

/**
 * Converts a list of [Message] domain models to a list of [MessageEntity].
 */
fun List<Message>.toMessageEntityList(): List<MessageEntity> = map { it.toEntity() }
