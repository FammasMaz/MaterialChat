package com.materialchat.domain.repository

import com.materialchat.domain.model.Persona
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for persona persistence operations.
 *
 * Provides observe, CRUD, and seeding capabilities for AI personas.
 */
interface PersonaRepository {

    /**
     * Observes all personas sorted by built-in status (first) then last-updated.
     *
     * @return A Flow emitting the list of personas whenever it changes
     */
    fun observeAllPersonas(): Flow<List<Persona>>

    /**
     * Gets a single persona by its ID.
     *
     * @param id The unique identifier of the persona
     * @return The persona, or null if not found
     */
    suspend fun getPersonaById(id: String): Persona?

    /**
     * Creates (inserts) a new persona.
     *
     * @param persona The persona to create
     */
    suspend fun createPersona(persona: Persona)

    /**
     * Updates an existing persona.
     *
     * @param persona The persona with updated fields
     */
    suspend fun updatePersona(persona: Persona)

    /**
     * Deletes a persona by its ID.
     *
     * @param personaId The ID of the persona to delete
     */
    suspend fun deletePersona(personaId: String)

    /**
     * Seeds a set of built-in personas into the database.
     * Uses REPLACE conflict strategy so re-seeding is safe.
     *
     * @param personas The list of built-in personas to insert
     */
    suspend fun seedBuiltinPersonas(personas: List<Persona>)

    /**
     * Returns the number of built-in personas currently in the database.
     * Used to decide whether seeding is required (e.g. for upgrading users).
     */
    suspend fun getBuiltinPersonaCount(): Int
}
