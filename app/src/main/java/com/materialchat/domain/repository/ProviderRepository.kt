package com.materialchat.domain.repository

import com.materialchat.domain.model.Provider
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for provider management and API key storage.
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
}
