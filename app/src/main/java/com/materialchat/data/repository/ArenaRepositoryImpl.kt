package com.materialchat.data.repository

import com.materialchat.data.local.database.dao.ArenaDao
import com.materialchat.data.local.database.entity.ArenaContenderEntity
import com.materialchat.data.mapper.toBattleDomainList
import com.materialchat.data.mapper.toDomain
import com.materialchat.data.mapper.toResult
import com.materialchat.data.mapper.toEntity
import com.materialchat.data.mapper.toRatingDomainList
import com.materialchat.domain.model.ArenaBattle
import com.materialchat.domain.model.ModelRating
import com.materialchat.domain.repository.ArenaRepository
import com.materialchat.domain.model.ContenderResult
import com.materialchat.domain.util.ModelIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
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
        return arenaDao.getAllBattles().map { it.toBattleDomainList() }.flowOn(Dispatchers.IO)
    }

    override fun getCompletedBattles(): Flow<List<ArenaBattle>> {
        return arenaDao.getCompletedBattles().map { it.toBattleDomainList() }.flowOn(Dispatchers.IO)
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
        return arenaDao.getAllRatings().map { mergeRatings(it.toRatingDomainList()) }.flowOn(Dispatchers.IO)
    }

    override suspend fun getAllRatingsOnce(): List<ModelRating> {
        return mergeRatings(arenaDao.getAllRatingsOnce().toRatingDomainList())
    }

    /**
     * Aggregates per-provider rating rows under canonical model identities so
     * the same model served by different providers appears ONCE on the
     * leaderboard. Stats are summed; ELO is averaged; identity shown is the
     * variant with the most battles.
     */
    private fun mergeRatings(ratings: List<ModelRating>): List<ModelRating> {
        data class Acc(
            val key: String,
            var eloSum: Double = 0.0,
            var wins: Int = 0,
            var losses: Int = 0,
            var ties: Int = 0,
            var totalBattles: Int = 0,
            var lastBattleAt: Long? = null,
            var bestName: String = "",
            var bestBattles: Int = -1
        )
        val merged = linkedMapOf<String, Acc>()
        for (rating in ratings) {
            val key = ModelIdentity.canonicalKey(rating.modelName) ?: continue
            val acc = merged.getOrPut(key) { Acc(key) }
            acc.eloSum += rating.eloRating
            acc.wins += rating.wins
            acc.losses += rating.losses
            acc.ties += rating.ties
            acc.totalBattles += rating.totalBattles
            if (rating.lastBattleAt != null) {
                val ratingLast = rating.lastBattleAt
                val accLast = acc.lastBattleAt
                if (accLast == null || ratingLast > accLast) {
                    acc.lastBattleAt = ratingLast
                }
            }
            if (rating.totalBattles > acc.bestBattles) {
                acc.bestName = rating.modelName
                acc.bestBattles = rating.totalBattles
            }
        }
        return merged.values
            .map { acc ->
                ModelRating(
                    modelName = acc.bestName.ifBlank { acc.key },
                    eloRating = acc.eloSum / maxOf(acc.totalBattles, 1),
                    wins = acc.wins,
                    losses = acc.losses,
                    ties = acc.ties,
                    totalBattles = acc.totalBattles,
                    lastBattleAt = acc.lastBattleAt
                )
            }
            .sortedByDescending { it.eloRating }
    }

    override suspend fun getRating(modelName: String): ModelRating? {
        return arenaDao.getRatingByModel(modelName)?.toDomain()
    }

    override suspend fun replaceContenders(battleId: String, contenders: List<ContenderResult>) {
        arenaDao.insertContenders(
            contenders.map { result ->
                ArenaContenderEntity(
                    id = "$battleId-slot-${result.slot}",
                    battleId = battleId,
                    slot = result.slot,
                    modelName = result.modelName,
                    providerId = result.providerId,
                    response = result.response,
                    thinkingContent = result.thinkingContent,
                    durationMs = result.durationMs
                )
            }
        )
    }

    override suspend fun updateContenderResult(
        battleId: String,
        slot: Int,
        response: String,
        thinkingContent: String?,
        durationMs: Long?
    ) {
        arenaDao.updateContenderResult("$battleId-slot-$slot", response, thinkingContent, durationMs)
    }

    override suspend fun getContenders(battleId: String): List<ContenderResult> {
        return arenaDao.getContenders(battleId).map { it.toResult() }
    }
}
