package com.materialchat.data.mapper

import kotlinx.serialization.Serializable

/**
 * Data class for serializing attachments to/from JSON for database storage.
 * This is separate from the domain Attachment model to maintain clean layer separation.
 */
@Serializable
data class AttachmentData(
    val id: String,
    val uri: String,
    val mimeType: String,
    val base64Data: String
)
