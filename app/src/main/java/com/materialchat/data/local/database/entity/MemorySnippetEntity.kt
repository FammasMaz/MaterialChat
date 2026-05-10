package com.materialchat.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Verbatim local memory drawer for MemPalace-style transcript recall.
 *
 * This is intentionally separate from user-visible extracted memories so the
 * Memories screen stays focused on durable facts/preferences while recall can
 * still search original conversation snippets when wording changes.
 */
@Entity(
    tableName = "memory_snippets",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["conversation_id"]),
        Index(value = ["message_id"], unique = true),
        Index(value = ["role"]),
        Index(value = ["updated_at"]),
        Index(value = ["last_recalled_at"]),
        Index(value = ["is_archived"])
    ]
)
data class MemorySnippetEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "conversation_id")
    val conversationId: String,

    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "role")
    val role: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "normalized_content")
    val normalizedContent: String,

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
