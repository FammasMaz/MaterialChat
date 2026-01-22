package com.materialchat.domain.model

/**
 * Represents a search result for conversations.
 *
 * Search results are organized by conversation (title-first approach),
 * with matching messages shown as context under each conversation.
 *
 * @property conversation The conversation that matched the search
 * @property matchType How the conversation matched (by title or content)
 * @property matchingMessages Messages within the conversation that match the query (excluding system messages)
 * @property matchedText The text that was matched (title or message content snippet)
 */
data class SearchResult(
    val conversation: Conversation,
    val matchType: MatchType,
    val matchingMessages: List<MessageMatch> = emptyList(),
    val matchedText: String
)

/**
 * Represents how a conversation matched the search query.
 */
enum class MatchType {
    /**
     * The conversation title matched the search query.
     */
    TITLE,

    /**
     * Message content within the conversation matched the search query.
     */
    CONTENT,

    /**
     * Both title and content matched the search query.
     */
    BOTH
}

/**
 * Represents a message that matched within a conversation.
 *
 * @property message The matching message
 * @property contextSnippet A snippet of the message content with context around the match
 */
data class MessageMatch(
    val message: Message,
    val contextSnippet: String
)

/**
 * Search query parameters.
 *
 * @property text The search query text
 * @property searchInTitles Whether to search in conversation titles
 * @property searchInContent Whether to search in message content
 * @property limit Maximum number of results to return
 */
data class SearchQuery(
    val text: String,
    val searchInTitles: Boolean = true,
    val searchInContent: Boolean = true,
    val limit: Int = 10
)
