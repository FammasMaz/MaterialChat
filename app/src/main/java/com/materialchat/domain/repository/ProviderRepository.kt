package com.materialchat.domain.repository

import android.net.Uri
import com.materialchat.domain.model.OAuthState
import com.materialchat.domain.model.OAuthTokens
import com.materialchat.domain.model.Provider
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for provider management, API key storage, and OAuth authentication.
 *
 * This interface bridges the domain layer with:
 * - Room database (provider persistence)
 * - EncryptedPreferences (API key storage)
 * - OAuthManager (OAuth authentication flows)
 */
interface ProviderRepository {

    // ========== Provider CRUD Operations ==========

    /**
     * Observes all providers sorted by name.
     *
     * @return A Flow emitting the list of providers whenever it changes
     */
    fun observeProviders(): Flow<List<Provider>>

    /**
     * Gets all providers.
     *
     * @return The list of all providers
     */
    suspend fun getProviders(): List<Provider>

    /**
     * Gets a provider by its ID.
     *
     * @param providerId The ID of the provider to retrieve
     * @return The provider, or null if not found
     */
    suspend fun getProvider(providerId: String): Provider?

    /**
     * Observes the currently active provider.
     *
     * @return A Flow emitting the active provider, or null if none is active
     */
    fun observeActiveProvider(): Flow<Provider?>

    /**
     * Gets the currently active provider.
     *
     * @return The active provider, or null if none is active
     */
    suspend fun getActiveProvider(): Provider?

    /**
     * Adds a new provider.
     *
     * @param provider The provider to add
     * @param apiKey Optional API key for the provider (will be encrypted)
     * @return The ID of the created provider
     */
    suspend fun addProvider(provider: Provider, apiKey: String? = null): String

    /**
     * Updates an existing provider.
     *
     * @param provider The provider with updated fields
     * @param apiKey Optional new API key (pass null to keep existing, empty string to remove)
     */
    suspend fun updateProvider(provider: Provider, apiKey: String? = null)

    /**
     * Deletes a provider and its associated API key.
     *
     * @param providerId The ID of the provider to delete
     */
    suspend fun deleteProvider(providerId: String)

    // ========== Active Provider Operations ==========

    /**
     * Sets a provider as the active provider.
     * This will deactivate any currently active provider.
     *
     * @param providerId The ID of the provider to activate
     */
    suspend fun setActiveProvider(providerId: String)

    // ========== API Key Operations ==========

    /**
     * Gets the API key for a provider.
     * The key is decrypted before being returned.
     *
     * @param providerId The ID of the provider
     * @return The decrypted API key, or null if not set
     */
    suspend fun getApiKey(providerId: String): String?

    /**
     * Checks if a provider has an API key stored.
     *
     * @param providerId The ID of the provider
     * @return True if an API key is stored, false otherwise
     */
    suspend fun hasApiKey(providerId: String): Boolean

    // ========== Initialization ==========

    /**
     * Seeds default providers if none exist.
     * This should be called on first app launch.
     */
    suspend fun seedDefaultProviders()

    /**
     * Checks if default providers have been seeded.
     *
     * @return True if providers exist, false otherwise
     */
    suspend fun hasProviders(): Boolean

    // ========== OAuth Operations ==========

    /**
     * Data class representing an OAuth authorization request.
     * Contains the authorization URL to open in a browser and the state for validation.
     */
    data class OAuthAuthorizationRequest(
        val url: String,
        val state: String,
        val providerId: String
    )

    /**
     * Builds an OAuth authorization URL for a provider.
     *
     * This creates a PKCE session and returns a URL that should be opened
     * in a Custom Tab or browser for user authentication.
     *
     * @param providerId The ID of the OAuth provider
     * @param projectId Optional project ID for providers like Antigravity
     * @return OAuthAuthorizationRequest containing the URL and state
     * @throws IllegalArgumentException if provider doesn't exist
     * @throws IllegalStateException if provider doesn't support OAuth
     */
    suspend fun buildOAuthAuthorizationUrl(
        providerId: String,
        projectId: String? = null
    ): OAuthAuthorizationRequest

    /**
     * Handles an OAuth callback URI after user authentication.
     *
     * Validates the state, exchanges the authorization code for tokens,
     * and stores them securely.
     *
     * @param uri The callback URI containing code and state parameters
     * @return Result containing OAuthTokens on success, or an exception on failure
     */
    suspend fun handleOAuthCallback(uri: Uri): Result<OAuthTokens>

    /**
     * Gets the current OAuth authentication state for a provider.
     *
     * @param providerId The ID of the provider
     * @return Current OAuthState (Unauthenticated, Authenticating, Authenticated, or Error)
     */
    suspend fun getOAuthState(providerId: String): OAuthState

    /**
     * Observes the OAuth authentication state for a provider.
     *
     * @param providerId The ID of the provider
     * @return Flow of OAuthState updates
     */
    fun observeOAuthState(providerId: String): Flow<OAuthState>

    /**
     * Gets a valid OAuth access token for a provider, refreshing if necessary.
     *
     * @param providerId The ID of the provider
     * @return Valid access token, or null if not authenticated
     */
    suspend fun getOAuthAccessToken(providerId: String): String?

    /**
     * Checks if a provider has valid OAuth tokens.
     *
     * @param providerId The ID of the provider
     * @return True if OAuth tokens exist and are valid
     */
    suspend fun hasValidOAuthTokens(providerId: String): Boolean

    /**
     * Signs out of OAuth for a provider.
     *
     * Clears all OAuth tokens and resets the authentication state.
     *
     * @param providerId The ID of the provider
     */
    suspend fun logoutOAuth(providerId: String)

    /**
     * Gets the email associated with an OAuth provider's authentication.
     *
     * @param providerId The ID of the provider
     * @return The authenticated user's email, or null if not authenticated
     */
    suspend fun getOAuthEmail(providerId: String): String?

    /**
     * Gets the project ID associated with an OAuth provider (e.g., for Antigravity).
     *
     * @param providerId The ID of the provider
     * @return The project ID, or null if not set
     */
    suspend fun getOAuthProjectId(providerId: String): String?
}
