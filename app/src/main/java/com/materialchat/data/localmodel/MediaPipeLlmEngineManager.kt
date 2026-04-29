package com.materialchat.data.localmodel

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.materialchat.domain.model.LocalModelDescriptor
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs MediaPipe `.task` LLM packages, used for tiny local models that are not
 * published as `.litertlm` yet.
 */
@Singleton
class MediaPipeLlmEngineManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    private var activeInference: LlmInference? = null

    fun stream(
        descriptor: LocalModelDescriptor,
        modelFile: File,
        messages: List<Message>,
        systemPrompt: String?,
        maxTokens: Int = 512
    ): Flow<String> = callbackFlow {
        if (!modelFile.exists()) {
            close(IllegalStateException("${descriptor.displayName} is not downloaded yet."))
            return@callbackFlow
        }
        if (messages.any { it.attachments.isNotEmpty() }) {
            close(IllegalStateException("On-device MediaPipe chat currently supports text prompts only."))
            return@callbackFlow
        }

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(maxTokens)
            .build()
        val inference = LlmInference.createFromOptions(context, options)
        activeInference = inference
        val future = inference.generateResponseAsync(buildPrompt(messages, systemPrompt)) { partialResult: String, done: Boolean ->
            if (partialResult.isNotEmpty()) trySend(partialResult)
            if (done) close()
        }
        future.addListener(
            {
                runCatching { future.get() }
                    .onFailure { close(it) }
            },
            DIRECT_EXECUTOR
        )

        awaitClose {
            if (activeInference === inference) activeInference = null
            runCatching { inference.close() }
        }
    }

    suspend fun generate(
        descriptor: LocalModelDescriptor,
        modelFile: File,
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int = 96
    ): String = withContext(Dispatchers.IO) {
        if (!modelFile.exists()) {
            throw IllegalStateException("${descriptor.displayName} is not downloaded yet.")
        }
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(maxTokens)
            .build()
        LlmInference.createFromOptions(context, options).use { inference ->
            activeInference = inference
            try {
                inference.generateResponse(listOfNotNull(systemPrompt, prompt).joinToString("\n\n")).trim()
            } finally {
                if (activeInference === inference) activeInference = null
            }
        }
    }

    fun cancelActiveGeneration() {
        runCatching { activeInference?.close() }
        activeInference = null
    }

    private fun buildPrompt(messages: List<Message>, systemPrompt: String?): String {
        val recent = messages.takeLast(MAX_CONTEXT_MESSAGES)
        return buildString {
            if (!systemPrompt.isNullOrBlank()) {
                append("System: ").append(systemPrompt.trim()).append("\n\n")
            }
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
        const val MAX_CONTEXT_MESSAGES = 12
        val DIRECT_EXECUTOR = Executor { runnable -> runnable.run() }
    }
}
