package com.materialchat.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.materialchat.data.local.database.entity.ArenaBattleEntity
import com.materialchat.data.local.database.entity.ArenaContenderEntity
import com.materialchat.data.local.database.entity.ModelRatingEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for arena battle and model rating operations.
 *
 * Provides CRUD operations for arena battles and ELO rating management.
 */
@Dao
interface ArenaDao {

    // ========== Battle Operations ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBattle(battle: ArenaBattleEntity)

    @Update
    suspend fun updateBattle(battle: ArenaBattleEntity)

    @Delete
    suspend fun deleteBattle(battle: ArenaBattleEntity)

    @Query("DELETE FROM arena_battles WHERE id = :battleId")
    suspend fun deleteBattleById(battleId: String)

    @Query("SELECT * FROM arena_battles ORDER BY created_at DESC")
    fun getAllBattles(): Flow<List<ArenaBattleEntity>>

    @Query("SELECT * FROM arena_battles WHERE id = :battleId")
    suspend fun getBattleById(battleId: String): ArenaBattleEntity?

    @Query("SELECT * FROM arena_battles WHERE winner IS NOT NULL ORDER BY created_at DESC")
    fun getCompletedBattles(): Flow<List<ArenaBattleEntity>>

    @Query("SELECT COUNT(*) FROM arena_battles")
    suspend fun getBattleCount(): Int


    // ========== Contender Operations ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContenders(contenders: List<ArenaContenderEntity>)

    @Query("SELECT * FROM arena_contenders WHERE battle_id = :battleId ORDER BY slot ASC")
    suspend fun getContenders(battleId: String): List<ArenaContenderEntity>

    @Query("SELECT COUNT(*) FROM arena_contenders WHERE battle_id = :battleId")
    suspend fun getContenderCount(battleId: String): Int

    @Query("UPDATE arena_contenders SET response = :response, thinking_content = :thinkingContent, duration_ms = :durationMs WHERE id = :contenderId")
    suspend fun updateContenderResult(contenderId: String, response: String, thinkingContent: String?, durationMs: Long?)

    @Query("""
        UPDATE arena_battles SET winner = 'SLOT_' || :slot WHERE id = :battleId
    """)
    suspend fun setWinnerSlot(battleId: String, slot: Int)

    // ========== Rating Operations ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRating(rating: ModelRatingEntity)

    @Query("SELECT * FROM model_ratings ORDER BY elo_rating DESC")
    fun getAllRatings(): Flow<List<ModelRatingEntity>>

    @Query("SELECT * FROM model_ratings ORDER BY elo_rating DESC")
    suspend fun getAllRatingsOnce(): List<ModelRatingEntity>

    @Query("SELECT * FROM model_ratings WHERE model_name = :modelName")
    suspend fun getRatingByModel(modelName: String): ModelRatingEntity?
}
