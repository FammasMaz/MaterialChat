package com.materialchat.domain.usecase

import com.materialchat.domain.model.ArenaVote
import com.materialchat.domain.repository.ArenaRepository
import javax.inject.Inject
import kotlin.math.pow

/**
 * Use case for voting on an arena battle and updating ELO ratings.
 *
 * Implements the ELO rating system with K-factor of 32:
 * - Expected score: E_A = 1 / (1 + 10^((R_B - R_A) / 400))
 * - New rating: R'_A = R_A + K * (S_A - E_A)
 *
 * Vote scores:
 * - LEFT wins: left=1.0, right=0.0
 * - RIGHT wins: left=0.0, right=1.0
 * - TIE: left=0.5, right=0.5
 * - BOTH_BAD: left=0.5, right=0.5 (treated as tie for ELO)
 */
class VoteArenaBattleUseCase @Inject constructor(
    private val arenaRepository: ArenaRepository
) {
    companion object {
        private const val K_FACTOR = 32.0
        private const val ELO_DIVISOR = 400.0
    }

    /**
     * Records a vote for a battle and updates both models' ELO ratings.
     *
     * @param battleId The ID of the battle to vote on
     * @param vote The voting outcome
     * @throws IllegalStateException if the battle is not found
     */
    suspend operator fun invoke(battleId: String, vote: ArenaVote) {
        val battle = arenaRepository.getBattle(battleId)
            ?: throw IllegalStateException("Battle not found: $battleId")

        // Update battle with the winner
        val updatedBattle = battle.copy(winner = vote.name)
        arenaRepository.updateBattle(updatedBattle)

        // Get or create ratings for both models
        val leftRating = arenaRepository.getOrCreateRating(battle.leftModelName)
        val rightRating = arenaRepository.getOrCreateRating(battle.rightModelName)

        // Calculate expected scores
        val expectedLeft = 1.0 / (1.0 + 10.0.pow(
            (rightRating.eloRating - leftRating.eloRating) / ELO_DIVISOR
        ))
        val expectedRight = 1.0 - expectedLeft

        // Determine actual scores based on vote
        val (leftScore, rightScore) = when (vote) {
            ArenaVote.LEFT -> 1.0 to 0.0
            ArenaVote.RIGHT -> 0.0 to 1.0
            ArenaVote.TIE -> 0.5 to 0.5
            ArenaVote.BOTH_BAD -> 0.5 to 0.5
        }

        // Calculate new ELO ratings
        val newLeftElo = leftRating.eloRating + K_FACTOR * (leftScore - expectedLeft)
        val newRightElo = rightRating.eloRating + K_FACTOR * (rightScore - expectedRight)

        val now = System.currentTimeMillis()

        // Update left model rating
        val updatedLeftRating = leftRating.copy(
            eloRating = newLeftElo,
            wins = leftRating.wins + if (vote == ArenaVote.LEFT) 1 else 0,
            losses = leftRating.losses + if (vote == ArenaVote.RIGHT) 1 else 0,
            ties = leftRating.ties + if (vote == ArenaVote.TIE || vote == ArenaVote.BOTH_BAD) 1 else 0,
            totalBattles = leftRating.totalBattles + 1,
            lastBattleAt = now
        )

        // Update right model rating
        val updatedRightRating = rightRating.copy(
            eloRating = newRightElo,
            wins = rightRating.wins + if (vote == ArenaVote.RIGHT) 1 else 0,
            losses = rightRating.losses + if (vote == ArenaVote.LEFT) 1 else 0,
            ties = rightRating.ties + if (vote == ArenaVote.TIE || vote == ArenaVote.BOTH_BAD) 1 else 0,
            totalBattles = rightRating.totalBattles + 1,
            lastBattleAt = now
        )

        arenaRepository.updateRating(updatedLeftRating)
        arenaRepository.updateRating(updatedRightRating)
    }
}
