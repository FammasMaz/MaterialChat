package com.materialchat.domain.usecase

import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.model.FusionConfig
import com.materialchat.domain.model.FusionIndividualResponse
import com.materialchat.domain.model.FusionMetadata
import com.materialchat.domain.model.FusionResult
import com.materialchat.domain.model.FusionSource
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.repository.ProviderRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Use case for running Response Fusion (Multi-Model Synthesis).
 *
 * Phase 1: Sends the user prompt to multiple models in parallel, streaming each
 *          response in real-time and emitting updates as tokens arrive.
 * Phase 2: Sends all individual responses to a judge model for synthesis into
 *          one best answer, streaming the synthesis in real-time.
 *
 * Uses [channelFlow] to support concurrent emissions from parallel model streams.
 */
class RunFusionUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val providerRepository: ProviderRepository,
    private val conversationRepository: ConversationRepository,
    private val appPreferences: AppPreferences
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Executes the fusion pipeline with real-time per-model streaming.
     *
     * @param conversationId The conversation to add the fused message to
     * @param userContent The user's prompt
     * @param fusionConfig The fusion configuration with selected models and judge
     * @param systemPrompt The system prompt to use
     * @param reasoningEffort The reasoning effort setting
     * @return A Flow emitting FusionResult updates in real-time as models stream
     */
    operator fun invoke(
        conversationId: String,
        userContent: String,
        fusionConfig: FusionConfig,
        systemPrompt: String,
        reasoningEffort: ReasoningEffort
    ): Flow<FusionResult> = channelFlow {
        // Get conversation
        val conversation = conversationRepository.getConversation(conversationId)
            ?: throw IllegalStateException("Conversation not found: $conversationId")

        // Save user message
        val userMessage = Message(
            conversationId = conversationId,
            role = MessageRole.USER,
            content = userContent
        )
        conversationRepository.addMessage(userMessage)

        // Get conversation history (before adding placeholder)
        val messages = conversationRepository.getMessages(conversationId)

        // Create placeholder assistant message for the fused response
        val assistantMessage = Message(
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true,
            modelName = fusionConfig.judgeModel?.modelName ?: "fusion"
        )
        val assistantMessageId = conversationRepository.addMessage(assistantMessage)

        // Phase 1: Query all models in parallel with real-time streaming
        val modelResponses = ConcurrentHashMap<String, FusionIndividualResponse>()

        // Initialize all models as waiting
        fusionConfig.selectedModels.forEach { modelSelection ->
            modelResponses[modelSelection.modelName] = FusionIndividualResponse(
                modelName = modelSelection.modelName,
                providerId = modelSelection.providerId,
                content = "",
                isStreaming = true
            )
        }
        send(FusionResult(individualResponses = modelResponses.values.toList()))

        // Launch parallel streams — coroutineScope waits for all to complete
        coroutineScope {
            fusionConfig.selectedModels.forEach { modelSelection ->
                launch {
                    val startTime = System.currentTimeMillis()
                    val provider = providerRepository.getProvider(modelSelection.providerId)
                        ?: throw IllegalStateException("Provider not found: ${modelSelection.providerId}")

                    chatRepository.sendMessage(
                        provider = provider,
                        messages = messages,
                        model = modelSelection.modelName,
                        reasoningEffort = reasoningEffort,
                        systemPrompt = systemPrompt
                    ).collect { state ->
                        when (state) {
                            is StreamingState.Streaming -> {
                                modelResponses[modelSelection.modelName] = FusionIndividualResponse(
                                    modelName = modelSelection.modelName,
                                    providerId = modelSelection.providerId,
                                    content = state.content,
                                    isStreaming = true
                                )
                                send(FusionResult(
                                    individualResponses = modelResponses.values.toList(),
                                    isSynthesizing = false
                                ))
                            }
                            is StreamingState.Completed -> {
                                val durationMs = System.currentTimeMillis() - startTime
                                modelResponses[modelSelection.modelName] = FusionIndividualResponse(
                                    modelName = modelSelection.modelName,
                                    providerId = modelSelection.providerId,
                                    content = state.finalContent,
                                    isStreaming = false,
                                    durationMs = durationMs
                                )
                                send(FusionResult(
                                    individualResponses = modelResponses.values.toList(),
                                    isSynthesizing = false
                                ))
                            }
                            is StreamingState.Error -> {
                                val durationMs = System.currentTimeMillis() - startTime
                                modelResponses[modelSelection.modelName] = FusionIndividualResponse(
                                    modelName = modelSelection.modelName,
                                    providerId = modelSelection.providerId,
                                    content = state.partialContent ?: "Error: ${state.error.message}",
                                    isStreaming = false,
                                    durationMs = durationMs
                                )
                                send(FusionResult(
                                    individualResponses = modelResponses.values.toList(),
                                    isSynthesizing = false
                                ))
                            }
                            else -> { /* Starting — ignore */ }
                        }
                    }
                }
            }
        }

        // Phase 2: Synthesize using judge model
        send(FusionResult(
            individualResponses = modelResponses.values.toList(),
            isSynthesizing = true
        ))

        val judgeModel = fusionConfig.judgeModel
            ?: fusionConfig.selectedModels.first()

        val judgeProvider = providerRepository.getProvider(judgeModel.providerId)
            ?: throw IllegalStateException("Judge provider not found: ${judgeModel.providerId}")

        // Build synthesis prompt
        val synthesisPrompt = buildSynthesisPrompt(userContent, modelResponses.values.toList())

        val synthesisMessages = listOf(
            Message(
                conversationId = conversationId,
                role = MessageRole.USER,
                content = synthesisPrompt
            )
        )

        var synthesizedContent = ""
        val synthStartTime = System.currentTimeMillis()

        chatRepository.sendMessage(
            provider = judgeProvider,
            messages = synthesisMessages,
            model = judgeModel.modelName,
            reasoningEffort = reasoningEffort,
            systemPrompt = "You are an expert synthesizer. Your job is to combine multiple AI responses into one optimal answer."
        ).collect { state ->
            when (state) {
                is StreamingState.Streaming -> {
                    synthesizedContent = state.content
                    send(FusionResult(
                        individualResponses = modelResponses.values.toList(),
                        synthesizedResponse = synthesizedContent,
                        isSynthesizing = true
                    ))
                }
                is StreamingState.Completed -> {
                    synthesizedContent = state.finalContent
                }
                is StreamingState.Error -> {
                    synthesizedContent = state.partialContent
                        ?: "Synthesis failed: ${state.error.message}"
                }
                else -> { /* ignore */ }
            }
        }

        val totalDurationMs = System.currentTimeMillis() - synthStartTime

        // Build fusion metadata
        val fusionMetadata = FusionMetadata(
            sources = modelResponses.values.map { response ->
                FusionSource(
                    modelName = response.modelName,
                    content = response.content,
                    durationMs = response.durationMs
                )
            },
            judgeModel = judgeModel.modelName
        )

        val metadataJson = json.encodeToString(fusionMetadata)

        // Update the assistant message with the synthesized content and metadata
        conversationRepository.updateMessageContent(assistantMessageId, synthesizedContent)
        conversationRepository.setMessageStreaming(assistantMessageId, false)
        conversationRepository.updateMessageDurations(assistantMessageId, null, totalDurationMs)
        conversationRepository.updateMessageFusionMetadata(assistantMessageId, metadataJson)

        // Emit final result
        send(FusionResult(
            individualResponses = modelResponses.values.toList(),
            synthesizedResponse = synthesizedContent,
            isSynthesizing = false
        ))
    }

    /**
     * Builds the synthesis prompt that instructs the judge model to combine responses.
     */
    private fun buildSynthesisPrompt(
        userPrompt: String,
        responses: List<FusionIndividualResponse>
    ): String {
        val responsesText = responses.mapIndexed { index, response ->
            "--- Response from ${response.modelName} ---\n${response.content}\n"
        }.joinToString("\n")

        return """You are an expert synthesizer. Given these responses to the same prompt from different AI models, create the best possible answer by combining the strengths of each response. Take the most accurate, complete, and well-explained parts from each.

Original user prompt: $userPrompt

$responsesText

Create a single, comprehensive response that represents the best synthesis of all the above responses. Do not mention the individual models or that this is a synthesis - just provide the best answer directly."""
    }
}
