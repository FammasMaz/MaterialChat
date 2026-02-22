package com.materialchat.domain.model

/**
 * Domain model representing an arena battle between two AI models.
 *
 * @property id Unique identifier for the battle
 * @property prompt The user prompt sent to both models
 * @property leftModelName The model name for the left panel
 * @property leftProviderId The provider ID for the left model
 * @property leftResponse The accumulated response from the left model
 * @property rightModelName The model name for the right panel
 * @property rightProviderId The provider ID for the right model
 * @property rightResponse The accumulated response from the right model
 * @property winner The voting result (LEFT, RIGHT, TIE, BOTH_BAD), null if not yet voted
 * @property leftThinkingContent Thinking/reasoning content from the left model
 * @property rightThinkingContent Thinking/reasoning content from the right model
 * @property leftDurationMs Response duration in milliseconds for the left model
 * @property rightDurationMs Response duration in milliseconds for the right model
 * @property createdAt Timestamp when the battle was created (epoch milliseconds)
 */
data class ArenaBattle(
    val id: String,
    val prompt: String,
    val leftModelName: String,
    val leftProviderId: String? = null,
    val leftResponse: String,
    val rightModelName: String,
    val rightProviderId: String? = null,
    val rightResponse: String,
    val winner: String? = null,
    val leftThinkingContent: String? = null,
    val rightThinkingContent: String? = null,
    val leftDurationMs: Long? = null,
    val rightDurationMs: Long? = null,
    val createdAt: Long
)
