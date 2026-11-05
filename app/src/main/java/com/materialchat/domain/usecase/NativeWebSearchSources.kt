package com.materialchat.domain.usecase

import com.materialchat.domain.model.WebSearchMetadata
import com.materialchat.domain.model.WebSearchProvider
import com.materialchat.domain.model.WebSearchResult
import java.net.URI

internal data class NativeWebSearchParseResult(
    val content: String,
    val metadata: WebSearchMetadata?
)

/**
 * Extracts the source list the model was asked to append after a native
 * proxy web search, turning it into carousel metadata.
 *
 * Recognized shapes:
 *  - A trailing "Sources" / "References" / "Citations" / "Search results"
 *    section (headings, bold "**Sources:**", plain "Sources:" all match),
 *    which is stripped from the answer and rendered as source cards.
 *  - A trailing block of bare link lines ("- [Title](url)") even without a
 *    header; the answer text is kept intact and the links become cards.
 */
internal fun extractNativeWebSearchSources(
    content: String,
    query: String
): NativeWebSearchParseResult {
    val lines = content.lines()
    val sourceHeading = lastSourceHeadingOutsideCode(lines)
    if (sourceHeading != null) {
        val sourceLines = lines.drop(sourceHeading + 1).filter { it.isNotBlank() }
        // Removing arbitrary prose here loses part of the answer. Only a tail
        // made entirely of recognizable source rows is safe to replace with cards.
        if (sourceLines.isNotEmpty() && sourceLines.all(::isSourceRow)) {
            val results = parseNativeSourceLines(sourceLines.joinToString("\n"))
            if (results.isNotEmpty()) {
                val strippedContent = lines.take(sourceHeading).joinToString("\n").trimEnd()
                return NativeWebSearchParseResult(
                    content = strippedContent.ifBlank { content },
                    metadata = nativeSearchMetadata(query, results)
                )
            }
        }
    }

    // Fallback: no recognizable header, but the answer ends with a short block
    // of link-only lines. Keep the text as-is and still surface the cards.
    val bareResults = parseTrailingBareLinkLines(content)
    if (bareResults.isNotEmpty()) {
        return NativeWebSearchParseResult(
            content = content,
            metadata = nativeSearchMetadata(query, bareResults)
        )
    }

    return NativeWebSearchParseResult(content, null)
}

private fun nativeSearchMetadata(
    query: String,
    results: List<WebSearchResult>
): WebSearchMetadata = WebSearchMetadata(
    query = query,
    provider = WebSearchProvider.NATIVE,
    results = results,
    searchDurationMs = null
)

/** Match only the heading line, never consume the answer that follows it. */
private val sourceHeadingRegex = Regex(
    pattern = "(?i)^[ \\t]*(?:#{1,6}[ \\t]*)?[*_~]{0,2}[ \\t]*" +
        "(?:sources|references|citations|web results|search results|further reading)" +
        "[ \\t]*[*_~]{0,2}[ \\t]*:?[ \\t]*[*_~]{0,2}[ \\t]*$"
)
private val codeFenceRegex = Regex("^[ \\t]{0,3}(`{3,}|~{3,})(.*)$")
private val namedSourceRowRegex = Regex(
    "^[^\\r\\n]+?[ \\t]+[—–:|-][ \\t]+<?https?://[^\\s<>]+>?[ \\t]*$"
)

private fun lastSourceHeadingOutsideCode(lines: List<String>): Int? {
    var fenceCharacter: Char? = null
    var fenceLength = 0
    var heading: Int? = null
    lines.forEachIndexed { index, line ->
        val fence = codeFenceRegex.matchEntire(line)
        if (fence != null) {
            val marker = fence.groupValues[1]
            if (fenceCharacter == null) {
                fenceCharacter = marker.first()
                fenceLength = marker.length
            } else if (marker.first() == fenceCharacter && marker.length >= fenceLength &&
                fence.groupValues[2].isBlank()
            ) {
                fenceCharacter = null
            }
        } else if (fenceCharacter == null && sourceHeadingRegex.matches(line)) {
            heading = index
        }
    }
    return heading
}

