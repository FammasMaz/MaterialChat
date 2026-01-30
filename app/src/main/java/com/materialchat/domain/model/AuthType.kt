package com.materialchat.domain.model

/**
 * Represents the authentication method used by a provider.
 *
 * Determines which authentication UI, storage mechanism, and
 * request header injection to use for the provider.
 */
enum class AuthType {
    /**
     * No authentication required.
     * Used for local providers like Ollama running on localhost.
     */
    NONE,

    /**
     * API key authentication.
     * Key is stored encrypted and sent as Bearer token in Authorization header.
     */
    API_KEY,

    /**
     * OAuth 2.0 authentication with PKCE.
     * Requires OAuth flow through browser/Custom Tabs.
     * Tokens are stored encrypted and automatically refreshed.
     */
    OAUTH
}
