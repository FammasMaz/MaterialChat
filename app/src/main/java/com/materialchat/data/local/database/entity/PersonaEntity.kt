package com.materialchat.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a custom AI persona.
 *
 * Stores persona configuration including system prompt, personality traits,
 * expertise tags, tone, and conversation starters.
 */
@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "emoji", defaultValue = "'🤖'")
    val emoji: String = "🤖",

    @ColumnInfo(name = "description", defaultValue = "''")
    val description: String = "",

    @ColumnInfo(name = "system_prompt")
    val systemPrompt: String,

    @ColumnInfo(name = "expertise_tags")
    val expertiseTags: String? = null,

    @ColumnInfo(name = "tone", defaultValue = "'balanced'")
    val tone: String = "balanced",

    @ColumnInfo(name = "conversation_starters")
    val conversationStarters: String? = null,

    @ColumnInfo(name = "color_seed")
    val colorSeed: Int? = null,

    @ColumnInfo(name = "is_builtin", defaultValue = "0")
    val isBuiltin: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
