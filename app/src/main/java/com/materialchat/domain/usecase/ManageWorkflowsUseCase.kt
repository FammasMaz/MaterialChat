package com.materialchat.domain.usecase

import com.materialchat.domain.model.Workflow
import com.materialchat.domain.model.WorkflowStep
import com.materialchat.domain.repository.WorkflowRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

/**
 * Use case for CRUD operations on workflows, including seeding pre-built templates.
 */
class ManageWorkflowsUseCase @Inject constructor(
    private val workflowRepository: WorkflowRepository
) {
    fun observeAll(): Flow<List<Workflow>> = workflowRepository.observeAllWorkflows()

    suspend fun get(workflowId: String): Workflow? = workflowRepository.getWorkflow(workflowId)

    suspend fun save(workflow: Workflow) = workflowRepository.saveWorkflow(workflow)

    suspend fun delete(workflowId: String) = workflowRepository.deleteWorkflow(workflowId)

    /**
     * Seeds pre-built workflow templates on first launch.
     */
    suspend fun seedTemplatesIfNeeded() {
        if (workflowRepository.hasWorkflows()) return

        val templates = listOf(
            Workflow(
                id = UUID.randomUUID().toString(),
                name = "Research & Summarize",
                description = "Research a topic, analyze findings, then create a concise summary",
                icon = "\uD83D\uDD0D",
                isTemplate = true,
                steps = listOf(
                    WorkflowStep(promptTemplate = "Research the following topic thoroughly and provide detailed findings:\n\n{{input}}"),
                    WorkflowStep(promptTemplate = "Analyze the following research findings. Identify key themes, patterns, and insights:\n\n{{step_1}}"),
                    WorkflowStep(promptTemplate = "Create a concise, well-structured summary based on this analysis. Include key takeaways and conclusions:\n\n{{step_2}}")
                )
            ),
            Workflow(
                id = UUID.randomUUID().toString(),
                name = "Brainstorm & Refine",
                description = "Generate ideas, evaluate them, then refine the best ones",
                icon = "\uD83D\uDCA1",
                isTemplate = true,
                steps = listOf(
                    WorkflowStep(promptTemplate = "Brainstorm 10 creative ideas for the following:\n\n{{input}}"),
                    WorkflowStep(promptTemplate = "Evaluate each of these ideas based on feasibility, impact, and originality. Rank the top 3:\n\n{{step_1}}"),
                    WorkflowStep(promptTemplate = "Take the top-ranked ideas and refine them into detailed, actionable proposals:\n\n{{step_2}}")
                )
            ),
            Workflow(
                id = UUID.randomUUID().toString(),
                name = "Write & Polish",
                description = "Draft content, review it, then polish for publication",
                icon = "\u270D\uFE0F",
                isTemplate = true,
                steps = listOf(
                    WorkflowStep(promptTemplate = "Write a first draft about the following topic:\n\n{{input}}"),
                    WorkflowStep(promptTemplate = "Review this draft. Identify areas for improvement in clarity, structure, tone, and accuracy:\n\n{{step_1}}"),
                    WorkflowStep(promptTemplate = "Apply the review feedback and polish this into a final, publication-ready version:\n\nOriginal draft:\n{{step_1}}\n\nReview feedback:\n{{step_2}}")
                )
            ),
            Workflow(
                id = UUID.randomUUID().toString(),
                name = "Code & Document",
                description = "Write code, debug it, then generate documentation",
                icon = "\uD83D\uDCBB",
                isTemplate = true,
                steps = listOf(
                    WorkflowStep(promptTemplate = "Write code for the following requirement:\n\n{{input}}"),
                    WorkflowStep(promptTemplate = "Review this code for bugs, edge cases, and improvements. Provide a corrected version:\n\n{{step_1}}"),
                    WorkflowStep(promptTemplate = "Generate clear documentation for this code, including usage examples, parameter descriptions, and edge cases:\n\n{{step_2}}")
                )
            )
        )

        templates.forEach { workflowRepository.saveWorkflow(it) }
    }
}
