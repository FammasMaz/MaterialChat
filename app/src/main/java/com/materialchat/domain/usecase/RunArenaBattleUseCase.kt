package com.materialchat.domain.usecase

import com.materialchat.domain.model.ArenaBattle
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.ContenderResult
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.repository.ArenaRepository
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ProviderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * One entrant in an N-model arena battle.
 */
data class ArenaContenderSpec(
    val providerId: String,
    val modelName: String
)



/**
 * Streaming progress across all arena contenders, indexed by slot.
 *
 * Slots are intentionally anonymous: UI must not attach model names while a
 * battle is running — identities are revealed only after voting.
 */
data class ArenaBattleProgress(
    val states: List<StreamingState>,
    val battleId: String,
    /** Set once every contender has finished (completed or errored). */
    val allFinished: Boolean = false
)

/**
 * Runs an arena battle between any number of models (>= 2).
 *
 * All streams run in parallel against the same prompt; progress is emitted as
 * a list indexed by slot. When every stream finishes, the battle and its
 * per-slot results are persisted. The first two slots are mirrored into the
 * legacy left/right battle columns so older leaderboards/history stay valid.
 */
class RunArenaBattleUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val providerRepository: ProviderRepository,
    private val arenaRepository: ArenaRepository
) {
    operator fun invoke(
        prompt: String,
        contenders: List<ArenaContenderSpec>,
        reasoningEffort: ReasoningEffort = ReasoningEffort.HIGH
    ): Flow<ArenaBattleProgress> = channelFlow {
        require(contenders.size >= 2) { "Arena needs at least two contenders" }
        val battleId = UUID.randomUUID().toString()

        val messages = listOf(
            Message(
                conversationId = "arena-$battleId",
                role = MessageRole.USER,
                content = prompt
            )
        )

        val states = MutableList(contenders.size) { StreamingState.Starting as StreamingState }
        val statesFlow = MutableStateFlow(states.toList())
        val finalContent = Array(contenders.size) { "" }
        val finalThinking = arrayOfNulls<String?>(contenders.size)
        val durations = arrayOfNulls<Long>(contenders.size)

        suspend fun emit() = send(ArenaBattleProgress(statesFlow.value, battleId))

        emit()

        // Persist the shell battle up-front (legacy columns mirror slots 0/1).
        val now = System.currentTimeMillis()
        arenaRepository.insertBattle(
            ArenaBattle(
                id = battleId,
                prompt = prompt,
                leftModelName = contenders[0].modelName,
                leftProviderId = contenders[0].providerId,
                leftResponse = "",
                rightModelName = contenders[1].modelName,
                rightProviderId = contenders[1].providerId,
                rightResponse = "",
                createdAt = now
            )
        )
        arenaRepository.replaceContenders(
            battleId = battleId,
            contenders = contenders.mapIndexed { index, spec ->
                ContenderResult(
                    slot = index,
                    modelName = spec.modelName,
                    providerId = spec.providerId,
                    response = "",
                    thinkingContent = null,
                    durationMs = null
                )
            }
        )

        val startTime = System.currentTimeMillis()
        val jobs = contenders.mapIndexed { slot, spec ->
            launch {
                val provider = providerRepository.getProvider(spec.providerId)
                if (provider == null) {
                    states[slot] = StreamingState.Error(
                        error = IllegalStateException("Provider ${spec.providerId} not found")
                    )
                    statesFlow.value = states.toList()
                    emit()
                    return@launch
                }
                try {
                    chatRepository.sendMessage(
                        provider = provider,
                        messages = messages,
                        model = spec.modelName,
                        reasoningEffort = reasoningEffort
                    ).collect { state ->
                        states[slot] = state
                        when (state) {
                            is StreamingState.Streaming -> {
                                finalContent[slot] = state.content
                                finalThinking[slot] = state.thinkingContent
                            }
                            is StreamingState.Completed -> {
                                finalContent[slot] = state.finalContent
                                finalThinking[slot] = state.finalThinkingContent
                                durations[slot] = System.currentTimeMillis() - startTime
                            }
                            is StreamingState.Error -> {
                                finalContent[slot] =
                                    state.partialContent ?: "Error: ${state.error.message}"
                                durations[slot] = System.currentTimeMillis() - startTime
                            }
                            else -> Unit
                        }
                        statesFlow.value = states.toList()
                        emit()
                    }
                } catch (e: Exception) {
                    finalContent[slot] = "Error: ${e.message}"
                    durations[slot] = System.currentTimeMillis() - startTime
                    states[slot] = StreamingState.Error(error = e)
                    statesFlow.value = states.toList()
                    emit()
                }

                // Persist this contender's result as soon as it settles.
                arenaRepository.updateContenderResult(
                    battleId = battleId,
                    slot = slot,
                    response = finalContent[slot],
                    thinkingContent = finalThinking[slot],
                    durationMs = durations[slot]
                )
            }
        }

        jobs.joinAll()

        // Mirror first two results into legacy columns for history compatibility.
        arenaRepository.getBattle(battleId)?.let { battle ->
            arenaRepository.updateBattle(
                battle.copy(
                    leftResponse = finalContent.getOrElse(0) { "" },
                    leftThinkingContent = finalThinking.getOrNull(0),
                    leftDurationMs = durations.getOrNull(0),
                    rightResponse = finalContent.getOrElse(1) { "" },
                    rightThinkingContent = finalThinking.getOrNull(1),
                    rightDurationMs = durations.getOrNull(1)
                )
            )
        }

        send(ArenaBattleProgress(statesFlow.value, battleId, allFinished = true))
    }
}
