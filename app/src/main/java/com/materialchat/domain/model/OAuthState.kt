package com.materialchat.domain.model

/**
 * Represents the current OAuth authentication state for a provider.
 *
 * Used by ViewModels and UI components to display the appropriate
 * authentication status and controls.
 */
sealed class OAuthState {
    /**
     * No OAuth tokens present. User needs to authenticate.
     */
    data object Unauthenticated : OAuthState()

    /**
     * OAuth flow is in progress. Show loading indicator.
     */
    data object Authenticating : OAuthState()

    /**
     * Successfully authenticated with OAuth.
     *
     * @property email The authenticated user's email
     * @property expiresAt Token expiry timestamp in milliseconds
     */
    data class Authenticated(
        val email: String,
        val expiresAt: Long
    ) : OAuthState() {
        /**
         * Checks if the authentication is still valid.
         */
        fun isValid(): Boolean = System.currentTimeMillis() < expiresAt
    }

    /**
     * OAuth authentication failed.
     *
     * @property message Error description for display
     * @property isRetryable Whether the user can retry authentication
     */
    data class Error(
        val message: String,
        val isRetryable: Boolean = true
    ) : OAuthState()
}
