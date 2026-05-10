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
        val context = messages
            .takeLast(6)
            .joinToString(separator = "\n") { message ->
                "${message.role.name.lowercase()}: ${message.content.take(MAX_CONTEXT_MESSAGE_CHARS)}"
            }
        val recalled = memoryRepository.recall(
            query = userContent,
            conversationContext = context,
            limit = limit
        )
        memoryRepository.markRecalled(recalled.map { it.memory.id })
        return recalled
    }

    private companion object {
        const val MAX_CONTEXT_MESSAGE_CHARS = 500
    }
}
