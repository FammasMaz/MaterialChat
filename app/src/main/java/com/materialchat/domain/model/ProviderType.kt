package com.materialchat.domain.model

/**
 * Represents the type of AI provider.
 */
enum class ProviderType {
    /**
     * OpenAI-compatible API (OpenAI, Groq, Together, Anthropic proxies, etc.)
     * Uses /v1/chat/completions endpoint with SSE streaming.
     */
    OPENAI_COMPATIBLE,

    /**
     * Native Ollama API for local LLM inference.
     * Uses /api/chat endpoint with NDJSON streaming.
     */
    OLLAMA_NATIVE,

    /**
     * OpenAI Codex via ChatGPT/Codex OAuth and the Responses API.
     */
    CODEX_NATIVE,

    /**
     * GitHub Copilot via GitHub device-flow OAuth and Copilot chat endpoints.
     */
    GITHUB_COPILOT_NATIVE,

    /**
     * Google Antigravity via Google OAuth and Antigravity streamGenerateContent.
     */
    ANTIGRAVITY_NATIVE;

    val isNativeAuth: Boolean
        get() = this == CODEX_NATIVE || this == GITHUB_COPILOT_NATIVE || this == ANTIGRAVITY_NATIVE

    val requiresStoredCredential: Boolean
        get() = this == OPENAI_COMPATIBLE || isNativeAuth

    val displayName: String
        get() = when (this) {
            OPENAI_COMPATIBLE -> "OpenAI-compatible"
            OLLAMA_NATIVE -> "Ollama"
            CODEX_NATIVE -> "Codex"
            GITHUB_COPILOT_NATIVE -> "GitHub Copilot"
            ANTIGRAVITY_NATIVE -> "Antigravity"
        }
}
