package com.materialchat.ui.screens.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.di.IoDispatcher
import com.materialchat.domain.model.Bookmark
import com.materialchat.domain.model.BookmarkCategory
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.usecase.ManageBookmarksUseCase
import com.materialchat.domain.usecase.SearchBookmarksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Bookmarks / Knowledge Base screen.
 *
 * Loads bookmarks joined with their associated message and conversation data,
 * supports category/tag filtering, search, and bookmark management.
 */
@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val searchBookmarksUseCase: SearchBookmarksUseCase,
    private val manageBookmarksUseCase: ManageBookmarksUseCase,
    private val conversationRepository: ConversationRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow<BookmarksUiState>(BookmarksUiState.Loading)
    val uiState: StateFlow<BookmarksUiState> = _uiState.asStateFlow()

    init {
        loadBookmarks()
    }

    /**
     * Loads bookmarks and observes changes, joining with message and conversation data.
     */
    private fun loadBookmarks() {
        viewModelScope.launch(ioDispatcher) {
            try {
                searchBookmarksUseCase.observeAllBookmarks()
                    .catch { e ->
                        _uiState.value = BookmarksUiState.Error(
                            message = e.message ?: "Failed to load bookmarks"
                        )
                    }
                    .collectLatest { bookmarks ->
                        val bookmarkItems = buildBookmarkItems(bookmarks)
                        val allTags = searchBookmarksUseCase.getAllTags()

                        val currentState = _uiState.value
                        val selectedCategory = (currentState as? BookmarksUiState.Success)?.selectedCategory
                        val selectedTag = (currentState as? BookmarksUiState.Success)?.selectedTag
                        val searchQuery = (currentState as? BookmarksUiState.Success)?.searchQuery ?: ""

                        _uiState.value = BookmarksUiState.Success(
                            bookmarks = bookmarkItems,
                            allTags = allTags,
                            selectedCategory = selectedCategory,
                            selectedTag = selectedTag,
                            searchQuery = searchQuery
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = BookmarksUiState.Error(
                    message = e.message ?: "Failed to load bookmarks"
                )
            }
        }
    }

    /**
     * Applies a category filter. Reloads bookmarks filtered by the selected category.
     * Passing null clears the category filter.
     */
    fun selectCategory(category: BookmarkCategory?) {
        val currentState = _uiState.value
        if (currentState !is BookmarksUiState.Success) return

        _uiState.value = currentState.copy(
            selectedCategory = category,
            selectedTag = null // Clear tag filter when changing category
        )

        // Reload with new filter
        viewModelScope.launch(ioDispatcher) {
            try {
                val flow = if (category != null) {
                    searchBookmarksUseCase.observeByCategory(category)
                } else {
                    searchBookmarksUseCase.observeAllBookmarks()
                }

                flow.catch { e ->
                    _uiState.value = BookmarksUiState.Error(
                        message = e.message ?: "Failed to filter bookmarks"
                    )
                }.collectLatest { bookmarks ->
                    val bookmarkItems = buildBookmarkItems(bookmarks)
                    val allTags = searchBookmarksUseCase.getAllTags()
                    val state = _uiState.value as? BookmarksUiState.Success ?: return@collectLatest

                    _uiState.value = state.copy(
                        bookmarks = bookmarkItems,
                        allTags = allTags
                    )
                }
            } catch (e: Exception) {
                _uiState.value = BookmarksUiState.Error(
                    message = e.message ?: "Failed to filter bookmarks"
                )
            }
        }
    }

    /**
     * Applies a tag filter. Reloads bookmarks containing the selected tag.
     * Passing null clears the tag filter.
     */
    fun selectTag(tag: String?) {
        val currentState = _uiState.value
        if (currentState !is BookmarksUiState.Success) return

        _uiState.value = currentState.copy(
            selectedTag = tag,
            selectedCategory = null // Clear category filter when selecting a tag
        )

        viewModelScope.launch(ioDispatcher) {
            try {
                val flow = if (tag != null) {
                    searchBookmarksUseCase.observeByTag(tag)
                } else {
                    searchBookmarksUseCase.observeAllBookmarks()
                }

                flow.catch { e ->
                    _uiState.value = BookmarksUiState.Error(
                        message = e.message ?: "Failed to filter bookmarks"
                    )
                }.collectLatest { bookmarks ->
                    val bookmarkItems = buildBookmarkItems(bookmarks)
                    val allTags = searchBookmarksUseCase.getAllTags()
                    val state = _uiState.value as? BookmarksUiState.Success ?: return@collectLatest

                    _uiState.value = state.copy(
                        bookmarks = bookmarkItems,
                        allTags = allTags
                    )
                }
            } catch (e: Exception) {
                _uiState.value = BookmarksUiState.Error(
                    message = e.message ?: "Failed to filter bookmarks"
                )
            }
        }
    }

    /**
     * Updates the search query for client-side filtering.
     */
    fun updateSearchQuery(query: String) {
        val currentState = _uiState.value
        if (currentState is BookmarksUiState.Success) {
            _uiState.value = currentState.copy(searchQuery = query)
        }
    }

    /**
     * Deletes a bookmark.
     */
    fun deleteBookmark(bookmarkId: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                manageBookmarksUseCase.removeBookmark(bookmarkId)
            } catch (_: Exception) {
                // Bookmark deletion is best-effort
            }
        }
    }

    /**
     * Updates a bookmark's category, tags, or note.
     */
    fun updateBookmark(bookmark: Bookmark) {
        viewModelScope.launch(ioDispatcher) {
            try {
                manageBookmarksUseCase.updateBookmark(bookmark)
            } catch (_: Exception) {
                // Update is best-effort
            }
        }
    }

    /**
     * Retries loading bookmarks after an error.
     */
    fun retry() {
        _uiState.value = BookmarksUiState.Loading
        loadBookmarks()
    }

    /**
     * Builds [BookmarkWithMessage] items by joining bookmarks with their message
     * and conversation data from the repository.
     */
    private suspend fun buildBookmarkItems(bookmarks: List<Bookmark>): List<BookmarkWithMessage> {
        return bookmarks.mapNotNull { bookmark ->
            try {
                val conversation = conversationRepository.getConversation(bookmark.conversationId)
                val messages = conversationRepository.getMessages(bookmark.conversationId)
                val message = messages.find { it.id == bookmark.messageId }

                if (message != null && conversation != null) {
                    BookmarkWithMessage(
                        bookmark = bookmark,
                        messageContent = message.content,
                        messageRole = message.role.name,
                        modelName = message.modelName,
                        conversationTitle = conversation.title,
                        conversationId = conversation.id
                    )
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
