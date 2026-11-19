package com.materialchat.ui.screens.bookmarks

import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.usecase.ManageBookmarksUseCase
import com.materialchat.domain.usecase.SearchBookmarksUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * A delete or update that fails after the list is showing must reach the screen's snackbar
 * host as a [BookmarksEvent] instead of being swallowed; a successful one emits nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksViewModelTest {

    private val search = mockk<SearchBookmarksUseCase>()
    private val manage = mockk<ManageBookmarksUseCase>()
    private val conversations = mockk<ConversationRepository>()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a failed delete surfaces a snackbar event and keeps the list`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        every { search.observeAllBookmarks() } returns flowOf(emptyList())
        coEvery { search.getAllTags() } returns emptyList()
        coEvery { manage.removeBookmark("b1") } throws IOException("database closed")

        val viewModel = BookmarksViewModel(search, manage, conversations, dispatcher)
        val events = mutableListOf<BookmarksEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        runCurrent()

        viewModel.deleteBookmark("b1")
        runCurrent()

        assertEquals(listOf(BookmarksEvent.ShowSnackbar("Could not delete bookmark")), events)
        assertTrue(viewModel.uiState.value is BookmarksUiState.Success)
    }

    @Test
    fun `a successful delete emits nothing`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        every { search.observeAllBookmarks() } returns flowOf(emptyList())
        coEvery { search.getAllTags() } returns emptyList()
        coEvery { manage.removeBookmark("b1") } returns Unit

        val viewModel = BookmarksViewModel(search, manage, conversations, dispatcher)
        val events = mutableListOf<BookmarksEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        runCurrent()

        viewModel.deleteBookmark("b1")
        runCurrent()

        assertTrue(events.isEmpty())
    }
}
