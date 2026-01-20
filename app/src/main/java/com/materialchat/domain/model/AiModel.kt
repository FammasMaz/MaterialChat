package com.materialchat.domain.model

/**
 * Represents an AI model available from a provider.
 *
 * @property id The unique identifier of the model (e.g., "gpt-4o", "llama3.2:latest")
 * @property name Display name for the model (often same as id)
 * @property providerId The ID of the provider that offers this model
 */
data class AiModel(
    val id: String,
    val name: String = id,
    val providerId: String
)
