package com.materialchat.data.local.preferences

/** When the assistant may use the screen it was opened over. */
enum class ScreenContextMode {
    /** The capture is never used, and the model is not told it exists. */
    NEVER,
    /** Used when the question refers to the screen (local heuristic), or when the model asks for it. */
    AUTOMATIC,
    /** Attached to every question while a capture is available. */
    ALWAYS;

    companion object {
        /** A stored name, or the default for anything unknown or unset. */
        fun fromStored(name: String?): ScreenContextMode = entries.firstOrNull { it.name == name } ?: AUTOMATIC
    }
}

/**
 * Settings > Assistant > Screen context. The two includes decide which halves of a capture are
 * sent when it is used: the screenshot as an image attachment, the flattened screen text in
 * the system prompt. Both default on; a text-only model works with just the text.
 */
data class AssistantScreenContextSettings(
    val mode: ScreenContextMode = ScreenContextMode.AUTOMATIC,
    val includeScreenshot: Boolean = true,
    val includeScreenText: Boolean = true
)
