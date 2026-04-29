package com.materialchat.domain.model

/**
 * Runtime used by an on-device model.
 */
enum class LocalModelBackend {
    LITERT_LM,
    MEDIAPIPE_LLM,
    AICORE_GEMINI_NANO
}

/**
 * Current install/availability status for an on-device model.
 */
enum class LocalModelAvailability {
    NOT_DOWNLOADED,
    DOWNLOADABLE,
    DOWNLOADING,
    DOWNLOADED,
    AVAILABLE,
    UNAVAILABLE,
    ERROR
}

/**
 * Static metadata for a model MaterialChat can run on-device.
 */
data class LocalModelDescriptor(
    val id: String,
    val displayName: String,
    val backend: LocalModelBackend,
    val filename: String? = null,
    val downloadUrl: String? = null,
    val approximateSizeBytes: Long? = null,
    val description: String = "",
    val titlePreferred: Boolean = false,
    val requiresLicenseAcknowledgement: Boolean = false
)

/**
 * Dynamic UI/runtime state for a local model.
 */
data class LocalModelState(
    val descriptor: LocalModelDescriptor,
    val availability: LocalModelAvailability,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val errorMessage: String? = null
) {
    val isUsable: Boolean
        get() = availability == LocalModelAvailability.DOWNLOADED ||
            availability == LocalModelAvailability.AVAILABLE

    val progress: Float?
        get() {
            val total = totalBytes ?: return null
            if (total <= 0L) return null
            return (downloadedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
}

object LocalModelIds {
    const val GEMMA3_1B_IT_INT4 = "litert/gemma3-1b-it-int4"
    const val QWEN25_05B_INSTRUCT = "litert/qwen2.5-0.5b-instruct"
    const val QWEN3_06B = "litert/qwen3-0.6b"
    const val GEMINI_NANO = "aicore/gemini-nano"
}
