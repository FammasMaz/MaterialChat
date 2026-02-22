package com.materialchat.domain.usecase

import com.materialchat.domain.model.ArenaBattle
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.repository.ArenaRepository
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ProviderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Represents the combined streaming progress of both arena models.
 *
 * @property leftState The streaming state of the left model
 * @property rightState The streaming state of the right model
 * @property battleId The unique identifier of this battle
 */
data class ArenaBattleProgress(
    val leftState: StreamingState = StreamingState.Starting,
    val rightState: StreamingState = StreamingState.Starting,
    val battleId: String
)

/**
 * Use case for running an arena battle between two AI models.
 *
 * Sends the same prompt to both models in parallel using [coroutineScope] and
 * emits combined streaming progress as a single [Flow]. Once both streams complete,
 * the battle is persisted to the database.
 */
class RunArenaBattleUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val providerRepository: ProviderRepository,
    private val arenaRepository: ArenaRepository
) {
    /**
     * Runs a battle between two models with the given prompt.
     *
     * @param prompt The user prompt to send to both models
     * @param leftModelName The model name for the left panel
     * @param leftProviderId The provider ID for the left model
     * @param rightModelName The model name for the right panel
     * @param rightProviderId The provider ID for the right model
     * @param reasoningEffort The reasoning effort setting for compatible models
     * @return A Flow of [ArenaBattleProgress] representing both models' streaming states
     */
    operator fun invoke(
        prompt: String,
        leftModelName: String,
        leftProviderId: String,
        rightModelName: String,
        rightProviderId: String,
        reasoningEffort: ReasoningEffort = ReasoningEffort.HIGH
    ): Flow<ArenaBattleProgress> = channelFlow {
        val battleId = UUID.randomUUID().toString()

        val leftProvider = providerRepository.getProvider(leftProviderId)
            ?: throw IllegalStateException("Left provider not found: $leftProviderId")
        val rightProvider = providerRepository.getProvider(rightProviderId)
            ?: throw IllegalStateException("Right provider not found: $rightProviderId")

        val messages = listOf(
            Message(
                conversationId = "arena-$battleId",
                role = MessageRole.USER,
                content = prompt
            )
        )

        val leftStateFlow = MutableStateFlow<StreamingState>(StreamingState.Starting)
        val rightStateFlow = MutableStateFlow<StreamingState>(StreamingState.Starting)

        // Track final content for database persistence
        var leftFinalContent = ""
        var leftThinking: String? = null
        var leftDuration: Long? = null
        var rightFinalContent = ""
        var rightThinking: String? = null
        var rightDuration: Long? = null

        val leftStartTime = System.currentTimeMillis()
        val rightStartTime = System.currentTimeMillis()

        // Emit combined progress whenever either side updates
        val combineJob = launch {
            combine(leftStateFlow, rightStateFlow) { left, right ->
                ArenaBattleProgress(left, right, battleId)
            }.collect { send(it) }
        }

        // Stream left model response
        val leftJob = launch {
            try {
                chatRepository.sendMessage(
                    provider = leftProvider,
                    messages = messages,
                    model = leftModelName,
                    reasoningEffort = reasoningEffort
                ).collect { state ->
                    leftStateFlow.value = state
                    when (state) {
                        is StreamingState.Streaming -> {
                            leftFinalContent = state.content
                            leftThinking = state.thinkingContent
                        }
                        is StreamingState.Completed -> {
                            leftFinalContent = state.finalContent
                            leftThinking = state.finalThinkingContent
                            leftDuration = System.currentTimeMillis() - leftStartTime
                        }
                        is StreamingState.Error -> {
                            leftFinalContent = state.partialContent ?: ""
                            leftDuration = System.currentTimeMillis() - leftStartTime
                        }
                        else -> { /* Starting, Idle, Cancelled */ }
                    }
                }
            } catch (e: Exception) {
                leftFinalContent = "Error: ${e.message}"
                leftDuration = System.currentTimeMillis() - leftStartTime
                leftStateFlow.value = StreamingState.Error(error = e)
            }
        }

        // Stream right model response
        val rightJob = launch {
            try {
                chatRepository.sendMessage(
                    provider = rightProvider,
                    messages = messages,
                    model = rightModelName,
                    reasoningEffort = reasoningEffort
                ).collect { state ->
                    rightStateFlow.value = state
                    when (state) {
                        is StreamingState.Streaming -> {
                            rightFinalContent = state.content
                            rightThinking = state.thinkingContent
                        }
                        is StreamingState.Completed -> {
                            rightFinalContent = state.finalContent
                            rightThinking = state.finalThinkingContent
                            rightDuration = System.currentTimeMillis() - rightStartTime
                        }
                        is StreamingState.Error -> {
                            rightFinalContent = state.partialContent ?: ""
                            rightDuration = System.currentTimeMillis() - rightStartTime
                        }
                        else -> { /* Starting, Idle, Cancelled */ }
                    }
                }
            } catch (e: Exception) {
                rightFinalContent = "Error: ${e.message}"
                rightDuration = System.currentTimeMillis() - rightStartTime
                rightStateFlow.value = StreamingState.Error(error = e)
            }
        }

        // Wait for both streams to complete
        leftJob.join()
        rightJob.join()

        // Persist the battle to the database
        val battle = ArenaBattle(
            id = battleId,
            prompt = prompt,
            leftModelName = leftModelName,
            leftProviderId = leftProviderId,
            leftResponse = leftFinalContent,
            rightModelName = rightModelName,
            rightProviderId = rightProviderId,
            rightResponse = rightFinalContent,
            leftThinkingContent = leftThinking,
            rightThinkingContent = rightThinking,
            leftDurationMs = leftDuration,
            rightDurationMs = rightDuration,
            createdAt = System.currentTimeMillis()
        )
        arenaRepository.insertBattle(battle)

        combineJob.cancel()
    }
}
