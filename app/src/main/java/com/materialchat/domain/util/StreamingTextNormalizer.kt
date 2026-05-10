package com.materialchat.domain.util

/**
 * Normalizes the display boundary between provider reasoning and the final answer.
 *
 * Some thinking-capable providers emit separator whitespace as trailing reasoning
 * chunks or as the first content chunks. If we keep that raw boundary whitespace,
 * Compose faithfully renders it as a large blank gap between thoughts and text.
 */
data class NormalizedStreamingText(
    val content: String,
    val thinkingContent: String?
)

fun normalizeStreamingTextBoundary(
    content: String,
    thinkingContent: String?
): NormalizedStreamingText {
    val normalizedThinking = thinkingContent
        ?.trimBoundaryStart()
        ?.trimBoundaryEnd()
        ?.takeIf { it.isNotEmpty() }
    val normalizedContent = if (normalizedThinking != null) {
        content.trimBoundaryStart()
    } else {
        content
    }

    return NormalizedStreamingText(
        content = normalizedContent,
        thinkingContent = normalizedThinking
    )
}

fun String.trimBoundaryStart(): String {
    var index = 0

    while (index < length) {
        var probe = index
        while (probe < length && this[probe].isInlineBoundaryWhitespace()) {
            probe++
        }

        if (probe >= length) return ""

        val lineBreakEnd = lineBreakEndAt(probe)
        if (lineBreakEnd == -1) return substring(index)

        index = lineBreakEnd
    }

    return ""
}

fun String.trimBoundaryEnd(): String = trimEnd { it.isBoundaryWhitespace() }

private fun String.lineBreakEndAt(index: Int): Int = when (this[index]) {
    '\r' -> if (getOrNull(index + 1) == '\n') index + 2 else index + 1
    '\n' -> index + 1
    else -> -1
}

private fun Char.isInlineBoundaryWhitespace(): Boolean =
    (isWhitespace() && this != '\r' && this != '\n') || this == '\u00A0'

private fun Char.isBoundaryWhitespace(): Boolean = isWhitespace() || this == '\u00A0'
