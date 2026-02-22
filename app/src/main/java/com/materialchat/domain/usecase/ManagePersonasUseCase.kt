package com.materialchat.domain.usecase

import com.materialchat.domain.model.Persona
import com.materialchat.domain.repository.PersonaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case that delegates persona CRUD operations to the repository.
 *
 * Provides a clean boundary between the UI/ViewModel layer and the data layer
 * for all persona management operations.
 */
class ManagePersonasUseCase @Inject constructor(
    private val personaRepository: PersonaRepository
) {
    /**
     * Observes all personas (built-ins first, then by last-updated).
     */
    fun observeAllPersonas(): Flow<List<Persona>> =
        personaRepository.observeAllPersonas()

    /**
     * Retrieves a single persona by ID.
     *
     * @return The persona, or null if not found
     */
    suspend fun getPersonaById(id: String): Persona? =
        personaRepository.getPersonaById(id)

    /**
     * Creates a new persona.
     */
    suspend fun createPersona(persona: Persona) =
        personaRepository.createPersona(persona)

    /**
     * Updates an existing persona.
     */
    suspend fun updatePersona(persona: Persona) =
        personaRepository.updatePersona(persona)

    /**
     * Deletes a persona by its ID.
     */
    suspend fun deletePersona(personaId: String) =
        personaRepository.deletePersona(personaId)
}
