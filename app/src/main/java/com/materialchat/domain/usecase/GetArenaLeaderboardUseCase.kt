package com.materialchat.domain.usecase

import com.materialchat.domain.model.ModelRating
import com.materialchat.domain.repository.ArenaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for fetching the arena leaderboard with ranked model ratings.
 *
 * Ratings are ordered by ELO rating descending (highest first).
 */
class GetArenaLeaderboardUseCase @Inject constructor(
    private val arenaRepository: ArenaRepository
) {
    /**
     * Observes all model ratings reactively.
     *
     * @return A Flow emitting the ranked list of model ratings
     */
    fun observeRatings(): Flow<List<ModelRating>> {
        return arenaRepository.getAllRatings()
    }

    /**
     * Gets all model ratings once (non-reactive).
     *
     * @return The ranked list of model ratings
     */
    suspend fun getRatingsOnce(): List<ModelRating> {
        return arenaRepository.getAllRatingsOnce()
    }
}
