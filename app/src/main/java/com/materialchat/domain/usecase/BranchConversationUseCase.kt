package com.materialchat.domain.usecase

import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.Message
import com.materialchat.domain.repository.ConversationRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Use case for creating a branch from an existing conversation.
 *
 * Creates a new conversation containing all messages up to and including
 * the specified message, enabling exploration of alternative paths
 * without losing the original thread.
 *
 * The branch is linked to its parent via the parentId field and starts
 * with a temporary "New Branch" title. When the user sends the first new
 * message in the branch, an AI-generated title will be created based on
 * what makes this branch different from the original.
 */
class BranchConversationUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository
) {
    /**
     * Creates a branched conversation from the source conversation up to the specified message.
     *
     * @param sourceConversationId The ID of the conversation to branch from
     * @param upToMessageId The ID of the message to branch up to (inclusive)
     * @return The ID of the newly created branched conversation
     * @throws IllegalStateException if the source conversation or message is not found
     */
    suspend operator fun invoke(
        sourceConversationId: String,
        upToMessageId: String
    ): String {
        // Get source conversation
        val sourceConversation = conversationRepository.getConversation(sourceConversationId)
            ?: throw IllegalStateException("Source conversation not found")

        // Get all messages from source
        val sourceMessages = conversationRepository.getMessages(sourceConversationId)

        // Find the index of the target message
        val targetIndex = sourceMessages.indexOfFirst { it.id == upToMessageId }
        if (targetIndex == -1) {
            throw IllegalStateException("Target message not found in conversation")
        }

        // Get messages up to and including the target
        val messagesToCopy = sourceMessages.subList(0, targetIndex + 1)

        // Determine the parent ID - if source is already a branch, use its parent
        // Otherwise, the source becomes the parent
        val parentId = sourceConversation.parentId ?: sourceConversation.id

        // Create the new conversation with parentId set and temporary title
        val newConversation = Conversation(
            id = UUID.randomUUID().toString(),
            title = Conversation.generateDefaultBranchTitle(),
            icon = null, // AI will generate new icon based on branch content
            providerId = sourceConversation.providerId,
            modelName = sourceConversation.modelName,
            parentId = parentId
        )

        val newConversationId = conversationRepository.createConversation(newConversation)

        // Copy messages with new IDs and new conversation ID
        for (message in messagesToCopy) {
            val copiedMessage = Message(
                id = UUID.randomUUID().toString(),
                conversationId = newConversationId,
                role = message.role,
                content = message.content,
                thinkingContent = message.thinkingContent,
                attachments = message.attachments,
                isStreaming = false, // Never copy streaming state
                createdAt = message.createdAt
            )
            conversationRepository.addMessage(copiedMessage)
        }

        return newConversationId
    }
}
