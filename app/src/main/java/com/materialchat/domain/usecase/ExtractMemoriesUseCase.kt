package com.materialchat.domain.usecase

import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.model.Memory
import com.materialchat.domain.model.MemoryCandidate
import com.materialchat.domain.model.MemoryKind
import com.materialchat.domain.model.MemorySnippetCandidate
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.LocalModelRepository
import com.materialchat.domain.repository.MemoryRepository
import com.materialchat.domain.repository.ProviderRepository
import com.materialchat.domain.util.TaskModelAssignmentCodec
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class ExtractMemoriesUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository,
    private val localModelRepository: LocalModelRepository,
    private val providerRepository: ProviderRepository,
    private val appPreferences: AppPreferences,
    private val json: Json
) {
    suspend operator fun invoke(
        provider: Provider,
        model: String,
        conversationId: String,
        sourceMessageId: String,
        assistantMessageId: String? = null,
        userContent: String,
        assistantResponse: String,
        recentMessages: List<Message>
    ): List<Memory> {
        saveVerbatimSnippets(
            conversationId = conversationId,
            sourceMessageId = sourceMessageId,
            assistantMessageId = assistantMessageId,
            userContent = userContent,
            assistantResponse = assistantResponse
        )

        if (userContent.isBlank()) return emptyList()
        if (SENSITIVE_REGEX.containsMatchIn(userContent)) return emptyList()

        val explicitCandidates = explicitMemoryCandidates(
            conversationId = conversationId,
            sourceMessageId = sourceMessageId,
            userContent = userContent
        )

        val llmCandidates = if (shouldAttemptModelExtraction(userContent, assistantResponse)) {
            generateCandidatesWithModel(
                defaultProvider = provider,
                defaultModel = model,
                conversationId = conversationId,
                sourceMessageId = sourceMessageId,
                userContent = userContent,
                assistantResponse = assistantResponse,
                recentMessages = recentMessages
            ).getOrDefault(emptyList())
        } else {
            emptyList()
        }

        // Heuristics are a last resort only when the user did not explicitly ask to
        // remember something and the model extracted nothing. They are intentionally
        // conservative so casual chat does not pollute long-term memory.
        val fallbackCandidates = if (explicitCandidates.isEmpty() && llmCandidates.isEmpty()) {
            heuristicCandidates(
                conversationId = conversationId,
                sourceMessageId = sourceMessageId,
                userContent = userContent
            )
        } else {
            emptyList()
        }

        val candidates = (explicitCandidates + llmCandidates + fallbackCandidates)
            .mapNotNull { candidate ->
                val cleaned = sanitizeMemoryCandidate(candidate.content) ?: return@mapNotNull null
                if (!isDurableMemoryCandidate(cleaned, candidate.kind, candidate.confidence)) {
                    return@mapNotNull null
                }
                candidate.copy(content = cleaned)
            }
            .distinctBy { it.content.normalizedCandidateKey() }
            .take(MAX_EXTRACTED_MEMORIES)
        return memoryRepository.saveCandidates(candidates)
    }

    private suspend fun saveVerbatimSnippets(
        conversationId: String,
        sourceMessageId: String,
        assistantMessageId: String?,
        userContent: String,
        assistantResponse: String
    ) {
        val candidates = buildList {
            add(
                MemorySnippetCandidate(
                    conversationId = conversationId,
                    messageId = sourceMessageId,
                    role = MessageRole.USER,
                    content = userContent
                )
            )
            if (!assistantMessageId.isNullOrBlank()) {
                add(
                    MemorySnippetCandidate(
                        conversationId = conversationId,
                        messageId = assistantMessageId,
                        role = MessageRole.ASSISTANT,
                        content = assistantResponse
                    )
                )
            }
        }
        runCatching { memoryRepository.saveSnippets(candidates) }
    }

    private suspend fun generateCandidatesWithModel(
        defaultProvider: Provider,
        defaultModel: String,
        conversationId: String,
        sourceMessageId: String,
        userContent: String,
        assistantResponse: String,
        recentMessages: List<Message>
    ): Result<List<MemoryCandidate>> {
        val prompt = buildExtractionPrompt(userContent, assistantResponse, recentMessages)
        val response = generateWithPreferredLightweightModel(
            prompt = prompt,
            defaultProvider = defaultProvider,
            defaultModel = defaultModel
        )
        return response.mapCatching { raw ->
            parseExtractionResponse(raw).map { extracted ->
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

    private suspend fun generateWithPreferredLightweightModel(
        prompt: String,
        defaultProvider: Provider,
        defaultModel: String
    ): Result<String> {
        val memoryModelSetting = appPreferences.memoryExtractionModel.first()
        val useChatModel = TaskModelAssignmentCodec.isChatModel(memoryModelSetting)
        val hasExplicitMemoryModel = memoryModelSetting.isNotBlank() && !useChatModel
        if (!hasExplicitMemoryModel && !useChatModel && appPreferences.preferOnDeviceBackgroundTasks.first()) {
            val localModelId = localModelRepository.preferredTitleModelIdOrNull()
            if (localModelId != null) {
                val localResult = localModelRepository.generateSimpleCompletion(
                    modelId = localModelId,
                    prompt = prompt,
                    systemPrompt = MEMORY_EXTRACTION_SYSTEM_PROMPT
                )
                if (localResult.isSuccess) return localResult
            }
        }

        val (providerId, configuredModel) = TaskModelAssignmentCodec.decode(memoryModelSetting)
        val configuredProvider = providerId?.let { providerRepository.getProvider(it) }
        val provider = configuredProvider ?: defaultProvider
        val model = if (useChatModel) defaultModel else configuredModel.ifBlank { defaultModel }
        return chatRepository.generateSimpleCompletion(
            provider = provider,
            prompt = prompt,
            model = model,
            systemPrompt = MEMORY_EXTRACTION_SYSTEM_PROMPT,
            reasoningEffort = ReasoningEffort.NONE
        )
    }

    private fun buildExtractionPrompt(
        userContent: String,
        assistantResponse: String,
        recentMessages: List<Message>
    ): String {
        val recentContext = recentMessages
            .takeLast(4)
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

    private fun explicitMemoryCandidates(
        conversationId: String,
        sourceMessageId: String,
        userContent: String
    ): List<MemoryCandidate> {
        return EXPLICIT_MEMORY_REGEX.findAll(userContent)
            .mapNotNull { match ->
                val remembered = sanitizeExplicitMemory(match.groupValues.getOrNull(1).orEmpty())
                    ?: return@mapNotNull null
                MemoryCandidate(
                    content = remembered,
                    kind = classifyMemory(remembered),
                    confidence = 0.90f,
                    sourceConversationId = conversationId,
                    sourceMessageId = sourceMessageId
                )
            }
            .toList()
            .take(MAX_EXPLICIT_MEMORIES)
    }

    private fun heuristicCandidates(
        conversationId: String,
        sourceMessageId: String,
        userContent: String
    ): List<MemoryCandidate> {
        val compact = sanitizeForExtraction(userContent)
        if (compact.length !in 16..180) return emptyList()
        if (QUESTION_REGEX.containsMatchIn(compact)) return emptyList()
        if (TRANSIENT_REGEX.containsMatchIn(compact)) return emptyList()
        if (!FIRST_PERSON_REGEX.containsMatchIn(compact)) return emptyList()
        val kind = classifyMemory(compact).takeUnless { it == MemoryKind.OTHER } ?: return emptyList()
        // Require a strong first-person durable claim, not a one-off task request.
        if (!isDurableMemoryCandidate(compact, kind, 0.68f)) return emptyList()
        return listOf(
            MemoryCandidate(
                content = compact,
                kind = kind,
                confidence = 0.68f,
                sourceConversationId = conversationId,
                sourceMessageId = sourceMessageId
            )
        )
    }

    private fun sanitizeMemoryCandidate(content: String): String? {
        val compact = sanitizeForExtraction(content)
            .trim(' ', '.', '!', '?', ':', ';', '-', '*', '•')
        if (compact.length !in 8..220) return null
        if (QUESTION_REGEX.containsMatchIn(compact)) return null
        if (TRANSIENT_REGEX.containsMatchIn(compact)) return null
        return compact.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun isDurableMemoryCandidate(
        content: String,
        kind: MemoryKind,
        confidence: Float
    ): Boolean {
        if (confidence < 0.55f) return false
        if (kind == MemoryKind.OTHER && confidence < 0.8f) return false
        if (QUESTION_REGEX.containsMatchIn(content)) return false
        if (TRANSIENT_REGEX.containsMatchIn(content)) return false
        // Reject generic short phrases that are not facts/preferences.
        val tokenCount = content.split(Regex("\\s+")).count { it.any(Char::isLetterOrDigit) }
        if (tokenCount < 3) return false
        return when (kind) {
            MemoryKind.USER_PREFERENCE,
            MemoryKind.PERSONAL_FACT,
            MemoryKind.PROJECT_FACT,
            MemoryKind.LONG_TERM_GOAL,
            MemoryKind.INSTRUCTION,
            MemoryKind.RELATIONSHIP -> true
            MemoryKind.OTHER -> FIRST_PERSON_REGEX.containsMatchIn(content)
        }
    }

    private fun classifyMemory(content: String): MemoryKind {
        return when {
            PREFERENCE_REGEX.containsMatchIn(content) -> MemoryKind.USER_PREFERENCE
            INSTRUCTION_REGEX.containsMatchIn(content) -> MemoryKind.INSTRUCTION
            GOAL_REGEX.containsMatchIn(content) -> MemoryKind.LONG_TERM_GOAL
            PROJECT_REGEX.containsMatchIn(content) -> MemoryKind.PROJECT_FACT
            RELATIONSHIP_REGEX.containsMatchIn(content) -> MemoryKind.RELATIONSHIP
            PERSONAL_REGEX.containsMatchIn(content) -> MemoryKind.PERSONAL_FACT
            else -> MemoryKind.OTHER
        }
    }

    private fun shouldAttemptModelExtraction(userContent: String, assistantResponse: String): Boolean {
        if (assistantResponse.isBlank()) return false
        if (userContent.length < 18) return false
        if (userContent.length > MAX_USER_EXTRACTION_CHARS) return false
        // Skip pure troubleshooting / one-off questions — they almost never yield durable memory.
        if (QUESTION_REGEX.containsMatchIn(userContent) && !FIRST_PERSON_REGEX.containsMatchIn(userContent)) {
            return false
        }
        if (TRANSIENT_REGEX.containsMatchIn(userContent) && !FIRST_PERSON_REGEX.containsMatchIn(userContent)) {
            return false
        }
        return true
    }

    private fun sanitizeExplicitMemory(text: String): String? {
        val compact = sanitizeForExtraction(text)
            .replace(Regex("^(that|to)\\s+", RegexOption.IGNORE_CASE), "")
            .trim(' ', '.', '!', '?', ':', ';')
        if (compact.length !in 8..240) return null
        return compact.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun sanitizeForExtraction(text: String): String {
        return text
            .replace(Regex("```[\\s\\S]*?```"), "[code]")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun String.normalizedCandidateKey(): String {
        return lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
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
        const val MAX_CONTEXT_CHARS = 420
        const val MAX_LATEST_CHARS = 1200
        const val MAX_USER_EXTRACTION_CHARS = 5000
        const val MAX_EXTRACTED_MEMORIES = 5
        const val MAX_EXPLICIT_MEMORIES = 3

        const val MEMORY_EXTRACTION_SYSTEM_PROMPT = """
You are MaterialChat's private passive memory filter. Return strict JSON only:
{"memories":[{"content":"...","kind":"USER_PREFERENCE|PERSONAL_FACT|PROJECT_FACT|LONG_TERM_GOAL|INSTRUCTION|RELATIONSHIP|OTHER","confidence":0.0}]}

Use the cheapest/lightest reasoning possible.
Save ONLY durable, user-confirmed facts that will still matter weeks later:
- stable preferences ("I prefer dark mode")
- personal identity facts (name, role, city, devices)
- ongoing projects and long-term goals
- standing instructions for how the assistant should behave
- important relationships the user volunteers

NEVER save:
- secrets, credentials, API keys, tokens, passwords
- one-off debugging/troubleshooting chatter
- temporary task details, error logs, stack traces
- questions, greetings, or assistant claims
- speculative or low-confidence guesses
- anything that would be irrelevant in a different conversation tomorrow

If unsure, return {"memories":[]}. Prefer zero memories over noisy ones.
Use the user's wording when possible. Keep each memory under 180 characters.
        """

        val EXPLICIT_MEMORY_REGEX = Regex(
            "\\b(?:please\\s+)?(?:remember|keep in mind|note|save)(?:\\s+(?:that|this|to))?[:\\s]+([^.!?\\n]+(?:[.!?](?!\\s*(?:and|also)\\b))?)",
            RegexOption.IGNORE_CASE
        )
        val PREFERENCE_REGEX = Regex("\\b(i prefer|i like|i don't like|i hate|my preference|please always use|please don't)\\b", RegexOption.IGNORE_CASE)
        val INSTRUCTION_REGEX = Regex("\\b(always|never|remember to|from now on|call me|address me|use .* style)\\b", RegexOption.IGNORE_CASE)
        val GOAL_REGEX = Regex("\\b(i want to|i'm trying to|my goal|i plan to)\\b", RegexOption.IGNORE_CASE)
        val PROJECT_REGEX = Regex("\\b(my app|my project|we are building|i am building|i'm building|project is called|app is called)\\b", RegexOption.IGNORE_CASE)
        val RELATIONSHIP_REGEX = Regex("\\b(my wife|my husband|my partner|my girlfriend|my boyfriend|my gf|my bf|my spouse|my fiancee?|my fianc[eé]e?|my friend|my son|my daughter|my manager|my team)\\b", RegexOption.IGNORE_CASE)
        val PERSONAL_REGEX = Regex("\\b(my name is|i live|i work|i study|my role|my job|i am a|i'm a)\\b", RegexOption.IGNORE_CASE)
        val FIRST_PERSON_REGEX = Regex("\\b(i|i'm|i am|my|me|we|our)\\b", RegexOption.IGNORE_CASE)
        val QUESTION_REGEX = Regex("(^|\\s)(what|why|how|when|where|which|who|can you|could you|would you|should i|do you|is there|are there)\\b|\\?$", RegexOption.IGNORE_CASE)
        val TRANSIENT_REGEX = Regex("\\b(fix this|debug|crash|stack trace|error|exception|bug|compile|build failed|nullpointer|room migration|logcat|traceback|this time|right now|for now|temporarily)\\b", RegexOption.IGNORE_CASE)
        val SENSITIVE_REGEX = Regex("\\b(api key|password|secret|token|private key|credit card|ssn|social security)\\b", RegexOption.IGNORE_CASE)
    }
}
