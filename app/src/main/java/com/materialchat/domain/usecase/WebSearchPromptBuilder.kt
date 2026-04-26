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

    if (searchQuery.isBlank()) {
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

internal fun buildNativeWebSearchPrompt(basePrompt: String): String {
    val nativeSearchBlock = """
[MATERIALCHAT_NATIVE_WEB_SEARCH]
Native web search is enabled for this turn.
- Use the available web_search/search tool when current or external information would improve the answer.
- Do not say you cannot browse if the search tool is available; search first when needed.
- In the final answer, cite web-backed claims inline with [1], [2], etc.
- End the answer with a compact Sources section in this exact shape when search results were used:
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
