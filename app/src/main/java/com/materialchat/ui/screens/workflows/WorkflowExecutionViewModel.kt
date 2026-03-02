package com.materialchat.ui.screens.workflows

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.model.Workflow
import com.materialchat.domain.model.WorkflowExecution
import com.materialchat.domain.model.WorkflowStatus
import com.materialchat.domain.repository.ProviderRepository
import com.materialchat.domain.usecase.ExecuteWorkflowUseCase
import com.materialchat.domain.usecase.ManageWorkflowsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the Workflow Execution screen.
 *
 * @property workflow The workflow being executed (null while loading)
 * @property execution The current execution state
 * @property showInputDialog Whether to show the initial input dialog
 * @property isLoading Whether the workflow is still being loaded
 * @property error Error message if loading failed
 */
data class WorkflowExecutionUiState(
    val workflow: Workflow? = null,
    val execution: WorkflowExecution? = null,
    val showInputDialog: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * ViewModel for the Workflow Execution screen.
 *
 * Loads the workflow on init, shows an input dialog for the user's initial
 * prompt, then executes the workflow step-by-step by collecting emissions
 * from [ExecuteWorkflowUseCase]. Supports cancellation of a running execution.
 */
@HiltViewModel
class WorkflowExecutionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val executeWorkflowUseCase: ExecuteWorkflowUseCase,
    private val manageWorkflowsUseCase: ManageWorkflowsUseCase,
    private val providerRepository: ProviderRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val workflowId: String = checkNotNull(savedStateHandle.get<String>("workflowId"))

    private val _uiState = MutableStateFlow(WorkflowExecutionUiState())
    val uiState: StateFlow<WorkflowExecutionUiState> = _uiState.asStateFlow()

    private var executionJob: Job? = null

    init {
        loadWorkflow()
    }

    /**
     * Loads the workflow metadata and shows the input dialog.
     */
    private fun loadWorkflow() {
        viewModelScope.launch {
            try {
                val workflow = manageWorkflowsUseCase.get(workflowId)
                if (workflow != null) {
                    _uiState.update {
                        it.copy(
                            workflow = workflow,
                            isLoading = false,
                            showInputDialog = true
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Workflow not found"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load workflow"
                    )
                }
            }
        }
    }

    /**
     * Starts executing the workflow with the given initial input.
     *
     * Resolves the default provider and model, then collects execution
     * state emissions from the use case and updates the UI state.
     *
     * @param initialInput The user's initial prompt text
     */
    fun execute(initialInput: String) {
        if (initialInput.isBlank()) return

        _uiState.update { it.copy(showInputDialog = false) }

        executionJob = viewModelScope.launch {
            try {
                // Resolve default provider
                val activeProvider = providerRepository.getActiveProvider()
                val providerId = activeProvider?.id ?: run {
                    // Fallback: try first available provider
                    val providers = providerRepository.getProviders()
                    providers.firstOrNull()?.id ?: throw IllegalStateException("No providers configured")
                }

                val provider = providerRepository.getProvider(providerId)
                    ?: throw IllegalStateException("Provider not found")

                val modelName = provider.defaultModel

                executeWorkflowUseCase(
                    workflowId = workflowId,
                    initialInput = initialInput,
                    defaultProviderId = providerId,
                    defaultModelName = modelName
                ).collect { execution ->
                    _uiState.update { it.copy(execution = execution) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        execution = WorkflowExecution(
                            workflowId = workflowId,
                            status = WorkflowStatus.FAILED,
                            error = e.message ?: "Execution failed"
                        )
                    )
                }
            }
        }
    }

    /**
     * Cancels the currently running workflow execution.
     */
    fun cancel() {
        executionJob?.cancel()
        executionJob = null
        _uiState.update { state ->
            state.copy(
                execution = state.execution?.copy(status = WorkflowStatus.CANCELLED)
                    ?: WorkflowExecution(
                        workflowId = workflowId,
                        status = WorkflowStatus.CANCELLED
                    )
            )
        }
    }
}
