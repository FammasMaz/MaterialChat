package com.materialchat.data.localmodel

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message as LiteRtMessage
import com.materialchat.domain.model.LocalModelDescriptor
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns LiteRT-LM engines. Only one model is kept warm at a time to reduce RAM pressure.
 */
@Singleton
class LiteRtLmEngineManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val lock = Any()
    private var loadedModelId: String? = null
    private var loadedEngine: Engine? = null
    private var activeConversation: Conversation? = null
    private val _mountedModelId = MutableStateFlow<String?>(null)
    val mountedModelId: StateFlow<String?> = _mountedModelId.asStateFlow()

    fun stream(
        descriptor: LocalModelDescriptor,
        modelFile: File,
        messages: List<Message>,
        systemPrompt: String?,
        maxOutputTokens: Int = 1024
    ): Flow<String> = flow {
        if (!modelFile.exists()) {
            throw IllegalStateException("${descriptor.displayName} is not downloaded yet.")
        }
        if (messages.any { it.attachments.isNotEmpty() }) {
            throw IllegalStateException("On-device LiteRT-LM chat currently supports text prompts only.")
        }

        val engine = getOrLoadEngine(descriptor.id, modelFile, maxOutputTokens)
        val conversation = createConversation(engine, systemPrompt)
        synchronized(lock) { activeConversation = conversation }
        try {
            val prompt = buildPrompt(messages)
            conversation.sendMessageAsync(LiteRtMessage.user(prompt)).collect { chunk ->
                val text = chunk.toString()
                if (text.isNotEmpty()) emit(text)
            }
        } catch (e: Exception) {
            throw IllegalStateException(
                e.message ?: "On-device model failed. Try Unmount in Settings → On-device models, then send again.",
                e
            )
        } finally {
            synchronized(lock) {
                if (activeConversation === conversation) activeConversation = null
            }
            runCatching { conversation.close() }
        }
    }.flowOn(Dispatchers.IO).catch { e ->
        throw IllegalStateException(
            e.message ?: "On-device model failed. Try Unmount in Settings → On-device models, then send again.",
            e
        )
    }

    suspend fun generate(
        descriptor: LocalModelDescriptor,
        modelFile: File,
        prompt: String,
        systemPrompt: String?,
        maxOutputTokens: Int = 96
    ): String = withContext(Dispatchers.IO) {
        if (!modelFile.exists()) {
            throw IllegalStateException("${descriptor.displayName} is not downloaded yet.")
        }
        val engine = getOrLoadEngine(descriptor.id, modelFile, maxOutputTokens)
        val conversation = createConversation(engine, systemPrompt)
        synchronized(lock) { activeConversation = conversation }
        try {
            conversation.sendMessage(LiteRtMessage.user(prompt)).toString().trim()
        } finally {
            synchronized(lock) {
                if (activeConversation === conversation) activeConversation = null
            }
            runCatching { conversation.close() }
        }
    }

    fun cancelActiveGeneration() {
        synchronized(lock) { activeConversation }?.let { conversation ->
            runCatching { conversation.cancelProcess() }
        }
    }

    fun closeModel(modelId: String) {
        synchronized(lock) {
            if (loadedModelId == modelId) {
                runCatching { activeConversation?.cancelProcess() }
                runCatching { activeConversation?.close() }
                runCatching { loadedEngine?.close() }
                activeConversation = null
                loadedEngine = null
                loadedModelId = null
                _mountedModelId.value = null
            }
        }
    }

    fun closeAll() {
        synchronized(lock) {
            runCatching { activeConversation?.cancelProcess() }
            runCatching { activeConversation?.close() }
            runCatching { loadedEngine?.close() }
            activeConversation = null
            loadedEngine = null
            loadedModelId = null
            _mountedModelId.value = null
        }
    }

    private fun getOrLoadEngine(modelId: String, modelFile: File, maxOutputTokens: Int): Engine {
        synchronized(lock) {
            loadedEngine?.takeIf { loadedModelId == modelId }?.let { return it }
            runCatching { activeConversation?.close() }
            runCatching { loadedEngine?.close() }
            activeConversation = null
            loadedEngine = null
            loadedModelId = null
            _mountedModelId.value = null

            val engine = Engine(
                EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.CPU(),
                    maxNumTokens = maxOutputTokens,
                    cacheDir = context.cacheDir.absolutePath
                )
            )
            engine.initialize()
            loadedEngine = engine
            loadedModelId = modelId
            _mountedModelId.value = modelId
            return engine
        }
    }

    private fun createConversation(engine: Engine, systemPrompt: String?): Conversation {
        val config = ConversationConfig(
            systemInstruction = systemPrompt
                ?.takeIf { it.isNotBlank() }
                ?.let { Contents.of(it) }
        )
        return engine.createConversation(config)
    }

    private fun buildPrompt(messages: List<Message>): String {
        val recent = messages.takeLast(MAX_CONTEXT_MESSAGES)
        return buildString {
            recent.forEach { message ->
                val role = when (message.role) {
                    MessageRole.USER -> "User"
                    MessageRole.ASSISTANT -> "Assistant"
                    MessageRole.SYSTEM -> "System"
                }
                append(role).append(": ").append(message.content.trim()).append("\n")
            }
            append("Assistant:")
        }
    }

    private companion object {
        const val MAX_CONTEXT_MESSAGES = 16
    }
}
