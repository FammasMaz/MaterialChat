package com.materialchat.ui.screens.workflows

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.domain.model.Workflow
import com.materialchat.domain.model.WorkflowStep
import com.materialchat.domain.usecase.ManageWorkflowsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * UI state for the Workflow Builder screen.
 *
 * @property name The workflow name
 * @property description The workflow description
 * @property icon The workflow emoji icon
 * @property steps The mutable list of step prompt templates
 * @property isEditing Whether we are editing an existing workflow (vs. creating new)
 * @property isSaving Whether a save operation is in progress
 * @property savedSuccessfully Whether the workflow was saved successfully (triggers navigation)
 */
data class WorkflowBuilderUiState(
    val name: String = "",
    val description: String = "",
    val icon: String = "\uD83D\uDD17",
    val steps: List<String> = listOf(""),
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false
)

/**
 * ViewModel for the Workflow Builder screen.
 *
 * Handles creating and editing workflows. If a workflowId is provided via
 * SavedStateHandle, loads the existing workflow for editing. Otherwise,
 * starts with a blank workflow for creation.
 */
@HiltViewModel
class WorkflowBuilderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val manageWorkflowsUseCase: ManageWorkflowsUseCase
) : ViewModel() {

    private val workflowId: String? = savedStateHandle.get<String>("workflowId")
        ?.takeIf { it != "new" }

    private val _uiState = MutableStateFlow(WorkflowBuilderUiState())
    val uiState: StateFlow<WorkflowBuilderUiState> = _uiState.asStateFlow()

    // Store the original workflow for updates
    private var existingWorkflow: Workflow? = null

    init {
        if (workflowId != null) {
            loadWorkflow(workflowId)
        }
    }

    /**
     * Loads an existing workflow for editing.
     */
    private fun loadWorkflow(id: String) {
        viewModelScope.launch {
            val workflow = manageWorkflowsUseCase.get(id) ?: return@launch
            existingWorkflow = workflow
            _uiState.update {
                it.copy(
                    name = workflow.name,
                    description = workflow.description,
                    icon = workflow.icon,
                    steps = workflow.steps.map { step -> step.promptTemplate }
                        .ifEmpty { listOf("") },
                    isEditing = true
                )
            }
        }
    }

    /**
     * Updates the workflow name.
     */
    fun updateName(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    /**
     * Updates the workflow description.
     */
    fun updateDescription(description: String) {
        _uiState.update { it.copy(description = description) }
    }

    /**
     * Adds a new empty step at the end of the list.
     */
    fun addStep() {
        _uiState.update { it.copy(steps = it.steps + "") }
    }

    /**
     * Removes a step at the given index.
     * At least one step must remain.
     */
    fun removeStep(index: Int) {
        _uiState.update { state ->
            if (state.steps.size <= 1) return@update state
            state.copy(steps = state.steps.toMutableList().apply { removeAt(index) })
        }
    }

    /**
     * Updates the prompt template at the given index.
     */
    fun updateStep(index: Int, promptTemplate: String) {
        _uiState.update { state ->
            val mutableSteps = state.steps.toMutableList()
            if (index in mutableSteps.indices) {
                mutableSteps[index] = promptTemplate
            }
            state.copy(steps = mutableSteps)
        }
    }

    /**
     * Moves a step from one position to another (drag-to-reorder).
     */
    fun moveStep(from: Int, to: Int) {
        _uiState.update { state ->
            val mutableSteps = state.steps.toMutableList()
            if (from in mutableSteps.indices && to in mutableSteps.indices) {
                val item = mutableSteps.removeAt(from)
                mutableSteps.add(to, item)
            }
            state.copy(steps = mutableSteps)
        }
    }

    /**
     * Saves the workflow (creates new or updates existing).
     * Sets [WorkflowBuilderUiState.savedSuccessfully] on success for navigation.
     */
    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) return
        if (state.isSaving) return

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                val workflowSteps = state.steps
                    .filter { it.isNotBlank() }
                    .mapIndexed { index, template ->
                        WorkflowStep(
                            id = existingWorkflow?.steps?.getOrNull(index)?.id
                                ?: UUID.randomUUID().toString(),
                            workflowId = workflowId ?: "",
                            stepOrder = index,
                            promptTemplate = template
                        )
                    }

                val workflow = Workflow(
                    id = workflowId ?: UUID.randomUUID().toString(),
                    name = state.name,
                    description = state.description,
                    icon = state.icon,
                    steps = workflowSteps,
                    isTemplate = existingWorkflow?.isTemplate ?: false,
                    createdAt = existingWorkflow?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                manageWorkflowsUseCase.save(workflow)
                _uiState.update { it.copy(isSaving = false, savedSuccessfully = true) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
