package com.materialchat.data.localmodel

import android.content.Context
import com.materialchat.data.local.preferences.EncryptedPreferences
import com.materialchat.di.IoDispatcher
import com.materialchat.di.StandardClient
import com.materialchat.domain.model.AiModel
import com.materialchat.domain.model.LocalModelAvailability
import com.materialchat.domain.model.LocalModelBackend
import com.materialchat.domain.model.LocalModelDescriptor
import com.materialchat.domain.model.LocalModelIds
import com.materialchat.domain.model.LocalModelState
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.repository.LocalModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalModelRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @StandardClient private val okHttpClient: OkHttpClient,
    private val encryptedPreferences: EncryptedPreferences,
    private val liteRtLmEngineManager: LiteRtLmEngineManager,
    private val mediaPipeLlmEngineManager: MediaPipeLlmEngineManager,
    private val geminiNanoClient: GeminiNanoClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : LocalModelRepository {

    private val downloadMutex = Mutex()
    private val _models = MutableStateFlow(initialStates())

    override fun observeModels(): Flow<List<LocalModelState>> = _models.asStateFlow()

    override suspend fun refreshStatuses() = withContext(ioDispatcher) {
        val refreshed = LocalModelCatalog.models.map { descriptor ->
            when (descriptor.backend) {
                LocalModelBackend.LITERT_LM,
                LocalModelBackend.MEDIAPIPE_LLM -> fileBackedState(descriptor)
                LocalModelBackend.AICORE_GEMINI_NANO -> aicoreState(descriptor)
            }
        }
        _models.value = refreshed
    }

    override suspend fun download(modelId: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            downloadMutex.withLock {
                val descriptor = requireDescriptor(modelId)
                try {
                    when (descriptor.backend) {
                        LocalModelBackend.LITERT_LM,
                        LocalModelBackend.MEDIAPIPE_LLM -> downloadFileBackedModel(descriptor)
                        LocalModelBackend.AICORE_GEMINI_NANO -> downloadAicoreModel(descriptor)
                    }
                    refreshStatuses()
                } catch (e: Exception) {
                    updateState(
                        modelId = descriptor.id,
                        availability = LocalModelAvailability.ERROR,
                        errorMessage = e.message
                    )
                    throw e
                }
            }
        }
    }

    override suspend fun delete(modelId: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val descriptor = requireDescriptor(modelId)
            when (descriptor.backend) {
                LocalModelBackend.LITERT_LM,
                LocalModelBackend.MEDIAPIPE_LLM -> {
                    liteRtLmEngineManager.closeModel(modelId)
                    mediaPipeLlmEngineManager.cancelActiveGeneration()
                    val dir = LocalModelCatalog.modelDir(context, descriptor)
                    if (dir.exists() && !dir.deleteRecursively()) {
                        throw IOException("Could not delete ${descriptor.displayName}")
                    }
                    refreshStatuses()
                }
                LocalModelBackend.AICORE_GEMINI_NANO -> {
                    throw UnsupportedOperationException(
                        "Gemini Nano is managed by Android AICore. Open the AICore app or system app settings to remove its storage."
                    )
                }
            }
        }
    }

    override suspend fun fetchAvailableAiModels(
        providerId: String,
        backend: LocalModelBackend
    ): List<AiModel> = withContext(ioDispatcher) {
        refreshStatuses()
        _models.value
            .filter { it.descriptor.backend.matchesProviderBackend(backend) }
            .map { state ->
                AiModel(
                    id = state.descriptor.id,
                    name = state.descriptor.displayName,
                    providerId = providerId
                )
            }
    }

    override fun streamChat(
        modelId: String,
        messages: List<Message>,
        systemPrompt: String?
    ): Flow<StreamingState> = flow {
        val descriptor = requireDescriptor(modelId)
        val messageId = UUID.randomUUID().toString()
        val accumulated = StringBuilder()
        emit(StreamingState.Starting)

        val tokenFlow = when (descriptor.backend) {
            LocalModelBackend.LITERT_LM -> {
                val file = LocalModelCatalog.modelFile(context, descriptor)
                liteRtLmEngineManager.stream(descriptor, file, messages, systemPrompt)
            }
            LocalModelBackend.MEDIAPIPE_LLM -> {
                val file = LocalModelCatalog.modelFile(context, descriptor)
                mediaPipeLlmEngineManager.stream(descriptor, file, messages, systemPrompt)
            }
            LocalModelBackend.AICORE_GEMINI_NANO -> {
                geminiNanoClient.stream(buildPrompt(messages, systemPrompt))
            }
        }

        tokenFlow.collect { token ->
            accumulated.append(token)
            emit(
                StreamingState.Streaming(
                    content = accumulated.toString(),
                    messageId = messageId
                )
            )
        }
        emit(
            StreamingState.Completed(
                finalContent = accumulated.toString(),
                messageId = messageId
            )
        )
    }.catch { error ->
        emit(StreamingState.Error(error = error))
    }

    override suspend fun generateSimpleCompletion(
        modelId: String,
        prompt: String,
        systemPrompt: String?
    ): Result<String> = withContext(ioDispatcher) {
        runCatching {
            val descriptor = requireDescriptor(modelId)
            when (descriptor.backend) {
                LocalModelBackend.LITERT_LM -> {
                    liteRtLmEngineManager.generate(
                        descriptor = descriptor,
                        modelFile = LocalModelCatalog.modelFile(context, descriptor),
                        prompt = prompt,
                        systemPrompt = systemPrompt
                    )
                }
                LocalModelBackend.MEDIAPIPE_LLM -> {
                    mediaPipeLlmEngineManager.generate(
                        descriptor = descriptor,
                        modelFile = LocalModelCatalog.modelFile(context, descriptor),
                        prompt = prompt,
                        systemPrompt = systemPrompt
                    )
                }
                LocalModelBackend.AICORE_GEMINI_NANO -> {
                    geminiNanoClient.generate(
                        prompt = listOfNotNull(systemPrompt, prompt).joinToString("\n\n"),
                        maxOutputTokens = 96
                    )
                }
            }
        }
    }

    override suspend fun isModelUsable(modelId: String): Boolean = withContext(ioDispatcher) {
        val descriptor = LocalModelCatalog.descriptor(modelId) ?: return@withContext false
        when (descriptor.backend) {
            LocalModelBackend.LITERT_LM,
            LocalModelBackend.MEDIAPIPE_LLM -> LocalModelCatalog.modelFile(context, descriptor).exists()
            LocalModelBackend.AICORE_GEMINI_NANO -> geminiNanoClient.availability() == LocalModelAvailability.AVAILABLE
        }
    }

    override suspend fun preferredTitleModelIdOrNull(): String? = withContext(ioDispatcher) {
        val preferredOrder = listOf(
            LocalModelIds.QWEN25_05B_INSTRUCT,
            LocalModelIds.QWEN3_06B,
            LocalModelIds.GEMMA3_1B_IT_INT4,
            LocalModelIds.GEMINI_NANO
        )
        preferredOrder.firstOrNull { isModelUsable(it) }
    }

    override suspend fun firstUsableModelId(backend: LocalModelBackend): String? = withContext(ioDispatcher) {
        LocalModelCatalog.models
            .filter { it.backend.matchesProviderBackend(backend) }
            .firstOrNull { isModelUsable(it.id) }
            ?.id
    }

    override suspend fun getHuggingFaceTokenPreview(): String? = withContext(ioDispatcher) {
        encryptedPreferences.getApiKey(HUGGING_FACE_TOKEN_ID)?.let { token ->
            if (token.length <= 12) "••••" else token.take(6) + "…" + token.takeLast(4)
        }
    }

    override suspend fun setHuggingFaceToken(token: String) = withContext(ioDispatcher) {
        val cleaned = token.trim()
        if (cleaned.isBlank()) {
            encryptedPreferences.deleteApiKey(HUGGING_FACE_TOKEN_ID)
        } else {
            encryptedPreferences.setApiKey(HUGGING_FACE_TOKEN_ID, cleaned)
        }
    }

    override suspend fun clearHuggingFaceToken() = withContext(ioDispatcher) {
        encryptedPreferences.deleteApiKey(HUGGING_FACE_TOKEN_ID)
    }

    override fun cancelActiveGeneration() {
        liteRtLmEngineManager.cancelActiveGeneration()
        mediaPipeLlmEngineManager.cancelActiveGeneration()
    }

    private fun initialStates(): List<LocalModelState> = LocalModelCatalog.models.map { descriptor ->
        when (descriptor.backend) {
            LocalModelBackend.LITERT_LM,
            LocalModelBackend.MEDIAPIPE_LLM -> fileBackedState(descriptor)
            LocalModelBackend.AICORE_GEMINI_NANO -> LocalModelState(
                descriptor = descriptor,
                availability = LocalModelAvailability.DOWNLOADABLE
            )
        }
    }

    private fun fileBackedState(descriptor: LocalModelDescriptor): LocalModelState {
        val file = LocalModelCatalog.modelFile(context, descriptor)
        return LocalModelState(
            descriptor = descriptor,
            availability = if (file.exists()) LocalModelAvailability.DOWNLOADED else LocalModelAvailability.NOT_DOWNLOADED,
            downloadedBytes = file.takeIf { it.exists() }?.length() ?: 0L,
            totalBytes = descriptor.approximateSizeBytes
        )
    }

    private suspend fun aicoreState(descriptor: LocalModelDescriptor): LocalModelState {
        return try {
            LocalModelState(
                descriptor = descriptor,
                availability = geminiNanoClient.availability()
            )
        } catch (e: Exception) {
            LocalModelState(
                descriptor = descriptor,
                availability = LocalModelAvailability.ERROR,
                errorMessage = e.message
            )
        }
    }

    private suspend fun downloadFileBackedModel(descriptor: LocalModelDescriptor) {
        val url = descriptor.downloadUrl ?: throw IllegalStateException("No download URL for ${descriptor.displayName}")
        val target = LocalModelCatalog.modelFile(context, descriptor)
        if (target.exists()) return

        updateState(
            descriptor.id,
            LocalModelAvailability.DOWNLOADING,
            downloadedBytes = 0L,
            totalBytes = descriptor.approximateSizeBytes,
            errorMessage = null
        )

        val tmp = File(target.parentFile, "${target.name}.download")
        target.parentFile?.mkdirs()
        if (tmp.exists()) tmp.delete()

        val requestBuilder = Request.Builder().url(url).get()
        encryptedPreferences.getApiKey(HUGGING_FACE_TOKEN_ID)
            ?.takeIf { url.startsWith("https://huggingface.co") }
            ?.let { token -> requestBuilder.addHeader("Authorization", "Bearer $token") }
        val request = requestBuilder.build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val hint = if (response.code == 401 || response.code == 403) {
                    " You may need to accept the model license on Hugging Face before downloading."
                } else ""
                throw IOException("Download failed for ${descriptor.displayName}: HTTP ${response.code}.$hint")
            }
            val body = response.body ?: throw IOException("Empty model download response")
            val total = body.contentLength().takeIf { it > 0L } ?: descriptor.approximateSizeBytes
            body.byteStream().use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    var lastEmit = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastEmit >= PROGRESS_EMIT_BYTES) {
                            lastEmit = downloaded
                            updateState(
                                descriptor.id,
                                LocalModelAvailability.DOWNLOADING,
                                downloadedBytes = downloaded,
                                totalBytes = total
                            )
                        }
                    }
                    output.flush()
                    updateState(
                        descriptor.id,
                        LocalModelAvailability.DOWNLOADING,
                        downloadedBytes = downloaded,
                        totalBytes = total
                    )
                }
            }
        }

        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private suspend fun downloadAicoreModel(descriptor: LocalModelDescriptor) {
        updateState(descriptor.id, LocalModelAvailability.DOWNLOADING)
        var totalBytes: Long? = null
        geminiNanoClient.download().collect { (downloaded, total) ->
            totalBytes = total ?: totalBytes
            updateState(
                descriptor.id,
                LocalModelAvailability.DOWNLOADING,
                downloadedBytes = downloaded,
                totalBytes = totalBytes
            )
        }
    }

    private fun requireDescriptor(modelId: String): LocalModelDescriptor {
        return LocalModelCatalog.descriptor(modelId)
            ?: throw IllegalArgumentException("Unknown on-device model: $modelId")
    }

    private fun LocalModelBackend.matchesProviderBackend(providerBackend: LocalModelBackend): Boolean {
        return this == providerBackend ||
            (providerBackend == LocalModelBackend.LITERT_LM && this == LocalModelBackend.MEDIAPIPE_LLM)
    }

    private fun updateState(
        modelId: String,
        availability: LocalModelAvailability,
        downloadedBytes: Long = 0L,
        totalBytes: Long? = null,
        errorMessage: String? = null
    ) {
        _models.update { states ->
            states.map { state ->
                if (state.descriptor.id == modelId) {
                    state.copy(
                        availability = availability,
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes ?: state.totalBytes,
                        errorMessage = errorMessage
                    )
                } else {
                    state
                }
            }
        }
    }

    private fun buildPrompt(messages: List<Message>, systemPrompt: String?): String {
        val recent = messages.takeLast(MAX_CONTEXT_MESSAGES)
        return buildString {
            if (!systemPrompt.isNullOrBlank()) {
                append("System: ").append(systemPrompt.trim()).append("\n\n")
            }
            recent.forEach { message ->
                val role = message.role.name.lowercase().replaceFirstChar { it.titlecase() }
                append(role).append(": ").append(message.content.trim()).append("\n")
            }
            append("Assistant:")
        }
    }

    private companion object {
        const val PROGRESS_EMIT_BYTES = 512L * 1024L
        const val MAX_CONTEXT_MESSAGES = 12
        const val HUGGING_FACE_TOKEN_ID = "huggingface_access_token"
    }
}
