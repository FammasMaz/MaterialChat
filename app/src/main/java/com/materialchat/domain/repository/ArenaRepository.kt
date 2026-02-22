package com.materialchat.domain.repository

import com.materialchat.domain.model.ArenaBattle
import com.materialchat.domain.model.ModelRating
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for arena battle and model rating operations.
 */
interface ArenaRepository {

    // ========== Battle Operations ==========

    /**
     * Inserts a new battle record.
     *
     * @param battle The battle to insert
     */
    suspend fun insertBattle(battle: ArenaBattle)

    /**
     * Updates an existing battle record (e.g., to set the winner).
     *
     * @param battle The battle with updated fields
     */
    suspend fun updateBattle(battle: ArenaBattle)

    /**
     * Gets a battle by its ID.
     *
     * @param battleId The ID of the battle
     * @return The battle, or null if not found
     */
    suspend fun getBattle(battleId: String): ArenaBattle?

    /**
     * Observes all battles ordered by creation date descending.
     *
     * @return A Flow emitting the list of all battles
     */
    fun getAllBattles(): Flow<List<ArenaBattle>>

    /**
     * Observes only completed (voted) battles.
     *
     * @return A Flow emitting the list of completed battles
     */
    fun getCompletedBattles(): Flow<List<ArenaBattle>>

    /**
     * Deletes a battle by its ID.
     *
     * @param battleId The ID of the battle to delete
     */
    suspend fun deleteBattle(battleId: String)

    // ========== Rating Operations ==========

    /**
     * Gets the rating for a model, creating a default one if it does not exist.
     *
     * @param modelName The model name
     * @return The existing or newly created rating
     */
    suspend fun getOrCreateRating(modelName: String): ModelRating

    /**
     * Updates (or inserts) a model rating.
     *
     * @param rating The rating to upsert
     */
    suspend fun updateRating(rating: ModelRating)

    /**
     * Observes all model ratings ordered by ELO descending.
     *
     * @return A Flow emitting the ranked list of ratings
     */
    fun getAllRatings(): Flow<List<ModelRating>>

    /**
     * Gets all model ratings once (non-reactive).
     *
     * @return The ranked list of ratings
     */
    suspend fun getAllRatingsOnce(): List<ModelRating>

    /**
     * Gets the rating for a specific model.
     *
     * @param modelName The model name
     * @return The rating, or null if not found
     */
    suspend fun getRating(modelName: String): ModelRating?
}
