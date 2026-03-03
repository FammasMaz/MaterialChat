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
    OLLAMA_NATIVE
}
