package com.materialchat.domain.usecase

import com.materialchat.domain.model.Message
import com.materialchat.domain.model.RecalledMemory
import com.materialchat.domain.repository.MemoryRepository
import javax.inject.Inject

class RecallMemoriesUseCase @Inject constructor(
    private val memoryRepository: MemoryRepository
) {
    suspend operator fun invoke(
        userContent: String,
        messages: List<Message>,
        limit: Int = 5
    ): List<RecalledMemory> {
        val recalled = memoryRepository.recall(
            query = userContent,
            conversationContext = "",
            limit = limit.coerceAtMost(MAX_RECALLED_MEMORIES)
        )
        memoryRepository.markRecalled(recalled.map { it.memory.id })
        return recalled
    }

    private companion object {
        const val MAX_RECALLED_MEMORIES = 3
    }
}
