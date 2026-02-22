package com.materialchat.domain.usecase

import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.Provider
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.repository.ProviderRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case for creating new conversations.
 *
 * This use case handles:
 * - Creating a conversation with the active provider
 * - Creating a conversation with a specific provider
 * - Setting default values for new conversations
 * - Using the last used model if the setting is enabled
 */
class CreateConversationUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val providerRepository: ProviderRepository,
    private val appPreferences: AppPreferences
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
     * Creates a conversation with the given provider.
     * Uses the last used model if the setting is enabled and a model was previously used,
     * otherwise falls back to the provider's default model.
     */
    private suspend fun createWithProvider(provider: Provider, personaId: String? = null): String {
        // Determine which model to use
        val modelToUse = if (appPreferences.rememberLastModel.first()) {
            val lastModel = appPreferences.lastUsedModel.first()
            if (lastModel.isNotBlank()) lastModel else provider.defaultModel
        } else {
            provider.defaultModel
        }

        val conversation = Conversation(
            title = Conversation.generateDefaultTitle(),
            providerId = provider.id,
            modelName = modelToUse,
            personaId = personaId
        )

        return conversationRepository.createConversation(conversation)
    }

    /**
     * Creates a new conversation bound to a specific persona.
     *
     * @param personaId The ID of the persona to associate with the conversation
     * @return The ID of the created conversation
     * @throws IllegalStateException if no active provider is set
     */
    suspend fun withPersona(personaId: String): String {
        val activeProvider = providerRepository.getActiveProvider()
            ?: throw IllegalStateException("No active provider configured. Please add a provider in settings.")

        return createWithProvider(activeProvider, personaId = personaId)
    }
}