private fun isSourceRow(line: String): Boolean =
    bareLinkLineRegex.matches(line) || namedSourceRowRegex.matches(line)

private val markdownLinkRegex = Regex("\\[([^]]+)]\\((https?://[^)\\s]+)\\)")
private val urlRegex = Regex("https?://[^\\s)>,]+")
private val sourcePrefixRegex = Regex("^\\s*(?:[-*•]\\s+|\\[?\\d+]?[.)]?\\s*)")
private val titleDecorationRegex = Regex("[*_~`]+")

/** A line that consists only of an optional list prefix and a single link. */
private val bareLinkLineRegex = Regex(
    pattern = "^\\s*(?:[-*•]\\s+|\\[?\\d+]?[.)]?\\s*)" +
        "(?:\\[([^]]+)]\\((https?://[^)\\s]+)\\)|<(https?://[^>\\s]+)>|(https?://[^\\s)>,]+))" +
        "\\s*[.,;:!?]?\\s*$"
)

private fun parseNativeSourceLines(sourcesText: String): List<WebSearchResult> {
    val seenUrls = linkedSetOf<String>()
    val parsed = mutableListOf<Pair<String, String>>()

    sourcesText.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { rawLine ->
            val markdownMatch = markdownLinkRegex.find(rawLine)
            val url = markdownMatch?.groupValues?.getOrNull(2)
                ?: urlRegex.find(rawLine)?.value
                ?: return@forEach

            val normalizedUrl = url.trim().trimEnd('.', ',', ';')
            if (!seenUrls.add(normalizedUrl)) {
                return@forEach
            }

            val title = markdownMatch?.groupValues?.getOrNull(1)?.trim()
                ?: rawLine
                    .replace(url, "")
                    .replace(markdownLinkRegex, "\$1")
                    .replace(sourcePrefixRegex, "")
                    .trim(' ', '-', '—', '–', ':', '*', '_', '~', '`', '\t')
                    .ifBlank { extractDomain(normalizedUrl) ?: normalizedUrl }

            parsed += cleanTitle(title) to normalizedUrl
        }

    return parsed.mapIndexed { index, (title, url) ->
        WebSearchResult(
            index = index + 1,
            url = url,
            title = title,
            snippet = "",
            domain = extractDomain(url)
        )
    }
}

/**
 * Scans the last contiguous run of link-only lines (list items or bare URLs).
 * Returns their results when at least two such lines form the tail of the
 * message — a bare source list the model emitted without a header.
 */
private fun parseTrailingBareLinkLines(content: String): List<WebSearchResult> {
    val tailLines = content.lineSequence()
        .toList()
        .takeLastWhile { it.isBlank() || bareLinkLineRegex.containsMatchIn(it) }
        .filter { it.isNotBlank() }

    if (tailLines.size < 2) return emptyList()

    val seenUrls = linkedSetOf<String>()
    val parsed = mutableListOf<Pair<String, String>>()
    tailLines.forEach { rawLine ->
        val match = bareLinkLineRegex.find(rawLine) ?: return@forEach
        val markdownTitle = match.groups[1]?.value
        val url = match.groups[2]?.value
            ?: match.groups[3]?.value
            ?: match.groups[4]?.value
            ?: return@forEach

        val normalizedUrl = url.trim().trimEnd('.', ',', ';')
        if (!seenUrls.add(normalizedUrl)) return@forEach

        val title = markdownTitle?.trim()?.takeIf { it.isNotBlank() }
            ?: extractDomain(normalizedUrl)
            ?: normalizedUrl

        parsed += cleanTitle(title) to normalizedUrl
    }

    return parsed.mapIndexed { index, (title, url) ->
        WebSearchResult(
            index = index + 1,
            url = url,
            title = title,
            snippet = "",
            domain = extractDomain(url)
        )
    }
}

/** Strips emphasis/heading decoration that leaks into parsed titles. */
private fun cleanTitle(title: String): String =
    title.replace(titleDecorationRegex, "").trim().ifBlank { title }

private fun extractDomain(url: String): String? {
    return runCatching {
        URI(url).host?.removePrefix("www.")
    }.getOrNull()
}
