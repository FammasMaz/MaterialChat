package com.materialchat.ui.screens.workflows

import com.materialchat.domain.model.Workflow

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
}
