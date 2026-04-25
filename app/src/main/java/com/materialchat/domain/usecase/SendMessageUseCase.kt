package com.materialchat.domain.usecase

import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.di.ApplicationScope
import com.materialchat.domain.model.Attachment
import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.model.WebSearchConfig
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.repository.PersonaRepository
import com.materialchat.domain.repository.ProviderRepository
import com.materialchat.domain.repository.WebSearchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Use case for sending a message in a conversation and receiving a streaming response.
 *
 * This use case orchestrates:
 * 1. Adding the user message to the database (with optional image attachments)
 * 2. Creating a placeholder assistant message
 * 3. Sending the message to the AI provider
 * 4. Updating the assistant message with streaming content
 * 5. Finalizing the message when streaming completes
 * 6. Generating AI-powered conversation title (if enabled)
 */
class SendMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val conversationRepository: ConversationRepository,
    private val providerRepository: ProviderRepository,
    private val personaRepository: PersonaRepository,
    private val webSearchRepository: WebSearchRepository,
    private val appPreferences: AppPreferences,
    private val generateConversationTitleUseCase: GenerateConversationTitleUseCase,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    /**
     * Sends a message and returns a flow of streaming states.
     *
     * @param conversationId The ID of the conversation to send the message in
     * @param userContent The content of the user's message
     * @param attachments Optional list of image attachments to include with the message
     * @param systemPrompt The system prompt to use for the conversation
     * @param reasoningEffort The reasoning effort setting for compatible models
     * @param forceImageGeneration Whether to route this request directly to the default image model
     * @return A Flow of StreamingState representing the response progress
     */
    operator fun invoke(
        conversationId: String,
        userContent: String,
        attachments: List<Attachment> = emptyList(),
        systemPrompt: String,
        reasoningEffort: ReasoningEffort,
        webSearchConfig: WebSearchConfig = WebSearchConfig(),
        forceImageGeneration: Boolean = false,
        imageModelOverride: String? = null
    ): Flow<StreamingState> = flow {
        // Get the conversation and provider
        val conversation = conversationRepository.getConversation(conversationId)
            ?: throw IllegalStateException("Conversation not found: $conversationId")

        val provider = providerRepository.getProvider(conversation.providerId)
            ?: throw IllegalStateException("Provider not found: ${conversation.providerId}")

        // Resolve the effective system prompt: persona overrides global
        val effectiveSystemPrompt = if (conversation.personaId != null) {
            val persona = personaRepository.getPersonaById(conversation.personaId)
            persona?.systemPrompt ?: systemPrompt
        } else {
            systemPrompt
        }

        // Create and save the user message (with attachments if any)
        val userMessage = Message(
            conversationId = conversationId,
            role = MessageRole.USER,
            content = userContent,
            attachments = attachments
        )
        conversationRepository.addMessage(userMessage)

        val imagePrompt = resolveImageGenerationPrompt(
            userContent = userContent,
            forceImageGeneration = forceImageGeneration,
            attachments = attachments
        )
        if (imagePrompt != null) {
            val imageModel = imageModelOverride?.takeIf { it.isNotBlank() }
                ?: appPreferences.defaultImageGenerationModel.first()
                    .ifBlank { AppPreferences.DEFAULT_IMAGE_GENERATION_MODEL }
            val outputFormat = appPreferences.defaultImageOutputFormat.first()
            val assistantMessage = Message(
                conversationId = conversationId,
                role = MessageRole.ASSISTANT,
                content = "",
                isStreaming = true,
                modelName = imageModel
            )
            val assistantMessageId = conversationRepository.addMessage(assistantMessage)
            emit(StreamingState.Starting)

            val startedAt = System.currentTimeMillis()
            var finalized = false
            try {
                val result = chatRepository.generateImage(
                    provider = provider,
                    prompt = imagePrompt,
                    model = imageModel,
                    outputFormat = outputFormat
                )

                result.onSuccess { attachment ->
                    val totalDurationMs = System.currentTimeMillis() - startedAt
                    conversationRepository.updateMessage(
                        assistantMessage.copy(
                            id = assistantMessageId,
                            attachments = listOf(attachment),
                            isStreaming = false,
                            totalDurationMs = totalDurationMs,
                            modelName = imageModel
                        )
                    )
                    finalized = true
                    emit(
                        StreamingState.Completed(
                            finalContent = "Generated image",
                            finalThinkingContent = null,
                            messageId = assistantMessageId
                        )
                    )
                    updateConversationTitleIfNeeded(conversation, userContent, "Generated image")
                }.onFailure { error ->
                    conversationRepository.setMessageStreaming(assistantMessageId, false)
                    finalized = true
                    emit(
                        StreamingState.Error(
                            error = error,
                            partialContent = null,
                            messageId = assistantMessageId
                        )
                    )
                }
            } finally {
                if (!finalized) {
                    withContext(NonCancellable) {
                        runCatching { conversationRepository.setMessageStreaming(assistantMessageId, false) }
                    }
                }
            }
            return@flow
        }

        // Get all messages for the conversation to send to the AI
        // IMPORTANT: Get messages BEFORE adding the placeholder to avoid sending empty assistant message
        val messages = conversationRepository.getMessages(conversationId)

        val webSearchContext = resolveWebSearchPromptContext(
            basePrompt = effectiveSystemPrompt,
            messages = messages,
            webSearchConfig = webSearchConfig,
            webSearchRepository = webSearchRepository
        )
        val requestSystemPrompt = appendImageToolInstructions(
            basePrompt = webSearchContext.systemPrompt,
            modelName = conversation.modelName
        )

        // Create a placeholder assistant message for streaming (after getting messages for API)
        val assistantMessage = Message(
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true,
            modelName = conversation.modelName
        )
        val assistantMessageId = conversationRepository.addMessage(assistantMessage)

        // Emit starting state
        emit(StreamingState.Starting)

        var accumulatedContent = ""
        var accumulatedThinking: String? = null
        var hasError = false
        val streamStartTime = System.currentTimeMillis()
        var thinkingEndTime: Long? = null
        var firstContentAtMs: Long? = null
        val contentUpdater = StreamingMessageContentUpdater(
            conversationRepository = conversationRepository,
            messageId = assistantMessageId
        )

        // Stream the response from the chat repository
        chatRepository.sendMessage(
            provider = provider,
            messages = messages,
            model = conversation.modelName,
            reasoningEffort = reasoningEffort,
            systemPrompt = requestSystemPrompt,
            disableTools = webSearchContext.metadata != null
        ).onEach { state ->
            when (state) {
                is StreamingState.Streaming -> {
                    accumulatedContent = state.content
                    accumulatedThinking = state.thinkingContent
                    if (firstContentAtMs == null && state.content.isNotEmpty()) {
                        firstContentAtMs = System.currentTimeMillis()
                    }
                    // Track when thinking ends (first time we get content while thinking exists)
                    if (thinkingEndTime == null && state.content.isNotEmpty() && !state.thinkingContent.isNullOrEmpty()) {
                        thinkingEndTime = System.currentTimeMillis()
                        // Persist thinking duration immediately so UI shows "Thought for Xs" on collapse
                        val thinkingDurationMs = thinkingEndTime!! - streamStartTime
                        conversationRepository.updateMessageDurations(assistantMessageId, thinkingDurationMs, null)
                    }
                    // Coalesce Room writes so each token does not invalidate the whole chat list.
                    contentUpdater.onStreaming(accumulatedContent, accumulatedThinking)
                }
                is StreamingState.Error -> {
                    hasError = true
                    // Save partial content if available
                    state.partialContent?.let { content ->
                        contentUpdater.persistPartial(content)
                    } ?: contentUpdater.flush()
                    conversationRepository.setMessageStreaming(assistantMessageId, false)
                }
                is StreamingState.Cancelled -> {
                    // Save partial content if available
                    state.partialContent?.let { content ->
                        contentUpdater.persistPartial(content)
                    } ?: contentUpdater.flush()
                    conversationRepository.setMessageStreaming(assistantMessageId, false)
                }
                is StreamingState.Completed -> {
                    // Capture final content for title generation
                    val imageToolPrompt = extractImageToolPrompt(state.finalContent)
                    val totalDurationMs = System.currentTimeMillis() - streamStartTime
                    val thinkingDurationMs = if (!state.finalThinkingContent.isNullOrEmpty()) {
                        (thinkingEndTime ?: System.currentTimeMillis()) - streamStartTime
                    } else null

                    if (imageToolPrompt != null) {
                        accumulatedContent = "Generated image"
                        contentUpdater.flush()
                        val imageModel = appPreferences.defaultImageGenerationModel.first()
                            .ifBlank { AppPreferences.DEFAULT_IMAGE_GENERATION_MODEL }
                        val outputFormat = appPreferences.defaultImageOutputFormat.first()
                        val imageResult = chatRepository.generateImage(
                            provider = provider,
                            prompt = imageToolPrompt,
                            model = imageModel,
                            outputFormat = outputFormat
                        )
                        imageResult.onSuccess { attachment ->
                            conversationRepository.updateMessage(
                                assistantMessage.copy(
                                    id = assistantMessageId,
                                    content = "",
                                    attachments = listOf(attachment),
                                    isStreaming = false,
                                    totalDurationMs = totalDurationMs,
                                    modelName = imageModel
                                )
                            )
                        }.onFailure { error ->
                            conversationRepository.updateMessage(
                                assistantMessage.copy(
                                    id = assistantMessageId,
                                    content = "Image generation failed: ${error.message ?: "Unknown error"}",
                                    isStreaming = false,
                                    totalDurationMs = totalDurationMs,
                                    modelName = imageModel
                                )
                            )
                        }
                        conversationRepository.updateMessageDurations(assistantMessageId, thinkingDurationMs, totalDurationMs)
                    } else {
                        accumulatedContent = state.finalContent
                        contentUpdater.persistFinal(state.finalContent, state.finalThinkingContent)
                        val revealHoldMs = calculatePostCompletionRevealHoldMs(
                            content = state.finalContent,
                            revealStartedAtMs = firstContentAtMs,
                            completedAtMs = System.currentTimeMillis()
                        )
                        if (revealHoldMs > 0) {
                            delay(revealHoldMs)
                        }
                        conversationRepository.setMessageStreaming(assistantMessageId, false)
                        conversationRepository.updateMessageDurations(assistantMessageId, thinkingDurationMs, totalDurationMs)
                    }

                    // Save web search metadata on the assistant message
                    webSearchContext.metadata?.let { meta ->
                        conversationRepository.updateMessageWebSearchMetadata(
                            assistantMessageId,
                            Json.encodeToString(meta)
                        )
                    }
                }
                else -> { /* Ignore other states */ }
            }
        }.onCompletion { cause ->
            // Always mark streaming as complete, regardless of success, error, or cancellation.
            // This prevents messages from getting stuck with isStreaming=true forever
            // (e.g. when the coroutine is cancelled by navigation or user action).
            withContext(NonCancellable) {
                try {
                    contentUpdater.flush()
                    conversationRepository.setMessageStreaming(assistantMessageId, false)
                } catch (_: Exception) { }
            }
        }.collect { state ->
            // Re-emit with the correct message ID for the UI
            val mappedState = when (state) {
                is StreamingState.Streaming -> StreamingState.Streaming(
                    content = state.content,
                    thinkingContent = state.thinkingContent,
                    messageId = assistantMessageId
                )
                is StreamingState.Completed -> StreamingState.Completed(
                    finalContent = state.finalContent,
                    finalThinkingContent = state.finalThinkingContent,
                    messageId = assistantMessageId
                )
                is StreamingState.Error -> StreamingState.Error(
                    error = state.error,
                    partialContent = state.partialContent,
                    messageId = assistantMessageId
                )
                is StreamingState.Cancelled -> StreamingState.Cancelled(
                    partialContent = state.partialContent,
                    messageId = assistantMessageId
                )
                else -> state
            }
            emit(mappedState)
        }

        // Update conversation title if this is the first message
        updateConversationTitleIfNeeded(conversation, userContent, accumulatedContent)
    }

    /**
     * Updates the conversation title based on the first message exchange.
     * Uses AI-generated title if enabled, otherwise falls back to truncation.
     * For branches, uses a specialized prompt that focuses on what makes the branch different.
     */
    private suspend fun updateConversationTitleIfNeeded(
        conversation: Conversation,
        userContent: String,
        assistantResponse: String
    ) {
        // For new conversations: title is "New Chat"
        // For branches: title is "New Branch"
        val needsTitle = conversation.title == Conversation.generateDefaultTitle() ||
                         conversation.title == Conversation.generateDefaultBranchTitle()

        if (needsTitle) {
            // Check if AI-generated titles are enabled
            val aiTitlesEnabled = appPreferences.aiGeneratedTitlesEnabled.first()

            if (aiTitlesEnabled && assistantResponse.isNotBlank()) {
                // Launch non-blocking AI title generation in application scope
                applicationScope.launch {
                    if (conversation.isBranch) {
                        // For branches, use specialized title generation
                        generateBranchTitle(
                            conversationId = conversation.id,
                            userMessage = userContent,
                            assistantResponse = assistantResponse
                        )
                    } else {
                        generateConversationTitleUseCase(
                            conversationId = conversation.id,
                            userMessage = userContent,
                            assistantResponse = assistantResponse
                        )
                    }
                }
            } else {
                // Fall back to simple truncation
                val newTitle = generateTitleFromMessage(userContent)
                conversationRepository.updateConversationTitle(conversation.id, newTitle)
            }
        }
    }

    /**
     * Generates a specialized title for branch conversations.
     * The title focuses on what makes this branch different from the original.
     */
    private suspend fun generateBranchTitle(
        conversationId: String,
        userMessage: String,
        assistantResponse: String
    ) {
        try {
            val conversation = conversationRepository.getConversation(conversationId) ?: return

            val provider = providerRepository.getProvider(conversation.providerId) ?: return

            // Check if a custom model is configured for title generation
            val customModel = appPreferences.titleGenerationModel.first()
            val modelToUse = if (customModel.isNotBlank()) customModel else conversation.modelName

            val prompt = buildBranchTitlePrompt(userMessage, assistantResponse)

            val result = chatRepository.generateSimpleCompletion(
                provider = provider,
                prompt = prompt,
                model = modelToUse,
                systemPrompt = BRANCH_TITLE_SYSTEM_PROMPT
            )

            result.onSuccess { generatedResponse ->
                if (isGarbageTitleResponse(generatedResponse)) {
                    val fallbackTitle = generateTitleFromMessage(userMessage)
                    conversationRepository.updateConversationTitle(conversationId, fallbackTitle)
                } else {
                    val parsed = parseBranchTitleResponse(generatedResponse)
                    conversationRepository.updateConversationTitleAndIcon(
                        conversationId,
                        parsed.title,
                        parsed.icon
                    )
                }
            }.onFailure {
                // Fall back to simple truncation
                val fallbackTitle = generateTitleFromMessage(userMessage)
                conversationRepository.updateConversationTitle(conversationId, fallbackTitle)
            }
        } catch (e: Exception) {
            val fallbackTitle = generateTitleFromMessage(userMessage)
            try {
                conversationRepository.updateConversationTitle(conversationId, fallbackTitle)
            } catch (_: Exception) { }
        }
    }

    /**
     * Builds the prompt for generating a branch title.
     */
    private fun buildBranchTitlePrompt(userMessage: String, assistantResponse: String): String {
        val truncatedUser = sanitizeForTitlePrompt(userMessage.take(500))
        val truncatedAssistant = sanitizeForTitlePrompt(assistantResponse.take(500))

        return "Generate a single emoji and a concise title (max 6 words) for this conversation branch.\n\n" +
            "NEW USER MESSAGE: $truncatedUser\n\n" +
            "ASSISTANT REPLY: $truncatedAssistant"
    }

    /**
     * Parses the AI response to extract emoji and title for a branch.
     */
    private fun parseBranchTitleResponse(response: String): BranchTitleResult {
        val trimmed = response
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .replace(Regex("^(Response|Title):\\s*", RegexOption.IGNORE_CASE), "")
            .trim()

        // Regex to match emoji at the start
        val emojiPattern = Regex(
            "^[\\p{So}\\p{Cs}\\u200D\\uFE0F\\u2600-\\u26FF\\u2700-\\u27BF]+"
        )
        val emojiMatch = emojiPattern.find(trimmed)

        return if (emojiMatch != null && emojiMatch.range.first == 0) {
            val emoji = emojiMatch.value
            val titlePart = trimmed.substring(emojiMatch.range.last + 1).trim()
            val cleanedTitle = cleanBranchTitle(titlePart)
            BranchTitleResult(title = cleanedTitle, icon = emoji)
        } else {
            BranchTitleResult(title = cleanBranchTitle(trimmed), icon = null)
        }
    }

    private fun cleanBranchTitle(title: String): String {
        var cleaned = title
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .replace(Regex("^Title:\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[.!?]+$"), "")
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val words = cleaned.split(" ")
        if (words.size > 6) {
            cleaned = words.take(6).joinToString(" ")
        }

        if (cleaned.length > 60) {
            cleaned = cleaned.take(57) + "..."
        }

        return cleaned.ifBlank { "Branch" }
    }

    private fun appendImageToolInstructions(basePrompt: String, modelName: String): String {
        val model = modelName.lowercase()
        if (!model.contains("codex/")) return basePrompt
        return buildString {
            append(basePrompt)
            if (basePrompt.isNotBlank()) append("\n\n")
            append(CODEX_IMAGE_TOOL_INSTRUCTIONS)
        }
    }

    private fun extractImageToolPrompt(content: String): String? {
        return IMAGE_TOOL_DIRECTIVE_REGEX.find(content)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Resolves whether this request should be sent to the configured image model.
     * Explicit image actions always win; otherwise we route obvious create/draw/render
     * image prompts so any chat model can seamlessly hand off to image generation.
     */
    private fun calculatePostCompletionRevealHoldMs(
        content: String,
        revealStartedAtMs: Long?,
        completedAtMs: Long
    ): Long {
        if (content.isBlank()) return 0L
        val words = content.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        val estimatedRevealMs = (words * 30L).coerceIn(360L, 2400L)
        val elapsedRevealMs = revealStartedAtMs?.let { completedAtMs - it } ?: 0L
        return (estimatedRevealMs - elapsedRevealMs).coerceIn(0L, 1800L)
    }

    private fun resolveImageGenerationPrompt(
        userContent: String,
        forceImageGeneration: Boolean,
        attachments: List<Attachment>
    ): String? {
        val prompt = userContent.trim()
        if (prompt.isBlank()) return null

        val explicitCommand = IMAGE_COMMAND_REGEX.find(prompt)
        if (explicitCommand != null) {
            val commandPrompt = prompt.substring(explicitCommand.range.last + 1).trim()
            return commandPrompt.ifBlank { prompt }
        }

        if (forceImageGeneration) return prompt
        if (attachments.isNotEmpty()) return null

        return if (IMAGE_REQUEST_REGEX.containsMatchIn(prompt)) prompt else null
    }

    private data class BranchTitleResult(val title: String, val icon: String?)

    /** System instruction for branch title generation. */
    private companion object {
        const val BRANCH_TITLE_SYSTEM_PROMPT =
            "You are a title generator. You ONLY output a single emoji followed by a short title (max 6 words). " +
            "Never explain, never apologize, never refuse. No quotes, no punctuation at the end. " +
            "Focus on what makes this branch different. Example: \uD83D\uDD00 Alternative API approach"

        val IMAGE_TOOL_DIRECTIVE_REGEX = Regex(
            "\\[\\[materialchat\\.generate_image:\\s*([\\s\\S]*?)]]",
            RegexOption.IGNORE_CASE
        )
        const val CODEX_IMAGE_TOOL_INSTRUCTIONS = """
MaterialChat image generation tool:
- When the user asks you to create, draw, render, generate, or design an image, you may call the app's image generator.
- To call it, respond with exactly one directive and no surrounding prose: [[materialchat.generate_image: rewritten image prompt]]
- The rewritten image prompt should be detailed and self-contained.
- Do not use this directive for normal visual analysis or when the user only asks for advice.
"""

        val IMAGE_COMMAND_REGEX = Regex(
            "^\\s*(/image|/img|image:)\\s*",
            RegexOption.IGNORE_CASE
        )
        val IMAGE_REQUEST_REGEX = Regex(
            "\\b(generate|create|make|draw|paint|render|design|illustrate|visualize)\\b[\\s\\S]{0,120}\\b(image|picture|photo|illustration|artwork|poster|logo|wallpaper|avatar|icon|sticker)\\b|" +
                "\\b(image|picture|photo|illustration|artwork|poster|logo|wallpaper|avatar|icon|sticker)\\b[\\s\\S]{0,80}\\b(of|showing|depicting|for)\\b",
            RegexOption.IGNORE_CASE
        )

        val GARBAGE_PATTERNS = listOf(
            "i don't", "i can't", "i notice", "i apologize", "i'm sorry",
            "i am not", "i cannot", "as an ai", "i'm unable",
            "tool result", "tool call", "function call",
            "previous user", "no previous", "don't have",
            "let me", "sure,", "here is", "here's",
            "based on", "the conversation"
        )
    }

    /** Detects conversational / garbage responses that aren't real titles. */
    private fun isGarbageTitleResponse(response: String): Boolean {
        val lower = response.lowercase().trim()
        if (lower.length > 120) return true
        return GARBAGE_PATTERNS.any { lower.contains(it) }
    }

    /** Strips markdown artifacts, tool references, and noise from text for the title prompt. */
    private fun sanitizeForTitlePrompt(text: String): String {
        return text
            .replace(Regex("```[\\s\\S]*?```"), "[code]")
            .replace(Regex("\\[tool_result[\\s\\S]*?]"), "")
            .replace(Regex("\\[tool_call[\\s\\S]*?]"), "")
            .replace(Regex("<tool_result>[\\s\\S]*?</tool_result>"), "")
            .replace(Regex("<tool_call>[\\s\\S]*?</tool_call>"), "")
            .replace(Regex("!\\[.*?]\\(.*?\\)"), "")
            .replace(Regex("\\[.*?]\\(.*?\\)"), "")
            .replace(Regex("#{1,6}\\s+"), "")
            .replace(Regex("\\*{1,3}(.*?)\\*{1,3}"), "$1")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "(empty)" }
    }

    /**
     * Generates a title from the user's first message.
     * Truncates long messages and removes special characters.
     */
    private fun generateTitleFromMessage(content: String): String {
        val maxLength = 40
        val cleaned = content
            .replace("\n", " ")
            .replace(Regex("[\\r\\t]"), "")
            .trim()

        return if (cleaned.length <= maxLength) {
            cleaned
        } else {
            cleaned.take(maxLength - 3) + "..."
        }
    }

    /**
     * Cancels any ongoing streaming request.
     */
    fun cancel() {
        chatRepository.cancelStreaming()
    }
}
