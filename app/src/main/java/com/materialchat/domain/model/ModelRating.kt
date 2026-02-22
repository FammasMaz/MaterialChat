package com.materialchat.domain.model

/**
 * Domain model representing an AI model's ELO rating in the arena.
 *
 * @property modelName The unique model identifier (used as primary key)
 * @property eloRating The computed ELO rating (starts at 1500.0)
 * @property wins Number of battles won
 * @property losses Number of battles lost
 * @property ties Number of tied battles (including BOTH_BAD votes)
 * @property totalBattles Total number of battles participated in
 * @property lastBattleAt Timestamp of the last battle (epoch milliseconds)
 */
data class ModelRating(
    val modelName: String,
    val eloRating: Double = 1500.0,
    val wins: Int = 0,
    val losses: Int = 0,
    val ties: Int = 0,
    val totalBattles: Int = 0,
    val lastBattleAt: Long? = null
) {
    /**
     * Win rate as a percentage (0-100).
     */
    val winRate: Double
        get() = if (totalBattles > 0) (wins.toDouble() / totalBattles) * 100.0 else 0.0
}
