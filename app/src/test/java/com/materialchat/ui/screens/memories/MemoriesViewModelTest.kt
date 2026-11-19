package com.materialchat.ui.screens.memories

import com.materialchat.domain.model.Memory
import com.materialchat.domain.repository.MemoryRepository
import com.materialchat.ui.components.LoadError
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * A failing memory store must reach [MemoriesUiState.error] instead of sitting on the
 * loading indicator forever, and a retry that succeeds must clear it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoriesViewModelTest {

    private val repository = mockk<MemoryRepository>()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a failing observation reaches the error state and a successful retry clears it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val memory = Memory(content = "Prefers tea over coffee")
        var attempts = 0
        every { repository.observeActiveMemories() } answers {
            attempts++
            if (attempts == 1) flow { throw IOException("database closed") } else flowOf(listOf(memory))
        }

        val viewModel = MemoriesViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        val failed = viewModel.uiState.value
        assertEquals(LoadError.Unavailable, failed.error)
        assertFalse("Error must leave the loading indicator", failed.isLoading)
        assertTrue(failed.memories.isEmpty())

        viewModel.retry()
        runCurrent()

        val recovered = viewModel.uiState.value
        assertNull("Retry must clear the error", recovered.error)
        assertFalse(recovered.isLoading)
        assertEquals(listOf(memory), recovered.memories)
        assertEquals(listOf(memory), recovered.filteredMemories)
        assertEquals(2, attempts)
    }

    @Test
    fun `a repository that throws on subscription reaches the error state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        every { repository.observeActiveMemories() } throws IllegalStateException("not initialised")

        val viewModel = MemoriesViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        assertEquals(LoadError.Unavailable, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `the search query survives a failure and filters after recovery`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val tea = Memory(content = "Prefers tea")
        val code = Memory(content = "Writes Kotlin")
        var attempts = 0
        every { repository.observeActiveMemories() } answers {
            attempts++
            if (attempts == 1) flow { throw IOException() } else flowOf(listOf(tea, code))
        }

        val viewModel = MemoriesViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.updateSearchQuery("tea")
        runCurrent()
        assertEquals("tea", viewModel.uiState.value.searchQuery)
        assertEquals(LoadError.Unavailable, viewModel.uiState.value.error)

        viewModel.retry()
        runCurrent()

        assertNull(viewModel.uiState.value.error)
        assertEquals(listOf(tea), viewModel.uiState.value.filteredMemories)
    }
}
