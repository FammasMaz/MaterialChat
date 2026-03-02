package com.materialchat.domain.model

import java.util.UUID

/**
 * Represents a multi-step prompt workflow.
 *
 * @property id Unique identifier
 * @property name Display name for the workflow
 * @property description Brief description of what the workflow does
 * @property icon Emoji icon for the workflow
 * @property steps Ordered list of workflow steps
 * @property isTemplate Whether this is a pre-built template
 * @property createdAt Creation timestamp (epoch ms)
 * @property updatedAt Last update timestamp (epoch ms)
 */
data class Workflow(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val icon: String = "\uD83D\uDD17",
    val steps: List<WorkflowStep> = emptyList(),
    val isTemplate: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
