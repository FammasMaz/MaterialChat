package com.materialchat.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.materialchat.data.local.database.entity.PersonaEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for persona operations.
 *
 * Provides CRUD operations for custom AI personas.
 */
@Dao
interface PersonaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(persona: PersonaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(personas: List<PersonaEntity>)

    @Update
    suspend fun update(persona: PersonaEntity)

    @Delete
    suspend fun delete(persona: PersonaEntity)

    @Query("DELETE FROM personas WHERE id = :personaId")
    suspend fun deleteById(personaId: String)

    @Query("SELECT * FROM personas ORDER BY is_builtin DESC, updated_at DESC")
    fun getAllPersonas(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas ORDER BY is_builtin DESC, updated_at DESC")
    suspend fun getAllPersonasOnce(): List<PersonaEntity>

    @Query("SELECT * FROM personas WHERE id = :personaId")
    suspend fun getPersonaById(personaId: String): PersonaEntity?

    @Query("SELECT * FROM personas WHERE is_builtin = 1")
    suspend fun getBuiltinPersonas(): List<PersonaEntity>

    @Query("SELECT COUNT(*) FROM personas WHERE is_builtin = 1")
    suspend fun getBuiltinPersonaCount(): Int

    @Query("SELECT * FROM personas WHERE name LIKE '%' || :query || '%' COLLATE NOCASE ORDER BY updated_at DESC")
    fun searchPersonas(query: String): Flow<List<PersonaEntity>>
}
