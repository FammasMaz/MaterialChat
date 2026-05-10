package com.materialchat.data.mapper

import com.materialchat.data.local.database.entity.MemoryEntity
import com.materialchat.data.local.database.entity.MemorySnippetEntity
import com.materialchat.domain.model.Memory
import com.materialchat.domain.model.MemoryKind
import com.materialchat.domain.model.MemorySnippet
import com.materialchat.domain.model.MessageRole

fun Memory.toEntity(): MemoryEntity = MemoryEntity(
    id = id,
    content = content,
    normalizedContent = content.normalizedMemoryContent(),
    kind = kind.name,
    confidence = confidence.coerceIn(0f, 1f),
    sourceConversationId = sourceConversationId,
    sourceMessageId = sourceMessageId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastRecalledAt = lastRecalledAt,
    recallCount = recallCount,
    isArchived = isArchived
)

fun MemoryEntity.toDomain(): Memory = Memory(
    id = id,
    content = content,
    kind = MemoryKind.fromRaw(kind),
    confidence = confidence.coerceIn(0f, 1f),
    sourceConversationId = sourceConversationId,
    sourceMessageId = sourceMessageId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastRecalledAt = lastRecalledAt,
    recallCount = recallCount,
    isArchived = isArchived
)

fun List<MemoryEntity>.toMemoryDomainList(): List<Memory> = map { it.toDomain() }

fun MemorySnippet.toEntity(): MemorySnippetEntity = MemorySnippetEntity(
    id = id,
    conversationId = conversationId,
    messageId = messageId,
    role = role.name,
    content = content,
    normalizedContent = content.normalizedMemoryContent(),
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastRecalledAt = lastRecalledAt,
    recallCount = recallCount,
    isArchived = isArchived
)

fun MemorySnippetEntity.toDomain(): MemorySnippet = MemorySnippet(
    id = id,
    conversationId = conversationId,
    messageId = messageId,
    role = runCatching { MessageRole.valueOf(role) }.getOrDefault(MessageRole.USER),
    content = content,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastRecalledAt = lastRecalledAt,
    recallCount = recallCount,
    isArchived = isArchived
)

fun List<MemorySnippetEntity>.toMemorySnippetDomainList(): List<MemorySnippet> = map { it.toDomain() }

fun String.normalizedMemoryContent(): String = lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()
