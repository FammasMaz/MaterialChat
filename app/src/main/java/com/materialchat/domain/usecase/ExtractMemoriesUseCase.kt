package com.materialchat.domain.usecase

import com.materialchat.domain.model.MemoryCandidate
import com.materialchat.domain.model.MemoryKind
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.MemoryRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class ExtractMemoriesUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository,
    private val json: Json
) {
    suspend operator fun invoke(
        provider: Provider,
        model: String,
        conversationId: String,
        sourceMessageId: String,
        userContent: String,
        assistantResponse: String,
        recentMessages: List<Message>
    ): List<com.materialchat.domain.model.Memory> {
        if (!shouldAttemptExtraction(userContent, assistantResponse)) return emptyList()

        val llmCandidates = generateCandidatesWithModel(
            provider = provider,
            model = model,
            conversationId = conversationId,
            sourceMessageId = sourceMessageId,
            userContent = userContent,
            assistantResponse = assistantResponse,
            recentMessages = recentMessages
        ).getOrDefault(emptyList())

        val candidates = if (llmCandidates.isNotEmpty()) {
            llmCandidates
        } else {
            heuristicCandidates(
                conversationId = conversationId,
                sourceMessageId = sourceMessageId,
                userContent = userContent
            )
        }

        return memoryRepository.saveCandidates(candidates)
    }

    private suspend fun generateCandidatesWithModel(
        provider: Provider,
        model: String,
        conversationId: String,
        sourceMessageId: String,
        userContent: String,
        assistantResponse: String,
        recentMessages: List<Message>
    ): Result<List<MemoryCandidate>> {
        val prompt = buildExtractionPrompt(userContent, assistantResponse, recentMessages)
        return chatRepository.generateSimpleCompletion(
            provider = provider,
            prompt = prompt,
            model = model,
            systemPrompt = MEMORY_EXTRACTION_SYSTEM_PROMPT,
            reasoningEffort = ReasoningEffort.NONE
        ).mapCatching { response ->
            parseExtractionResponse(response).map { extracted ->
                MemoryCandidate(
                    content = extracted.content,
                    kind = MemoryKind.fromRaw(extracted.kind),
                    confidence = extracted.confidence ?: DEFAULT_CONFIDENCE,
                    sourceConversationId = conversationId,
                    sourceMessageId = sourceMessageId
                )
            }
        }
    }

    private fun buildExtractionPrompt(
        userContent: String,
        assistantResponse: String,
        recentMessages: List<Message>
    ): String {
        val recentContext = recentMessages
            .takeLast(8)
            .joinToString(separator = "\n") { message ->
                "${message.role.name.lowercase()}: ${sanitizeForExtraction(message.content).take(MAX_CONTEXT_CHARS)}"
            }
        return """
Recent conversation context:
$recentContext

Latest user message:
${sanitizeForExtraction(userContent).take(MAX_LATEST_CHARS)}

Assistant response:
${sanitizeForExtraction(assistantResponse).take(MAX_LATEST_CHARS)}

Extract only durable memories that will help future chats.
        """.trimIndent()
    }

    private fun parseExtractionResponse(response: String): List<ExtractedMemory> {
        val cleaned = response
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val payload = runCatching { json.decodeFromString<MemoryExtractionPayload>(cleaned) }
            .getOrNull()
            ?: return emptyList()
        return payload.memories
            .filter { it.content.isNotBlank() }
            .take(MAX_EXTRACTED_MEMORIES)
    }

    private fun heuristicCandidates(
        conversationId: String,
        sourceMessageId: String,
        userContent: String
    ): List<MemoryCandidate> {
        val compact = sanitizeForExtraction(userContent)
        if (compact.length !in 12..280) return emptyList()
        val kind = when {
            PREFERENCE_REGEX.containsMatchIn(compact) -> MemoryKind.USER_PREFERENCE
            INSTRUCTION_REGEX.containsMatchIn(compact) -> MemoryKind.INSTRUCTION
            GOAL_REGEX.containsMatchIn(compact) -> MemoryKind.LONG_TERM_GOAL
            PROJECT_REGEX.containsMatchIn(compact) -> MemoryKind.PROJECT_FACT
            PERSONAL_REGEX.containsMatchIn(compact) -> MemoryKind.PERSONAL_FACT
            else -> return emptyList()
        }
        return listOf(
            MemoryCandidate(
                content = compact,
                kind = kind,
                confidence = 0.62f,
                sourceConversationId = conversationId,
                sourceMessageId = sourceMessageId
            )
        )
    }

    private fun shouldAttemptExtraction(userContent: String, assistantResponse: String): Boolean {
        if (userContent.isBlank()) return false
        if (assistantResponse.isBlank()) return false
        if (userContent.length < 12) return false
        if (userContent.length > MAX_USER_EXTRACTION_CHARS) return false
        if (SENSITIVE_REGEX.containsMatchIn(userContent)) return false
        return true
    }

    private fun sanitizeForExtraction(text: String): String {
        return text
            .replace(Regex("```[\\s\\S]*?```"), "[code]")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    @Serializable
    private data class MemoryExtractionPayload(
        val memories: List<ExtractedMemory> = emptyList()
    )

    @Serializable
    private data class ExtractedMemory(
        val content: String,
        val kind: String? = null,
        val confidence: Float? = null
    )

    private companion object {
        const val DEFAULT_CONFIDENCE = 0.72f
        const val MAX_CONTEXT_CHARS = 600
        const val MAX_LATEST_CHARS = 1400
        const val MAX_USER_EXTRACTION_CHARS = 5000
        const val MAX_EXTRACTED_MEMORIES = 5

        const val MEMORY_EXTRACTION_SYSTEM_PROMPT = """
You are MaterialChat's private passive memory filter. Return strict JSON only:
{"memories":[{"content":"...","kind":"USER_PREFERENCE|PERSONAL_FACT|PROJECT_FACT|LONG_TERM_GOAL|INSTRUCTION|RELATIONSHIP|OTHER","confidence":0.0}]}

Save only durable, user-confirmed facts/preferences/goals/instructions that will likely matter in future chats.
Do not save secrets, credentials, API keys, private tokens, temporary debugging details, one-off questions, or assistant claims.
Use the user's wording when possible. Keep each memory under 220 characters. Return {"memories":[]} when nothing is worth saving.
        """

        val PREFERENCE_REGEX = Regex("\\b(i prefer|i like|i don't like|i hate|my preference|please use|please don't)\\b", RegexOption.IGNORE_CASE)
        val INSTRUCTION_REGEX = Regex("\\b(always|never|remember to|from now on|call me|address me|use .* style)\\b", RegexOption.IGNORE_CASE)
        val GOAL_REGEX = Regex("\\b(i want to|i'm trying to|my goal|we need to|i plan to)\\b", RegexOption.IGNORE_CASE)
        val PROJECT_REGEX = Regex("\\b(my app|my project|we are building|i am building|i'm building|project is called|app is called)\\b", RegexOption.IGNORE_CASE)
        val PERSONAL_REGEX = Regex("\\b(my name is|i live|i work|i study|my role|my job|i am a|i'm a)\\b", RegexOption.IGNORE_CASE)
        val SENSITIVE_REGEX = Regex("\\b(api key|password|secret|token|private key|credit card|ssn|social security)\\b", RegexOption.IGNORE_CASE)
    }
}
