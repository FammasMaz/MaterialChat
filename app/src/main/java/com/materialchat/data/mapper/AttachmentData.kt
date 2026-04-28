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
    // Kept only for backward compatibility with older rows. New messages store
    // image bytes on disk and leave this empty to avoid CursorWindow/row limits.
    val base64Data: String = ""
)
