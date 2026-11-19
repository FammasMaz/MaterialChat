package com.materialchat.ui.screens.search

import com.materialchat.domain.usecase.SearchConversationsUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Search transitions: a short query stays idle, a query with no hits is [SearchUiState.Empty],
 * a throwing search is [SearchUiState.Error] with fixed copy, and clearing returns to idle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val useCase = mockk<SearchConversationsUseCase>()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a throwing search reaches the error state with fixed copy and clear returns to idle`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { useCase("boom", any()) } throws IOException("fts table missing")

        val viewModel = SearchViewModel(useCase)
        viewModel.onQueryChange("boom")
        assertEquals("typing a long-enough query shows loading at once", SearchUiState.Loading, viewModel.uiState.value)
        advanceUntilIdle()

        val failed = viewModel.uiState.value
        assertEquals(SearchUiState.Error(SearchViewModel.SEARCH_UNAVAILABLE), failed)
        assertTrue("fts table missing" !in (failed as SearchUiState.Error).message)

        viewModel.clearSearch()
        advanceUntilIdle()
        assertEquals(SearchUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `retry after an error shows loading at once and re-runs the same query`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { useCase("boom", any()) } throws IOException("fts table missing") andThen emptyList()

        val viewModel = SearchViewModel(useCase)
        viewModel.onQueryChange("boom")
        advanceUntilIdle()
        assertEquals(SearchUiState.Error(SearchViewModel.SEARCH_UNAVAILABLE), viewModel.uiState.value)

        viewModel.retry()
        assertEquals("the error card leaves synchronously", SearchUiState.Loading, viewModel.uiState.value)
        advanceUntilIdle()
        assertEquals(SearchUiState.Empty("boom"), viewModel.uiState.value)
    }

    @Test
    fun `retry with a query that is now too short returns to idle without searching`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        val viewModel = SearchViewModel(useCase)
        viewModel.onQueryChange("a")
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(SearchUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `no hits is the empty state carrying the query`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { useCase("zzzz", any()) } returns emptyList()

        val viewModel = SearchViewModel(useCase)
        viewModel.onQueryChange("zzzz")
        advanceUntilIdle()

        assertEquals(SearchUiState.Empty("zzzz"), viewModel.uiState.value)
    }

    @Test
    fun `a one-character query never searches and stays idle`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        val viewModel = SearchViewModel(useCase)
        viewModel.onQueryChange("a")
        advanceUntilIdle()

        assertEquals(SearchUiState.Idle, viewModel.uiState.value)
    }
}
