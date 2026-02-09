package com.materialchat.domain.usecase

import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.repository.ConversationRepository
import javax.inject.Inject

/**
 * Use case for redoing an assistant response with a different model.
 *
 * Orchestrates:
 * 1. Finding the user message before the target assistant message
 * 2. Branching the conversation up to that user message with the new model
 * 3. Returning the new conversation ID (caller navigates and auto-sends)
 *
 * Sibling logic: if the source conversation already has a branchSourceMessageId,
 * inherit it so redo-from-redo creates siblings (not nested branches).
 * Otherwise, use the user message ID as the new branchSourceMessageId.
 */
class RedoWithModelUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val branchConversationUseCase: BranchConversationUseCase
) {
    /**
     * Creates a branch with a different model from the target assistant message.
     *
     * @param conversationId The current conversation ID
     * @param targetAssistantMessageId The assistant message to redo
     * @param newModelName The model to use for the new response
     * @return The ID of the newly created branched conversation
     */
    suspend operator fun invoke(
        conversationId: String,
        targetAssistantMessageId: String,
        newModelName: String
    ): String {
        val conversation = conversationRepository.getConversation(conversationId)
            ?: throw IllegalStateException("Conversation not found: $conversationId")

        val messages = conversationRepository.getMessages(conversationId)

        // Find the target assistant message index
        val assistantIndex = messages.indexOfFirst { it.id == targetAssistantMessageId }
        if (assistantIndex == -1) {
            throw IllegalStateException("Target assistant message not found")
        }

        // Find the user message immediately before the assistant message
        val userMessage = messages.subList(0, assistantIndex)
            .lastOrNull { it.role == MessageRole.USER }
            ?: throw IllegalStateException("No user message found before the assistant message")

        // Determine branchSourceMessageId for sibling tracking:
        // If source already has one, inherit it (so redo-from-redo creates siblings)
        // Otherwise, use the user message ID
        val branchSourceMessageId = conversation.branchSourceMessageId ?: userMessage.id

        // Branch up to the user message (model override applied via navigation, not conversation default)
        return branchConversationUseCase(
            sourceConversationId = conversationId,
            upToMessageId = userMessage.id,
            branchSourceMessageId = branchSourceMessageId
        )
    }
}
