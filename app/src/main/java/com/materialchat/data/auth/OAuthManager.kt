package com.materialchat.data.auth

import android.net.Uri
import com.materialchat.data.local.preferences.EncryptedPreferences
import com.materialchat.domain.model.AntigravityConfig
import com.materialchat.domain.model.AuthType
import com.materialchat.domain.model.OAuthState
import com.materialchat.domain.model.OAuthTokens
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central OAuth orchestration manager.
 *
 * Handles the complete OAuth 2.0 authorization code flow with PKCE:
 * 1. Building authorization URLs with PKCE challenge
 * 2. Tracking in-progress OAuth flows (state validation)
 * 3. Exchanging authorization codes for tokens
 * 4. Storing tokens securely
 * 5. Refreshing expired tokens
 * 6. Observing authentication state
 *
 * Thread-safe and designed for use as a singleton.
 */
@Singleton
class OAuthManager @Inject constructor(
    private val encryptedPreferences: EncryptedPreferences,
    private val pkceGenerator: PkceGenerator,
    private val httpClient: OkHttpClient,
    private val json: Json
) {
    /**
     * Active PKCE sessions keyed by state parameter.
     * Used to validate callbacks and retrieve code verifiers.
     */
    private val activeSessions = ConcurrentHashMap<String, PkceState>()

    /**
     * Mutex for token refresh to prevent concurrent refreshes.
     */
    private val refreshMutex = Mutex()

    /**
     * Observable auth state per provider.
     */
    private val authStates = ConcurrentHashMap<String, MutableStateFlow<OAuthState>>()

    /**
     * Represents an authorization request ready to be opened in a browser.
     */
    data class AuthorizationRequest(
        val url: String,
        val state: String,
        val providerId: String
    )

    /**
     * Builds an OAuth authorization URL for a provider.
     *
     * This creates a PKCE session, stores it for callback validation,
     * and returns a URL that should be opened in a Custom Tab.
     *
     * @param provider The provider to authenticate with
     * @param projectId Optional project ID (for providers like Antigravity)
     * @return AuthorizationRequest containing the URL and state
     * @throws OAuthException.UnsupportedProvider if provider doesn't support OAuth
     */
    fun buildAuthorizationUrl(
        provider: Provider,
        projectId: String? = null
    ): AuthorizationRequest {
        if (provider.authType != AuthType.OAUTH) {
            throw OAuthException.UnsupportedProvider(provider.name)
        }

        val config = getOAuthConfig(provider)

        // Generate PKCE parameters
        val codeVerifier = pkceGenerator.generateCodeVerifier()
        val codeChallenge = pkceGenerator.generateCodeChallenge(codeVerifier)
        val state = pkceGenerator.generateState()

        // Store PKCE session for callback validation
        val pkceState = PkceState(
            codeVerifier = codeVerifier,
            state = state,
            providerId = provider.id,
            projectId = projectId
        )
        activeSessions[state] = pkceState

        // Update auth state to Authenticating
        getOrCreateAuthStateFlow(provider.id).value = OAuthState.Authenticating

        // Build authorization URL
        val urlBuilder = Uri.parse(config.authorizationUrl).buildUpon()
            .appendQueryParameter("client_id", config.clientId)
            .appendQueryParameter("redirect_uri", config.redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", config.scopes.joinToString(" "))
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", PkceGenerator.CODE_CHALLENGE_METHOD)

        // Add any provider-specific parameters
        config.additionalParams.forEach { (key, value) ->
            urlBuilder.appendQueryParameter(key, value)
        }

        return AuthorizationRequest(
            url = urlBuilder.build().toString(),
            state = state,
            providerId = provider.id
        )
    }

    /**
     * Handles an OAuth callback URI.
     *
     * Validates the state, exchanges the authorization code for tokens,
     * and stores them securely.
     *
     * @param uri The callback URI containing code and state parameters
     * @return OAuthTokens on success
     * @throws OAuthException on any failure
     */
    suspend fun handleCallback(uri: Uri): Result<OAuthTokens> {
        return try {
            // Extract parameters from callback
            val code = uri.getQueryParameter("code")
                ?: return Result.failure(OAuthException.InvalidCallback("Missing authorization code"))

            val state = uri.getQueryParameter("state")
                ?: return Result.failure(OAuthException.InvalidCallback("Missing state parameter"))

            // Check for error in callback
            val error = uri.getQueryParameter("error")
            if (error != null) {
                val description = uri.getQueryParameter("error_description") ?: error
                activeSessions.remove(state)
                return Result.failure(
                    if (error == "access_denied") {
                        OAuthException.UserCancelled()
                    } else {
                        OAuthException.TokenExchangeFailed(description)
                    }
                )
            }

            // Validate state and retrieve PKCE session
            val pkceState = activeSessions.remove(state)
                ?: return Result.failure(OAuthException.InvalidState(state))

            if (pkceState.isExpired()) {
                return Result.failure(OAuthException.InvalidState(state))
            }

            // Exchange code for tokens
            val tokens = exchangeCodeForTokens(
                code = code,
                codeVerifier = pkceState.codeVerifier,
                providerId = pkceState.providerId,
                projectId = pkceState.projectId
            )

            // Store tokens securely
            storeTokens(pkceState.providerId, tokens)

            // Update auth state
            getOrCreateAuthStateFlow(pkceState.providerId).value = OAuthState.Authenticated(
                email = tokens.email ?: "Unknown",
                expiresAt = tokens.expiresAt
            )

            Result.success(tokens)
        } catch (e: OAuthException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(OAuthException.NetworkError(e))
        } catch (e: Exception) {
            Result.failure(OAuthException.TokenExchangeFailed(e.message ?: "Unknown error", e))
        }
    }

    /**
     * Refreshes tokens for a provider if they're expired.
     *
     * @param providerId The provider to refresh tokens for
     * @return Fresh OAuthTokens, or failure if refresh not possible
     */
    suspend fun refreshTokens(providerId: String): Result<OAuthTokens> {
        return refreshMutex.withLock {
            try {
                val refreshToken = encryptedPreferences.getRefreshToken(providerId)
                    ?: return@withLock Result.failure(
                        OAuthException.RefreshFailed("No refresh token available")
                    )

                // Get provider config (for now, assume Antigravity)
                val config = AntigravityConfig.getOAuthConfig()

                // Build refresh request
                val formBody = FormBody.Builder()
                    .add("client_id", config.clientId)
                    .add("client_secret", config.clientSecret ?: "")
                    .add("refresh_token", refreshToken)
                    .add("grant_type", "refresh_token")
                    .build()

                val request = Request.Builder()
                    .url(config.tokenUrl)
                    .post(formBody)
                    .build()

                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    return@withLock Result.failure(
                        OAuthException.RefreshFailed("Token refresh failed: $errorBody")
                    )
                }

                val responseBody = response.body?.string()
                    ?: return@withLock Result.failure(
                        OAuthException.RefreshFailed("Empty response from token endpoint")
                    )

                val tokens = parseTokenResponse(responseBody, providerId)

                // Store new tokens
                storeTokens(providerId, tokens)

                // Update auth state
                getOrCreateAuthStateFlow(providerId).value = OAuthState.Authenticated(
                    email = tokens.email ?: encryptedPreferences.getOAuthEmail(providerId) ?: "Unknown",
                    expiresAt = tokens.expiresAt
                )

                Result.success(tokens)
            } catch (e: IOException) {
                Result.failure(OAuthException.NetworkError(e))
            } catch (e: Exception) {
                Result.failure(OAuthException.RefreshFailed(e.message ?: "Unknown error", e))
            }
        }
    }

    /**
     * Gets a valid access token for a provider, refreshing if necessary.
     *
     * @param providerId The provider to get a token for
     * @return Valid access token, or null if not authenticated
     */
    suspend fun getValidAccessToken(providerId: String): String? {
        val accessToken = encryptedPreferences.getAccessToken(providerId) ?: return null
        val expiry = encryptedPreferences.getTokenExpiry(providerId)

        // Check if token needs refresh (expired or expiring within 60 seconds)
        val needsRefresh = expiry != null && System.currentTimeMillis() >= expiry - 60_000L

        return if (needsRefresh) {
            refreshTokens(providerId).getOrNull()?.accessToken ?: accessToken
        } else {
            accessToken
        }
    }

    /**
     * Clears OAuth tokens and signs out a provider.
     *
     * @param providerId The provider to sign out
     */
    suspend fun logout(providerId: String) {
        encryptedPreferences.clearOAuthTokens(providerId)
        getOrCreateAuthStateFlow(providerId).value = OAuthState.Unauthenticated
    }

    /**
     * Observes the authentication state for a provider.
     *
     * @param providerId The provider to observe
     * @return Flow of OAuthState updates
     */
    fun observeAuthState(providerId: String): Flow<OAuthState> {
        return getOrCreateAuthStateFlow(providerId)
    }

    /**
     * Gets the current authentication state for a provider.
     *
     * @param providerId The provider to check
     * @return Current OAuthState
     */
    suspend fun getAuthState(providerId: String): OAuthState {
        // Check if we have valid tokens
        if (encryptedPreferences.hasValidTokens(providerId)) {
            val email = encryptedPreferences.getOAuthEmail(providerId) ?: "Unknown"
            val expiry = encryptedPreferences.getTokenExpiry(providerId) ?: Long.MAX_VALUE
            return OAuthState.Authenticated(email = email, expiresAt = expiry)
        }

        // Check if we have a refresh token (can try to refresh)
        val hasRefreshToken = encryptedPreferences.getRefreshToken(providerId) != null
        return if (hasRefreshToken) {
            // Try to refresh
            val result = refreshTokens(providerId)
            if (result.isSuccess) {
                val tokens = result.getOrThrow()
                OAuthState.Authenticated(
                    email = tokens.email ?: "Unknown",
                    expiresAt = tokens.expiresAt
                )
            } else {
                OAuthState.Unauthenticated
            }
        } else {
            OAuthState.Unauthenticated
        }
    }

    // ============================================================================
    // Private Helper Methods
    // ============================================================================

    private fun getOrCreateAuthStateFlow(providerId: String): MutableStateFlow<OAuthState> {
        return authStates.getOrPut(providerId) {
            MutableStateFlow(OAuthState.Unauthenticated)
        }
    }

    private fun getOAuthConfig(provider: Provider) = when (provider.type) {
        ProviderType.ANTIGRAVITY -> AntigravityConfig.getOAuthConfig()
        else -> throw OAuthException.UnsupportedProvider(provider.name)
    }

    private suspend fun exchangeCodeForTokens(
        code: String,
        codeVerifier: String,
        providerId: String,
        projectId: String?
    ): OAuthTokens {
        // For now, only Antigravity is supported
        val config = AntigravityConfig.getOAuthConfig()

        val formBody = FormBody.Builder()
            .add("client_id", config.clientId)
            .add("client_secret", config.clientSecret ?: "")
            .add("code", code)
            .add("code_verifier", codeVerifier)
            .add("grant_type", "authorization_code")
            .add("redirect_uri", config.redirectUri)
            .build()

        val request = Request.Builder()
            .url(config.tokenUrl)
            .post(formBody)
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw OAuthException.TokenExchangeFailed("HTTP ${response.code}: $errorBody")
        }

        val responseBody = response.body?.string()
            ?: throw OAuthException.TokenExchangeFailed("Empty response from token endpoint")

        return parseTokenResponse(responseBody, providerId, projectId)
    }

    private fun parseTokenResponse(
        responseBody: String,
        providerId: String,
        projectId: String? = null
    ): OAuthTokens {
        val jsonObject = json.parseToJsonElement(responseBody).jsonObject

        val accessToken = jsonObject["access_token"]?.jsonPrimitive?.content
            ?: throw OAuthException.TokenExchangeFailed("Missing access_token in response")

        val refreshToken = jsonObject["refresh_token"]?.jsonPrimitive?.content

        val expiresIn = jsonObject["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
        val expiresAt = System.currentTimeMillis() + (expiresIn * 1000)

        val tokenType = jsonObject["token_type"]?.jsonPrimitive?.content ?: "Bearer"
        val scope = jsonObject["scope"]?.jsonPrimitive?.content

        return OAuthTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = expiresAt,
            tokenType = tokenType,
            scope = scope,
            projectId = projectId
        )
    }

    private suspend fun storeTokens(providerId: String, tokens: OAuthTokens) {
        encryptedPreferences.setAccessToken(providerId, tokens.accessToken)
        tokens.refreshToken?.let { encryptedPreferences.setRefreshToken(providerId, it) }
        encryptedPreferences.setTokenExpiry(providerId, tokens.expiresAt)
        tokens.email?.let { encryptedPreferences.setOAuthEmail(providerId, it) }
        tokens.projectId?.let { encryptedPreferences.setOAuthProjectId(providerId, it) }
    }
}
