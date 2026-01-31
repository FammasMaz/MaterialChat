package com.materialchat.domain.model

/**
 * Centralized registry of built-in provider configurations.
 *
 * This object provides:
 * - Default provider templates for first-launch seeding
 * - All available provider templates for user selection
 * - Provider categorization (by auth type, features, etc.)
 *
 * Why this exists:
 * - Single source of truth for provider configurations
 * - Easy to add/modify providers without touching repository code
 * - Clear separation between provider definitions and persistence logic
 */
object BuiltInProviders {

    /**
     * Providers seeded on first launch.
     *
     * These are the default providers available to new users:
     * - Ollama Local: No-setup local AI (active by default)
     * - OpenAI: Most popular cloud AI provider
     * - OpenRouter: Multi-provider gateway with many models
     * - Antigravity: OAuth-authenticated agentic AI service
     */
    val defaultProviders: List<Provider> = listOf(
        Provider.ollamaLocalTemplate(),    // Active by default
        Provider.openAiTemplate(),
        Provider.openRouterTemplate(),
        Provider.antigravityTemplate()
    )

    /**
     * All available provider templates that users can add.
     *
     * Includes both default and optional providers.
     */
    val allProviderTemplates: List<Provider> = listOf(
        Provider.ollamaLocalTemplate(),
        Provider.openAiTemplate(),
        Provider.openRouterTemplate(),
        Provider.antigravityTemplate(),
        Provider.anthropicTemplate(),
        Provider.geminiTemplate()
    )

    /**
     * Providers that require API key authentication.
     */
    val apiKeyProviders: List<Provider>
        get() = allProviderTemplates.filter { it.authType == AuthType.API_KEY }

    /**
     * Providers that require OAuth authentication.
     */
    val oauthProviders: List<Provider>
        get() = allProviderTemplates.filter { it.authType == AuthType.OAUTH }

    /**
     * Providers that don't require authentication.
     */
    val noAuthProviders: List<Provider>
        get() = allProviderTemplates.filter { it.authType == AuthType.NONE }

    /**
     * Providers that support extended thinking/reasoning.
     */
    val reasoningProviders: List<Provider>
        get() = allProviderTemplates.filter { it.supportsReasoning }

    /**
     * Providers that support image attachments.
     */
    val visionProviders: List<Provider>
        get() = allProviderTemplates.filter { it.supportsImages }

    /**
     * Gets a provider template by its default ID.
     *
     * @param id The provider template ID (e.g., "openai-default", "antigravity-default")
     * @return The provider template, or null if not found
     */
    fun getTemplateById(id: String): Provider? {
        return allProviderTemplates.find { it.id == id }
    }

    /**
     * Gets all provider templates for a specific provider type.
     *
     * @param type The provider type to filter by
     * @return List of matching provider templates
     */
    fun getTemplatesByType(type: ProviderType): List<Provider> {
        return allProviderTemplates.filter { it.type == type }
    }
}
