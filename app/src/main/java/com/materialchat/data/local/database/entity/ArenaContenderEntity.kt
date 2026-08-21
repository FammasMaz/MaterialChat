package com.materialchat.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "arena_contenders",
    foreignKeys = [
        ForeignKey(
            entity = ArenaBattleEntity::class,
            parentColumns = ["id"],
            childColumns = ["battle_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["battle_id"])]
)
data class ArenaContenderEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "battle_id")
    val battleId: String,
    @ColumnInfo(name = "slot")
    val slot: Int,
    @ColumnInfo(name = "model_name")
    val modelName: String,
    @ColumnInfo(name = "provider_id")
    val providerId: String?,
    @ColumnInfo(name = "response")
    val response: String,
    @ColumnInfo(name = "thinking_content")
    val thinkingContent: String?,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long?
)
