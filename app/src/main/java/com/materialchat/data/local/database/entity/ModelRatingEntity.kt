package com.materialchat.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an AI model's ELO rating in the arena.
 *
 * Tracks win/loss/tie statistics and the computed ELO rating.
 */
@Entity(tableName = "model_ratings")
data class ModelRatingEntity(
    @PrimaryKey
    @ColumnInfo(name = "model_name")
    val modelName: String,

    @ColumnInfo(name = "elo_rating")
    val eloRating: Double = 1500.0,

    @ColumnInfo(name = "wins")
    val wins: Int = 0,

    @ColumnInfo(name = "losses")
    val losses: Int = 0,

    @ColumnInfo(name = "ties")
    val ties: Int = 0,

    @ColumnInfo(name = "total_battles")
    val totalBattles: Int = 0,

    @ColumnInfo(name = "last_battle_at")
    val lastBattleAt: Long? = null
)
