package com.materialchat.ui.screens.workflows

import com.materialchat.domain.model.Workflow
import com.materialchat.ui.components.LoadError

/**
 * UI state for the Workflows list screen.
 */
sealed interface WorkflowsUiState {

    /**
     * Initial loading state while workflows are being fetched.
     */
    data object Loading : WorkflowsUiState

    /**
     * Successfully loaded workflows.
     *
     * @property workflows The list of all workflows
     */
    data class Success(
        val workflows: List<Workflow> = emptyList()
    ) : WorkflowsUiState

    /**
     * The workflow store could not be observed. Retrying returns to [Loading].
     *
     * @property error Why the list is unavailable
     */
    data class Error(
        val error: LoadError
    ) : WorkflowsUiState
}
