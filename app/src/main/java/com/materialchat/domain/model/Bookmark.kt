package com.materialchat.domain.model

import java.util.UUID

/**
 * Represents a bookmarked message in the knowledge base.
 *
 * @property id Unique identifier for the bookmark
 * @property messageId The ID of the bookmarked message
 * @property conversationId The ID of the conversation containing the message
 * @property tags User-defined tags for categorization
 * @property note Optional user note about why this was bookmarked
 * @property category The bookmark category for organization
 * @property createdAt Timestamp when the bookmark was created (epoch milliseconds)
 */
data class Bookmark(
    val id: String = UUID.randomUUID().toString(),
    val messageId: String,
    val conversationId: String,
    val tags: List<String> = emptyList(),
    val note: String? = null,
    val category: BookmarkCategory = BookmarkCategory.GENERAL,
    val createdAt: Long = System.currentTimeMillis()
)
