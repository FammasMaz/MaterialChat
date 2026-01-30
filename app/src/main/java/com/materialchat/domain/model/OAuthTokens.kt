package com.materialchat.domain.model

/**
 * OAuth token data for authenticated providers.
 *
 * Used for in-memory token passing between components.
 * Actual token storage is handled by EncryptedPreferences.
 *
 * @property accessToken The OAuth access token for API requests
 * @property refreshToken The refresh token for obtaining new access tokens
 * @property expiresAt Token expiry timestamp in milliseconds since epoch
 * @property tokenType The token type (usually "Bearer")
 * @property scope The granted OAuth scopes
 * @property email The authenticated user's email (if available)
 * @property projectId Provider-specific project ID (e.g., for Antigravity)
 */
data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAt: Long,
    val tokenType: String = "Bearer",
    val scope: String? = null,
    val email: String? = null,
    val projectId: String? = null
) {
    /**
     * Checks if the access token has expired.
     * Uses a 60-second buffer to account for clock drift and network latency.
     */
    fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAt - EXPIRY_BUFFER_MS

    /**
     * Checks if the token needs to be refreshed.
     * Returns true if the token is expired and a refresh token is available.
     */
    fun needsRefresh(): Boolean = isExpired() && refreshToken != null

    companion object {
        /**
         * Buffer time before actual expiry to trigger refresh (60 seconds).
         */
        private const val EXPIRY_BUFFER_MS = 60_000L
    }
}
