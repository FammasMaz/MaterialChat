package com.materialchat.data.mapper

import com.materialchat.data.local.database.entity.ArenaBattleEntity
import com.materialchat.data.local.database.entity.ModelRatingEntity
import com.materialchat.domain.model.ArenaBattle
import com.materialchat.domain.model.ModelRating

// ============================================================================
// ArenaBattle Mappers
// ============================================================================

/**
 * Converts an [ArenaBattleEntity] from the database to an [ArenaBattle] domain model.
 */
fun ArenaBattleEntity.toDomain(): ArenaBattle = ArenaBattle(
    id = id,
    prompt = prompt,
    leftModelName = leftModelName,
    leftProviderId = leftProviderId,
    leftResponse = leftResponse,
    rightModelName = rightModelName,
    rightProviderId = rightProviderId,
    rightResponse = rightResponse,
    winner = winner,
    leftThinkingContent = leftThinkingContent,
    rightThinkingContent = rightThinkingContent,
    leftDurationMs = leftDurationMs,
    rightDurationMs = rightDurationMs,
    createdAt = createdAt
)

/**
 * Converts an [ArenaBattle] domain model to an [ArenaBattleEntity] for Room database storage.
 */
fun ArenaBattle.toEntity(): ArenaBattleEntity = ArenaBattleEntity(
    id = id,
    prompt = prompt,
    leftModelName = leftModelName,
    leftProviderId = leftProviderId,
    leftResponse = leftResponse,
    rightModelName = rightModelName,
    rightProviderId = rightProviderId,
    rightResponse = rightResponse,
    winner = winner,
    leftThinkingContent = leftThinkingContent,
    rightThinkingContent = rightThinkingContent,
    leftDurationMs = leftDurationMs,
    rightDurationMs = rightDurationMs,
    createdAt = createdAt
)

/**
 * Converts a list of [ArenaBattleEntity] to a list of [ArenaBattle] domain models.
 */
fun List<ArenaBattleEntity>.toBattleDomainList(): List<ArenaBattle> = map { it.toDomain() }

// ============================================================================
// ModelRating Mappers
// ============================================================================

/**
 * Converts a [ModelRatingEntity] from the database to a [ModelRating] domain model.
 */
fun ModelRatingEntity.toDomain(): ModelRating = ModelRating(
    modelName = modelName,
    eloRating = eloRating,
    wins = wins,
    losses = losses,
    ties = ties,
    totalBattles = totalBattles,
    lastBattleAt = lastBattleAt
)

/**
 * Converts a [ModelRating] domain model to a [ModelRatingEntity] for Room database storage.
 */
fun ModelRating.toEntity(): ModelRatingEntity = ModelRatingEntity(
    modelName = modelName,
    eloRating = eloRating,
    wins = wins,
    losses = losses,
    ties = ties,
    totalBattles = totalBattles,
    lastBattleAt = lastBattleAt
)

/**
 * Converts a list of [ModelRatingEntity] to a list of [ModelRating] domain models.
 */
fun List<ModelRatingEntity>.toRatingDomainList(): List<ModelRating> = map { it.toDomain() }
