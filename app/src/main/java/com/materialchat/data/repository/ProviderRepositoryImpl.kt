package com.materialchat.data.repository

import com.materialchat.data.local.database.dao.ProviderDao
import com.materialchat.data.local.preferences.EncryptedPreferences
import com.materialchat.data.mapper.toDomain
import com.materialchat.data.mapper.toEntity
import com.materialchat.data.mapper.toProviderDomainList
import com.materialchat.domain.model.Provider
import com.materialchat.domain.repository.ProviderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [ProviderRepository] that uses Room for persistence
 * and Tink-encrypted SharedPreferences for API key storage.
 */
@Singleton
class ProviderRepositoryImpl @Inject constructor(
    private val providerDao: ProviderDao,
    private val encryptedPreferences: EncryptedPreferences
) : ProviderRepository {

    // ========== Provider CRUD Operations ==========

    override fun observeProviders(): Flow<List<Provider>> {
        return providerDao.getAllProviders().map { entities ->
            entities.toProviderDomainList()
        }
    }

    override suspend fun getProviders(): List<Provider> {
        return providerDao.getAllProvidersOnce().toProviderDomainList()
    }

    override suspend fun getProvider(providerId: String): Provider? {
        return providerDao.getProviderById(providerId)?.toDomain()
    }

    override fun observeActiveProvider(): Flow<Provider?> {
        return providerDao.getActiveProviderFlow().map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun getActiveProvider(): Provider? {
        return providerDao.getActiveProvider()?.toDomain()
    }

    override suspend fun addProvider(provider: Provider, apiKey: String?): String {
        // Insert the provider entity
        providerDao.insert(provider.toEntity())

        // Store the API key if provided
        if (!apiKey.isNullOrBlank()) {
            encryptedPreferences.setApiKey(provider.id, apiKey)
        }

        return provider.id
    }

    override suspend fun updateProvider(provider: Provider, apiKey: String?) {
        // Update the provider entity
        providerDao.update(provider.toEntity())

        // Handle API key update
        when {
            // New API key provided - store it
            apiKey != null && apiKey.isNotEmpty() -> {
                encryptedPreferences.setApiKey(provider.id, apiKey)
            }
            // Empty string means remove the key
            apiKey == "" -> {
                encryptedPreferences.deleteApiKey(provider.id)
            }
            // null means keep existing key (do nothing)
        }
    }

    override suspend fun deleteProvider(providerId: String) {
        // Delete the provider entity
        providerDao.deleteById(providerId)

        // Delete the associated API key
        encryptedPreferences.deleteApiKey(providerId)
    }

    // ========== Active Provider Operations ==========

    override suspend fun setActiveProvider(providerId: String) {
        // Deactivate all providers first
        providerDao.deactivateAllProviders()

        // Activate the specified provider
        providerDao.activateProvider(providerId)
    }

    // ========== API Key Operations ==========

    override suspend fun getApiKey(providerId: String): String? {
        return encryptedPreferences.getApiKey(providerId)
    }

    override suspend fun hasApiKey(providerId: String): Boolean {
        return encryptedPreferences.hasApiKey(providerId)
    }

    // ========== Initialization ==========

    override suspend fun seedDefaultProviders() {
        // Only seed if no providers exist
        if (providerDao.getProviderCount() > 0) {
            return
        }

        // Create default provider templates
        val openAiProvider = Provider.openAiTemplate()
        val ollamaProvider = Provider.ollamaLocalTemplate()

        // Insert default providers
        providerDao.insertAll(listOf(
            openAiProvider.toEntity(),
            ollamaProvider.toEntity()
        ))
    }

    override suspend fun hasProviders(): Boolean {
        return providerDao.getProviderCount() > 0
    }
}
