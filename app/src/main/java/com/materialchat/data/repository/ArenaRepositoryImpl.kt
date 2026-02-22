package com.materialchat.data.repository

import com.materialchat.data.local.database.dao.ArenaDao
import com.materialchat.data.mapper.toBattleDomainList
import com.materialchat.data.mapper.toDomain
import com.materialchat.data.mapper.toEntity
import com.materialchat.data.mapper.toRatingDomainList
import com.materialchat.domain.model.ArenaBattle
import com.materialchat.domain.model.ModelRating
import com.materialchat.domain.repository.ArenaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementation of [ArenaRepository] backed by Room database via [ArenaDao].
 */
class ArenaRepositoryImpl @Inject constructor(
    private val arenaDao: ArenaDao
) : ArenaRepository {

    override suspend fun insertBattle(battle: ArenaBattle) {
        arenaDao.insertBattle(battle.toEntity())
    }

    override suspend fun updateBattle(battle: ArenaBattle) {
        arenaDao.updateBattle(battle.toEntity())
    }

    override suspend fun getBattle(battleId: String): ArenaBattle? {
        return arenaDao.getBattleById(battleId)?.toDomain()
    }

    override fun getAllBattles(): Flow<List<ArenaBattle>> {
        return arenaDao.getAllBattles().map { it.toBattleDomainList() }
    }

    override fun getCompletedBattles(): Flow<List<ArenaBattle>> {
        return arenaDao.getCompletedBattles().map { it.toBattleDomainList() }
    }

    override suspend fun deleteBattle(battleId: String) {
        arenaDao.deleteBattleById(battleId)
    }

    override suspend fun getOrCreateRating(modelName: String): ModelRating {
        return arenaDao.getRatingByModel(modelName)?.toDomain()
            ?: ModelRating(modelName = modelName)
    }

    override suspend fun updateRating(rating: ModelRating) {
        arenaDao.insertOrUpdateRating(rating.toEntity())
    }

    override fun getAllRatings(): Flow<List<ModelRating>> {
        return arenaDao.getAllRatings().map { it.toRatingDomainList() }
    }

    override suspend fun getAllRatingsOnce(): List<ModelRating> {
        return arenaDao.getAllRatingsOnce().toRatingDomainList()
    }

    override suspend fun getRating(modelName: String): ModelRating? {
        return arenaDao.getRatingByModel(modelName)?.toDomain()
    }
}
