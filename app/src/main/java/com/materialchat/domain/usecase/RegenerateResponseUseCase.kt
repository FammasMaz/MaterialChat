package com.materialchat.domain.usecase

import android.content.Context
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.model.WebSearchConfig
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.repository.PersonaRepository
import com.materialchat.domain.repository.ProviderRepository
import com.materialchat.domain.repository.WebSearchRepository
import com.materialchat.notifications.ImageGenerationForegroundService
import com.materialchat.ui.screens.chat.ContextWindowEstimator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Use case for regenerating the last AI response in a conversation.
 *
 * This use case orchestrates:
 * 1. Keeping the saved answer until its replacement succeeds
 * 2. Creating a separate placeholder for the retry
 * 3. Re-sending the conversation without the old answer
 * 4. Persisting the replacement before removing the old answer
 */
class RegenerateResponseUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val conversationRepository: ConversationRepository,
    private val providerRepository: ProviderRepository,
    private val personaRepository: PersonaRepository,
    private val webSearchRepository: WebSearchRepository,
    @ApplicationContext private val context: Context
) {
    /**
     * Regenerates the last response in a conversation.
     *
     * @param conversationId The ID of the conversation to regenerate the response for
     * @param systemPrompt The system prompt to use for the conversation
     * @param reasoningEffort The reasoning effort setting for compatible models
     * @return A Flow of StreamingState representing the response progress
     */
    operator fun invoke(
        conversationId: String,
        systemPrompt: String,
        reasoningEffort: ReasoningEffort,
        overrideModelName: String? = null,
        webSearchConfig: WebSearchConfig = WebSearchConfig()
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

        // Get current messages
        val messages = conversationRepository.getMessages(conversationId)
        if (messages.isEmpty()) {
            emit(StreamingState.Error(IllegalStateException("No messages to regenerate")))
            return@flow
        }

        // Exclude the answer from the request, not from storage. A failed retry
        // must not destroy the saved text, attachments, or generation metadata.
        val previousAnswer = messages.last().takeIf { it.role == MessageRole.ASSISTANT }
        val updatedMessages = if (previousAnswer != null) messages.dropLast(1) else messages

        val webSearchContext = resolveWebSearchPromptContext(
            basePrompt = effectiveSystemPrompt,
            messages = updatedMessages,
            webSearchConfig = webSearchConfig,
            webSearchRepository = webSearchRepository
        )

        // Determine model to use (override for redo-with-model, otherwise conversation default)
        val modelToUse = overrideModelName ?: conversation.modelName

        // Create a new placeholder assistant message for streaming
        val assistantMessage = Message(
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true,
            modelName = modelToUse
        )
        val assistantMessageId = conversationRepository.addMessage(assistantMessage)
        var hasTerminalState = false
        var hasPartialResponse = false
        val streamStartTime = System.currentTimeMillis()
        var thinkingEndTime: Long? = null
        // First token of ANY kind (thinking deltas included) starts the TTFT clock,
        // matching SendMessageUseCase so redo'd responses report speed stats too.
        var firstContentAtMs: Long? = null
        var accumulatedThinking: String? = null
        val contentUpdater = StreamingMessageContentUpdater(
            conversationRepository = conversationRepository,
            messageId = assistantMessageId
        )

        try {
            webSearchContext.metadata?.let { metadata ->
                conversationRepository.updateMessageWebSearchMetadata(
                    assistantMessageId,
                    Json.encodeToString(metadata)
                )
            }
            emit(StreamingState.Starting)
            ImageGenerationForegroundService.startChat(context, modelToUse)

            chatRepository.sendMessage(
                provider = provider,
                messages = updatedMessages,
                model = modelToUse,
                reasoningEffort = reasoningEffort,
                systemPrompt = webSearchContext.systemPrompt,
                disableTools = webSearchContext.metadata != null,
                nativeWebSearch = webSearchContext.nativeWebSearchEnabled,
                ownerId = conversationId
            ).collect { state ->
                // Some providers emit more than one terminal event. Never apply
                // tokens or a second completion after the request has finished.
                if (hasTerminalState) return@collect

                var emittedState: StreamingState = state
                when (state) {
                    is StreamingState.Streaming -> {
                        hasPartialResponse = state.content.isNotBlank() ||
                            !state.thinkingContent.isNullOrBlank()
                        // Coalesce Room writes so each token does not invalidate the whole chat list.
                        contentUpdater.onStreaming(state.content, state.thinkingContent)
                        if (firstContentAtMs == null &&
                            (state.content.isNotEmpty() || !state.thinkingContent.isNullOrEmpty())
                        ) {
                            firstContentAtMs = System.currentTimeMillis()
                        }
                        if (!state.thinkingContent.isNullOrEmpty()) {
                            accumulatedThinking = state.thinkingContent
                        }
                        // Track when thinking ends
                        if (thinkingEndTime == null &&
                            state.content.isNotEmpty() &&
                            !state.thinkingContent.isNullOrEmpty()
                        ) {
                            thinkingEndTime = System.currentTimeMillis()
                            // Persist thinking duration immediately so UI shows "Thought for Xs" on collapse
                            conversationRepository.updateMessageDurations(
                                assistantMessageId,
                                thinkingEndTime!! - streamStartTime,
                                null
                            )
                        }
                    }
                    is StreamingState.Error -> {
                        hasTerminalState = true
                        state.partialContent?.takeIf { it.isNotBlank() }?.let { content ->
                            hasPartialResponse = true
                            contentUpdater.persistPartial(content)
                        } ?: contentUpdater.flush()
                    }
                    is StreamingState.Cancelled -> {
                        hasTerminalState = true
                        state.partialContent?.takeIf { it.isNotBlank() }?.let { content ->
                            hasPartialResponse = true
                            contentUpdater.persistPartial(content)
                        } ?: contentUpdater.flush()
                    }
                    is StreamingState.Completed -> {
                        val completedContent = if (webSearchContext.nativeWebSearchEnabled) {
                            val query = updatedMessages
                                .lastOrNull { it.role == MessageRole.USER }
                                ?.content
                                .orEmpty()
                            val parsed = extractNativeWebSearchSources(
                                content = state.finalContent,
                                query = query
                            )
                            parsed.metadata?.let { metadata ->
                                conversationRepository.updateMessageWebSearchMetadata(
                                    assistantMessageId,
                                    Json.encodeToString(metadata)
                                )
                            }
                            parsed.content
                        } else {
                            state.finalContent
                        }
                        hasTerminalState = true
                        if (completedContent.isBlank()) {
                            // An empty completion is a failure: keep the saved answer.
                            if (!state.finalThinkingContent.isNullOrBlank()) {
                                hasPartialResponse = true
                                contentUpdater.persistFinal(completedContent, state.finalThinkingContent)
                            }
                            emittedState = StreamingState.Error(
                                error = IllegalStateException("The provider returned an empty response"),
                                messageId = assistantMessageId
                            )
                        } else {
                            contentUpdater.persistFinal(completedContent, state.finalThinkingContent)
                            hasPartialResponse = true
                            conversationRepository.setMessageStreaming(assistantMessageId, false)

                            // Save duration data
                            val totalDurationMs = System.currentTimeMillis() - streamStartTime
                            val thinkingDurationMs = if (!state.finalThinkingContent.isNullOrEmpty()) {
                                (thinkingEndTime ?: System.currentTimeMillis()) - streamStartTime
                            } else null
                            conversationRepository.updateMessageDurations(
                                assistantMessageId,
                                thinkingDurationMs,
                                totalDurationMs
                            )

                            // Persist generation metrics for the speed pill (same contract as
                            // SendMessageUseCase so regenerated responses report TPS/TTFT too).
                            val ttftMs = firstContentAtMs?.let { it - streamStartTime }
                            val tokens = ContextWindowEstimator.estimateTokens(completedContent) +
                                ContextWindowEstimator.estimateTokens(accumulatedThinking ?: "")
                            conversationRepository.updateGenerationMetrics(
                                messageId = assistantMessageId,
                                firstTokenLatencyMs = ttftMs,
                                generatedTokenCount = if (tokens > 0) tokens else null
                            )
                            // The replacement is durable before the old row (and its
                            // unshared files) can be removed. Never delete on errors.
                            previousAnswer?.let { conversationRepository.deleteMessage(it.id) }
                            emittedState = state.copy(finalContent = completedContent)
                        }
                    }
                    else -> { /* Ignore other states */ }
                }

                // Re-emit with the correct message ID for the UI.
                emit(
                    when (val mapped = emittedState) {
                        is StreamingState.Streaming -> StreamingState.Streaming(
                            content = mapped.content,
                            thinkingContent = mapped.thinkingContent,
                            messageId = assistantMessageId
                        )
                        is StreamingState.Completed -> StreamingState.Completed(
                            finalContent = mapped.finalContent,
                            finalThinkingContent = mapped.finalThinkingContent,
                            messageId = assistantMessageId
                        )
                        is StreamingState.Error -> StreamingState.Error(
                            error = mapped.error,
                            partialContent = mapped.partialContent,
                            messageId = assistantMessageId
                        )
                        is StreamingState.Cancelled -> StreamingState.Cancelled(
                            partialContent = mapped.partialContent,
                            messageId = assistantMessageId
                        )
                        else -> mapped
                    }
                )
            }
            if (!hasTerminalState) {
                emit(
                    StreamingState.Error(
                        error = IllegalStateException(
                            "The provider ended the stream without completing the response"
                        ),
                        messageId = assistantMessageId
                    )
                )
            }
        } finally {
            withContext(NonCancellable) {
                try {
                    contentUpdater.flush()
                    if (hasPartialResponse) {
                        conversationRepository.setMessageStreaming(assistantMessageId, false)
                    } else {
                        conversationRepository.deleteMessage(assistantMessageId)
                    }
                } finally {
                    ImageGenerationForegroundService.stop(context)
                }
            }
        }
    }

    /**
     * Cancels any ongoing streaming request.
     */
    fun cancel(conversationId: String) {
        chatRepository.cancelStreaming(conversationId)
    }
}
