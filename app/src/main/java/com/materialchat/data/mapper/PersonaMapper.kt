package com.materialchat.data.mapper

import com.materialchat.data.local.database.entity.PersonaEntity
import com.materialchat.domain.model.Persona
import com.materialchat.domain.model.PersonaTone
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

// ============================================================================
// Persona Mappers
// ============================================================================

/**
 * Converts a [Persona] domain model to a [PersonaEntity] for Room database storage.
 *
 * List fields ([Persona.expertiseTags], [Persona.conversationStarters]) are
 * serialised to JSON strings via kotlinx.serialization.
 */
fun Persona.toEntity(): PersonaEntity = PersonaEntity(
    id = id,
    name = name,
    emoji = emoji,
    description = description,
    systemPrompt = systemPrompt,
    expertiseTags = if (expertiseTags.isNotEmpty()) json.encodeToString(expertiseTags) else null,
    tone = tone.name.lowercase(),
    conversationStarters = if (conversationStarters.isNotEmpty()) json.encodeToString(conversationStarters) else null,
    colorSeed = colorSeed,
    isBuiltin = if (isBuiltin) 1 else 0,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/**
 * Converts a [PersonaEntity] from the database to a [Persona] domain model.
 *
 * JSON-encoded list fields are deserialised back to Kotlin lists.
 */
fun PersonaEntity.toDomain(): Persona = Persona(
    id = id,
    name = name,
    emoji = emoji,
    description = description,
    systemPrompt = systemPrompt,
    expertiseTags = expertiseTags?.let {
        try { json.decodeFromString<List<String>>(it) } catch (_: Exception) { emptyList() }
    } ?: emptyList(),
    tone = PersonaTone.fromString(tone),
    conversationStarters = conversationStarters?.let {
        try { json.decodeFromString<List<String>>(it) } catch (_: Exception) { emptyList() }
    } ?: emptyList(),
    colorSeed = colorSeed,
    isBuiltin = isBuiltin == 1,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/**
 * Converts a list of [PersonaEntity] to a list of [Persona] domain models.
 */
fun List<PersonaEntity>.toPersonaDomainList(): List<Persona> = map { it.toDomain() }

/**
 * Converts a list of [Persona] domain models to a list of [PersonaEntity].
 */
fun List<Persona>.toPersonaEntityList(): List<PersonaEntity> = map { it.toEntity() }
