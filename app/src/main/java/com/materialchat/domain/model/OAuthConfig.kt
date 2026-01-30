package com.materialchat.domain.model

/**
 * Configuration for OAuth 2.0 authentication with a provider.
 *
 * Contains all the necessary endpoints and credentials to perform
 * the OAuth authorization code flow with PKCE.
 *
 * @property clientId The OAuth client ID
 * @property clientSecret The OAuth client secret (optional for public clients using PKCE)
 * @property authorizationUrl The URL to redirect users for authorization
 * @property tokenUrl The URL to exchange authorization code for tokens
 * @property redirectUri The URI to redirect back to after authorization
 * @property scopes The OAuth scopes to request
 * @property additionalParams Additional query parameters for the authorization URL
 */
data class OAuthConfig(
    val clientId: String,
    val clientSecret: String? = null,
    val authorizationUrl: String,
    val tokenUrl: String,
    val redirectUri: String,
    val scopes: List<String>,
    val additionalParams: Map<String, String> = emptyMap()
)
