package com.materialchat.domain.usecase

import com.materialchat.domain.model.ConversationTreeNode
import com.materialchat.domain.repository.ConversationRepository
import javax.inject.Inject

/**
 * Use case for building a conversation tree starting from any conversation.
 * Walks up to the root via parentId, then recursively builds children.
 */
class GetConversationTreeUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository
) {
    /**
     * Builds the full conversation tree containing the given conversation.
     *
     * @param conversationId The ID of the current conversation
     * @return The root ConversationTreeNode with all descendants populated
     */
    suspend operator fun invoke(conversationId: String): ConversationTreeNode? {
        // Find the root conversation by walking up the parent chain
        var currentId = conversationId
        while (true) {
            val conversation = conversationRepository.getConversation(currentId) ?: return null
            if (conversation.parentId == null) {
                // Found the root — build tree from here
                return buildNode(conversation.id, conversationId)
            }
            currentId = conversation.parentId
        }
    }

    private suspend fun buildNode(nodeConversationId: String, currentConversationId: String): ConversationTreeNode? {
        val conversation = conversationRepository.getConversation(nodeConversationId) ?: return null
        val messages = conversationRepository.getMessages(nodeConversationId)
        val branches = conversationRepository.getBranches(nodeConversationId)

        val children = branches.mapNotNull { branch ->
            buildNode(branch.id, currentConversationId)
        }

        return ConversationTreeNode(
            id = conversation.id,
            title = conversation.title,
            icon = conversation.icon,
            modelName = conversation.modelName,
            messageCount = messages.size,
            children = children,
            isCurrentConversation = conversation.id == currentConversationId
        )
    }
}
