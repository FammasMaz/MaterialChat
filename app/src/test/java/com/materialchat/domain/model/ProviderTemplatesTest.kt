package com.materialchat.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Provider templates and BuiltInProviders.
 *
 * Tests cover:
 * - Provider template creation (all types)
 * - BuiltInProviders registry
 * - Provider categorization by auth type and features
 */
class ProviderTemplatesTest {

    // ============================================================================
    // Provider Template Tests
    // ============================================================================

    @Test
    fun `openAiTemplate - creates correct OpenAI provider configuration`() {
        val provider = Provider.openAiTemplate()

        assertEquals("openai-default", provider.id)
        assertEquals("OpenAI", provider.name)
        assertEquals(ProviderType.OPENAI_COMPATIBLE, provider.type)
        assertEquals(AuthType.API_KEY, provider.authType)
        assertEquals("https://api.openai.com", provider.baseUrl)
        assertEquals("gpt-4o", provider.defaultModel)
        assertTrue(provider.requiresApiKey)
        assertFalse(provider.isActive)
        assertTrue(provider.supportsImages)
        assertTrue(provider.supportsReasoning)
    }

    @Test
    fun `ollamaLocalTemplate - creates correct Ollama provider configuration`() {
        val provider = Provider.ollamaLocalTemplate()

        assertEquals("ollama-local", provider.id)
        assertEquals("Local Ollama", provider.name)
        assertEquals(ProviderType.OLLAMA_NATIVE, provider.type)
        assertEquals(AuthType.NONE, provider.authType)
        assertEquals("http://localhost:11434", provider.baseUrl)
        assertEquals("llama3.2", provider.defaultModel)
        assertFalse(provider.requiresApiKey)
        assertTrue(provider.isActive) // Ollama is active by default
        assertTrue(provider.supportsImages)
        assertTrue(provider.supportsReasoning)
    }

    @Test
    fun `openRouterTemplate - creates correct OpenRouter provider configuration`() {
        val provider = Provider.openRouterTemplate()

        assertEquals("openrouter-default", provider.id)
        assertEquals("OpenRouter", provider.name)
        assertEquals(ProviderType.OPENAI_COMPATIBLE, provider.type)
        assertEquals(AuthType.API_KEY, provider.authType)
        assertEquals("https://openrouter.ai/api/v1", provider.baseUrl)
        assertEquals("openai/gpt-4o", provider.defaultModel)
        assertTrue(provider.requiresApiKey)
        assertFalse(provider.isActive)
        assertTrue(provider.headers.containsKey("HTTP-Referer"))
    }

    @Test
    fun `antigravityTemplate - creates correct Antigravity provider configuration`() {
        val provider = Provider.antigravityTemplate()

        assertEquals("antigravity-default", provider.id)
        assertEquals("Antigravity", provider.name)
        assertEquals(ProviderType.ANTIGRAVITY, provider.type)
        assertEquals(AuthType.OAUTH, provider.authType)
        assertFalse(provider.requiresApiKey)
        assertFalse(provider.isActive)
        assertTrue(provider.supportsStreaming)
        assertTrue(provider.supportsImages)
        assertTrue(provider.supportsPdf)
        assertTrue(provider.supportsReasoning)
        assertNotNull(provider.systemPrompt)
        assertTrue(provider.headers.isNotEmpty())
    }

    @Test
    fun `anthropicTemplate - creates correct Anthropic provider configuration`() {
        val provider = Provider.anthropicTemplate()

        assertEquals("anthropic-default", provider.id)
        assertEquals("Anthropic", provider.name)
        assertEquals(ProviderType.ANTHROPIC, provider.type)
        assertEquals(AuthType.API_KEY, provider.authType)
        assertEquals("https://api.anthropic.com", provider.baseUrl)
        assertTrue(provider.defaultModel.contains("claude"))
        assertTrue(provider.requiresApiKey)
        assertTrue(provider.headers.containsKey("anthropic-version"))
    }

    @Test
    fun `geminiTemplate - creates correct Google Gemini provider configuration`() {
        val provider = Provider.geminiTemplate()

        assertEquals("gemini-default", provider.id)
        assertEquals("Google Gemini", provider.name)
        assertEquals(ProviderType.GOOGLE_GEMINI, provider.type)
        assertEquals(AuthType.API_KEY, provider.authType)
        assertEquals("https://generativelanguage.googleapis.com", provider.baseUrl)
        assertTrue(provider.defaultModel.contains("gemini"))
        assertTrue(provider.requiresApiKey)
    }

