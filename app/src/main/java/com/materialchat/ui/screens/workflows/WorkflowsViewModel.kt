package com.materialchat.ui.screens.workflows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.domain.usecase.ManageWorkflowsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Workflows list screen.
 *
 * Seeds template workflows on first launch, then observes all workflows
 * for display in the list. Supports deletion of individual workflows.
 */
@HiltViewModel
class WorkflowsViewModel @Inject constructor(
    private val manageWorkflowsUseCase: ManageWorkflowsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<WorkflowsUiState>(WorkflowsUiState.Loading)
    val uiState: StateFlow<WorkflowsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            manageWorkflowsUseCase.seedTemplatesIfNeeded()
        }
        observeWorkflows()
    }

    /**
     * Observes all workflows from the repository and updates UI state.
     */
    private fun observeWorkflows() {
        viewModelScope.launch {
            manageWorkflowsUseCase.observeAll()
                .catch { /* Silently handle — list remains empty */ }
                .collect { workflows ->
                    _uiState.value = WorkflowsUiState.Success(workflows = workflows)
                }
        }
    }

    /**
     * Deletes a workflow by its ID.
     *
     * @param id The ID of the workflow to delete
     */
    fun deleteWorkflow(id: String) {
        viewModelScope.launch {
            try {
                manageWorkflowsUseCase.delete(id)
            } catch (_: Exception) {
                // Deletion is best-effort
            }
        }
    }
}
