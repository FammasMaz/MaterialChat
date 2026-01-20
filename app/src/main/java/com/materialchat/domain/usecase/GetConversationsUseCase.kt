package com.materialchat.domain.usecase

import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.Message
import com.materialchat.domain.repository.ConversationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for observing and retrieving conversations.
 *
 * This use case provides:
 * - Observable list of all conversations
 * - Single conversation retrieval
 * - Messages for a conversation
 * - Conversation deletion
 */
class GetConversationsUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository
) {
    /**
     * Observes all conversations sorted by most recently updated.
     *
     * @return A Flow emitting the list of conversations whenever it changes
     */
    fun observeConversations(): Flow<List<Conversation>> {
        return conversationRepository.observeConversations()
    }

    /**
     * Gets a single conversation by its ID.
     *
     * @param conversationId The ID of the conversation to retrieve
     * @return The conversation, or null if not found
     */
    suspend fun getConversation(conversationId: String): Conversation? {
        return conversationRepository.getConversation(conversationId)
    }

    /**
     * Observes messages in a conversation.
     *
     * @param conversationId The ID of the conversation
     * @return A Flow emitting the list of messages whenever it changes
     */
    fun observeMessages(conversationId: String): Flow<List<Message>> {
        return conversationRepository.observeMessages(conversationId)
    }

    /**
     * Gets all messages in a conversation.
     *
     * @param conversationId The ID of the conversation
     * @return The list of messages
     */
    suspend fun getMessages(conversationId: String): List<Message> {
        return conversationRepository.getMessages(conversationId)
    }

    /**
     * Deletes a conversation and all its messages.
     *
     * @param conversationId The ID of the conversation to delete
     */
    suspend fun deleteConversation(conversationId: String) {
        conversationRepository.deleteConversation(conversationId)
    }

    /**
     * Updates the model used in a conversation.
     *
     * @param conversationId The ID of the conversation
     * @param modelName The new model name
     */
    suspend fun updateConversationModel(conversationId: String, modelName: String) {
        conversationRepository.updateConversationModel(conversationId, modelName)
    }
}
