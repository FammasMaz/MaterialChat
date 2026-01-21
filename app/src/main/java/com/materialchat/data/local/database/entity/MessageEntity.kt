package com.materialchat.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single message in a conversation.
 *
 * This entity stores message content, role, and metadata. Messages are
 * automatically cascade deleted when their parent conversation is deleted.
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["conversation_id"]),
        Index(value = ["created_at"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "conversation_id")
    val conversationId: String,

    @ColumnInfo(name = "role")
    val role: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "thinking_content")
    val thinkingContent: String? = null,

    @ColumnInfo(name = "is_streaming")
    val isStreaming: Boolean,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
