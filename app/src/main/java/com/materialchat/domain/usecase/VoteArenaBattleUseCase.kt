package com.materialchat.domain.usecase

import com.materialchat.domain.repository.ArenaRepository
import com.materialchat.domain.util.ModelIdentity
import javax.inject.Inject
import kotlin.math.pow

/**
 * The outcome of a blind arena vote.
 *
 * @property WIN The slot index of the winning contender (0-based)
 */
sealed interface ArenaVerdict {
    data class Win(val slot: Int) : ArenaVerdict
    data object Tie : ArenaVerdict
    data object BothBad : ArenaVerdict
}

/**
 * Votes on an N-model arena battle and updates every contender's ELO rating.
 *
 * ELO with K-factor 32: the chosen winner beats ALL other contenders pairwise.
 * Ties and both-bad count as draws for everyone. Legacy two-slot battles are
 * upgraded transparently because contenders were backfilled into the same table.
 */
class VoteArenaBattleUseCase @Inject constructor(
    private val arenaRepository: ArenaRepository
) {
    companion object {
        private const val K_FACTOR = 32.0
        private const val ELO_DIVISOR = 400.0
    }

    suspend operator fun invoke(battleId: String, verdict: ArenaVerdict) {
        val battle = arenaRepository.getBattle(battleId)
            ?: throw IllegalStateException("Battle not found: $battleId")
        val contenders = arenaRepository.getContenders(battleId)
        if (contenders.isEmpty()) {
            throw IllegalStateException("Battle has no contenders: $battleId")
        }

        // Record the outcome on the battle row (legacy string format preserved).
        val winnerLabel = when (verdict) {
            is ArenaVerdict.Win -> "SLOT_${verdict.slot}"
            ArenaVerdict.Tie -> "TIE"
            ArenaVerdict.BothBad -> "BOTH_BAD"
        }
        arenaRepository.updateBattle(battle.copy(winner = winnerLabel))

        val now = System.currentTimeMillis()

        when (verdict) {
            is ArenaVerdict.Win -> {
                // Key ratings by canonical model identity so provider variants
                // of the same model (e.g. ox-alpha vs glm-5.3-flash) share one ELO.
                val ratings = contenders.associate { contender ->
                    val key = ModelIdentity.canonicalKey(contender.modelName)
                        ?: contender.modelName
                    key to arenaRepository.getOrCreateRating(contender.modelName)
                }
                val winnerName = contenders.firstOrNull { it.slot == verdict.slot }
                    ?.let { ModelIdentity.canonicalKey(it.modelName) ?: it.modelName }
                    ?: return
                val winnerRating = ratings.getValue(winnerName)

                var updatedWinner = winnerRating.copy(
                    wins = winnerRating.wins + 1,
                    totalBattles = winnerRating.totalBattles + 1,
                    lastBattleAt = now
                )

                for ((name, rating) in ratings) {
                    if (name == winnerName) continue
                    // Pairwise: winner beats this contender.
                    val expectedWin = 1.0 / (1.0 + 10.0.pow(
                        (rating.eloRating - winnerRating.eloRating) / ELO_DIVISOR
                    ))
                    updatedWinner = updatedWinner.copy(
                        eloRating = updatedWinner.eloRating +
                                K_FACTOR * (1.0 - expectedWin)
                    )
                    val expectedLose = 1.0 - expectedWin
                    arenaRepository.updateRating(
                        rating.copy(
                            eloRating = rating.eloRating + K_FACTOR * (0.0 - expectedLose),
                            losses = rating.losses + 1,
                            totalBattles = rating.totalBattles + 1,
                            lastBattleAt = now
                        )
                    )
                }
                arenaRepository.updateRating(updatedWinner)
            }

            ArenaVerdict.Tie, ArenaVerdict.BothBad -> {
                for (contender in contenders) {
                    // Canonical key so provider variants of one model share one rating row.
                    val rating = arenaRepository.getOrCreateRating(
                        ModelIdentity.canonicalKey(contender.modelName) ?: contender.modelName
                    )
                    arenaRepository.updateRating(
                        rating.copy(
                            ties = rating.ties + 1,
                            totalBattles = rating.totalBattles + 1,
                            lastBattleAt = now
                        )
                    )
                }
            }
        }

    }
}
