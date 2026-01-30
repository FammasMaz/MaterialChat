package com.materialchat.data.auth

/**
 * OAuth-specific exceptions for error handling during authentication flows.
 *
 * These exceptions provide descriptive messages for UI display and
 * indicate whether the error is recoverable (user can retry).
 */
sealed class OAuthException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /**
     * The OAuth state parameter from the callback doesn't match the one we sent.
     * This could indicate a CSRF attack or stale OAuth flow.
     */
    class InvalidState(
        receivedState: String? = null
    ) : OAuthException(
        message = "OAuth state mismatch. The authorization request may have expired. " +
                "Please try signing in again."
    )

    /**
     * Failed to exchange the authorization code for tokens.
     * This could be due to network issues, invalid code, or server errors.
     */
    class TokenExchangeFailed(
        reason: String,
        cause: Throwable? = null
    ) : OAuthException(
        message = "Failed to complete sign-in: $reason",
        cause = cause
    )

    /**
     * Failed to refresh an expired access token.
     * User may need to re-authenticate.
     */
    class RefreshFailed(
        reason: String,
        cause: Throwable? = null
    ) : OAuthException(
        message = "Session expired and could not be renewed: $reason",
        cause = cause
    )

    /**
     * Network error during OAuth flow.
     */
    class NetworkError(
        cause: Throwable? = null
    ) : OAuthException(
        message = "Network error during authentication. Please check your connection and try again.",
        cause = cause
    )

    /**
     * User cancelled the OAuth flow.
     */
    class UserCancelled : OAuthException(
        message = "Sign-in was cancelled."
    )

    /**
     * The OAuth callback was missing required parameters (code, state).
     */
    class InvalidCallback(
        reason: String
    ) : OAuthException(
        message = "Invalid OAuth callback: $reason"
    )

    /**
     * The provider doesn't support OAuth authentication.
     */
    class UnsupportedProvider(
        providerName: String
    ) : OAuthException(
        message = "$providerName does not support OAuth authentication."
    )

    /**
     * Failed to fetch user information after authentication.
     */
    class UserInfoFailed(
        reason: String,
        cause: Throwable? = null
    ) : OAuthException(
        message = "Failed to retrieve user information: $reason",
        cause = cause
    )

    /**
     * Checks if this error is recoverable (user can retry).
     */
    val isRecoverable: Boolean
        get() = when (this) {
            is InvalidState -> true
            is TokenExchangeFailed -> true
            is RefreshFailed -> false // Need to re-authenticate
            is NetworkError -> true
            is UserCancelled -> true
            is InvalidCallback -> false
            is UnsupportedProvider -> false
            is UserInfoFailed -> true
        }
}
