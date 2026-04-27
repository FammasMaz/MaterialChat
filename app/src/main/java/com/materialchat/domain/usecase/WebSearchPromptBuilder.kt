package com.materialchat.domain.usecase

import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.WebSearchConfig
import com.materialchat.domain.model.WebSearchMetadata
import com.materialchat.domain.model.WebSearchProvider
import com.materialchat.domain.repository.WebSearchRepository

internal data class WebSearchPromptContext(
    val systemPrompt: String,
    val metadata: WebSearchMetadata? = null,
    val nativeWebSearchEnabled: Boolean = false
)

internal suspend fun resolveWebSearchPromptContext(
    basePrompt: String,
    messages: List<Message>,
    webSearchConfig: WebSearchConfig,
    webSearchRepository: WebSearchRepository
): WebSearchPromptContext {
    if (!webSearchConfig.isEnabled) {
        return WebSearchPromptContext(systemPrompt = basePrompt)
    }

    val searchQuery = messages
        .lastOrNull { it.role == MessageRole.USER }
        ?.content
        ?.trim()
        .orEmpty()

    if (searchQuery.isBlank() || !shouldUseWebSearchForQuery(searchQuery)) {
        return WebSearchPromptContext(systemPrompt = basePrompt)
    }

    if (webSearchConfig.provider == WebSearchProvider.NATIVE) {
        return WebSearchPromptContext(
            systemPrompt = buildNativeWebSearchPrompt(basePrompt),
            nativeWebSearchEnabled = true
        )
    }

    val searchResult = webSearchRepository.search(searchQuery, webSearchConfig)
    val metadata = searchResult.getOrNull()
        ?: return WebSearchPromptContext(systemPrompt = basePrompt)

    if (metadata.results.isEmpty()) {
        return WebSearchPromptContext(systemPrompt = basePrompt)
    }

    return WebSearchPromptContext(
        systemPrompt = buildWebSearchAugmentedPrompt(basePrompt, metadata),
        metadata = metadata
    )
}

internal fun shouldUseWebSearchForQuery(query: String): Boolean {
    val normalized = query.trim()
    if (normalized.isBlank()) return false

    val lower = normalized.lowercase()
    if (ACKNOWLEDGEMENT_QUERY.matches(lower)) return false

    return WEB_SEARCH_TRIGGER_PATTERNS.any { it.containsMatchIn(lower) }
}

private val ACKNOWLEDGEMENT_QUERY = Regex(
    pattern = "^(thanks?|thank you|thx|ty|ok(?:ay)?|cool|great|nice|awesome|perfect|got it|yes|yeah|yep|no|nope|lol|haha)[.!?\\s]*$"
)

private val WEB_SEARCH_TRIGGER_PATTERNS = listOf(
    Regex("\\b(search|browse|look\\s*up|google|web|online|source|sources|cite|citation)\\b"),
    Regex("\\b(today|today's|tonight|right\\s+now|currently|current|latest|recent|recently|live|breaking|news)\\b"),
    Regex("\\b(score|scores|standings|table|fixture|fixtures|playoff|playoffs|qualification|qualified|weather|stock|price|prices)\\b"),
    Regex("\\b(released?|announced|launched|updated?|changelog|version|deadline|schedule)\\b"),
    Regex("\\b20(2[5-9]|3[0-9])\\b")
)

internal fun buildNativeWebSearchPrompt(basePrompt: String): String {
    val nativeSearchBlock = """
[MATERIALCHAT_NATIVE_WEB_SEARCH]
This turn appears to need current or external web information, and the proxy may expose a web_search tool.
- Use web_search for the current facts, then answer directly from the returned results.
- Avoid repeated searches unless the first result set is clearly missing the key fact.
- If the search results are insufficient, say what is missing instead of continuing to speculate.
- Cite web-backed claims inline as [1], [2], etc.
- If web_search was used, end with this compact machine-readable list so MaterialChat can render source cards:
Sources:
1. Source title — https://example.com/page
2. Another source — https://example.com/other
- Only include URLs that came from search results.
[/MATERIALCHAT_NATIVE_WEB_SEARCH]
""".trimIndent()

    return listOf(basePrompt.trim(), nativeSearchBlock)
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
}

internal fun buildWebSearchAugmentedPrompt(
    basePrompt: String,
    metadata: WebSearchMetadata
): String {
    val resultsBlock = metadata.results.joinToString("\n\n") { result ->
        val snippet = result.snippet.trim().ifBlank { "No excerpt returned; use only the title and URL for this source." }
        "[${result.index}] ${result.title}\nURL: ${result.url}\nExcerpt: $snippet"
    }

    val webSearchBlock = """
[MATERIALCHAT_WEB_SEARCH]
MaterialChat has already fetched current web results for this turn. These results are not a callable tool; they are retrieved evidence that you can read now.

HOW TO ANSWER:
- Use the numbered results below as the browsing/search evidence for this request.
- Answer directly from these results instead of saying you cannot browse, cannot search, or need a web-search tool.
- If the results are insufficient, explain what is missing and answer only what the evidence supports.
- Do not claim you searched beyond the results shown here.

CITATION RULES:
- Cite sources inline as [1], [2], etc. at the end of the sentence using that information.
- Multiple sources for one claim: [1][3].
- Cite at least one result for factual/current claims that come from the search evidence.
- Do not add a separate references list; MaterialChat shows sources in the UI.

SEARCH RESULTS:
$resultsBlock
[/MATERIALCHAT_WEB_SEARCH]
""".trimIndent()

    return listOf(basePrompt.trim(), webSearchBlock)
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
}
