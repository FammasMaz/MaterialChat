package com.materialchat.domain.usecase

import com.materialchat.domain.model.AiModel
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ProviderType
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ProviderRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for managing AI providers.
 *
 * This use case handles:
 * - CRUD operations for providers
 * - Setting the active provider
 * - Fetching available models from a provider
 * - Testing provider connections
 */
class ManageProvidersUseCase @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val chatRepository: ChatRepository
) {
    /**
     * Observes all providers.
     *
     * @return A Flow emitting the list of providers whenever it changes
     */
    fun observeProviders(): Flow<List<Provider>> {
        return providerRepository.observeProviders()
    }

    /**
     * Observes the active provider.
     *
     * @return A Flow emitting the active provider, or null if none is active
     */
    fun observeActiveProvider(): Flow<Provider?> {
        return providerRepository.observeActiveProvider()
    }

    /**
     * Gets all providers.
     *
     * @return The list of all providers
     */
    suspend fun getProviders(): List<Provider> {
        return providerRepository.getProviders()
    }

    /**
     * Gets the active provider.
     *
     * @return The active provider, or null if none is active
     */
    suspend fun getActiveProvider(): Provider? {
        return providerRepository.getActiveProvider()
    }

    /**
     * Gets a provider by its ID.
     *
     * @param providerId The ID of the provider
     * @return The provider, or null if not found
     */
    suspend fun getProvider(providerId: String): Provider? {
        return providerRepository.getProvider(providerId)
    }

    /**
     * Adds a new provider.
     *
     * @param name Display name for the provider
     * @param type The type of provider (OpenAI-compatible or Ollama)
     * @param baseUrl The base URL for API requests
     * @param defaultModel The default model to use
     * @param apiKey Optional API key (required for OpenAI-compatible providers)
     * @param setAsActive Whether to set this provider as the active provider
     * @return The ID of the created provider
     */
    suspend fun addProvider(
        name: String,
        type: ProviderType,
        baseUrl: String,
        defaultModel: String,
        apiKey: String? = null,
        setAsActive: Boolean = false
    ): String {
        val provider = Provider(
            name = name.trim(),
            type = type,
            baseUrl = baseUrl.trimEnd('/'),
            defaultModel = defaultModel.trim(),
            requiresApiKey = type == ProviderType.OPENAI_COMPATIBLE,
            isActive = setAsActive
        )

        val providerId = providerRepository.addProvider(provider, apiKey)

        if (setAsActive) {
            providerRepository.setActiveProvider(providerId)
        }

        return providerId
    }

    /**
     * Updates an existing provider.
     *
     * @param providerId The ID of the provider to update
     * @param name New display name
     * @param baseUrl New base URL
     * @param defaultModel New default model
     * @param apiKey New API key (null to keep existing, empty string to remove)
     */
    suspend fun updateProvider(
        providerId: String,
        name: String,
        baseUrl: String,
        defaultModel: String,
        apiKey: String? = null
    ) {
        val existingProvider = providerRepository.getProvider(providerId)
            ?: throw IllegalStateException("Provider not found: $providerId")

        val updatedProvider = existingProvider.copy(
            name = name.trim(),
            baseUrl = baseUrl.trimEnd('/'),
            defaultModel = defaultModel.trim()
        )

        providerRepository.updateProvider(updatedProvider, apiKey)
    }

    /**
     * Deletes a provider.
     *
     * @param providerId The ID of the provider to delete
     */
    suspend fun deleteProvider(providerId: String) {
        providerRepository.deleteProvider(providerId)
    }

    /**
     * Sets a provider as the active provider.
     *
     * @param providerId The ID of the provider to activate
     */
    suspend fun setActiveProvider(providerId: String) {
        providerRepository.setActiveProvider(providerId)
    }

    /**
     * Fetches available models from a provider.
     *
     * @param providerId The ID of the provider
     * @return A Result containing the list of models or an error
     */
    suspend fun fetchModels(providerId: String): Result<List<AiModel>> {
        val provider = providerRepository.getProvider(providerId)
            ?: return Result.failure(IllegalStateException("Provider not found: $providerId"))

        return chatRepository.fetchModels(provider)
    }

    /**
     * Tests the connection to a provider.
     *
     * @param providerId The ID of the provider to test
     * @return A Result indicating success or failure
     */
    suspend fun testConnection(providerId: String): Result<Boolean> {
        val provider = providerRepository.getProvider(providerId)
            ?: return Result.failure(IllegalStateException("Provider not found: $providerId"))

        return chatRepository.testConnection(provider)
    }

    /**
     * Tests the connection to a provider configuration without saving it.
     * Useful for validating settings before creating a provider.
     *
     * @param type The provider type
     * @param baseUrl The base URL to test
     * @param apiKey Optional API key
     * @return A Result indicating success or failure
     */
    suspend fun testNewConnection(
        type: ProviderType,
        baseUrl: String,
        apiKey: String? = null
    ): Result<Boolean> {
        val tempProvider = Provider(
            id = "temp-test-provider",
            name = "Test Provider",
            type = type,
            baseUrl = baseUrl.trimEnd('/'),
            defaultModel = "",
            requiresApiKey = type == ProviderType.OPENAI_COMPATIBLE
        )

        return chatRepository.testConnection(tempProvider)
    }

    /**
     * Checks if a provider has an API key configured.
     *
     * @param providerId The ID of the provider
     * @return True if an API key is stored
     */
    suspend fun hasApiKey(providerId: String): Boolean {
        return providerRepository.hasApiKey(providerId)
    }

    /**
     * Seeds default providers if none exist.
     * Should be called on first app launch.
     */
    suspend fun seedDefaultProvidersIfNeeded() {
        if (!providerRepository.hasProviders()) {
            providerRepository.seedDefaultProviders()
        }
    }
}
