package com.materialchat.domain.repository

import com.materialchat.domain.model.AiModel
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.model.StreamingState
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for chat operations including sending messages
 * and receiving streaming responses from AI providers.
 */
interface ChatRepository {

    /**
     * Sends a message to the AI provider and streams the response.
     *
     * @param provider The AI provider to send the message to
     * @param messages The conversation history including the new message
     * @param model The model to use for generation
     * @param reasoningEffort The reasoning effort setting for compatible models
     * @param systemPrompt Optional system prompt to include
     * @return A Flow of StreamingState representing the response progress
     */
    fun sendMessage(
        provider: Provider,
        messages: List<Message>,
        model: String,
        reasoningEffort: ReasoningEffort,
        systemPrompt: String? = null
    ): Flow<StreamingState>

    /**
     * Fetches the list of available models from a provider.
     *
     * @param provider The AI provider to fetch models from
     * @return A list of available AI models
     */
    suspend fun fetchModels(provider: Provider): Result<List<AiModel>>

    /**
     * Cancels any ongoing streaming request.
     */
    fun cancelStreaming()

    /**
     * Generates a simple non-streaming completion from the AI provider.
     * Useful for short tasks like generating conversation titles.
     *
     * @param provider The AI provider to use
     * @param prompt The prompt to send
     * @param model The model to use for generation
     * @return The generated text, or an error
     */
    suspend fun generateSimpleCompletion(
        provider: Provider,
        prompt: String,
        model: String
    ): Result<String>

    /**
     * Tests the connection to a provider.
     *
     * @param provider The AI provider to test
     * @return True if the connection is successful, false otherwise
     */
    suspend fun testConnection(provider: Provider): Result<Boolean>
}
