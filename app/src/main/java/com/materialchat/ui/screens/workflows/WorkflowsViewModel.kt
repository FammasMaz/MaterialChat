package com.materialchat.ui.screens.workflows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.domain.usecase.ManageWorkflowsUseCase
import com.materialchat.ui.components.LoadError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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

    /** Failures of best-effort actions (delete) that the list state cannot carry. */
    private val _events = MutableSharedFlow<WorkflowsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<WorkflowsEvent> = _events.asSharedFlow()

    private var observeJob: Job? = null

    init {
        load()
    }

    /** Seeds templates (best effort) and starts observing the workflow list. */
    private fun load() {
        viewModelScope.launch {
            // Seeding must not take the list down with it: the observation below reports
            // the store's real state either way.
            runCatching { manageWorkflowsUseCase.seedTemplatesIfNeeded() }
        }
        observeWorkflows()
    }

    /**
     * Observes all workflows from the repository and updates UI state. A failure anywhere
     * in the observation (including a synchronous throw from the use case) lands in
     * [WorkflowsUiState.Error] instead of leaving the screen on its loading indicator.
     */
    private fun observeWorkflows() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            flow { emitAll(manageWorkflowsUseCase.observeAll()) }
                .catch { _uiState.value = WorkflowsUiState.Error(LoadError.Unavailable) }
                .collect { workflows ->
                    _uiState.value = WorkflowsUiState.Success(workflows = workflows)
                }
        }
    }

    /** Returns to [WorkflowsUiState.Loading] and observes the store again. */
    fun retry() {
        _uiState.value = WorkflowsUiState.Loading
        load()
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
                // The list keeps showing the workflow; tell the user instead of failing silently.
                _events.emit(WorkflowsEvent.ShowSnackbar("Could not delete workflow"))
            }
        }
    }
}
