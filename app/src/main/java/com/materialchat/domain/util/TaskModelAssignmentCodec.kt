package com.materialchat.domain.util

/**
 * Encodes cloud task models as `providerId|modelId` or bare `modelId` (conversation provider).
 */
object TaskModelAssignmentCodec {
    const val CHAT_MODEL = "__chat_model__"

    fun chatModel(): String = CHAT_MODEL

    fun isChatModel(raw: String): Boolean = raw.trim() == CHAT_MODEL

    fun encode(providerId: String?, modelId: String): String {
        val id = modelId.trim()
        if (id.isEmpty()) return ""
        val provider = providerId?.trim().orEmpty()
        return if (provider.isEmpty()) id else "$provider|$id"
    }

    fun decode(raw: String): Pair<String?, String> {
        if (raw.isBlank() || isChatModel(raw)) return null to raw.trim()
        val pipe = raw.indexOf('|')
        if (pipe < 0) return null to raw.trim()
        val providerId = raw.substring(0, pipe).trim()
        val modelId = raw.substring(pipe + 1).trim()
        return (providerId.ifBlank { null }) to modelId
    }

    fun displayLabel(raw: String, providerName: String? = null): String {
        if (raw.isBlank()) return "Automatic"
        if (isChatModel(raw)) return "Chat model"
        val (providerId, modelId) = decode(raw)
        return when {
            providerId != null && providerName != null -> "$providerName · $modelId"
            providerId != null -> "$providerId · $modelId"
            else -> modelId
        }
    }
}