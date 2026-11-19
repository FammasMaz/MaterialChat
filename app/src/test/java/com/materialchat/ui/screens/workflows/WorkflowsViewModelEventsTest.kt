package com.materialchat.ui.screens.workflows

import com.materialchat.domain.usecase.ManageWorkflowsUseCase
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

/** A delete that fails after the list is showing reaches the snackbar host as a [WorkflowsEvent]. */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkflowsViewModelEventsTest {

    private val useCase = mockk<ManageWorkflowsUseCase>()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a failed delete surfaces a snackbar event and keeps the list`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { useCase.seedTemplatesIfNeeded() } returns Unit
        every { useCase.observeAll() } returns flowOf(emptyList())
        coEvery { useCase.delete("w1") } throws IOException("database closed")

        val viewModel = WorkflowsViewModel(useCase)
        val events = mutableListOf<WorkflowsEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        runCurrent()

        viewModel.deleteWorkflow("w1")
        runCurrent()

        assertEquals(listOf(WorkflowsEvent.ShowSnackbar("Could not delete workflow")), events)
        assertTrue(viewModel.uiState.value is WorkflowsUiState.Success)
    }

    @Test
    fun `a successful delete emits nothing`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { useCase.seedTemplatesIfNeeded() } returns Unit
        every { useCase.observeAll() } returns flowOf(emptyList())
        coEvery { useCase.delete("w1") } returns Unit

        val viewModel = WorkflowsViewModel(useCase)
        val events = mutableListOf<WorkflowsEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        runCurrent()

        viewModel.deleteWorkflow("w1")
        runCurrent()

        assertTrue(events.isEmpty())
    }
}
