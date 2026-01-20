package com.materialchat.domain.usecase

import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.Provider
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.repository.ProviderRepository
import javax.inject.Inject

/**
 * Use case for creating new conversations.
 *
 * This use case handles:
 * - Creating a conversation with the active provider
 * - Creating a conversation with a specific provider
 * - Setting default values for new conversations
 */
class CreateConversationUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val providerRepository: ProviderRepository
) {
    /**
     * Creates a new conversation using the active provider.
     *
     * @return The ID of the created conversation
     * @throws IllegalStateException if no active provider is set
     */
    suspend operator fun invoke(): String {
        val activeProvider = providerRepository.getActiveProvider()
            ?: throw IllegalStateException("No active provider configured. Please add a provider in settings.")

        return createWithProvider(activeProvider)
    }

    /**
     * Creates a new conversation with a specific provider.
     *
     * @param providerId The ID of the provider to use
     * @return The ID of the created conversation
     * @throws IllegalStateException if the provider is not found
     */
    suspend fun withProvider(providerId: String): String {
        val provider = providerRepository.getProvider(providerId)
            ?: throw IllegalStateException("Provider not found: $providerId")

        return createWithProvider(provider)
    }

    /**
     * Creates a new conversation with a specific provider and model.
     *
     * @param providerId The ID of the provider to use
     * @param modelName The model to use for the conversation
     * @return The ID of the created conversation
     * @throws IllegalStateException if the provider is not found
     */
    suspend fun withProviderAndModel(providerId: String, modelName: String): String {
        val provider = providerRepository.getProvider(providerId)
            ?: throw IllegalStateException("Provider not found: $providerId")

        val conversation = Conversation(
            title = Conversation.generateDefaultTitle(),
            providerId = provider.id,
            modelName = modelName
        )

        return conversationRepository.createConversation(conversation)
    }

    /**
     * Creates a conversation with the given provider using its default model.
     */
    private suspend fun createWithProvider(provider: Provider): String {
        val conversation = Conversation(
            title = Conversation.generateDefaultTitle(),
            providerId = provider.id,
            modelName = provider.defaultModel
        )

        return conversationRepository.createConversation(conversation)
    }
}
