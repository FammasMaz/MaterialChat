package com.materialchat.domain.repository

import com.materialchat.domain.model.AiModel
import com.materialchat.domain.model.LocalModelBackend
import com.materialchat.domain.model.LocalModelState
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.StreamingState
import kotlinx.coroutines.flow.Flow

/**
 * Repository for models that run fully on the Android device.
 */
interface LocalModelRepository {
    fun observeModels(): Flow<List<LocalModelState>>

    suspend fun refreshStatuses()

    suspend fun download(modelId: String): Result<Unit>

    suspend fun delete(modelId: String): Result<Unit>

    suspend fun unmount(modelId: String): Result<Unit>

    suspend fun fetchAvailableAiModels(
        providerId: String,
        backend: LocalModelBackend
    ): List<AiModel>

    fun streamChat(
        modelId: String,
        messages: List<Message>,
        systemPrompt: String? = null
    ): Flow<StreamingState>

    suspend fun generateSimpleCompletion(
        modelId: String,
        prompt: String,
        systemPrompt: String? = null
    ): Result<String>

    suspend fun isModelUsable(modelId: String): Boolean

    suspend fun preferredTitleModelIdOrNull(): String?

    suspend fun firstUsableModelId(backend: LocalModelBackend): String?

    suspend fun getHuggingFaceTokenPreview(): String?

    suspend fun setHuggingFaceToken(token: String)

    suspend fun clearHuggingFaceToken()

    fun cancelActiveGeneration()
}
