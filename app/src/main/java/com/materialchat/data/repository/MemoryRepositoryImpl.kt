package com.materialchat.data.repository

import com.materialchat.data.local.database.dao.MemoryDao
import com.materialchat.data.mapper.normalizedMemoryContent
import com.materialchat.data.mapper.toDomain
import com.materialchat.data.mapper.toEntity
import com.materialchat.data.mapper.toMemoryDomainList
import com.materialchat.data.mapper.toMemorySnippetDomainList
import com.materialchat.di.IoDispatcher
import com.materialchat.domain.model.Memory
import com.materialchat.domain.model.MemoryCandidate
import com.materialchat.domain.model.MemoryKind
import com.materialchat.domain.model.MemorySnippet
import com.materialchat.domain.model.MemorySnippetCandidate
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.RecalledMemory
import com.materialchat.domain.model.RecalledMemorySource
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

    private enum class RecallIntent {
        GENERAL,
        PREFERENCE,
        PERSONAL,
        PROJECT,
        GOAL,
        INSTRUCTION,
        RELATIONSHIP
    }

    override fun observeActiveMemories(): Flow<List<Memory>> {
        return memoryDao.observeActiveMemories().map { entities -> entities.toMemoryDomainList() }
    }

    override suspend fun recall(
        query: String,
        conversationContext: String,
        limit: Int
    ): List<RecalledMemory> = withContext(ioDispatcher) {
        val queryTokens = tokenize(query)
        val contextTokens = tokenize(conversationContext)
        val intents = classifyRecallIntents(query)
        if (queryTokens.isEmpty() && contextTokens.isEmpty() && intents.isEmpty()) return@withContext emptyList()
        val minimumScore = if (intents.isEmpty()) MIN_PASSIVE_RECALL_SCORE else MIN_INTENT_RECALL_SCORE

        val extractedMemories = memoryDao.getActiveMemories(limit = MAX_RECALL_POOL)
            .mapNotNull { entity ->
                val memory = entity.toDomain()
                val score = scoreMemory(memory, queryTokens, contextTokens, intents)
                if (score >= minimumScore) RecalledMemory(memory, score) else null
            }
            .sortedForRecall()

        val recalledSnippets = if (shouldRecallVerbatimSnippets(query)) {
            val snippetMinimumScore = if (intents.isEmpty()) {
                MIN_SNIPPET_PASSIVE_RECALL_SCORE
            } else {
                MIN_SNIPPET_INTENT_RECALL_SCORE
            }
            getSnippetRecallCandidates(queryTokens, contextTokens)
                .mapNotNull { snippet ->
                    val score = scoreSnippet(snippet, queryTokens, contextTokens, intents)
                    if (score >= snippetMinimumScore) snippet.toRecalledMemory(score) else null
                }
                .sortedForRecall()
                .take(MAX_RECALLED_SNIPPETS)
        } else {
            emptyList()
        }

        (extractedMemories + recalledSnippets)
            .sortedForRecall()
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

    override suspend fun saveSnippet(candidate: MemorySnippetCandidate): MemorySnippet? = withContext(ioDispatcher) {
        val content = sanitizeSnippetContent(candidate.content) ?: return@withContext null
        val snippet = MemorySnippet(
            id = snippetIdForMessage(candidate.messageId),
            conversationId = candidate.conversationId,
            messageId = candidate.messageId,
            role = candidate.role,
            content = content,
            createdAt = candidate.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        memoryDao.insertSnippet(snippet.toEntity())
        snippet
    }

    override suspend fun saveSnippets(candidates: List<MemorySnippetCandidate>): List<MemorySnippet> {
        if (candidates.isEmpty()) return emptyList()
        val saved = mutableListOf<MemorySnippet>()
        candidates.take(MAX_SAVE_SNIPPETS).forEach { candidate ->
            saveSnippet(candidate)?.let { saved.add(it) }
        }
        return saved.distinctBy { it.id }
    }

    override suspend fun markRecalled(memoryIds: List<String>) = withContext(ioDispatcher) {
        val ids = memoryIds.distinct().filter { it.isNotBlank() }
        if (ids.isNotEmpty()) {
            memoryDao.markRecalled(ids, System.currentTimeMillis())
        }
    }

    override suspend fun markSnippetsRecalled(snippetIds: List<String>) = withContext(ioDispatcher) {
        val ids = snippetIds.distinct().filter { it.isNotBlank() }
        if (ids.isNotEmpty()) {
            memoryDao.markSnippetsRecalled(ids, System.currentTimeMillis())
        }
    }

    override suspend fun getMemories(memoryIds: List<String>): List<Memory> = withContext(ioDispatcher) {
        val ids = memoryIds.distinct().filter { it.isNotBlank() }
        if (ids.isEmpty()) emptyList() else memoryDao.getByIds(ids).toMemoryDomainList()
    }

    override suspend fun archiveMemory(memoryId: String) = withContext(ioDispatcher) {
        memoryDao.setArchived(memoryId, archived = true, updatedAt = System.currentTimeMillis())
    }

    override suspend fun deleteMemory(memoryId: String) = withContext(ioDispatcher) {
        memoryDao.deleteById(memoryId)
    }

    override suspend fun deleteAllMemories() = withContext(ioDispatcher) {
        memoryDao.deleteAll()
        memoryDao.deleteAllSnippets()
    }

    private fun scoreMemory(
        memory: Memory,
        queryTokens: Set<String>,
        contextTokens: Set<String>,
        intents: Set<RecallIntent>
    ): Double {
        val memoryTokens = tokenize(memory.content)
        if (memoryTokens.isEmpty()) return 0.0

        val queryOverlap = memoryTokens.intersect(queryTokens)
        val contextOverlap = memoryTokens.intersect(contextTokens) - queryOverlap
        val kindMatchesIntent = memory.kind.matchesAny(intents)
        val generalRecall = RecallIntent.GENERAL in intents
        val hasRecallIntent = intents.isNotEmpty()
        val intentOnlyMatch = queryOverlap.isEmpty() && (kindMatchesIntent || generalRecall)

        if (queryOverlap.isEmpty() && !intentOnlyMatch) return 0.0
        if (!intentOnlyMatch && !hasStrongEnoughLexicalMatch(memory, queryOverlap, hasRecallIntent)) return 0.0

        val confidence = memory.confidence.coerceIn(0f, 1f).toDouble()
        val confidenceBoost = 0.10 * confidence
        val recallBoost = ln((memory.recallCount + 1).toDouble()) * 0.012
        val intentBoost = when {
            kindMatchesIntent -> 0.24
            generalRecall -> 0.16
            hasRecallIntent -> 0.08
            else -> 0.0
        }

        if (intentOnlyMatch) {
            return 0.34 + confidenceBoost + intentBoost + recallBoost
        }

        val coverage = queryOverlap.size.toDouble() / memoryTokens.size.coerceAtLeast(1)
        val queryCoverage = queryOverlap.size.toDouble() / queryTokens.size.coerceAtLeast(1)
        val contextCoverage = contextOverlap.size.toDouble() / memoryTokens.size.coerceAtLeast(1)
        return (coverage * 0.44) +
            (queryCoverage * 0.28) +
            (contextCoverage * 0.10) +
            (queryOverlap.size * 0.055) +
            confidenceBoost +
            recallBoost +
            intentBoost
    }

    private fun hasStrongEnoughLexicalMatch(
        memory: Memory,
        queryOverlap: Set<String>,
        hasRecallIntent: Boolean
    ): Boolean {
        if (queryOverlap.size >= 2) return true
        if (queryOverlap.isEmpty()) return false
        if (hasRecallIntent) return true

        val singleToken = queryOverlap.first()
        val distinctiveSingleToken = singleToken.length >= 5 && singleToken !in GENERIC_MEMORY_TOKENS
        val highConfidenceDurableMemory = memory.confidence >= 0.74f && memory.kind in PASSIVE_ONE_TOKEN_KINDS
        return distinctiveSingleToken && highConfidenceDurableMemory
    }

    private fun classifyRecallIntents(query: String): Set<RecallIntent> {
        val intents = buildSet {
            if (GENERAL_RECALL_REGEX.containsMatchIn(query)) add(RecallIntent.GENERAL)
            if (PREFERENCE_RECALL_REGEX.containsMatchIn(query)) add(RecallIntent.PREFERENCE)
            if (PERSONAL_RECALL_REGEX.containsMatchIn(query)) add(RecallIntent.PERSONAL)
            if (PROJECT_RECALL_REGEX.containsMatchIn(query)) add(RecallIntent.PROJECT)
            if (GOAL_RECALL_REGEX.containsMatchIn(query)) add(RecallIntent.GOAL)
            if (INSTRUCTION_RECALL_REGEX.containsMatchIn(query)) add(RecallIntent.INSTRUCTION)
            if (RELATIONSHIP_RECALL_REGEX.containsMatchIn(query)) add(RecallIntent.RELATIONSHIP)
        }
        return intents
    }

    private fun MemoryKind.matchesAny(intents: Set<RecallIntent>): Boolean {
        if (intents.isEmpty()) return false
        return when (this) {
            MemoryKind.USER_PREFERENCE -> RecallIntent.PREFERENCE in intents || RecallIntent.GENERAL in intents
            MemoryKind.PERSONAL_FACT -> RecallIntent.PERSONAL in intents || RecallIntent.GENERAL in intents
            MemoryKind.PROJECT_FACT -> RecallIntent.PROJECT in intents || RecallIntent.GENERAL in intents
            MemoryKind.LONG_TERM_GOAL -> RecallIntent.GOAL in intents || RecallIntent.PROJECT in intents || RecallIntent.GENERAL in intents
            MemoryKind.INSTRUCTION -> RecallIntent.INSTRUCTION in intents || RecallIntent.PREFERENCE in intents || RecallIntent.GENERAL in intents
            MemoryKind.RELATIONSHIP -> RecallIntent.RELATIONSHIP in intents || RecallIntent.PERSONAL in intents || RecallIntent.GENERAL in intents
            MemoryKind.OTHER -> RecallIntent.GENERAL in intents
        }
    }

    private fun shouldRecallVerbatimSnippets(query: String): Boolean {
        return SNIPPET_RECALL_REGEX.containsMatchIn(query) ||
            (SNIPPET_TEMPORAL_REGEX.containsMatchIn(query) && SNIPPET_DISCUSSION_REGEX.containsMatchIn(query))
    }

    private suspend fun getSnippetRecallCandidates(
        queryTokens: Set<String>,
        contextTokens: Set<String>
    ): List<MemorySnippet> {
        val searchTerms = (queryTokens + contextTokens)
            .asSequence()
            .filter { it.length >= 4 && it !in GENERIC_MEMORY_TOKENS }
            .distinct()
            .take(MAX_SNIPPET_SEARCH_TERMS)
            .toList()
        val searched = if (searchTerms.isEmpty()) {
            emptyList()
        } else {
            memoryDao.searchActiveSnippets(
                term1 = searchTerms.getOrElse(0) { "" },
                term2 = searchTerms.getOrElse(1) { "" },
                term3 = searchTerms.getOrElse(2) { "" },
                term4 = searchTerms.getOrElse(3) { "" },
                limit = MAX_SNIPPET_SEARCH_POOL
            )
        }
        val recent = memoryDao.getActiveSnippets(limit = MAX_SNIPPET_RECALL_POOL)
        return (searched + recent)
            .distinctBy { it.id }
            .toMemorySnippetDomainList()
    }

    private fun scoreSnippet(
        snippet: MemorySnippet,
        queryTokens: Set<String>,
        contextTokens: Set<String>,
        intents: Set<RecallIntent>
    ): Double {
        val snippetTokens = tokenize(snippet.content)
        if (snippetTokens.isEmpty()) return 0.0

        val queryOverlap = snippetTokens.intersect(queryTokens)
        val contextOverlap = snippetTokens.intersect(contextTokens) - queryOverlap
        val hasRecallIntent = intents.isNotEmpty()
        if (!hasStrongEnoughSnippetMatch(queryOverlap, contextOverlap, hasRecallIntent)) return 0.0

        val coverage = queryOverlap.size.toDouble() / snippetTokens.size.coerceAtLeast(1)
        val queryCoverage = queryOverlap.size.toDouble() / queryTokens.size.coerceAtLeast(1)
        val contextCoverage = contextOverlap.size.toDouble() / snippetTokens.size.coerceAtLeast(1)
        val recallBoost = ln((snippet.recallCount + 1).toDouble()) * 0.008
        val intentBoost = if (hasRecallIntent) 0.10 else 0.0
        val roleBoost = if (snippet.role == MessageRole.USER) 0.04 else 0.0
        val recencyBoost = recencyBoost(snippet.updatedAt)

        return (coverage * 0.26) +
            (queryCoverage * 0.36) +
            (contextCoverage * 0.10) +
            (queryOverlap.size * 0.045) +
            recallBoost +
            intentBoost +
            roleBoost +
            recencyBoost
    }

    private fun hasStrongEnoughSnippetMatch(
        queryOverlap: Set<String>,
        contextOverlap: Set<String>,
        hasRecallIntent: Boolean
    ): Boolean {
        if (queryOverlap.size >= 2) return true
        if (hasRecallIntent && queryOverlap.isNotEmpty()) return true
        if (queryOverlap.size == 1) {
            val token = queryOverlap.first()
            return token.length >= 5 && token !in GENERIC_MEMORY_TOKENS
        }
        return contextOverlap.size >= 2 && hasRecallIntent
    }

    private fun recencyBoost(updatedAt: Long): Double {
        val ageDays = ((System.currentTimeMillis() - updatedAt).coerceAtLeast(0L) / DAY_MS.toDouble())
        return 0.055 / (1.0 + (ageDays / 14.0))
    }

    private fun MemorySnippet.toRecalledMemory(score: Double): RecalledMemory {
        val label = when (role) {
            MessageRole.USER -> "User said: $content"
            MessageRole.ASSISTANT -> "Assistant replied: $content"
            MessageRole.SYSTEM -> content
        }
        return RecalledMemory(
            memory = Memory(
                id = id,
                content = label,
                kind = MemoryKind.OTHER,
                confidence = SNIPPET_CONFIDENCE,
                sourceConversationId = conversationId,
                sourceMessageId = messageId,
                createdAt = createdAt,
                updatedAt = updatedAt,
                lastRecalledAt = lastRecalledAt,
                recallCount = recallCount,
                isArchived = isArchived
            ),
            score = score,
            source = RecalledMemorySource.VERBATIM_SNIPPET
        )
    }

    private fun List<RecalledMemory>.sortedForRecall(): List<RecalledMemory> {
        return sortedWith(
            compareByDescending<RecalledMemory> { it.score }
                .thenByDescending { it.memory.confidence }
                .thenByDescending { it.memory.updatedAt }
        )
    }

    private fun tokenize(text: String): Set<String> {
        return text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .asSequence()
            .map { it.trim() }
            .filter { it.length >= 3 }
            .filter { it !in STOP_WORDS }
            .flatMap { expandToken(it) }
            .toSet()
    }

    private fun expandToken(token: String): Set<String> {
        return TOKEN_SYNONYMS[token]?.plus(token) ?: setOf(token)
    }

    private fun sanitizeMemoryContent(raw: String): String? {
        val compact = raw
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', '*', '•', ':', ';', '.', '\n', '\t')
        if (compact.length !in MIN_MEMORY_LENGTH..MAX_MEMORY_LENGTH) return null
        if (compact.count { it.isLetterOrDigit() } < MIN_NORMALIZED_LENGTH) return null
        return compact.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun sanitizeSnippetContent(raw: String): String? {
        val compact = raw
            .replace(Regex("```[\\s\\S]*?```"), "[code]")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (compact.length !in MIN_SNIPPET_LENGTH..MAX_SNIPPET_LENGTH) return null
        if (compact.count { it.isLetterOrDigit() } < MIN_SNIPPET_ALNUM_LENGTH) return null
        if (SENSITIVE_SNIPPET_REGEX.containsMatchIn(compact)) return null
        return compact
    }

    private fun snippetIdForMessage(messageId: String): String = "$SNIPPET_ID_PREFIX$messageId"

    private companion object {
        const val MAX_RECALL_POOL = 500
        const val MAX_SNIPPET_RECALL_POOL = 700
        const val MAX_SNIPPET_SEARCH_POOL = 1200
        const val MAX_SNIPPET_SEARCH_TERMS = 4
        const val MAX_RECALLED_SNIPPETS = 2
        const val MIN_PASSIVE_RECALL_SCORE = 0.48
        const val MIN_INTENT_RECALL_SCORE = 0.46
        const val MIN_SNIPPET_PASSIVE_RECALL_SCORE = 0.34
        const val MIN_SNIPPET_INTENT_RECALL_SCORE = 0.34
        const val MAX_SAVE_CANDIDATES = 5
        const val MAX_SAVE_SNIPPETS = 2
        const val MIN_MEMORY_LENGTH = 12
        const val MAX_MEMORY_LENGTH = 280
        const val MIN_NORMALIZED_LENGTH = 10
        const val MIN_SNIPPET_LENGTH = 24
        const val MAX_SNIPPET_LENGTH = 2400
        const val MIN_SNIPPET_ALNUM_LENGTH = 16
        const val SNIPPET_CONFIDENCE = 0.55f
        const val SNIPPET_ID_PREFIX = "snippet:"
        const val DAY_MS = 86_400_000L

        val GENERAL_RECALL_REGEX = Regex(
            "\\b(remember|memory|memories|what do you know about me|what have i told you|what do you know|do you know (?:about me|my|what i|what my|who i|where i|which)|previously|before)\\b",
            RegexOption.IGNORE_CASE
        )
        val PREFERENCE_RECALL_REGEX = Regex(
            "\\b(my preference|my preferences|do i prefer|what do i prefer|what i prefer|what do i like|things i like|my style|my tastes?|my favou?rite|what(?:'s| is)? my favou?rite)\\b",
            RegexOption.IGNORE_CASE
        )
        val PERSONAL_RECALL_REGEX = Regex(
            "\\b(about me|who am i|my name|where do i live|where i live|what do i do|my job|my role|my phone|my device|what [a-z0-9 ]{0,40}do i have|which [a-z0-9 ]{0,40}do i have|have i told you)\\b",
            RegexOption.IGNORE_CASE
        )
        val PROJECT_RECALL_REGEX = Regex(
            "\\b(my app|my project|this app|this project|our app|our project|project stack|tech stack|what stack|what are we building|what am i building)\\b",
            RegexOption.IGNORE_CASE
        )
        val GOAL_RECALL_REGEX = Regex(
            "\\b(my goal|my goals|what am i trying|what are we trying|roadmap|long term|plan for)\\b",
            RegexOption.IGNORE_CASE
        )
        val INSTRUCTION_RECALL_REGEX = Regex(
            "\\b(my instructions|how should you|how do i want you|from now on|always do|never do)\\b",
            RegexOption.IGNORE_CASE
        )
        val RELATIONSHIP_RECALL_REGEX = Regex(
            "\\b(my wife|my husband|my partner|my friend|my son|my daughter|my team|my manager|family)\\b",
            RegexOption.IGNORE_CASE
        )
        val SENSITIVE_SNIPPET_REGEX = Regex(
            "\\b(api key|password|secret|token|private key|credit card|ssn|social security)\\b",
            RegexOption.IGNORE_CASE
        )
        val SNIPPET_RECALL_REGEX = Regex(
            "\\b(what did (?:we|i|you) (?:discuss|talk about|say|decide|mention)|what have (?:we|i) (?:discussed|talked about|said|decided|mentioned)|what did i tell you|what did i say|what did you say|did we decide|what did we decide|earlier (?:chat|conversation|discussion)|previous (?:chat|conversation|discussion)|last time|we discussed|we talked|i told you|you told me|mentioned before|said before|discussed before|talked about before)\\b",
            RegexOption.IGNORE_CASE
        )
        val SNIPPET_TEMPORAL_REGEX = Regex(
            "\\b(previously|before|earlier|last time|past|old chat|old conversation)\\b",
            RegexOption.IGNORE_CASE
        )
        val SNIPPET_DISCUSSION_REGEX = Regex(
            "\\b(discuss|discussed|talk|talked|say|said|tell|told|mention|mentioned|decide|decided|conversation|chat)\\b",
            RegexOption.IGNORE_CASE
        )

        val PASSIVE_ONE_TOKEN_KINDS = setOf(
            MemoryKind.USER_PREFERENCE,
            MemoryKind.INSTRUCTION,
            MemoryKind.PROJECT_FACT,
            MemoryKind.LONG_TERM_GOAL
        )
        val GENERIC_MEMORY_TOKENS = setOf(
            "user", "prefers", "prefer", "preference", "likes", "like", "wants", "goal",
            "project", "app", "assistant", "materialchat"
        )

        val TOKEN_SYNONYMS = mapOf(
            "dark" to setOf("black", "theme", "mode"),
            "black" to setOf("dark", "theme", "mode"),
            "light" to setOf("white", "theme", "mode"),
            "white" to setOf("light", "theme", "mode"),
            "theme" to setOf("mode", "style", "appearance"),
            "mode" to setOf("theme", "style", "appearance"),
            "style" to setOf("theme", "appearance"),
            "favorite" to setOf("favourite", "prefer", "prefers", "preference", "like", "likes"),
            "favourite" to setOf("favorite", "prefer", "prefers", "preference", "like", "likes"),
            "prefer" to setOf("favorite", "favourite", "prefers", "preference", "like", "likes"),
            "prefers" to setOf("favorite", "favourite", "prefer", "preference", "like", "likes"),
            "color" to setOf("colour", "colors", "colours"),
            "colors" to setOf("color", "colour", "colours"),
            "colour" to setOf("color", "colors", "colours"),
            "stack" to setOf("tech", "technology", "framework", "language", "kotlin", "android", "compose"),
            "tech" to setOf("stack", "technology", "framework", "language"),
            "technology" to setOf("tech", "stack", "framework", "language"),
            "language" to setOf("stack", "tech", "technology"),
            "framework" to setOf("stack", "tech", "technology"),
            "android" to setOf("kotlin", "compose", "mobile", "app"),
            "compose" to setOf("android", "kotlin", "jetpack", "ui"),
            "jetpack" to setOf("android", "compose", "kotlin"),
            "kotlin" to setOf("android", "compose", "language", "stack"),
            "phone" to setOf("device", "mobile", "android", "iphone", "pixel"),
            "device" to setOf("phone", "mobile", "android", "iphone", "pixel"),
            "mobile" to setOf("phone", "device", "android", "iphone", "pixel"),
            "pixel" to setOf("phone", "device", "android", "mobile"),
            "iphone" to setOf("phone", "device", "mobile"),
            "discuss" to setOf("talked", "conversation", "mentioned"),
            "discussed" to setOf("talked", "conversation", "mentioned"),
            "talked" to setOf("discussed", "conversation", "mentioned"),
            "decide" to setOf("decided", "decision", "choice", "settled"),
            "decided" to setOf("decide", "decision", "choice", "settled"),
            "decision" to setOf("decided", "choice", "settled")
        )

        val STOP_WORDS = setOf(
            "the", "and", "for", "that", "this", "with", "you", "your", "are", "was",
            "were", "have", "has", "had", "but", "not", "can", "could", "would", "should",
            "about", "from", "into", "onto", "over", "under", "then", "than", "them", "they",
            "our", "out", "all", "any", "just", "like", "what", "when", "where", "why", "how",
            "app", "chat", "model", "models", "make", "made", "using", "use", "need", "needs",
            "want", "wants", "memory", "memories", "remember", "thing", "stuff", "screen", "system"
        )
    }
}
