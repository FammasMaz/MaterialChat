package com.materialchat.domain.model

/**
 * Reasoning effort settings for models that support extended thinking.
 */
enum class ReasoningEffort(
    val apiValue: String,
    val displayName: String
) {
    NONE("none", "None"),
    LOW("low", "Low"),
    MEDIUM("medium", "Medium"),
    HIGH("high", "High"),
    XHIGH("xhigh", "Extra High");

    val enablesThinking: Boolean
        get() = this != NONE

    companion object {
        fun fromStoredValue(value: String?): ReasoningEffort {
            return try {
                if (value.isNullOrBlank()) {
                    HIGH
                } else {
                    valueOf(value)
                }
            } catch (e: IllegalArgumentException) {
                HIGH
            }
        }
    }
}
