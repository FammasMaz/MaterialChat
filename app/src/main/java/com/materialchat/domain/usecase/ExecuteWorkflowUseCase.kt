package com.materialchat.domain.usecase

import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.model.WorkflowExecution
import com.materialchat.domain.model.WorkflowStatus
import com.materialchat.domain.model.WorkflowStep
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ProviderRepository
import com.materialchat.domain.repository.WorkflowRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Use case for executing a workflow step-by-step.
 * Each step's output feeds into the next step's template variables.
 */
class ExecuteWorkflowUseCase @Inject constructor(
    private val workflowRepository: WorkflowRepository,
    private val chatRepository: ChatRepository,
    private val providerRepository: ProviderRepository
) {
    /**
     * Executes a workflow with the given initial input.
     *
     * @param workflowId The ID of the workflow to execute
     * @param initialInput The user's initial input (replaces {{input}} placeholders)
     * @param defaultProviderId The default provider ID to use for steps without overrides
     * @param defaultModelName The default model name to use for steps without overrides
     * @return A Flow emitting progressive WorkflowExecution states
     */
    operator fun invoke(
        workflowId: String,
        initialInput: String,
        defaultProviderId: String,
        defaultModelName: String
    ): Flow<WorkflowExecution> = flow {
        val workflow = workflowRepository.getWorkflow(workflowId)
            ?: throw IllegalStateException("Workflow not found: $workflowId")

        val steps = workflow.steps
        if (steps.isEmpty()) {
            emit(WorkflowExecution(
                workflowId = workflowId,
                status = WorkflowStatus.COMPLETED,
                totalSteps = 0
            ))
            return@flow
        }

        val stepResults = mutableMapOf<Int, String>()

        steps.forEachIndexed { index, step ->
            // Resolve template variables
            val resolvedPrompt = resolveTemplate(step.promptTemplate, initialInput, stepResults)

            // Determine provider and model for this step
            val providerId = step.providerId ?: defaultProviderId
            val modelName = step.modelName ?: defaultModelName

            val provider = providerRepository.getProvider(providerId)
                ?: throw IllegalStateException("Provider not found: $providerId")

            // Emit running state
            emit(WorkflowExecution(
                workflowId = workflowId,
                stepResults = stepResults.toMap(),
                currentStepIndex = index,
                totalSteps = steps.size,
                status = WorkflowStatus.RUNNING,
                currentStepContent = ""
            ))

            // Create message list for the API call
            val messages = listOf(
                com.materialchat.domain.model.Message(
                    conversationId = "",
                    role = com.materialchat.domain.model.MessageRole.USER,
                    content = resolvedPrompt
                )
            )

            // Stream the response
            var accumulatedContent = ""
            var wasCancelled = false
            try {
                chatRepository.sendMessage(
                    provider = provider,
                    messages = messages,
                    model = modelName,
                    reasoningEffort = com.materialchat.domain.model.ReasoningEffort.HIGH,
                    systemPrompt = step.systemPrompt
                ).collect { state ->
                    when (state) {
                        is StreamingState.Streaming -> {
                            accumulatedContent = state.content
                            emit(WorkflowExecution(
                                workflowId = workflowId,
                                stepResults = stepResults.toMap(),
                                currentStepIndex = index,
                                totalSteps = steps.size,
                                status = WorkflowStatus.RUNNING,
                                currentStepContent = accumulatedContent
                            ))
                        }
                        is StreamingState.Completed -> {
                            accumulatedContent = state.finalContent
                        }
                        is StreamingState.Error -> {
                            throw state.error
                        }
                        is StreamingState.Cancelled -> {
                            wasCancelled = true
                        }
                        else -> { /* ignore */ }
                    }
                }

                if (wasCancelled) {
                    emit(WorkflowExecution(
                        workflowId = workflowId,
                        stepResults = stepResults.toMap(),
                        currentStepIndex = index,
                        totalSteps = steps.size,
                        status = WorkflowStatus.CANCELLED
                    ))
                    return@flow
                }
            } catch (e: Exception) {
                emit(WorkflowExecution(
                    workflowId = workflowId,
                    stepResults = stepResults.toMap(),
                    currentStepIndex = index,
                    totalSteps = steps.size,
                    status = WorkflowStatus.FAILED,
                    error = e.message ?: "Step ${index + 1} failed"
                ))
                return@flow
            }

            // Store result for next steps
            stepResults[index] = accumulatedContent
        }

        // All steps completed
        emit(WorkflowExecution(
            workflowId = workflowId,
            stepResults = stepResults.toMap(),
            currentStepIndex = steps.size - 1,
            totalSteps = steps.size,
            status = WorkflowStatus.COMPLETED
        ))
    }

    /**
     * Resolves template variables in a prompt.
     * - {{input}} → initialInput
     * - {{step_N}} → output of step N (1-indexed)
     */
    private fun resolveTemplate(
        template: String,
        initialInput: String,
        stepResults: Map<Int, String>
    ): String {
        var resolved = template.replace("{{input}}", initialInput)

        // Replace {{step_N}} with corresponding step output (1-indexed)
        val stepPattern = Regex("\\{\\{step_(\\d+)\\}\\}")
        resolved = stepPattern.replace(resolved) { match ->
            val stepNumber = match.groupValues[1].toIntOrNull() ?: return@replace match.value
            val stepIndex = stepNumber - 1 // Convert 1-indexed to 0-indexed
            stepResults[stepIndex] ?: "[Step $stepNumber output not available]"
        }

        return resolved
    }
}
