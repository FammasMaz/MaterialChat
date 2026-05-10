package com.materialchat.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for passive long-term memories.
 */
@Entity(
    tableName = "memories",
    indices = [
        Index(value = ["normalized_content"], unique = true),
        Index(value = ["kind"]),
        Index(value = ["updated_at"]),
        Index(value = ["last_recalled_at"]),
        Index(value = ["source_conversation_id"]),
        Index(value = ["source_message_id"]),
        Index(value = ["is_archived"])
    ]
)
data class MemoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "normalized_content")
    val normalizedContent: String,

    @ColumnInfo(name = "kind")
    val kind: String,

    @ColumnInfo(name = "confidence")
    val confidence: Float,

    @ColumnInfo(name = "source_conversation_id")
    val sourceConversationId: String? = null,

    @ColumnInfo(name = "source_message_id")
    val sourceMessageId: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "last_recalled_at")
    val lastRecalledAt: Long? = null,

    @ColumnInfo(name = "recall_count")
    val recallCount: Int = 0,

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false
)
