package com.materialchat.data.localmodel

import android.content.Context
import com.materialchat.domain.model.LocalModelBackend
import com.materialchat.domain.model.LocalModelDescriptor
import com.materialchat.domain.model.LocalModelIds
import java.io.File

/**
 * Built-in on-device model catalog. Keeping download URLs here makes it easy to
 * update model revisions without touching chat/runtime code.
 */
object LocalModelCatalog {
    private const val MODELS_DIR = "local_models"

    val models: List<LocalModelDescriptor> = listOf(
        LocalModelDescriptor(
            id = LocalModelIds.GEMMA3_1B_IT_INT4,
            displayName = "Gemma 3 1B IT int4",
            backend = LocalModelBackend.LITERT_LM,
            filename = "gemma3-1b-it-int4.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm?download=true",
            approximateSizeBytes = 529L * 1024L * 1024L,
            description = "Balanced local chat model from Google. Best starter model for private on-device chat.",
            requiresLicenseAcknowledgement = true
        ),
        LocalModelDescriptor(
            id = LocalModelIds.QWEN25_05B_INSTRUCT,
            displayName = "Qwen2.5 0.5B Instruct",
            backend = LocalModelBackend.MEDIAPIPE_LLM,
            filename = "qwen2.5-0.5b-instruct-q8.task",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true",
            approximateSizeBytes = 521L * 1024L * 1024L,
            description = "Small Apache-licensed model. Preferred for fast private title generation.",
            titlePreferred = true
        ),
        LocalModelDescriptor(
            id = LocalModelIds.QWEN3_06B,
            displayName = "Qwen3 0.6B",
            backend = LocalModelBackend.LITERT_LM,
            filename = "Qwen3-0.6B.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm?download=true",
            approximateSizeBytes = 586L * 1024L * 1024L,
            description = "Compact Apache-licensed LiteRT-LM chat model with a native .litertlm package.",
            titlePreferred = true
        ),
        LocalModelDescriptor(
            id = LocalModelIds.GEMINI_NANO,
            displayName = "Gemini Nano",
            backend = LocalModelBackend.AICORE_GEMINI_NANO,
            description = "Android AICore system model. Availability depends on device support and Google Play services.",
            titlePreferred = true
        )
    )

    fun descriptor(modelId: String): LocalModelDescriptor? = models.firstOrNull { it.id == modelId }

    fun modelFile(context: Context, descriptor: LocalModelDescriptor): File {
        val safeId = descriptor.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val fileName = descriptor.filename ?: "model.bin"
        return File(File(context.filesDir, MODELS_DIR), "$safeId/$fileName")
    }

    fun modelDir(context: Context, descriptor: LocalModelDescriptor): File = modelFile(context, descriptor).parentFile!!
}
