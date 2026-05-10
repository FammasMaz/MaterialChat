package com.materialchat.domain.model

import kotlinx.serialization.Serializable

/**
 * Compact message-level metadata used by the chat UI to disclose passive memory.
 */
@Serializable
data class MemoryMessageMetadata(
    val recalled: List<MemoryReference> = emptyList(),
    val saved: List<MemoryReference> = emptyList()
) {
    val hasAny: Boolean
        get() = recalled.isNotEmpty() || saved.isNotEmpty()
}

@Serializable
data class MemoryReference(
    val id: String,
    val label: String,
    val kind: MemoryKind = MemoryKind.OTHER
)

fun Memory.toReference(): MemoryReference = MemoryReference(
    id = id,
    label = content.takeReferenceLabel(),
    kind = kind
)

private fun String.takeReferenceLabel(maxLength: Int = 120): String {
    val compact = replace(Regex("\\s+"), " ").trim()
    if (compact.length <= maxLength) return compact
    return compact.take(maxLength - 1).trimEnd() + "…"
}
