package com.materialchat.ui.screens.workflows

import com.materialchat.domain.model.Workflow
import com.materialchat.domain.usecase.ManageWorkflowsUseCase
import com.materialchat.ui.components.LoadError
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
 * A failing workflow store must reach [WorkflowsUiState.Error] instead of sitting on the
 * loading indicator forever, and a retry that succeeds must return to [WorkflowsUiState.Success].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkflowsViewModelTest {

    private val useCase = mockk<ManageWorkflowsUseCase>()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a failing observation reaches the error state and a successful retry clears it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { useCase.seedTemplatesIfNeeded() } returns Unit
        val workflow = Workflow(name = "Research")
        var attempts = 0
        every { useCase.observeAll() } answers {
            attempts++
            if (attempts == 1) flow { throw IOException("database closed") } else flowOf(listOf(workflow))
        }

        val viewModel = WorkflowsViewModel(useCase)
        runCurrent()
        assertEquals(WorkflowsUiState.Error(LoadError.Unavailable), viewModel.uiState.value)

        viewModel.retry()
        assertEquals("Retry must show loading first", WorkflowsUiState.Loading, viewModel.uiState.value)
        runCurrent()

        assertEquals(WorkflowsUiState.Success(listOf(workflow)), viewModel.uiState.value)
        assertEquals(2, attempts)
    }

    @Test
    fun `a use case that throws on subscription reaches the error state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { useCase.seedTemplatesIfNeeded() } returns Unit
        every { useCase.observeAll() } throws IllegalStateException("not initialised")

        val viewModel = WorkflowsViewModel(useCase)
        runCurrent()

        assertTrue(viewModel.uiState.value is WorkflowsUiState.Error)
    }

    @Test
    fun `a failing template seed does not take the list down`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { useCase.seedTemplatesIfNeeded() } throws IOException("seed failed")
        every { useCase.observeAll() } returns flowOf(emptyList())

        val viewModel = WorkflowsViewModel(useCase)
        runCurrent()

        assertEquals(WorkflowsUiState.Success(emptyList()), viewModel.uiState.value)
    }
}
