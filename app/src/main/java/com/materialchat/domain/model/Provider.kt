package com.materialchat.domain.model

import java.util.UUID

/**
 * Represents an AI provider configuration.
 *
 * @property id Unique identifier for the provider
 * @property name Display name for the provider
 * @property type The type of provider (OpenAI-compatible, Ollama, Antigravity, etc.)
 * @property authType The authentication method (API_KEY, OAUTH, NONE)
 * @property baseUrl The base URL for API requests
 * @property defaultModel The default model to use for new conversations
 * @property requiresApiKey Whether this provider requires an API key (deprecated, use authType)
 * @property isActive Whether this is the currently active provider
 * @property systemPrompt Provider-specific system prompt to prepend to conversations
 * @property headers Custom HTTP headers to include in API requests
 * @property options Provider-specific configuration options
 * @property supportsStreaming Whether the provider supports streaming responses
 * @property supportsImages Whether the provider supports image attachments
 * @property supportsPdf Whether the provider supports PDF attachments
 * @property supportsReasoning Whether the provider supports extended thinking/reasoning
 */
data class Provider(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: ProviderType,
    val authType: AuthType = AuthType.API_KEY,
    val baseUrl: String,
    val defaultModel: String,
    val requiresApiKey: Boolean,
    val isActive: Boolean = false,
    val systemPrompt: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val options: Map<String, Any> = emptyMap(),
    val supportsStreaming: Boolean = true,
    val supportsImages: Boolean = false,
    val supportsPdf: Boolean = false,
    val supportsReasoning: Boolean = false
) {
    companion object {
        /**
         * Creates a default OpenAI provider template.
         */
        fun openAiTemplate(): Provider = Provider(
            id = "openai-default",
            name = "OpenAI",
            type = ProviderType.OPENAI_COMPATIBLE,
            authType = AuthType.API_KEY,
            baseUrl = "https://api.openai.com",
            defaultModel = "gpt-4o",
            requiresApiKey = true,
            isActive = false,
            supportsImages = true,
            supportsReasoning = true
        )

        /**
         * Creates a default local Ollama provider template.
         */
        fun ollamaLocalTemplate(): Provider = Provider(
            id = "ollama-local",
            name = "Local Ollama",
            type = ProviderType.OLLAMA_NATIVE,
            authType = AuthType.NONE,
            baseUrl = "http://localhost:11434",
            defaultModel = "llama3.2",
            requiresApiKey = false,
            isActive = true,
            supportsImages = true,
            supportsReasoning = true
        )

        /**
         * Creates a default OpenRouter provider template.
         */
        fun openRouterTemplate(): Provider = Provider(
            id = "openrouter-default",
            name = "OpenRouter",
            type = ProviderType.OPENAI_COMPATIBLE,
            authType = AuthType.API_KEY,
            baseUrl = "https://openrouter.ai/api/v1",
            defaultModel = "openai/gpt-4o",
            requiresApiKey = true,
            isActive = false,
            headers = mapOf("HTTP-Referer" to "https://materialchat.app"),
            supportsImages = true,
            supportsReasoning = true
        )

        /**
         * Creates an Antigravity provider template.
         * Requires OAuth authentication with Google.
         */
        fun antigravityTemplate(): Provider = Provider(
            id = "antigravity-default",
            name = "Antigravity",
            type = ProviderType.ANTIGRAVITY,
            authType = AuthType.OAUTH,
            baseUrl = AntigravityConfig.ENDPOINT_PROD,
            defaultModel = "antigravity-claude-sonnet-4-5",
            requiresApiKey = false,
            isActive = false,
            systemPrompt = AntigravityConfig.SYSTEM_INSTRUCTION,
            headers = AntigravityConfig.REQUEST_HEADERS,
            supportsStreaming = true,
            supportsImages = true,
            supportsPdf = true,
            supportsReasoning = true
        )

        /**
         * Creates a native Anthropic provider template.
         */
        fun anthropicTemplate(): Provider = Provider(
            id = "anthropic-default",
            name = "Anthropic",
            type = ProviderType.ANTHROPIC,
            authType = AuthType.API_KEY,
            baseUrl = "https://api.anthropic.com",
            defaultModel = "claude-sonnet-4-20250514",
            requiresApiKey = true,
            isActive = false,
            headers = mapOf(
                "anthropic-version" to "2023-06-01",
                "anthropic-beta" to "output-128k-2025-02-19"
            ),
            supportsImages = true,
            supportsReasoning = true
        )

        /**
         * Creates a Google Gemini provider template.
         */
        fun geminiTemplate(): Provider = Provider(
            id = "gemini-default",
            name = "Google Gemini",
            type = ProviderType.GOOGLE_GEMINI,
            authType = AuthType.API_KEY,
            baseUrl = "https://generativelanguage.googleapis.com",
            defaultModel = "gemini-2.0-flash",
            requiresApiKey = true,
            isActive = false,
            supportsImages = true,
            supportsReasoning = true
        )
    }
}
