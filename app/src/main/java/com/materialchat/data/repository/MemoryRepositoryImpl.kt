package com.materialchat.data.repository

import com.materialchat.data.local.database.dao.MemoryDao
import com.materialchat.data.mapper.normalizedMemoryContent
import com.materialchat.data.mapper.toDomain
import com.materialchat.data.mapper.toEntity
import com.materialchat.data.mapper.toMemoryDomainList
import com.materialchat.di.IoDispatcher
import com.materialchat.domain.model.Memory
import com.materialchat.domain.model.MemoryCandidate
import com.materialchat.domain.model.RecalledMemory
import com.materialchat.domain.repository.MemoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln

@Singleton
class MemoryRepositoryImpl @Inject constructor(
    private val memoryDao: MemoryDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : MemoryRepository {

    override fun observeActiveMemories(): Flow<List<Memory>> {
        return memoryDao.observeActiveMemories().map { entities -> entities.toMemoryDomainList() }
    }

    override suspend fun recall(
        query: String,
        conversationContext: String,
        limit: Int
    ): List<RecalledMemory> = withContext(ioDispatcher) {
        val searchText = listOf(query, conversationContext)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
        val queryTokens = tokenize(searchText)
        if (queryTokens.isEmpty()) return@withContext emptyList()

        memoryDao.getActiveMemories(limit = MAX_RECALL_POOL)
            .mapNotNull { entity ->
                val memory = entity.toDomain()
                val score = scoreMemory(memory, queryTokens)
                if (score >= MIN_RECALL_SCORE) RecalledMemory(memory, score) else null
            }
            .sortedWith(
                compareByDescending<RecalledMemory> { it.score }
                    .thenByDescending { it.memory.confidence }
                    .thenByDescending { it.memory.updatedAt }
            )
            .take(limit.coerceIn(1, 12))
    }

    override suspend fun saveCandidate(candidate: MemoryCandidate): Memory? = withContext(ioDispatcher) {
        val content = sanitizeMemoryContent(candidate.content) ?: return@withContext null
        val normalized = content.normalizedMemoryContent()
        if (normalized.length < MIN_NORMALIZED_LENGTH) return@withContext null

        val existing = memoryDao.getByNormalizedContent(normalized)
        val now = System.currentTimeMillis()
        if (existing != null) {
            if (existing.isArchived) return@withContext null
            val updated = existing.copy(
                confidence = maxOf(existing.confidence, candidate.confidence.coerceIn(0f, 1f)),
                sourceConversationId = existing.sourceConversationId ?: candidate.sourceConversationId,
                sourceMessageId = existing.sourceMessageId ?: candidate.sourceMessageId,
                updatedAt = now
            )
            memoryDao.update(updated)
            return@withContext updated.toDomain()
        }

        val memory = Memory(
            content = content,
            kind = candidate.kind,
            confidence = candidate.confidence.coerceIn(0f, 1f),
            sourceConversationId = candidate.sourceConversationId,
            sourceMessageId = candidate.sourceMessageId,
            createdAt = now,
            updatedAt = now
        )
        val inserted = memoryDao.insert(memory.toEntity())
        if (inserted == -1L) null else memory
    }

    override suspend fun saveCandidates(candidates: List<MemoryCandidate>): List<Memory> {
        if (candidates.isEmpty()) return emptyList()
        val saved = mutableListOf<Memory>()
        candidates.take(MAX_SAVE_CANDIDATES).forEach { candidate ->
            saveCandidate(candidate)?.let { saved.add(it) }
        }
        return saved.distinctBy { it.id }
    }

    override suspend fun markRecalled(memoryIds: List<String>) = withContext(ioDispatcher) {
        val ids = memoryIds.distinct().filter { it.isNotBlank() }
        if (ids.isNotEmpty()) {
            memoryDao.markRecalled(ids, System.currentTimeMillis())
        }
    }

    override suspend fun getMemories(memoryIds: List<String>): List<Memory> = withContext(ioDispatcher) {
        val ids = memoryIds.distinct().filter { it.isNotBlank() }
        if (ids.isEmpty()) emptyList() else memoryDao.getByIds(ids).toMemoryDomainList()
    }

    override suspend fun archiveMemory(memoryId: String) = withContext(ioDispatcher) {
        memoryDao.setArchived(memoryId, archived = true, updatedAt = System.currentTimeMillis())
    }

    private fun scoreMemory(memory: Memory, queryTokens: Set<String>): Double {
        val memoryTokens = tokenize(memory.content)
        if (memoryTokens.isEmpty()) return 0.0

        val overlap = memoryTokens.intersect(queryTokens)
        if (overlap.isEmpty()) return 0.0

        val coverage = overlap.size.toDouble() / memoryTokens.size.coerceAtLeast(1)
        val queryCoverage = overlap.size.toDouble() / queryTokens.size.coerceAtLeast(1)
        val confidenceBoost = 0.25 * memory.confidence.coerceIn(0f, 1f)
        val recallBoost = ln((memory.recallCount + 1).toDouble()) * 0.03
        return (coverage * 0.55) + (queryCoverage * 0.30) + confidenceBoost + recallBoost
    }

    private fun tokenize(text: String): Set<String> {
        return text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .asSequence()
            .map { it.trim() }
            .filter { it.length >= 3 }
            .filter { it !in STOP_WORDS }
            .toSet()
    }

    private fun sanitizeMemoryContent(raw: String): String? {
        val compact = raw
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', '*', '•', ':', ';', '.', '\n', '\t')
        if (compact.length !in MIN_MEMORY_LENGTH..MAX_MEMORY_LENGTH) return null
        if (compact.count { it.isLetterOrDigit() } < MIN_NORMALIZED_LENGTH) return null
        return compact.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private companion object {
        const val MAX_RECALL_POOL = 500
        const val MIN_RECALL_SCORE = 0.28
        const val MAX_SAVE_CANDIDATES = 5
        const val MIN_MEMORY_LENGTH = 12
        const val MAX_MEMORY_LENGTH = 280
        const val MIN_NORMALIZED_LENGTH = 10

        val STOP_WORDS = setOf(
            "the", "and", "for", "that", "this", "with", "you", "your", "are", "was",
            "were", "have", "has", "had", "but", "not", "can", "could", "would", "should",
            "about", "from", "into", "onto", "over", "under", "then", "than", "them", "they",
            "our", "out", "all", "any", "just", "like", "what", "when", "where", "why", "how"
        )
    }
}
