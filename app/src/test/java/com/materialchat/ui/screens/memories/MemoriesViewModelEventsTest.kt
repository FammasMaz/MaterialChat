package com.materialchat.ui.screens.memories

import com.materialchat.domain.repository.MemoryRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * A delete that throws must become a [MemoriesEvent] for the snackbar host; before this the
 * exception escaped viewModelScope with no handler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoriesViewModelEventsTest {

    private val repository = mockk<MemoryRepository>()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a failed single delete surfaces a snackbar event`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { repository.deleteMemory("m1") } throws IOException("database closed")

        val viewModel = MemoriesViewModel(repository)
        val events = mutableListOf<MemoriesEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        runCurrent()

        viewModel.deleteMemory("m1")
        runCurrent()

        assertEquals(listOf(MemoriesEvent.ShowSnackbar("Could not delete memory")), events)
    }

    @Test
    fun `a failed delete-all surfaces a snackbar event and a successful one emits nothing`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var calls = 0
        coEvery { repository.deleteAllMemories() } answers {
            calls++
            if (calls == 1) throw IOException("database closed")
        }

        val viewModel = MemoriesViewModel(repository)
        val events = mutableListOf<MemoriesEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        runCurrent()

        viewModel.deleteAllMemories()
        runCurrent()
        assertEquals(listOf(MemoriesEvent.ShowSnackbar("Could not delete memories")), events)

        viewModel.deleteAllMemories()
        runCurrent()
        assertTrue("second call succeeds and adds no event", events.size == 1)
    }
}
