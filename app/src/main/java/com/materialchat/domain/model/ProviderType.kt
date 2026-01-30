package com.materialchat.domain.model

/**
 * Represents the type of AI provider.
 *
 * Each type determines the API format, streaming parser, authentication method,
 * and default behavior for interacting with the provider.
 */
enum class ProviderType {
    /**
     * OpenAI-compatible API (OpenAI, Groq, Together, OpenRouter, etc.)
     * Uses /v1/chat/completions endpoint with SSE streaming.
     */
    OPENAI_COMPATIBLE,

    /**
     * Native Ollama API for local LLM inference.
     * Uses /api/chat endpoint with NDJSON streaming.
     */
    OLLAMA_NATIVE,

    /**
     * Native Anthropic Claude API.
     * Uses /v1/messages endpoint with SSE streaming.
     * Requires anthropic-beta headers for extended features.
     */
    ANTHROPIC,

    /**
     * Google Gemini API.
     * Uses Gemini-style request/response format.
     * Supports both API key and OAuth authentication.
     */
    GOOGLE_GEMINI,

    /**
     * GitHub Copilot API.
     * Requires OAuth authentication with GitHub.
     */
    GITHUB_COPILOT,

    /**
     * Google Antigravity (internal Gemini/Claude access).
     * Uses Gemini-style API format with Google OAuth.
     * Provides access to Claude and Gemini models.
     */
    ANTIGRAVITY
}
