package com.materialchat.data.local.database.entity

import androidx.room.ColumnInfo

/**
 * Projection for assistant messages that contain generated image attachments.
 */
data class GeneratedImageMessageEntity(
    @ColumnInfo(name = "message_id") val messageId: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "image_attachments") val imageAttachments: String?,
    @ColumnInfo(name = "model_name") val modelName: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "conversation_title") val conversationTitle: String,
    @ColumnInfo(name = "conversation_icon") val conversationIcon: String?,
    @ColumnInfo(name = "prompt") val prompt: String?
)
