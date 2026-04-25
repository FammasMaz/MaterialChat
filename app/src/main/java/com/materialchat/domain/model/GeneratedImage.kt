package com.materialchat.domain.model

/**
 * A generated image saved by MaterialChat and linked back to its source thread.
 */
data class GeneratedImage(
    val id: String,
    val uri: String,
    val mimeType: String,
    val conversationId: String,
    val messageId: String,
    val conversationTitle: String,
    val conversationIcon: String?,
    val prompt: String?,
    val modelName: String?,
    val createdAt: Long
)
