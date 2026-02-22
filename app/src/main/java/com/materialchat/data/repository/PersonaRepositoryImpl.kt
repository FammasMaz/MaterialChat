package com.materialchat.data.repository

import com.materialchat.data.local.database.dao.PersonaDao
import com.materialchat.data.mapper.toDomain
import com.materialchat.data.mapper.toEntity
import com.materialchat.data.mapper.toPersonaEntityList
import com.materialchat.domain.model.Persona
import com.materialchat.domain.repository.PersonaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [PersonaRepository].
 */
@Singleton
class PersonaRepositoryImpl @Inject constructor(
    private val personaDao: PersonaDao
) : PersonaRepository {

    override fun observeAllPersonas(): Flow<List<Persona>> =
        personaDao.getAllPersonas().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getPersonaById(id: String): Persona? =
        personaDao.getPersonaById(id)?.toDomain()

    override suspend fun createPersona(persona: Persona) =
        personaDao.insert(persona.toEntity())

    override suspend fun updatePersona(persona: Persona) =
        personaDao.update(persona.toEntity())

    override suspend fun deletePersona(personaId: String) =
        personaDao.deleteById(personaId)

    override suspend fun seedBuiltinPersonas(personas: List<Persona>) =
        personaDao.insertAll(personas.toPersonaEntityList())

    override suspend fun getBuiltinPersonaCount(): Int =
        personaDao.getBuiltinPersonaCount()
}
