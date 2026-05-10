package com.materialchat.domain.repository

import com.materialchat.domain.model.Memory
import com.materialchat.domain.model.MemoryCandidate
import com.materialchat.domain.model.RecalledMemory
import kotlinx.coroutines.flow.Flow

interface MemoryRepository {
    fun observeActiveMemories(): Flow<List<Memory>>

    suspend fun recall(
        query: String,
        conversationContext: String = "",
        limit: Int = 5
    ): List<RecalledMemory>

    suspend fun saveCandidate(candidate: MemoryCandidate): Memory?

    suspend fun saveCandidates(candidates: List<MemoryCandidate>): List<Memory>

    suspend fun markRecalled(memoryIds: List<String>)

    suspend fun getMemories(memoryIds: List<String>): List<Memory>

    suspend fun archiveMemory(memoryId: String)

    suspend fun deleteMemory(memoryId: String)

    suspend fun deleteAllMemories()
}
