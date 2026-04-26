package com.materialchat.domain.usecase

import com.materialchat.domain.model.WebSearchMetadata
import com.materialchat.domain.model.WebSearchProvider
import com.materialchat.domain.model.WebSearchResult
import java.net.URI

internal data class NativeWebSearchParseResult(
    val content: String,
    val metadata: WebSearchMetadata?
)

internal fun extractNativeWebSearchSources(
    content: String,
    query: String
): NativeWebSearchParseResult {
    val match = sourceSectionRegex.findAll(content).lastOrNull()
        ?: return NativeWebSearchParseResult(content, null)

    val sourcesText = match.groups["items"]?.value.orEmpty()
    val results = parseNativeSourceLines(sourcesText)
    if (results.isEmpty()) {
        return NativeWebSearchParseResult(content, null)
    }

    val strippedContent = content.substring(0, match.range.first).trimEnd()
    return NativeWebSearchParseResult(
        content = strippedContent.ifBlank { content },
        metadata = WebSearchMetadata(
            query = query,
            provider = WebSearchProvider.NATIVE,
            results = results,
            searchDurationMs = null
        )
    )
}

private val sourceSectionRegex = Regex(
    pattern = "(?ims)(?:^|\\n)\\s*(?:#{1,6}\\s*)?(?:sources|references)\\s*:?\\s*\\n(?<items>.*)\\s*$"
)

private val markdownLinkRegex = Regex("\\[([^]]+)]\\((https?://[^)\\s]+)\\)")
private val urlRegex = Regex("https?://[^\\s)>,]+")
private val sourcePrefixRegex = Regex("^\\s*(?:[-*]\\s+|\\[?\\d+]?[.)]?\\s*)")

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
                    .trim(' ', '-', '—', ':')
                    .ifBlank { extractDomain(normalizedUrl) ?: normalizedUrl }

            parsed += title to normalizedUrl
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

private fun extractDomain(url: String): String? {
    return runCatching {
        URI(url).host?.removePrefix("www.")
    }.getOrNull()
}
