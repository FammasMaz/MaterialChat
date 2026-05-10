package com.materialchat.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A durable, user-visible memory extracted from conversation turns.
 *
 * Memories are intentionally concise facts/preferences/goals rather than raw
 * transcript chunks. Full transcripts remain in the messages table; this layer
 * is for passive recall before a model answers.
 */
@Serializable
data class Memory(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val kind: MemoryKind = MemoryKind.OTHER,
    val confidence: Float = 0.7f,
    val sourceConversationId: String? = null,
    val sourceMessageId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val lastRecalledAt: Long? = null,
    val recallCount: Int = 0,
    val isArchived: Boolean = false
)

@Serializable
enum class MemoryKind(val displayName: String) {
    USER_PREFERENCE("Preference"),
    PERSONAL_FACT("Personal fact"),
    PROJECT_FACT("Project fact"),
    LONG_TERM_GOAL("Goal"),
    INSTRUCTION("Instruction"),
    RELATIONSHIP("Relationship"),
    OTHER("Memory");

    companion object {
        fun fromRaw(value: String?): MemoryKind {
            val normalized = value
                ?.trim()
                ?.uppercase()
                ?.replace('-', '_')
                ?.replace(' ', '_')
                .orEmpty()
            return entries.firstOrNull { it.name == normalized } ?: OTHER
        }
    }
}

data class MemoryCandidate(
    val content: String,
    val kind: MemoryKind = MemoryKind.OTHER,
    val confidence: Float = 0.7f,
    val sourceConversationId: String? = null,
    val sourceMessageId: String? = null
)

data class RecalledMemory(
    val memory: Memory,
    val score: Double
)
