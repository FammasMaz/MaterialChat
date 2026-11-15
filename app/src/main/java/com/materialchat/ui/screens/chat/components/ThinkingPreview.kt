package com.materialchat.ui.screens.chat.components

/** Longest preview shown under a collapsed thinking header before the renderer ellipsizes. */
internal const val THINKING_PREVIEW_MAX_CHARS = 160

/**
 * One legible line of a model's reasoning for the collapsed thinking header.
 *
 * Takes the first non-blank line, strips leading markdown furniture (headings, list markers,
 * quotes, emphasis) so the preview reads as prose, collapses whitespace, and caps the length.
 * Returns an empty string when there is nothing readable to preview.
 */
internal fun String.thinkingPreviewLine(): String {
    val line = lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?: return ""
    val stripped = line
        .trimStart('#', '>', '-', '*', '_', '`', ' ', '\t')
        .replace(Regex("^\\d+[.)]\\s+"), "")
        .replace(Regex("[*_`]{1,3}"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    return if (stripped.length > THINKING_PREVIEW_MAX_CHARS) {
        stripped.take(THINKING_PREVIEW_MAX_CHARS).trimEnd() + "\u2026"
    } else {
        stripped
    }
}
