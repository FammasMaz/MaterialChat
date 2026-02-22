package com.materialchat.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an arena battle between two AI models.
 *
 * Stores the prompt, both model responses, voting result, and timing data.
 */
@Entity(tableName = "arena_battles")
data class ArenaBattleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "prompt")
    val prompt: String,

    @ColumnInfo(name = "left_model_name")
    val leftModelName: String,

    @ColumnInfo(name = "left_provider_id")
    val leftProviderId: String? = null,

    @ColumnInfo(name = "left_response")
    val leftResponse: String,

    @ColumnInfo(name = "right_model_name")
    val rightModelName: String,

    @ColumnInfo(name = "right_provider_id")
    val rightProviderId: String? = null,

    @ColumnInfo(name = "right_response")
    val rightResponse: String,

    @ColumnInfo(name = "winner")
    val winner: String? = null,

    @ColumnInfo(name = "left_thinking_content")
    val leftThinkingContent: String? = null,

    @ColumnInfo(name = "right_thinking_content")
    val rightThinkingContent: String? = null,

    @ColumnInfo(name = "left_duration_ms")
    val leftDurationMs: Long? = null,

    @ColumnInfo(name = "right_duration_ms")
    val rightDurationMs: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
