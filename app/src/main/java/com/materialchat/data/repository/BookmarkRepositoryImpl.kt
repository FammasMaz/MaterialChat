package com.materialchat.data.repository

import com.materialchat.data.local.database.dao.BookmarkDao
import com.materialchat.data.mapper.toBookmarkDomainList
import com.materialchat.data.mapper.toDomain
import com.materialchat.data.mapper.toEntity
import com.materialchat.domain.model.Bookmark
import com.materialchat.domain.model.BookmarkCategory
import com.materialchat.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Implementation of [BookmarkRepository] backed by Room database.
 *
 * Handles bookmark CRUD operations, tag aggregation, and reactive observation
 * through the [BookmarkDao].
 */
class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao
) : BookmarkRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun observeAllBookmarks(): Flow<List<Bookmark>> =
        bookmarkDao.getAllBookmarks().map { it.toBookmarkDomainList() }

    override fun observeBookmarksByCategory(category: BookmarkCategory): Flow<List<Bookmark>> =
        bookmarkDao.getBookmarksByCategory(category.name.lowercase())
            .map { it.toBookmarkDomainList() }

    override fun observeBookmarksByTag(tag: String): Flow<List<Bookmark>> =
        bookmarkDao.getBookmarksByTag(tag).map { it.toBookmarkDomainList() }

    override fun isMessageBookmarkedFlow(messageId: String): Flow<Boolean> =
        bookmarkDao.isMessageBookmarkedFlow(messageId)

    override suspend fun getBookmarkById(bookmarkId: String): Bookmark? =
        bookmarkDao.getBookmarkById(bookmarkId)?.toDomain()

    override suspend fun getBookmarkByMessageId(messageId: String): Bookmark? =
        bookmarkDao.getBookmarkByMessageId(messageId)?.toDomain()

    override suspend fun isMessageBookmarked(messageId: String): Boolean =
        bookmarkDao.isMessageBookmarked(messageId)

    override suspend fun addBookmark(bookmark: Bookmark) =
        bookmarkDao.insert(bookmark.toEntity())

    override suspend fun updateBookmark(bookmark: Bookmark) =
        bookmarkDao.update(bookmark.toEntity())

    override suspend fun deleteBookmark(bookmark: Bookmark) =
        bookmarkDao.delete(bookmark.toEntity())

    override suspend fun deleteBookmarkById(bookmarkId: String) =
        bookmarkDao.deleteById(bookmarkId)

    override suspend fun getAllTags(): List<String> {
        val rawTags = bookmarkDao.getAllTags()
        return rawTags.flatMap { tagsString ->
            try {
                json.decodeFromString<List<String>>(tagsString)
            } catch (_: Exception) {
                emptyList()
            }
        }.distinct().sorted()
    }

    override suspend fun getBookmarkCount(): Int =
        bookmarkDao.getBookmarkCount()
}
