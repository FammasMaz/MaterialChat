package com.materialchat.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.materialchat.data.local.database.entity.ProviderEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for provider operations.
 *
 * Provides CRUD operations for AI provider configurations stored in the database.
 * All query methods return Flow for reactive UI updates.
 */
@Dao
interface ProviderDao {

    /**
     * Insert a new provider. Replaces on conflict.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(provider: ProviderEntity)

    /**
     * Insert multiple providers. Replaces on conflict.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(providers: List<ProviderEntity>)

    /**
     * Update an existing provider.
     */
    @Update
    suspend fun update(provider: ProviderEntity)

    /**
     * Delete a provider.
     */
    @Delete
    suspend fun delete(provider: ProviderEntity)

    /**
     * Delete a provider by ID.
     */
    @Query("DELETE FROM providers WHERE id = :providerId")
    suspend fun deleteById(providerId: String)

    /**
     * Get all providers as a Flow, ordered by name.
     */
    @Query("SELECT * FROM providers ORDER BY name ASC")
    fun getAllProviders(): Flow<List<ProviderEntity>>

    /**
     * Get all providers as a one-shot list.
     */
    @Query("SELECT * FROM providers ORDER BY name ASC")
    suspend fun getAllProvidersOnce(): List<ProviderEntity>

    /**
     * Get a provider by ID.
     */
    @Query("SELECT * FROM providers WHERE id = :providerId")
    suspend fun getProviderById(providerId: String): ProviderEntity?

    /**
     * Get a provider by ID as a Flow for reactive updates.
     */
    @Query("SELECT * FROM providers WHERE id = :providerId")
    fun getProviderByIdFlow(providerId: String): Flow<ProviderEntity?>

    /**
     * Get the currently active provider.
     */
    @Query("SELECT * FROM providers WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveProvider(): ProviderEntity?

    /**
     * Get the currently active provider as a Flow.
     */
    @Query("SELECT * FROM providers WHERE is_active = 1 LIMIT 1")
    fun getActiveProviderFlow(): Flow<ProviderEntity?>

    /**
     * Set a provider as active and deactivate all others.
     * This is done in two queries for simplicity.
     */
    @Query("UPDATE providers SET is_active = 0")
    suspend fun deactivateAllProviders()

    /**
     * Activate a specific provider by ID.
     */
    @Query("UPDATE providers SET is_active = 1 WHERE id = :providerId")
    suspend fun activateProvider(providerId: String)

    /**
     * Get the count of providers.
     */
    @Query("SELECT COUNT(*) FROM providers")
    suspend fun getProviderCount(): Int

    /**
     * Check if a provider exists by ID.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM providers WHERE id = :providerId)")
    suspend fun providerExists(providerId: String): Boolean

    /**
     * Get providers by authentication type.
     *
     * @param authType The authentication type to filter by (NONE, API_KEY, OAUTH)
     * @return Flow of providers with the specified auth type
     */
    @Query("SELECT * FROM providers WHERE auth_type = :authType ORDER BY name ASC")
    fun getProvidersByAuthType(authType: String): Flow<List<ProviderEntity>>

    /**
     * Get all OAuth providers.
     *
     * @return Flow of providers that use OAuth authentication
     */
    @Query("SELECT * FROM providers WHERE auth_type = 'OAUTH' ORDER BY name ASC")
    fun getOAuthProviders(): Flow<List<ProviderEntity>>

    /**
     * Get providers that require API keys.
     *
     * @return Flow of providers that use API key authentication
     */
    @Query("SELECT * FROM providers WHERE auth_type = 'API_KEY' ORDER BY name ASC")
    fun getApiKeyProviders(): Flow<List<ProviderEntity>>
}
