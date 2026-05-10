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

        val fallbackCandidates = if (llmCandidates.isEmpty()) {
            heuristicCandidates(
                conversationId = conversationId,
                sourceMessageId = sourceMessageId,
                userContent = userContent
            )
        } else {
            emptyList()
        }

        val candidates = (explicitCandidates + llmCandidates + fallbackCandidates)
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
        val titleModelSetting = appPreferences.titleGenerationModel.first()
        val hasExplicitTitleModel = titleModelSetting.isNotBlank()
        if (!hasExplicitTitleModel && appPreferences.preferOnDeviceTitleModel.first()) {
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

        val (providerId, configuredModel) = parseTitleModelSetting(titleModelSetting)
        val configuredProvider = providerId?.let { providerRepository.getProvider(it) }
        val provider = configuredProvider ?: defaultProvider
        val model = configuredModel.ifBlank { defaultModel }
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
        if (compact.length !in 12..260) return emptyList()
        val kind = classifyMemory(compact).takeUnless { it == MemoryKind.OTHER } ?: return emptyList()
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
        if (userContent.length < 12) return false
        if (userContent.length > MAX_USER_EXTRACTION_CHARS) return false
        return true
    }

    private fun parseTitleModelSetting(raw: String): Pair<String?, String> {
        if (raw.isBlank()) return null to ""
        val pipe = raw.indexOf('|')
        if (pipe < 0) return null to raw.trim()
        val providerId = raw.substring(0, pipe).trim()
        val modelId = raw.substring(pipe + 1).trim()
        return providerId.ifBlank { null } to modelId
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

Use the cheapest/lightest reasoning possible. Save only durable, user-confirmed facts/preferences/goals/instructions that will likely matter in future chats.
Do not save secrets, credentials, API keys, private tokens, temporary debugging details, one-off questions, or assistant claims.
Use the user's wording when possible. Keep each memory under 220 characters. Return {"memories":[]} when nothing is worth saving.
        """

        val EXPLICIT_MEMORY_REGEX = Regex(
            "\\b(?:please\\s+)?(?:remember|keep in mind|note|save)(?:\\s+(?:that|this|to))?[:\\s]+([^.!?\\n]+(?:[.!?](?!\\s*(?:and|also)\\b))?)",
            RegexOption.IGNORE_CASE
        )
        val PREFERENCE_REGEX = Regex("\\b(i prefer|i like|i don't like|i hate|my preference|please use|please don't)\\b", RegexOption.IGNORE_CASE)
        val INSTRUCTION_REGEX = Regex("\\b(always|never|remember to|from now on|call me|address me|use .* style)\\b", RegexOption.IGNORE_CASE)
        val GOAL_REGEX = Regex("\\b(i want to|i'm trying to|my goal|we need to|i plan to)\\b", RegexOption.IGNORE_CASE)
        val PROJECT_REGEX = Regex("\\b(my app|my project|we are building|i am building|i'm building|project is called|app is called)\\b", RegexOption.IGNORE_CASE)
        val RELATIONSHIP_REGEX = Regex("\\b(my wife|my husband|my partner|my friend|my son|my daughter|my manager|my team)\\b", RegexOption.IGNORE_CASE)
        val PERSONAL_REGEX = Regex("\\b(my name is|i live|i work|i study|my role|my job|i am a|i'm a)\\b", RegexOption.IGNORE_CASE)
        val SENSITIVE_REGEX = Regex("\\b(api key|password|secret|token|private key|credit card|ssn|social security)\\b", RegexOption.IGNORE_CASE)
    }
}
