package com.materialchat.domain.model

import java.util.UUID

/**
 * Represents a single step in a workflow.
 *
 * Supports template variables:
 * - `{{input}}` — The user's initial input
 * - `{{step_N}}` — The output of step N (1-indexed)
 *
 * @property id Unique identifier
 * @property workflowId Parent workflow ID
 * @property stepOrder The order of this step (0-indexed)
 * @property promptTemplate The prompt template with variable placeholders
 * @property modelName Optional model override for this step
 * @property providerId Optional provider override for this step
 * @property systemPrompt Optional system prompt override for this step
 */
data class WorkflowStep(
    val id: String = UUID.randomUUID().toString(),
    val workflowId: String = "",
    val stepOrder: Int = 0,
    val promptTemplate: String,
    val modelName: String? = null,
    val providerId: String? = null,
    val systemPrompt: String? = null
)
