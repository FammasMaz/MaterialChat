package com.materialchat.data.mapper

import com.materialchat.data.local.database.entity.BookmarkEntity
import com.materialchat.domain.model.Bookmark
import com.materialchat.domain.model.BookmarkCategory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val bookmarkJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

// ============================================================================
// Bookmark Mappers
// ============================================================================

/**
 * Converts a [BookmarkEntity] from the database to a [Bookmark] domain model.
 * Deserializes the JSON tags string into a list and parses the category string.
 */
fun BookmarkEntity.toDomain(): Bookmark = Bookmark(
    id = id,
    messageId = messageId,
    conversationId = conversationId,
    tags = deserializeTags(tags),
    note = note,
    category = parseCategory(category),
    createdAt = createdAt
)

/**
 * Converts a [Bookmark] domain model to a [BookmarkEntity] for Room database storage.
 * Serializes the tags list to JSON and converts the category enum to a lowercase string.
 */
fun Bookmark.toEntity(): BookmarkEntity = BookmarkEntity(
    id = id,
    messageId = messageId,
    conversationId = conversationId,
    tags = serializeTags(tags),
    note = note,
    category = category.name.lowercase(),
    createdAt = createdAt
)

/**
 * Converts a list of [BookmarkEntity] to a list of [Bookmark] domain models.
 */
fun List<BookmarkEntity>.toBookmarkDomainList(): List<Bookmark> = map { it.toDomain() }

/**
 * Serializes a list of tag strings to a JSON array string for database storage.
 * Returns null if the list is empty.
 */
private fun serializeTags(tags: List<String>): String? {
    if (tags.isEmpty()) return null
    return try {
        bookmarkJson.encodeToString(tags)
    } catch (_: Exception) {
        null
    }
}

/**
 * Deserializes a JSON array string from the database to a list of tag strings.
 * Returns an empty list if the string is null, empty, or malformed.
 */
private fun deserializeTags(tagsJson: String?): List<String> {
    if (tagsJson.isNullOrEmpty()) return emptyList()
    return try {
        bookmarkJson.decodeFromString<List<String>>(tagsJson)
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Parses a category string from the database into a [BookmarkCategory] enum value.
 * Falls back to [BookmarkCategory.GENERAL] if the string is unrecognized.
 */
private fun parseCategory(category: String): BookmarkCategory {
    return try {
        BookmarkCategory.valueOf(category.uppercase())
    } catch (_: IllegalArgumentException) {
        BookmarkCategory.GENERAL
    }
}
