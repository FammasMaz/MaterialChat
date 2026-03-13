package com.materialchat.domain.usecase

import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.WebSearchConfig
import com.materialchat.domain.model.WebSearchMetadata
import com.materialchat.domain.repository.WebSearchRepository

internal data class WebSearchPromptContext(
    val systemPrompt: String,
    val metadata: WebSearchMetadata? = null
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

    val searchResult = webSearchRepository.search(searchQuery, webSearchConfig)
    val metadata = searchResult.getOrNull()
        ?: return WebSearchPromptContext(systemPrompt = basePrompt)

    return WebSearchPromptContext(
        systemPrompt = buildWebSearchAugmentedPrompt(basePrompt, metadata),
        metadata = metadata
    )
}

internal fun buildWebSearchAugmentedPrompt(
    basePrompt: String,
    metadata: WebSearchMetadata
): String {
    val resultsBlock = metadata.results.joinToString("\n\n") { result ->
        "[${result.index}] Title: ${result.title}\n    URL: ${result.url}\n    ${result.snippet}"
    }

    val webSearchBlock = """
[MATERIALCHAT_WEB_SEARCH]
Web search results are provided below for your reference. Use them to give accurate, current answers.
TOOLING RULES:
- The user is using MaterialChat's on-device web search for this turn
- Treat the MATERIALCHAT_WEB_SEARCH block as the browsing source of truth for this request
- Do NOT call or rely on provider-hosted, server-side, or external browsing/search tools when answering from these results
- If the provided search results are insufficient, say what is missing instead of claiming you browsed elsewhere

CITATION RULES:
- Cite sources inline as [1], [2], etc. at the end of the sentence using that information
- Multiple sources for one claim: [1][3]
- You MUST cite at least one source for any factual claim from the search results
- Do NOT add a references/sources list at the end

SEARCH RESULTS:
$resultsBlock
[/MATERIALCHAT_WEB_SEARCH]
""".trimIndent()

    return listOf(basePrompt.trim(), webSearchBlock)
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
}