    // ============================================================================
    // BuiltInProviders Tests
    // ============================================================================

    @Test
    fun `defaultProviders - contains expected providers`() {
        val defaults = BuiltInProviders.defaultProviders

        assertEquals(4, defaults.size)
        assertTrue(defaults.any { it.id == "ollama-local" })
        assertTrue(defaults.any { it.id == "openai-default" })
        assertTrue(defaults.any { it.id == "openrouter-default" })
        assertTrue(defaults.any { it.id == "antigravity-default" })
    }

    @Test
    fun `defaultProviders - only Ollama is active by default`() {
        val defaults = BuiltInProviders.defaultProviders

        val activeProviders = defaults.filter { it.isActive }
        assertEquals(1, activeProviders.size)
        assertEquals("ollama-local", activeProviders.first().id)
    }

    @Test
    fun `allProviderTemplates - contains all 6 provider templates`() {
        val all = BuiltInProviders.allProviderTemplates

        assertEquals(6, all.size)
        assertTrue(all.any { it.type == ProviderType.OPENAI_COMPATIBLE })
        assertTrue(all.any { it.type == ProviderType.OLLAMA_NATIVE })
        assertTrue(all.any { it.type == ProviderType.ANTIGRAVITY })
        assertTrue(all.any { it.type == ProviderType.ANTHROPIC })
        assertTrue(all.any { it.type == ProviderType.GOOGLE_GEMINI })
    }

    @Test
    fun `apiKeyProviders - returns only API_KEY auth type providers`() {
        val apiKeyProviders = BuiltInProviders.apiKeyProviders

        assertTrue(apiKeyProviders.isNotEmpty())
        assertTrue(apiKeyProviders.all { it.authType == AuthType.API_KEY })
        assertTrue(apiKeyProviders.any { it.id == "openai-default" })
        assertTrue(apiKeyProviders.any { it.id == "anthropic-default" })
    }

    @Test
    fun `oauthProviders - returns only OAuth providers`() {
        val oauthProviders = BuiltInProviders.oauthProviders

        assertTrue(oauthProviders.isNotEmpty())
        assertTrue(oauthProviders.all { it.authType == AuthType.OAUTH })
        assertTrue(oauthProviders.any { it.id == "antigravity-default" })
    }

    @Test
    fun `noAuthProviders - returns only NONE auth type providers`() {
        val noAuthProviders = BuiltInProviders.noAuthProviders

        assertTrue(noAuthProviders.isNotEmpty())
        assertTrue(noAuthProviders.all { it.authType == AuthType.NONE })
        assertTrue(noAuthProviders.any { it.id == "ollama-local" })
    }

    @Test
    fun `reasoningProviders - returns providers with reasoning support`() {
        val reasoningProviders = BuiltInProviders.reasoningProviders

        assertTrue(reasoningProviders.isNotEmpty())
        assertTrue(reasoningProviders.all { it.supportsReasoning })
    }

    @Test
    fun `visionProviders - returns providers with image support`() {
        val visionProviders = BuiltInProviders.visionProviders

        assertTrue(visionProviders.isNotEmpty())
        assertTrue(visionProviders.all { it.supportsImages })
    }

    @Test
    fun `getTemplateById - returns correct template`() {
        val openAi = BuiltInProviders.getTemplateById("openai-default")
        val antigravity = BuiltInProviders.getTemplateById("antigravity-default")
        val nonExistent = BuiltInProviders.getTemplateById("non-existent")

        assertNotNull(openAi)
        assertEquals("OpenAI", openAi?.name)
        assertNotNull(antigravity)
        assertEquals("Antigravity", antigravity?.name)
        assertNull(nonExistent)
    }

    @Test
    fun `getTemplatesByType - returns matching templates`() {
        val openAiCompatible = BuiltInProviders.getTemplatesByType(ProviderType.OPENAI_COMPATIBLE)
        val antigravity = BuiltInProviders.getTemplatesByType(ProviderType.ANTIGRAVITY)

        assertTrue(openAiCompatible.size >= 2) // OpenAI and OpenRouter
        assertTrue(openAiCompatible.all { it.type == ProviderType.OPENAI_COMPATIBLE })
        assertEquals(1, antigravity.size)
        assertEquals(ProviderType.ANTIGRAVITY, antigravity.first().type)
    }
}
