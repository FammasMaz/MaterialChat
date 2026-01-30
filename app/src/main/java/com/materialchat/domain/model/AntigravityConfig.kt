package com.materialchat.domain.model

/**
 * Configuration constants for the Antigravity provider.
 *
 * Antigravity provides access to Claude and Gemini models via Google OAuth.
 * Based on reference implementation from opencode-antigravity-auth plugin.
 */
object AntigravityConfig {
    /**
     * OAuth client ID for Antigravity authentication.
     */
    const val CLIENT_ID = "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com"

    /**
     * OAuth client secret for Antigravity authentication.
     */
    const val CLIENT_SECRET = "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"

    /**
     * Google OAuth authorization URL.
     */
    const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"

    /**
     * Google OAuth token exchange URL.
     */
    const val TOKEN_URL = "https://oauth2.googleapis.com/token"

    /**
     * Google userinfo API URL for fetching user profile.
     */
    const val USERINFO_URL = "https://www.googleapis.com/oauth2/v1/userinfo"

    /**
     * OAuth redirect URI for MaterialChat app.
     */
    const val REDIRECT_URI = "materialchat://oauth/antigravity"

    /**
     * OAuth scopes required for Antigravity access.
     */
    val SCOPES = listOf(
        "https://www.googleapis.com/auth/cloud-platform",
        "https://www.googleapis.com/auth/userinfo.email",
        "https://www.googleapis.com/auth/userinfo.profile",
        "https://www.googleapis.com/auth/cclog",
        "https://www.googleapis.com/auth/experimentsandconfigs"
    )

    /**
     * Daily sandbox endpoint (primary, most up-to-date features).
     */
    const val ENDPOINT_DAILY = "https://daily-cloudcode-pa.sandbox.googleapis.com"

    /**
     * Autopush sandbox endpoint (secondary fallback).
     */
    const val ENDPOINT_AUTOPUSH = "https://autopush-cloudcode-pa.sandbox.googleapis.com"

    /**
     * Production endpoint (final fallback, most stable).
     */
    const val ENDPOINT_PROD = "https://cloudcode-pa.googleapis.com"

    /**
     * Ordered list of endpoints to try, with fallback logic.
     */
    val ENDPOINT_FALLBACKS = listOf(ENDPOINT_DAILY, ENDPOINT_AUTOPUSH, ENDPOINT_PROD)

    /**
     * Default project ID if resolution fails.
     */
    const val DEFAULT_PROJECT_ID = "rising-fact-p41fc"

    /**
     * Plugin version for User-Agent header.
     */
    const val VERSION = "1.15.8"

    /**
     * System instruction injected for Antigravity requests.
     * This establishes the Antigravity identity and capabilities.
     */
    const val SYSTEM_INSTRUCTION = """You are Antigravity, a powerful agentic AI coding assistant designed by the Google DeepMind team working on Advanced Agentic Coding.
You are pair programming with a USER to solve their coding task. The task may require creating a new codebase, modifying or debugging an existing codebase, or simply answering a question.
**Absolute paths only**
**Proactiveness**"""

    /**
     * Required HTTP headers for Antigravity API requests.
     */
    val REQUEST_HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android) Antigravity/$VERSION",
        "X-Goog-Api-Client" to "google-cloud-sdk vscode_cloudshelleditor/0.1",
        "Client-Metadata" to """{"ideType":"IDE_UNSPECIFIED","platform":"PLATFORM_UNSPECIFIED","pluginType":"GEMINI"}"""
    )

    /**
     * Returns the OAuth configuration for Antigravity.
     */
    fun getOAuthConfig(): OAuthConfig = OAuthConfig(
        clientId = CLIENT_ID,
        clientSecret = CLIENT_SECRET,
        authorizationUrl = AUTH_URL,
        tokenUrl = TOKEN_URL,
        redirectUri = REDIRECT_URI,
        scopes = SCOPES,
        additionalParams = mapOf(
            "access_type" to "offline",
            "prompt" to "consent"
        )
    )

    /**
     * Predefined Antigravity models with their capabilities.
     */
    val MODELS = listOf(
        AiModel(
            id = "antigravity-claude-opus-4-5-thinking",
            name = "Claude Opus 4.5 Thinking",
            providerId = "antigravity-default",
            contextWindow = 200000,
            maxOutputTokens = 64000,
            supportsThinking = true,
            maxThinkingTokens = 32768,
            supportsImages = true,
            supportsTools = true
        ),
        AiModel(
            id = "antigravity-claude-sonnet-4-5-thinking",
            name = "Claude Sonnet 4.5 Thinking",
            providerId = "antigravity-default",
            contextWindow = 200000,
            maxOutputTokens = 64000,
            supportsThinking = true,
            maxThinkingTokens = 32768,
            supportsImages = true,
            supportsTools = true
        ),
        AiModel(
            id = "antigravity-claude-sonnet-4-5",
            name = "Claude Sonnet 4.5",
            providerId = "antigravity-default",
            contextWindow = 200000,
            maxOutputTokens = 64000,
            supportsThinking = false,
            supportsImages = true,
            supportsTools = true
        ),
        AiModel(
            id = "antigravity-gemini-3-pro",
            name = "Gemini 3 Pro",
            providerId = "antigravity-default",
            contextWindow = 1048576,
            maxOutputTokens = 65535,
            supportsThinking = true,
            supportsImages = true,
            supportsTools = true
        ),
        AiModel(
            id = "antigravity-gemini-3-flash",
            name = "Gemini 3 Flash",
            providerId = "antigravity-default",
            contextWindow = 1048576,
            maxOutputTokens = 65536,
            supportsThinking = true,
            supportsImages = true,
            supportsTools = true
        )
    )
}
