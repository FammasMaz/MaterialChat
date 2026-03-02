package com.materialchat.domain.model

/**
 * Represents the execution state of a running workflow.
 *
 * @property workflowId The workflow being executed
 * @property stepResults Map of step index to output content
 * @property currentStepIndex The index of the currently executing step (0-indexed)
 * @property totalSteps Total number of steps in the workflow
 * @property status The current execution status
 * @property currentStepContent Partial streaming content for the current step
 * @property error Error message if status is FAILED
 */
data class WorkflowExecution(
    val workflowId: String,
    val stepResults: Map<Int, String> = emptyMap(),
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val status: WorkflowStatus = WorkflowStatus.IDLE,
    val currentStepContent: String = "",
    val error: String? = null
)

/**
 * Status of a workflow execution.
 */
enum class WorkflowStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
