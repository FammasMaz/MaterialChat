package com.materialchat.domain.usecase

import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.RecalledMemory
import com.materialchat.domain.model.RecalledMemorySource
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
            conversationContext = buildRecallContext(messages, userContent),
            limit = limit.coerceAtMost(MAX_RECALLED_MEMORIES)
        )
        memoryRepository.markRecalled(
            recalled
                .filter { it.source == RecalledMemorySource.EXTRACTED_MEMORY }
                .map { it.memory.id }
        )
        memoryRepository.markSnippetsRecalled(
            recalled
                .filter { it.source == RecalledMemorySource.VERBATIM_SNIPPET }
                .map { it.memory.id }
        )
        return recalled
    }

    private fun buildRecallContext(messages: List<Message>, userContent: String): String {
        val current = userContent.trim()
        return messages
            .asReversed()
            .asSequence()
            .filter { it.role == MessageRole.USER }
            .map { it.content.trim() }
            .filter { it.isNotBlank() && it != current }
            .take(MAX_CONTEXT_MESSAGES)
            .joinToString(separator = "\n") { it.take(MAX_CONTEXT_MESSAGE_CHARS) }
    }

    private companion object {
        const val MAX_RECALLED_MEMORIES = 3
        const val MAX_CONTEXT_MESSAGES = 3
        const val MAX_CONTEXT_MESSAGE_CHARS = 360
    }
}
