package com.materialchat.domain.repository

import com.materialchat.domain.model.Workflow
import com.materialchat.domain.model.WorkflowStep
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for workflow persistence operations.
 */
interface WorkflowRepository {

    /**
     * Observes all workflows sorted by most recently updated.
     */
    fun observeAllWorkflows(): Flow<List<Workflow>>

    /**
     * Gets a workflow by its ID, including its steps.
     */
    suspend fun getWorkflow(workflowId: String): Workflow?

    /**
     * Gets all steps for a workflow, ordered by stepOrder.
     */
    suspend fun getStepsForWorkflow(workflowId: String): List<WorkflowStep>

    /**
     * Creates or updates a workflow.
     */
    suspend fun saveWorkflow(workflow: Workflow)

    /**
     * Saves a list of steps for a workflow, replacing existing steps.
     */
    suspend fun saveSteps(workflowId: String, steps: List<WorkflowStep>)

    /**
     * Deletes a workflow and all its steps.
     */
    suspend fun deleteWorkflow(workflowId: String)

    /**
     * Checks if any workflows exist (used for template seeding).
     */
    suspend fun hasWorkflows(): Boolean
}
