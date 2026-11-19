package com.materialchat.ui.screens.workflows

import androidx.lifecycle.SavedStateHandle
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.model.Workflow
import com.materialchat.domain.repository.ProviderRepository
import com.materialchat.domain.usecase.ExecuteWorkflowUseCase
import com.materialchat.domain.usecase.ManageWorkflowsUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The workflow-execution load error must be recoverable from the shared error card's
 * "Try again" action: [WorkflowExecutionViewModel.retry] clears the error, returns to loading
 * and reads the workflow again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkflowExecutionViewModelRetryTest {

    private val manageWorkflows = mockk<ManageWorkflowsUseCase>()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a failing load reaches the error state and a successful retry clears it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val workflow = Workflow(name = "Research")
        var attempts = 0
        coEvery { manageWorkflows.get("wf-1") } answers {
            attempts++
            if (attempts == 1) throw IOException("database closed") else workflow
        }

        val viewModel = WorkflowExecutionViewModel(
            savedStateHandle = SavedStateHandle(mapOf("workflowId" to "wf-1")),
            executeWorkflowUseCase = mockk<ExecuteWorkflowUseCase>(relaxed = true),
            manageWorkflowsUseCase = manageWorkflows,
            providerRepository = mockk<ProviderRepository>(relaxed = true),
            appPreferences = mockk<AppPreferences>(relaxed = true)
        )
        runCurrent()

        val failed = viewModel.uiState.value
        assertNotNull("Load failure must reach the error state", failed.error)
        assertTrue(!failed.isLoading)
        assertNull(failed.workflow)

        viewModel.retry()
        val retrying = viewModel.uiState.value
        assertTrue("Retry must show loading first", retrying.isLoading)
        assertNull(retrying.error)
        runCurrent()

        val recovered = viewModel.uiState.value
        assertNull(recovered.error)
        assertTrue(!recovered.isLoading)
        assertEquals(workflow, recovered.workflow)
        assertEquals(2, attempts)
    }
}
