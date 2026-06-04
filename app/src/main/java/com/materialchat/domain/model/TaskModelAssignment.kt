package com.materialchat.domain.model

/**
 * Background tasks that can use a dedicated model instead of the active chat model.
 */
enum class TaskModelAssignment(
    val title: String,
    val subtitle: String,
    val footnote: String?
) {
    CONVERSATION_TITLE(
        title = "Conversation titles",
        subtitle = "Emoji + short title after the first reply",
        footnote = "Runs only when AI-generated titles are enabled in Settings."
    ),
    MEMORY_EXTRACTION(
        title = "Memory extraction",
        subtitle = "Facts saved from chat for long-term recall",
        footnote = null
    ),
    IMAGE_GENERATION(
        title = "Image generation",
        subtitle = "Default model when a chat asks to create an image",
        footnote = "Requires a vision/image-capable cloud provider."
    );

    /** Preference storage uses the same string key as legacy settings where applicable. */
    val usesProviderPipeFormat: Boolean
        get() = this != IMAGE_GENERATION
}

/**
 * Smallest on-device models in [LocalModelCatalog], in preferred order for lightweight tasks.
 */
object LightweightOnDeviceModels {
    val preferredOrder: List<String> = listOf(
        LocalModelIds.QWEN25_05B_INSTRUCT,
        LocalModelIds.QWEN3_06B,
        LocalModelIds.GEMMA3_1B_IT_INT4,
        LocalModelIds.GEMINI_NANO
    )

    const val SMALLEST_RECOMMENDATION =
        "Qwen2.5 0.5B Instruct (~521 MB) is the smallest download — ideal for titles and memory on-device."
}