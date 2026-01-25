package com.materialchat.domain.model

import java.util.UUID

/**
 * Represents an AI provider configuration.
 *
 * @property id Unique identifier for the provider
 * @property name Display name for the provider
 * @property type The type of provider (OpenAI-compatible or Ollama)
 * @property baseUrl The base URL for API requests
 * @property defaultModel The default model to use for new conversations
 * @property requiresApiKey Whether this provider requires an API key
 * @property isActive Whether this is the currently active provider
 */
data class Provider(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: ProviderType,
    val baseUrl: String,
    val defaultModel: String,
    val requiresApiKey: Boolean,
    val isActive: Boolean = false
) {
    companion object {
        /**
         * Creates a default OpenAI provider template.
         */
        fun openAiTemplate(): Provider = Provider(
            id = "openai-default",
            name = "OpenAI",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.openai.com",
            defaultModel = "gpt-4o",
            requiresApiKey = true,
            isActive = false
        )

        /**
         * Creates a default local Ollama provider template.
         */
        fun ollamaLocalTemplate(): Provider = Provider(
            id = "ollama-local",
            name = "Local Ollama",
            type = ProviderType.OLLAMA_NATIVE,
            baseUrl = "http://localhost:11434",
            defaultModel = "llama3.2",
            requiresApiKey = false,
            isActive = true
        )

        /**
         * Creates a default OpenRouter provider template.
         */
        fun openRouterTemplate(): Provider = Provider(
            id = "openrouter-default",
            name = "OpenRouter",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://openrouter.ai/api/v1",
            defaultModel = "openai/gpt-4o",
            requiresApiKey = true,
            isActive = false
        )
    }
}
