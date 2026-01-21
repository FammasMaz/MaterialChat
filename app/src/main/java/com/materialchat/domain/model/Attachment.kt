package com.materialchat.domain.model

import java.util.UUID

/**
 * Represents an image attachment in a message.
 *
 * @property id Unique identifier for the attachment
 * @property uri The content URI of the image (for local display)
 * @property mimeType The MIME type of the image (e.g., "image/jpeg", "image/png")
 * @property base64Data The base64-encoded image data for API transmission
 */
data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val mimeType: String,
    val base64Data: String
) {
    companion object {
        /**
         * Supported image MIME types for attachments.
         */
        val SUPPORTED_IMAGE_TYPES = setOf(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/gif",
            "image/webp"
        )

        /**
         * Maximum file size for image attachments (10 MB).
         */
        const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024L

        /**
         * Checks if the given MIME type is a supported image type.
         */
        fun isSupportedImageType(mimeType: String?): Boolean {
            return mimeType?.lowercase() in SUPPORTED_IMAGE_TYPES
        }
    }
}
