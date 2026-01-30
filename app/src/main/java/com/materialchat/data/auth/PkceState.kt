package com.materialchat.data.auth

/**
 * Tracks the state of an in-progress OAuth PKCE flow.
 *
 * This data is stored in memory during the OAuth authorization flow and
 * validated when the callback is received. It ensures:
 * 1. The callback matches the request we initiated (state validation)
 * 2. We have the code_verifier to exchange for tokens
 * 3. We know which provider the flow is for
 *
 * @property codeVerifier The PKCE code verifier (kept secret, sent during token exchange)
 * @property state The random state/nonce sent with the authorization request
 * @property providerId The ID of the provider this OAuth flow is for
 * @property projectId Optional project ID (for providers like Antigravity)
 * @property createdAt Timestamp when this PKCE session was created (for expiry)
 */
data class PkceState(
    val codeVerifier: String,
    val state: String,
    val providerId: String,
    val projectId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Checks if this PKCE session has expired.
     * OAuth authorization flows should complete within a reasonable time.
     *
     * @param maxAgeMs Maximum age in milliseconds (default: 10 minutes)
     * @return True if the session has expired
     */
    fun isExpired(maxAgeMs: Long = DEFAULT_MAX_AGE_MS): Boolean {
        return System.currentTimeMillis() - createdAt > maxAgeMs
    }

    /**
     * Validates that the state from the callback matches this session.
     *
     * @param callbackState The state parameter from the OAuth callback
     * @return True if the states match
     */
    fun validateState(callbackState: String): Boolean {
        return state == callbackState
    }

    companion object {
        /**
         * Default maximum age for a PKCE session (10 minutes).
         * After this time, the authorization flow should be restarted.
         */
        const val DEFAULT_MAX_AGE_MS = 10 * 60 * 1000L
    }
}
