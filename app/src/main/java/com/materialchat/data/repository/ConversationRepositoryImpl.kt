package com.materialchat.data.repository

import com.materialchat.data.local.database.dao.ConversationDao
import com.materialchat.data.local.database.dao.MessageDao
import com.materialchat.data.mapper.toDomain
import com.materialchat.data.mapper.toEntity
import com.materialchat.data.mapper.toConversationDomainList
import com.materialchat.data.mapper.toMessageDomainList
import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.MatchType
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageMatch
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.SearchQuery
import com.materialchat.domain.model.SearchResult
import com.materialchat.domain.repository.ConversationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [ConversationRepository] that uses Room for persistence.
 */
@Singleton
class ConversationRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) : ConversationRepository {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    // ========== Conversation Operations ==========

    override fun observeConversations(): Flow<List<Conversation>> {
        return conversationDao.getAllConversations().map { entities ->
            entities.toConversationDomainList()
        }
    }

    override suspend fun getConversation(conversationId: String): Conversation? {
        return conversationDao.getConversationById(conversationId)?.toDomain()
    }

    override fun observeConversation(conversationId: String): Flow<Conversation?> {
        return conversationDao.getConversationByIdFlow(conversationId).map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun createConversation(conversation: Conversation): String {
        conversationDao.insert(conversation.toEntity())
        return conversation.id
    }

    override suspend fun updateConversation(conversation: Conversation) {
        conversationDao.update(conversation.toEntity())
    }

    override suspend fun deleteConversation(conversationId: String) {
        // Messages are cascade deleted via Room foreign key constraint
        conversationDao.deleteById(conversationId)
    }

    override suspend fun updateConversationTitle(conversationId: String, title: String) {
        conversationDao.updateTitle(
            conversationId = conversationId,
            title = title,
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun updateConversationTitleAndIcon(
        conversationId: String,
        title: String,
        icon: String?
    ) {
        conversationDao.updateTitleAndIcon(
            conversationId = conversationId,
            title = title,
            icon = icon,
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun updateConversationModel(conversationId: String, modelName: String) {
        conversationDao.updateModel(
            conversationId = conversationId,
            modelName = modelName,
            updatedAt = System.currentTimeMillis()
        )
    }

    // ========== Message Operations ==========

    override fun observeMessages(conversationId: String): Flow<List<Message>> {
        return messageDao.getMessagesForConversation(conversationId).map { entities ->
            entities.toMessageDomainList()
        }
    }

    override suspend fun getMessages(conversationId: String): List<Message> {
        return messageDao.getMessagesForConversationOnce(conversationId).toMessageDomainList()
    }

    override suspend fun addMessage(message: Message): String {
        messageDao.insert(message.toEntity())

        // Update the conversation's updatedAt timestamp
        conversationDao.updateTimestamp(
            conversationId = message.conversationId,
            updatedAt = System.currentTimeMillis()
        )

        return message.id
    }

    override suspend fun updateMessage(message: Message) {
        messageDao.update(message.toEntity())
    }

    override suspend fun updateMessageContent(messageId: String, content: String) {
        messageDao.updateContent(messageId, content)
    }

    override suspend fun updateMessageContentWithThinking(
        messageId: String,
        content: String,
        thinkingContent: String?
    ) {
        messageDao.updateContentWithThinking(messageId, content, thinkingContent)
    }

    override suspend fun setMessageStreaming(messageId: String, isStreaming: Boolean) {
        messageDao.updateStreamingStatus(messageId, isStreaming)
    }

    override suspend fun deleteMessage(messageId: String) {
        messageDao.deleteById(messageId)
    }

    override suspend fun deleteLastMessage(conversationId: String) {
        messageDao.deleteLastMessages(conversationId, count = 1)
    }

    // ========== Export Operations ==========

    override suspend fun exportToJson(conversationId: String): String {
        val conversation = conversationDao.getConversationById(conversationId)?.toDomain()
            ?: return "{}"

        val messages = messageDao.getMessagesForConversationOnce(conversationId)
            .toMessageDomainList()
            .filter { it.role != MessageRole.SYSTEM } // Exclude system messages from export

        val exportData = ConversationExportJson(
            id = conversation.id,
            title = conversation.title,
            modelName = conversation.modelName,
            createdAt = conversation.createdAt,
            updatedAt = conversation.updatedAt,
            messages = messages.map { message ->
                MessageExportJson(
                    id = message.id,
                    role = message.role.name.lowercase(),
                    content = message.content,
                    createdAt = message.createdAt
                )
            }
        )

        return json.encodeToString(exportData)
    }

    override suspend fun exportToMarkdown(conversationId: String): String {
        val conversation = conversationDao.getConversationById(conversationId)?.toDomain()
            ?: return ""

        val messages = messageDao.getMessagesForConversationOnce(conversationId)
            .toMessageDomainList()
            .filter { it.role != MessageRole.SYSTEM } // Exclude system messages from export

        return buildString {
            // Header
            appendLine("# ${conversation.title}")
            appendLine()
            appendLine("**Model:** ${conversation.modelName}")
            appendLine("**Created:** ${formatTimestamp(conversation.createdAt)}")
            appendLine()
            appendLine("---")
            appendLine()

            // Messages
            messages.forEach { message ->
                when (message.role) {
                    MessageRole.USER -> {
                        appendLine("## 🧑 User")
                        appendLine()
                        appendLine(message.content)
                        appendLine()
                    }
                    MessageRole.ASSISTANT -> {
                        appendLine("## 🤖 Assistant")
                        appendLine()
                        appendLine(message.content)
                        appendLine()
                    }
                    MessageRole.SYSTEM -> {
                        // System messages excluded from export
                    }
                }
            }
        }
    }

    /**
     * Formats a timestamp to a human-readable date string.
     */
    private fun formatTimestamp(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return format.format(date)
    }

    // ========== Search Operations ==========

    override suspend fun searchConversations(query: SearchQuery): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val processedConversationIds = mutableSetOf<String>()

        // Step 1: Search by title first (title-first approach)
        if (query.searchInTitles) {
            val titleMatches = conversationDao.searchConversationsByTitle(query.text, query.limit)

            for (entity in titleMatches) {
                val conversation = entity.toDomain()
                processedConversationIds.add(conversation.id)

                // Get matching messages for this conversation (for context)
                val matchingMessages = if (query.searchInContent) {
                    getMatchingMessagesForConversation(conversation.id, query.text)
                } else {
                    emptyList()
                }

                val matchType = if (matchingMessages.isNotEmpty()) MatchType.BOTH else MatchType.TITLE

                results.add(
                    SearchResult(
                        conversation = conversation,
                        matchType = matchType,
                        matchingMessages = matchingMessages,
                        matchedText = conversation.title
                    )
                )
            }
        }

        // Step 2: Search in message content for conversations not already matched by title
        if (query.searchInContent && results.size < query.limit) {
            val remainingLimit = query.limit - results.size
            val contentMatches = messageDao.searchMessageContent(query.text, limit = remainingLimit * 3)
                .toMessageDomainList()

            // Group messages by conversation
            val messagesByConversation = contentMatches
                .filter { it.conversationId !in processedConversationIds }
                .groupBy { it.conversationId }

            for ((conversationId, messages) in messagesByConversation) {
                if (results.size >= query.limit) break

                val conversationEntity = conversationDao.getConversationById(conversationId) ?: continue
                val conversation = conversationEntity.toDomain()

                val matchingMessages = messages.take(3).map { message ->
                    MessageMatch(
                        message = message,
                        contextSnippet = extractContextSnippet(message.content, query.text)
                    )
                }

                results.add(
                    SearchResult(
                        conversation = conversation,
                        matchType = MatchType.CONTENT,
                        matchingMessages = matchingMessages,
                        matchedText = matchingMessages.firstOrNull()?.contextSnippet ?: ""
                    )
                )
            }
        }

        return results.take(query.limit)
    }

    /**
     * Gets matching messages for a conversation, excluding system messages.
     */
    private suspend fun getMatchingMessagesForConversation(
        conversationId: String,
        query: String
    ): List<MessageMatch> {
        return messageDao.getNonSystemMessagesForConversation(conversationId)
            .toMessageDomainList()
            .filter { it.content.contains(query, ignoreCase = true) }
            .take(3)
            .map { message ->
                MessageMatch(
                    message = message,
                    contextSnippet = extractContextSnippet(message.content, query)
                )
            }
    }

    /**
     * Extracts a context snippet around the matched text.
     */
    private fun extractContextSnippet(content: String, query: String, contextLength: Int = 50): String {
        val index = content.indexOf(query, ignoreCase = true)
        if (index == -1) return content.take(100)

        val start = maxOf(0, index - contextLength)
        val end = minOf(content.length, index + query.length + contextLength)

        return buildString {
            if (start > 0) append("...")
            append(content.substring(start, end))
            if (end < content.length) append("...")
        }
    }

    // ========== Branch Operations ==========

    override fun observeRootConversations(): Flow<List<Conversation>> {
        return conversationDao.getRootConversations().map { entities ->
            entities.toConversationDomainList()
        }
    }

    override fun observeBranches(parentId: String): Flow<List<Conversation>> {
        return conversationDao.getBranchesForConversation(parentId).map { entities ->
            entities.toConversationDomainList()
        }
    }

    override suspend fun getBranches(parentId: String): List<Conversation> {
        return conversationDao.getBranchesForConversationOnce(parentId).toConversationDomainList()
    }

    override suspend fun getBranchCount(parentId: String): Int {
        return conversationDao.getBranchCount(parentId)
    }
}

/**
 * JSON export data structure for a conversation.
 */
@Serializable
private data class ConversationExportJson(
    val id: String,
    val title: String,
    val modelName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<MessageExportJson>
)

/**
 * JSON export data structure for a message.
 */
@Serializable
private data class MessageExportJson(
    val id: String,
    val role: String,
    val content: String,
    val createdAt: Long
)
